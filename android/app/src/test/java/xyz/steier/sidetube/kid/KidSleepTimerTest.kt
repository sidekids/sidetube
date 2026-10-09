// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import xyz.steier.sidetube.core.player.PlaybackModel

@OptIn(ExperimentalCoroutinesApi::class)
class KidSleepTimerTest {
    private var clock: Instant = Instant.parse("2026-09-12T19:00:00Z")

    /**
     * Die Fristen des ViewModels laufen in virtueller Zeit, seine Entscheidungen an der gestellten
     * Uhr. Beide muessen zusammen bewegt werden, sonst laufen sie auseinander.
     */
    private fun TestScope.jump(seconds: Long) {
        clock = clock.plusSeconds(seconds)
        advanceTimeBy(seconds * 1000)
        runCurrent()
    }

    /** Sekundenweise, damit die Ausblendschleife jeden Takt wirklich durchlaeuft. */
    private fun TestScope.tick(seconds: Int) = repeat(seconds) { jump(1) }

    /** Jeder Fall startet mit laufender Wiedergabe. */
    private fun playing(body: suspend TestScope.(KidFixture, KidViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture()
        val vm = f.model { clock }
        try {
            runCurrent()
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNotNull()
            body(f, vm)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `der abgelaufene Schlaf-Timer beendet die Wiedergabe und meldet gute Nacht`() =
        playing { _, vm ->
            vm.startSleepTimer(2)
            jump(60)          // bis zum Beginn der Ausblendung
            assertThat(vm.state.value.playback).isNotNull()
            tick(60)          // durch die Ausblendung bis zum Ablauf
            assertThat(vm.state.value.playback).isNull()
            assertThat(vm.state.value.sleepRemainingSeconds).isEqualTo(0)
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.GUTE_NACHT)
        }

    @Test fun `in der letzten Minute wird die Lautstaerke schrittweise abgesenkt`() =
        playing { f, vm ->
            vm.startSleepTimer(2)
            jump(60); tick(60)
            val volumes = f.commands.filterIsInstance<PlaybackModel.Command.SetVolume>().map { it.percent }
            assertThat(volumes).isNotEmpty()
            assertThat(volumes.first()).isEqualTo(100)
            assertThat(volumes.last()).isLessThan(volumes.first())
            assertThat(volumes).isInOrder(compareByDescending<Int> { it })
        }

    @Test fun `nach dem Ablauf laesst sich kein neues Video starten`() =
        playing { _, vm ->
            vm.startSleepTimer(2)
            jump(60); tick(60)
            assertThat(vm.state.value.playback).isNull()

            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNull()
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.GUTE_NACHT)
        }

    @Test fun `die Eltern koennen den Timer aufheben, danach laeuft die Wiedergabe weiter`() =
        playing { _, vm ->
            vm.startSleepTimer(2)
            jump(30)
            vm.stopSleepTimer()
            assertThat(vm.state.value.sleepRemainingSeconds).isNull()
            jump(300)
            assertThat(vm.state.value.playback).isNotNull()
        }

    @Test fun `eine zurueckgestellte Uhr beendet die Sitzung nicht vorzeitig`() =
        playing { _, vm ->
            vm.startSleepTimer(2)
            jump(60); tick(30)
            assertThat(vm.state.value.playback).isNotNull()

            // Sommerzeitende oder gestellte Uhr: Der Ablauf richtet sich nach der Uhr, nicht nach
            // einem gesetzten Merker - der Timer laeuft dadurch wieder laenger.
            clock = clock.minusSeconds(120)
            tick(30)
            assertThat(vm.state.value.playback).isNotNull()
        }
}
