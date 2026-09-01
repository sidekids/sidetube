package io.github.degipe.youtubewhitelist.core.data.bedtime

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Quiet hours of a kid profile. Times are minutes since midnight, so time zones
 * and daylight saving never shift them.
 */
data class BedtimeSettings(
    val enabled: Boolean = true,
    val startMinutes: Int = DEFAULT_START_MINUTES,
    val endMinutes: Int = DEFAULT_END_MINUTES,
    val weekendOffsetMinutes: Int = DEFAULT_WEEKEND_OFFSET_MINUTES,
    /** Set by a parent to suspend quiet hours until this point in time. */
    val skipUntilEpochMillis: Long? = null
) {
    companion object {
        const val DEFAULT_START_MINUTES = 20 * 60
        const val DEFAULT_END_MINUTES = 6 * 60 + 30
        const val DEFAULT_WEEKEND_OFFSET_MINUTES = 60

        /** Age suggestions offered to parents. */
        val AGE_SUGGESTIONS = mapOf(
            "6-9" to 19 * 60,
            "10-12" to 20 * 60,
            "13+" to 21 * 60
        )
    }
}

sealed interface BedtimeState {
    /** Quiet hours are off or still far away. */
    data object Off : BedtimeState

    /** Quiet hours start in [minutesLeft] minutes. */
    data class Warning(val minutesLeft: Int) : BedtimeState

    /** Quiet hours are running. */
    data object Active : BedtimeState

    val isActive: Boolean get() = this is Active
}

object BedtimeEvaluator {

    /** How long before the start the kid is warned. */
    const val WARNING_LEAD_MINUTES = 15

    /** The second, more urgent warning. */
    const val WARNING_LAST_MINUTES = 5

    private const val MINUTES_PER_DAY = 24 * 60

    fun evaluate(
        settings: BedtimeSettings,
        now: LocalDateTime,
        nowEpochMillis: Long
    ): BedtimeState {
        if (!settings.enabled) return BedtimeState.Off
        settings.skipUntilEpochMillis?.let { skipUntil ->
            if (nowEpochMillis < skipUntil) return BedtimeState.Off
        }

        val nowMinutes = now.hour * 60 + now.minute
        val startToday = startFor(settings, now.dayOfWeek)
        val startYesterday = startFor(settings, now.dayOfWeek.minus(1))
        val end = settings.endMinutes

        val wrapsMidnight = end <= startToday
        val inEvening = nowMinutes >= startToday && (wrapsMidnight || nowMinutes < end)
        val inMorning = end <= startYesterday && nowMinutes < end
        if (inEvening || inMorning) return BedtimeState.Active

        val minutesUntilStart = startToday - nowMinutes
        return if (minutesUntilStart in 1..WARNING_LEAD_MINUTES) {
            BedtimeState.Warning(minutesUntilStart)
        } else {
            BedtimeState.Off
        }
    }

    /**
     * Minutes until the state can change, so a watcher can sleep instead of
     * polling. Never longer than an hour, so a changed setting is picked up.
     */
    fun minutesUntilNextChange(settings: BedtimeSettings, now: LocalDateTime): Int {
        if (!settings.enabled) return MAX_SLEEP_MINUTES

        val nowMinutes = now.hour * 60 + now.minute
        val startToday = startFor(settings, now.dayOfWeek)
        val boundaries = listOf(
            startToday - WARNING_LEAD_MINUTES,
            startToday - WARNING_LAST_MINUTES,
            startToday,
            settings.endMinutes
        )

        val next = boundaries
            .map { boundary -> Math.floorMod(boundary - nowMinutes, MINUTES_PER_DAY) }
            .filter { it > 0 }
            .minOrNull() ?: MAX_SLEEP_MINUTES

        return next.coerceIn(1, MAX_SLEEP_MINUTES)
    }

    private fun startFor(settings: BedtimeSettings, day: DayOfWeek): Int {
        val offset = if (day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY) {
            settings.weekendOffsetMinutes
        } else {
            0
        }
        return (settings.startMinutes + offset).coerceAtMost(MINUTES_PER_DAY - 1)
    }

    private const val MAX_SLEEP_MINUTES = 60
}
