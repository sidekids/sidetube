// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.repo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import xyz.steier.sidetube.core.db.WatchHistoryEntity

/**
 * Auswertung der Sehzeit fuer den Elternbereich – reine Rechnung auf den Verlaufseintraegen, wie
 * `WatchStatsCalculator` auf iOS: Zeitraum bis einschliesslich heute, jeder Tag erscheint, auch
 * ohne Sehzeit; gezaehlt wird die tatsaechlich gespielte Zeit, nicht die Laenge der Videos.
 */
data class WatchStats(
    val totalSeconds: Int = 0,
    val videoCount: Int = 0,
    val days: List<Day> = emptyList(),
    val topVideos: List<Video> = emptyList()
) {
    /** [date] ist der Kalendertag; [seconds] die darauf gebuchte Sehzeit. */
    data class Day(val date: LocalDate, val seconds: Int)
    data class Video(val videoId: String, val title: String, val seconds: Int, val plays: Int)

    val isEmpty: Boolean get() = totalSeconds == 0

    companion object {
        /** Tage je Zeitraum: heute, sieben, dreissig. */
        val PERIODS = listOf(1, 7, 30)
        const val TOP_LIMIT = 5

        fun stats(
            entries: List<WatchHistoryEntity>, periodDays: Int, now: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault(), topLimit: Int = TOP_LIMIT
        ): WatchStats {
            val today = now.atZone(zone).toLocalDate()
            val start = today.minusDays((periodDays - 1).coerceAtLeast(0).toLong())
            val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
            val until = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val inRange = entries.filter { it.watchedAt in from until until }
            val perDay = mutableMapOf<LocalDate, Int>()
            val perVideo = mutableMapOf<String, Video>()
            for (e in inRange) {
                val day = Instant.ofEpochMilli(e.watchedAt).atZone(zone).toLocalDate()
                perDay[day] = (perDay[day] ?: 0) + e.watchedSeconds
                val v = perVideo[e.videoId] ?: Video(e.videoId, e.videoTitle, 0, 0)
                perVideo[e.videoId] = v.copy(seconds = v.seconds + e.watchedSeconds, plays = v.plays + 1)
            }
            val days = (0 until periodDays).map { offset -> start.plusDays(offset.toLong()).let { Day(it, perDay[it] ?: 0) } }
            val top = perVideo.values.sortedWith(compareByDescending<Video> { it.seconds }.thenByDescending { it.title }).take(topLimit)
            return WatchStats(inRange.sumOf { it.watchedSeconds }, perVideo.size, days, top)
        }
    }
}
