// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.repo

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import xyz.steier.sidetube.core.db.WatchHistoryEntity

class WatchStatsTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(day: Int, hour: Int) = LocalDate.of(2026, 10, day).atTime(hour, 0).atZone(zone).toInstant()
    private fun entry(day: Int, hour: Int, video: String, seconds: Int, title: String = video) =
        WatchHistoryEntity(profileId = "p", videoId = video, videoTitle = title, watchedSeconds = seconds, watchedAt = at(day, hour).toEpochMilli())

    private val entries = listOf(
        entry(9, 8, "a", 300), entry(9, 17, "a", 120), entry(8, 20, "b", 600),
        entry(3, 12, "c", 60), entry(2, 23, "d", 1000)   // d liegt vor dem 7-Tage-Fenster ab dem 3.
    )
    private val now = LocalDate.of(2026, 10, 9).atTime(LocalTime.of(21, 0)).atZone(zone).toInstant()

    @Test fun `heute zaehlt nur den Kalendertag`() {
        val s = WatchStats.stats(entries, 1, now, zone)
        assertThat(s.totalSeconds).isEqualTo(420)
        assertThat(s.videoCount).isEqualTo(1)
        assertThat(s.days.map { it.seconds }).containsExactly(420)
        assertThat(s.topVideos.single().plays).isEqualTo(2)
    }

    @Test fun `sieben Tage fuehren jeden Tag auf, auch ohne Sehzeit, und ordnen die Videos nach Zeit`() {
        val s = WatchStats.stats(entries, 7, now, zone)
        assertThat(s.days).hasSize(7)
        assertThat(s.days.first().date).isEqualTo(LocalDate.of(2026, 10, 3))
        assertThat(s.days.map { it.seconds }).containsExactly(60, 0, 0, 0, 0, 600, 420).inOrder()
        assertThat(s.totalSeconds).isEqualTo(1080)
        assertThat(s.topVideos.map { it.videoId }).containsExactly("b", "a", "c").inOrder()
    }

    @Test fun `ohne Eintraege ist die Auswertung leer, hat aber die Tage`() {
        val s = WatchStats.stats(emptyList(), 30, now, zone)
        assertThat(s.isEmpty).isTrue()
        assertThat(s.days).hasSize(30)
        assertThat(s.topVideos).isEmpty()
    }

    @Test fun `ein Eintrag kurz vor Mitternacht gehoert zum richtigen Tag der Zeitzone`() {
        val spaet = listOf(entry(8, 23, "x", 10))
        val s = WatchStats.stats(spaet, 2, now, zone)
        assertThat(s.days.map { it.seconds }).containsExactly(10, 0).inOrder()
    }
}
