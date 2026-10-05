// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.player

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class SleepTimerPolicyTest {
    private val now: Instant = Instant.parse("2026-09-12T20:00:00Z")

    @Test
    fun `die Dauer wird auf den anbietbaren Bereich geklemmt`() {
        assertThat(SleepTimerPolicy.start(0, now).endsAt).isEqualTo(now.plusSeconds(60))
        assertThat(SleepTimerPolicy.start(-5, now).endsAt).isEqualTo(now.plusSeconds(60))
        assertThat(SleepTimerPolicy.start(9999, now).endsAt).isEqualTo(now.plusSeconds(180 * 60))
        assertThat(SleepTimerPolicy.start(30, now).endsAt).isEqualTo(now.plusSeconds(1800))
    }

    @Test
    fun `die Restzeit wird aufgerundet und nie negativ`() {
        val timer = SleepTimerPolicy.start(1, now)
        assertThat(SleepTimerPolicy.remainingSeconds(timer, now)).isEqualTo(60)
        assertThat(SleepTimerPolicy.remainingSeconds(timer, now.plusMillis(500))).isEqualTo(60)
        assertThat(SleepTimerPolicy.remainingSeconds(timer, now.plusSeconds(59))).isEqualTo(1)
        assertThat(SleepTimerPolicy.remainingSeconds(timer, now.plusSeconds(120))).isEqualTo(0)
        assertThat(SleepTimerPolicy.remainingSeconds(null, now)).isNull()
    }

    @Test
    fun `abgelaufen ist der Timer ab dem Endzeitpunkt, nicht erst danach`() {
        val timer = SleepTimerPolicy.start(1, now)
        assertThat(SleepTimerPolicy.hasExpired(timer, now.plusSeconds(59))).isFalse()
        assertThat(SleepTimerPolicy.hasExpired(timer, now.plusSeconds(60))).isTrue()
        assertThat(SleepTimerPolicy.hasExpired(timer, now.plusSeconds(61))).isTrue()
        assertThat(SleepTimerPolicy.hasExpired(null, now)).isFalse()
    }

    @Test
    fun `eine zurueckspringende Uhr laesst den Timer wieder laufen statt abgelaufen zu bleiben`() {
        val timer = SleepTimerPolicy.start(10, now)
        assertThat(SleepTimerPolicy.hasExpired(timer, now.plusSeconds(700))).isTrue()
        // Sommerzeitende, Zeitzonenwechsel, gestellte Uhr: Die Antwort folgt der Uhr.
        assertThat(SleepTimerPolicy.hasExpired(timer, now.plusSeconds(60))).isFalse()
    }

    @Test
    fun `ausgeblendet wird nur in der letzten Minute, linear bis auf null`() {
        val timer = SleepTimerPolicy.start(5, now)
        assertThat(SleepTimerPolicy.fadeVolume(timer, now)).isNull()
        assertThat(SleepTimerPolicy.fadeVolume(timer, now.plusSeconds(239))).isNull()
        assertThat(SleepTimerPolicy.fadeVolume(timer, now.plusSeconds(240))).isEqualTo(100)
        assertThat(SleepTimerPolicy.fadeVolume(timer, now.plusSeconds(270))).isEqualTo(50)
        assertThat(SleepTimerPolicy.fadeVolume(timer, now.plusSeconds(300))).isEqualTo(0)
        assertThat(SleepTimerPolicy.fadeVolume(null, now)).isNull()
    }

    @Test
    fun `die Restzeit wird wie auf iOS dargestellt`() {
        assertThat(SleepTimerPolicy.format(0)).isEqualTo("<1m")
        assertThat(SleepTimerPolicy.format(1)).isEqualTo("1m")
        assertThat(SleepTimerPolicy.format(60)).isEqualTo("1m")
        assertThat(SleepTimerPolicy.format(90)).isEqualTo("2m")
        assertThat(SleepTimerPolicy.format(3600)).isEqualTo("1h 0m")
        assertThat(SleepTimerPolicy.format(5400)).isEqualTo("1h 30m")
    }
}
