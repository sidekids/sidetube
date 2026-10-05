// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import xyz.steier.sidetube.core.input.KeyAction
import xyz.steier.sidetube.core.player.PlaybackModel

/**
 * Der Ring im Player nach SideUI ADR 0015 und die äußeren Tasten (ADR 0013): Mitte = Abspielen/
 * Pause, links/rechts = Video, hoch/runter = 10 s spulen, Lautstärke nicht am Ring.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidPlayerRingTest {

    private fun playing(body: suspend TestScope.(KidFixture, KidViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture()
        val vm = f.model()
        try {
            runCurrent()
            vm.activateRow(0); runCurrent()
            vm.onPlayerEvent(PlayerEventKind.State, 1); runCurrent()   // spielt
            assertThat(vm.state.value.playback?.current?.videoId).isEqualTo("a")
            f.commands.clear()
            body(f, vm)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `hoch und runter spulen 10 Sekunden und wechseln nicht das Video`() = playing { f, vm ->
        vm.onKey(KeyAction.FocusNext); runCurrent()
        vm.onKey(KeyAction.FocusPrevious); runCurrent()
        assertThat(f.commands).containsExactly(PlaybackModel.Command.SeekBy(10), PlaybackModel.Command.SeekBy(-10)).inOrder()
        assertThat(vm.state.value.playback?.current?.videoId).isEqualTo("a")
    }

    @Test fun `links und rechts wechseln das Video`() = playing { _, vm ->
        vm.onKey(KeyAction.MediaNext); runCurrent()
        assertThat(vm.state.value.playback?.current?.videoId).isEqualTo("b")
        vm.onKey(KeyAction.MediaPrevious); runCurrent()
        assertThat(vm.state.value.playback?.current?.videoId).isEqualTo("a")
    }

    @Test fun `die Mitte haelt an, keine Taste regelt die Lautstaerke`() = playing { f, vm ->
        vm.onKey(KeyAction.Select); runCurrent()
        assertThat(f.commands).containsExactly(PlaybackModel.Command.Pause)
        for (action in listOf(KeyAction.FocusNext, KeyAction.FocusPrevious, KeyAction.MediaNext, KeyAction.MediaPrevious)) {
            vm.onKey(action); runCurrent()
        }
        assertThat(f.commands.filterIsInstance<PlaybackModel.Command.SetVolume>()).isEmpty()
    }

    @Test fun `aussen unten rechts lang oeffnet das Player-Menue`() = playing { _, vm ->
        vm.onKey(KeyAction.ContextMenu); runCurrent()
        assertThat(vm.state.value.playerMenu).isNotNull()
        vm.onKey(KeyAction.ContextMenu); runCurrent()
        assertThat(vm.state.value.playerMenu).isNull()
        assertThat(vm.state.value.playback).isNotNull()
    }

    @Test fun `Zurueck kurz schliesst nur den Player`() = playing { _, vm ->
        vm.onKey(KeyAction.Back); runCurrent()
        assertThat(vm.state.value.playback).isNull()
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Home)
    }

    @Test fun `aussen oben rechts oeffnet auch aus dem Player die Suche`() = playing { _, vm ->
        vm.onKey(KeyAction.Search); runCurrent()
        assertThat(vm.state.value.playback).isNull()
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Search)
    }

    @Test fun `Zurueck lang fuehrt aus dem Player zum Start`() = playing { _, vm ->
        vm.onKey(KeyAction.Home); runCurrent()
        assertThat(vm.state.value.playback).isNull()
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Home)
    }

    @Test fun `ohne laufendes Video spult nichts`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture()
        val vm = f.model()
        try {
            runCurrent()
            vm.activateRow(0); runCurrent()   // lädt noch
            f.commands.clear()
            vm.onKey(KeyAction.FocusNext); runCurrent()
            assertThat(f.commands.filterIsInstance<PlaybackModel.Command.SeekBy>()).isEmpty()
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
