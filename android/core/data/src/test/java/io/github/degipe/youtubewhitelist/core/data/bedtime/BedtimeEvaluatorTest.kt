package io.github.degipe.youtubewhitelist.core.data.bedtime

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime

class BedtimeEvaluatorTest {

    private val settings = BedtimeSettings(
        enabled = true,
        startMinutes = 20 * 60,
        endMinutes = 6 * 60 + 30,
        weekendOffsetMinutes = 60
    )

    // Montag, 17.08.2026
    private fun monday(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 8, 17, hour, minute)
    private fun friday(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 8, 21, hour, minute)
    private fun saturday(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 8, 22, hour, minute)
    private fun sunday(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 8, 23, hour, minute)

    private fun evaluate(at: LocalDateTime, settings: BedtimeSettings = this.settings) =
        BedtimeEvaluator.evaluate(settings, at, at.toLocalTime().toSecondOfDay() * 1000L)

    @Test
    fun `afternoon is outside quiet hours`() {
        assertThat(evaluate(monday(16))).isEqualTo(BedtimeState.Off)
    }

    @Test
    fun `evening after the start is quiet time`() {
        assertThat(evaluate(monday(20, 1))).isEqualTo(BedtimeState.Active)
        assertThat(evaluate(monday(23, 30))).isEqualTo(BedtimeState.Active)
    }

    @Test
    fun `after midnight the window that started yesterday still holds`() {
        assertThat(evaluate(monday(1))).isEqualTo(BedtimeState.Active)
        assertThat(evaluate(monday(6, 29))).isEqualTo(BedtimeState.Active)
    }

    @Test
    fun `quiet hours end at the wake time`() {
        assertThat(evaluate(monday(6, 30))).isEqualTo(BedtimeState.Off)
        assertThat(evaluate(monday(7))).isEqualTo(BedtimeState.Off)
    }

    @Test
    fun `the kid is warned fifteen and five minutes before`() {
        assertThat(evaluate(monday(19, 45))).isEqualTo(BedtimeState.Warning(15))
        assertThat(evaluate(monday(19, 55))).isEqualTo(BedtimeState.Warning(5))
        assertThat(evaluate(monday(19, 44))).isEqualTo(BedtimeState.Off)
    }

    @Test
    fun `friday and saturday start later`() {
        assertThat(evaluate(friday(20, 30))).isEqualTo(BedtimeState.Off)
        assertThat(evaluate(friday(21, 1))).isEqualTo(BedtimeState.Active)
        assertThat(evaluate(saturday(20, 30))).isEqualTo(BedtimeState.Off)
    }

    @Test
    fun `sunday morning still belongs to the later saturday window`() {
        assertThat(evaluate(sunday(2))).isEqualTo(BedtimeState.Active)
        assertThat(evaluate(sunday(20, 1))).isEqualTo(BedtimeState.Active)
    }

    @Test
    fun `disabled quiet hours never activate`() {
        val off = settings.copy(enabled = false)
        assertThat(evaluate(monday(23), off)).isEqualTo(BedtimeState.Off)
    }

    @Test
    fun `a parent can suspend quiet hours`() {
        val at = monday(21)
        val nowMillis = 1_000_000L
        val suspended = settings.copy(skipUntilEpochMillis = nowMillis + 30 * 60_000L)

        assertThat(BedtimeEvaluator.evaluate(suspended, at, nowMillis))
            .isEqualTo(BedtimeState.Off)
        assertThat(BedtimeEvaluator.evaluate(suspended, at, nowMillis + 31 * 60_000L))
            .isEqualTo(BedtimeState.Active)
    }

    @Test
    fun `without a weekend offset every day starts at the same time`() {
        val noOffset = settings.copy(weekendOffsetMinutes = 0)
        assertThat(evaluate(friday(20, 1), noOffset)).isEqualTo(BedtimeState.Active)
    }

    @Test
    fun `the watcher sleeps until the next boundary`() {
        assertThat(BedtimeEvaluator.minutesUntilNextChange(settings, monday(12)))
            .isEqualTo(60)
        assertThat(BedtimeEvaluator.minutesUntilNextChange(settings, monday(19, 40)))
            .isEqualTo(5)
        assertThat(BedtimeEvaluator.minutesUntilNextChange(settings, monday(19, 50)))
            .isEqualTo(5)
        assertThat(BedtimeEvaluator.minutesUntilNextChange(settings, monday(20, 30)))
            .isEqualTo(60)
    }
}
