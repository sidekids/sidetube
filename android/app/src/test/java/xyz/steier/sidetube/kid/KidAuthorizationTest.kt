// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import xyz.steier.sidetube.core.curation.*
import xyz.steier.sidetube.core.db.*
import xyz.steier.sidetube.core.input.KeyAction
import xyz.steier.sidetube.core.net.*
import xyz.steier.sidetube.core.player.PlaybackModel
import xyz.steier.sidetube.core.provider.ChannelFeedSource
import xyz.steier.sidetube.core.repo.*

@OptIn(ExperimentalCoroutinesApi::class)
class KidAuthorizationTest {

    @Test fun `home and search playback stop when any queued approval is revoked`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            for (search in listOf(false, true)) for (playerState in listOf<Int?>(null, 1, 2)) {
                val f = KidFixture(); val vm = f.model()
                try {
                    runCurrent()
                    if (search) { vm.onKey(KeyAction.Search); vm.setQuery("Video") }
                    vm.activateRow(0); runCurrent()
                    if (playerState != null) { vm.onPlayerEvent(PlayerEventKind.State, playerState); runCurrent() }
                    assertThat(vm.state.value.playback).isNotNull()
                    f.rejectB(); runCurrent()
                    assertThat(vm.state.value.playback).isNull()
                    assertThat(f.commands.last()).isEqualTo(PlaybackModel.Command.Stop)
                    val count = f.commands.size
                    vm.onPlayerEvent(PlayerEventKind.State, 1)
                    assertThat(f.commands).hasSize(count)
                    assertThat(f.markers).isEmpty()
                } finally { vm.viewModelScope.cancel() }
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `source emission cancels a start waiting on the budget read`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture(); val vm = f.model()
        try {
            runCurrent(); f.readStarted = false; f.readGate = CompletableDeferred()
            vm.activateRow(0); runCurrent()
            assertThat(f.readStarted).isTrue()
            f.sources.value = listOf(CuratedSourceEntity("unrelated", title = "Changed policy"))
            runCurrent(); f.readGate!!.complete(Unit); runCurrent()
            assertThat(vm.state.value.playback).isNull()
            assertThat(f.commands).isEmpty()
            assertThat(f.beginStarted).isFalse()
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `revocation during committed admission leaves recovery marker but never loads`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture(); val vm = f.model()
        try {
            runCurrent(); f.beginGate = CompletableDeferred()
            vm.activateRow(0); runCurrent()
            assertThat(f.beginStarted).isTrue()
            f.rejectB(); runCurrent()
            f.beginGate!!.complete(Unit); runCurrent()
            assertThat(vm.state.value.playback).isNull()
            assertThat(f.commands).isEmpty()
            assertThat(f.markers).containsKey("p")
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNull()
            assertThat(f.commands).isEmpty()
        } finally { f.beginGate?.complete(Unit); vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
