// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.player

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import xyz.steier.sidetube.core.db.KidProfileEntity

/** Was gerade gilt - dieselben drei Zustaende wie auf iOS. */
sealed interface BedtimeState {
    data object Off : BedtimeState
    /** Die Ruhezeit beginnt in [minutesLeft] Minuten. */
    data class Warning(val minutesLeft: Int) : BedtimeState
    data object Active : BedtimeState
}

/** Local wall-clock rules matching iOS: Friday/Saturday start later, end is exclusive. */
object BedtimePolicy {
    /** Vorwarnung vor dem Beginn. */
    const val WARNING_LEAD_MINUTES = 15
    /** Zweite, dringlichere Warnung. */
    const val WARNING_LAST_MINUTES = 5

    private fun valid(profile: KidProfileEntity) =
        profile.bedtimeStartMinutes in 0..1439 && profile.bedtimeEndMinutes in 0..1439 &&
            profile.bedtimeWeekendOffsetMinutes in 0..120

    private fun start(profile: KidProfileEntity, date: LocalDate): Int {
        val weekend = date.dayOfWeek == DayOfWeek.FRIDAY || date.dayOfWeek == DayOfWeek.SATURDAY
        return (profile.bedtimeStartMinutes + if (weekend) profile.bedtimeWeekendOffsetMinutes else 0)
            .coerceAtMost(1439)
    }

    fun isActive(profile: KidProfileEntity, now: Instant, zone: ZoneId): Boolean =
        state(profile, now, zone) == BedtimeState.Active

    fun state(profile: KidProfileEntity, now: Instant, zone: ZoneId): BedtimeState {
        if (!profile.bedtimeEnabled) return BedtimeState.Off
        // Corrupt/imported settings must not silently disable parental protection.
        if (!valid(profile)) return BedtimeState.Active
        if (profile.bedtimeSkipUntil?.let { now.toEpochMilli() < it } == true) return BedtimeState.Off
        val local = now.atZone(zone)
        val minutes = local.hour * 60 + local.minute
        val today = start(profile, local.toLocalDate())
        val yesterday = start(profile, local.toLocalDate().minusDays(1))
        val end = profile.bedtimeEndMinutes
        val active = (minutes >= today && (end <= today || minutes < end)) ||
            (end <= yesterday && minutes < end)
        if (active) return BedtimeState.Active
        val untilStart = today - minutes
        return if (untilStart in 1..WARNING_LEAD_MINUTES) BedtimeState.Warning(untilStart)
        else BedtimeState.Off
    }

    /**
     * Ende der laufenden Ruhezeit - Grundlage fuer eine Ausnahme durch die Eltern. `null`, wenn
     * gerade keine laeuft. Die Minuten werden auf den Tagesbeginn addiert statt als Uhrzeit
     * gesetzt, damit auch unplausible gespeicherte Werte keinen Absturz ausloesen.
     */
    fun endOfCurrentWindow(profile: KidProfileEntity, now: Instant, zone: ZoneId): Instant? {
        // Kaputte Einstellungen sperren zwar (fail closed), taugen aber nicht als Grundlage einer
        // Ausnahme: Ein Endwert von 99999 Minuten ergaebe eine Ausnahme ueber Wochen. Die Eltern
        // muessen die Zeiten erst richtigstellen.
        if (!valid(profile)) return null
        if (state(profile, now, zone) != BedtimeState.Active) return null
        val day = now.atZone(zone).toLocalDate()
        val minutes = profile.bedtimeEndMinutes.toLong()
        val todayEnd = day.atStartOfDay(zone).plusMinutes(minutes).toInstant()
        return if (todayEnd.isAfter(now)) todayEnd
        else day.plusDays(1).atStartOfDay(zone).plusMinutes(minutes).toInstant()
    }

    /** Exact next boundary, including DST discontinuities; no periodic polling is needed. */
    fun nextBoundary(profile: KidProfileEntity, now: Instant, zone: ZoneId): Instant? {
        if (!profile.bedtimeEnabled || !valid(profile)) return null
        val date = now.atZone(zone).toLocalDate()
        val candidates = mutableListOf<Instant>()
        for (day in listOf(date, date.plusDays(1))) {
            val beginning = start(profile, day)
            // Auch die Warnschwellen sind Grenzen: Ohne sie kaeme die Vorwarnung nie an.
            val minutes = listOf(
                0, beginning - WARNING_LEAD_MINUTES, beginning - WARNING_LAST_MINUTES,
                beginning, profile.bedtimeEndMinutes
            ).filter { it >= 0 }
            for (minute in minutes) {
                val time = day.atTime(minute / 60, minute % 60).atZone(zone)
                candidates += time.withEarlierOffsetAtOverlap().toInstant()
                candidates += time.withLaterOffsetAtOverlap().toInstant()
            }
        }
        profile.bedtimeSkipUntil?.let { candidates += Instant.ofEpochMilli(it) }
        zone.rules.nextTransition(now)?.let { candidates += it.instant }
        return candidates.filter { it > now }.minOrNull()
    }
}
