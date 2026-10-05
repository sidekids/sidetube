// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import xyz.steier.sidetube.core.db.KidProfileEntity

class BedtimePolicyTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val profile = KidProfileEntity(id = "example", name = "Example")
    private fun instant(local: String) = ZonedDateTime.parse(local + "[Europe/Berlin]").toInstant()

    @Test fun `overnight window includes start and excludes end`() {
        for ((time, active) in listOf(
            "2026-08-31T19:59:59+02:00" to false,
            "2026-08-31T20:00:00+02:00" to true,
            "2026-09-01T00:00:00+02:00" to true,
            "2026-09-01T06:29:59+02:00" to true,
            "2026-09-01T06:30:00+02:00" to false
        )) assertThat(BedtimePolicy.isActive(profile, instant(time), zone)).isEqualTo(active)
    }

    @Test fun `only Friday and Saturday shift later including their following mornings`() {
        for ((day, active) in listOf(28 to false, 29 to false, 30 to true)) {
            assertThat(BedtimePolicy.isActive(profile, instant("2026-08-${day}T20:30:00+02:00"), zone)).isEqualTo(active)
        }
        assertThat(BedtimePolicy.isActive(profile, instant("2026-08-28T21:00:00+02:00"), zone)).isTrue()
        assertThat(BedtimePolicy.isActive(profile, instant("2026-08-29T06:00:00+02:00"), zone)).isTrue()
    }

    @Test fun `parent exception expires exactly at its stored timestamp`() {
        val end = instant("2026-08-31T22:30:00+02:00")
        val exception = profile.copy(bedtimeSkipUntil = end.toEpochMilli())
        assertThat(BedtimePolicy.isActive(exception, end.minusMillis(1), zone)).isFalse()
        assertThat(BedtimePolicy.isActive(exception, end, zone)).isTrue()
        assertThat(BedtimePolicy.nextBoundary(exception, end.minusSeconds(30), zone)).isEqualTo(end)
    }

    @Test fun `disabled policy has neither blocking nor a deadline`() {
        val disabled = profile.copy(bedtimeEnabled = false)
        val now = instant("2026-08-31T23:00:00+02:00")
        assertThat(BedtimePolicy.isActive(disabled, now, zone)).isFalse()
        assertThat(BedtimePolicy.nextBoundary(disabled, now, zone)).isNull()
    }

    @Test fun `daytime window and equal boundaries follow iOS semantics`() {
        val daytime = profile.copy(bedtimeStartMinutes = 12 * 60, bedtimeEndMinutes = 14 * 60)
        assertThat(BedtimePolicy.isActive(daytime, instant("2026-08-31T13:00:00+02:00"), zone)).isTrue()
        assertThat(BedtimePolicy.isActive(daytime, instant("2026-08-31T14:00:00+02:00"), zone)).isFalse()
        val fullDay = daytime.copy(bedtimeEndMinutes = 12 * 60)
        assertThat(BedtimePolicy.isActive(fullDay, instant("2026-08-31T10:00:00+02:00"), zone)).isTrue()
    }

    @Test fun `invalid enabled settings fail closed`() {
        val now = instant("2026-08-31T12:00:00+02:00")
        for (bad in listOf(profile.copy(bedtimeStartMinutes = -1), profile.copy(bedtimeEndMinutes = 1440),
            profile.copy(bedtimeWeekendOffsetMinutes = Int.MAX_VALUE))) {
            assertThat(BedtimePolicy.isActive(bad, now, zone)).isTrue()
        }
    }

    @Test fun `deadline is exact rather than a rounded polling interval`() {
        val now = instant("2026-08-31T19:59:59+02:00").plusMillis(500)
        assertThat(BedtimePolicy.nextBoundary(profile, now, zone)).isEqualTo(now.plusMillis(500))
        assertThat(BedtimePolicy.nextBoundary(profile, instant("2026-08-31T23:59:59+02:00"), zone))
            .isEqualTo(instant("2026-09-01T00:00:00+02:00"))
    }

    @Test fun `DST spring gap and autumn repeated hour produce timely reevaluation`() {
        val custom = profile.copy(bedtimeStartMinutes = 150, bedtimeEndMinutes = 240, bedtimeWeekendOffsetMinutes = 0)
        val spring = instant("2026-03-29T01:59:59+01:00")
        assertThat(BedtimePolicy.nextBoundary(custom, spring, zone)).isEqualTo(spring.plusSeconds(1))
        assertThat(BedtimePolicy.isActive(custom, spring.plusSeconds(1), zone)).isTrue()
        val autumn = instant("2026-10-25T02:59:59+02:00")
        assertThat(BedtimePolicy.nextBoundary(custom, autumn, zone)).isEqualTo(autumn.plusSeconds(1))
        assertThat(BedtimePolicy.isActive(custom, autumn.plusSeconds(1), zone)).isFalse()
        // 02:15 statt 02:30: Die Vorwarnung ist selbst eine Grenze und liegt davor.
        assertThat(BedtimePolicy.nextBoundary(custom, autumn.plusSeconds(1), zone))
            .isEqualTo(instant("2026-10-25T02:15:00+01:00"))
    }

    @Test fun `evaluation uses supplied local zone rather than a fixed UTC offset`() {
        val now = Instant.parse("2026-08-31T18:30:00Z")
        assertThat(BedtimePolicy.isActive(profile, now, zone)).isTrue()
        assertThat(BedtimePolicy.isActive(profile, now, ZoneId.of("America/New_York"))).isFalse()
    }

    @Test fun `vor dem Beginn wird gewarnt, danach gilt die Ruhezeit`() {
        // Beginn 20:00, Vorwarnung ab 19:45.
        assertThat(BedtimePolicy.state(profile, instant("2026-08-31T19:44:00+02:00"), zone))
            .isEqualTo(BedtimeState.Off)
        assertThat(BedtimePolicy.state(profile, instant("2026-08-31T19:45:00+02:00"), zone))
            .isEqualTo(BedtimeState.Warning(15))
        assertThat(BedtimePolicy.state(profile, instant("2026-08-31T19:55:00+02:00"), zone))
            .isEqualTo(BedtimeState.Warning(5))
        assertThat(BedtimePolicy.state(profile, instant("2026-08-31T20:00:00+02:00"), zone))
            .isEqualTo(BedtimeState.Active)
    }

    @Test fun `eine Elternausnahme schaltet auch die Warnung ab`() {
        val until = instant("2026-08-31T21:00:00+02:00").toEpochMilli()
        val skipping = profile.copy(bedtimeSkipUntil = until)
        assertThat(BedtimePolicy.state(skipping, instant("2026-08-31T19:50:00+02:00"), zone))
            .isEqualTo(BedtimeState.Off)
        assertThat(BedtimePolicy.state(skipping, instant("2026-08-31T20:30:00+02:00"), zone))
            .isEqualTo(BedtimeState.Off)
        // Nach Ablauf der Ausnahme greift die Ruhezeit wieder.
        assertThat(BedtimePolicy.state(skipping, instant("2026-08-31T21:00:00+02:00"), zone))
            .isEqualTo(BedtimeState.Active)
    }

    @Test fun `das Fensterende ist die Grundlage der Elternausnahme`() {
        // Ruhezeit 20:00-06:30: abends endet sie am naechsten Morgen.
        assertThat(BedtimePolicy.endOfCurrentWindow(profile, instant("2026-08-31T21:00:00+02:00"), zone))
            .isEqualTo(instant("2026-09-01T06:30:00+02:00"))
        // Morgens innerhalb des Fensters endet sie noch am selben Tag.
        assertThat(BedtimePolicy.endOfCurrentWindow(profile, instant("2026-09-01T05:00:00+02:00"), zone))
            .isEqualTo(instant("2026-09-01T06:30:00+02:00"))
        // Ausserhalb der Ruhezeit gibt es nichts auszusetzen.
        assertThat(BedtimePolicy.endOfCurrentWindow(profile, instant("2026-08-31T12:00:00+02:00"), zone))
            .isNull()
    }

    @Test fun `unplausible gespeicherte Werte sperren, ohne abzustuerzen`() {
        val corrupt = profile.copy(bedtimeEndMinutes = 99999)
        assertThat(BedtimePolicy.state(corrupt, instant("2026-08-31T12:00:00+02:00"), zone))
            .isEqualTo(BedtimeState.Active)
        // Gesperrt wird trotzdem - aber eine Ausnahme darauf zu stuetzen ergaebe ein Fensterende
        // Wochen spaeter. Erst die Zeiten richtigstellen.
        assertThat(BedtimePolicy.endOfCurrentWindow(corrupt, instant("2026-08-31T12:00:00+02:00"), zone))
            .isNull()
    }
}
