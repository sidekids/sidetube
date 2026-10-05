// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import xyz.steier.sidetube.core.db.KidProfileEntity

@OptIn(ExperimentalCoroutinesApi::class)
class KidBedtimeWarningTest {
    /** Ein Montag: kein Wochenend-Versatz auf den Beginn. */
    private fun at(hour: Int, minute: Int): Instant =
        LocalDate.of(2026, 9, 14).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant()

    private var clock: Instant = at(19, 44)

    /** Ruhezeit 20:00-06:30, wie die Vorgabe; Tageslimit hier ohne Belang. */
    private val profile = KidProfileEntity("p", "Synthetic", bedtimeEnabled = true)

    private fun TestScope.jump(seconds: Long) {
        clock = clock.plusSeconds(seconds)
        advanceTimeBy(seconds * 1000)
        runCurrent()
    }

    private fun playing(body: suspend TestScope.(KidFixture, KidViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture(profile)
        val vm = f.model { clock }
        try {
            runCurrent()
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNotNull()
            body(f, vm)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `vor der Ruhezeit wird zweimal gewarnt, dann endet die Wiedergabe`() = playing { _, vm ->
        jump(60)          // 19:45 - erste Schwelle
        assertThat(vm.state.value.hint).isEqualTo("In 15 Minuten beginnt die Ruhezeit.")
        assertThat(vm.state.value.playback).isNotNull()

        vm.clearHint()
        jump(10 * 60)     // 19:55 - zweite Schwelle
        assertThat(vm.state.value.hint).isEqualTo("In 5 Minuten beginnt die Ruhezeit.")
        assertThat(vm.state.value.playback).isNotNull()

        jump(5 * 60)      // 20:00 - Beginn
        assertThat(vm.state.value.playback).isNull()
        assertThat(vm.state.value.hint).contains("Ruhezeit")
    }

    @Test fun `waehrend einer Elternausnahme wird weder gewarnt noch beendet`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val until = at(23, 0).toEpochMilli()
        val f = KidFixture(profile.copy(bedtimeSkipUntil = until))
        val vm = f.model { clock }
        try {
            runCurrent()
            vm.activateRow(0); runCurrent()
            jump(60); jump(10 * 60); jump(5 * 60)   // ueber beide Schwellen und den Beginn hinaus
            assertThat(vm.state.value.playback).isNotNull()
            assertThat(vm.state.value.hint).isNull()
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
