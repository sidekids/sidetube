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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.input.KeyAction

/**
 * Die Kinder-Suche am SidePhone: Zifferntasten sind T9, gesucht wird nur im Freigegebenen.
 * Vorher landeten die Ziffern woertlich im Suchfeld und fanden nichts (BEFUNDE S1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidT9SearchTest {

    private fun fixture() = KidFixture().apply {
        items.value = listOf(
            WhitelistItemEntity("bbb", "p", "VIDEO", contentId = "bbb", title = "Big Buck Bunny",
                channelTitle = "Blender", approvalStatus = "approved"),
            WhitelistItemEntity("sin", "p", "VIDEO", contentId = "sin", title = "Sintel",
                channelTitle = "Blender", approvalStatus = "approved"),
            WhitelistItemEntity("nas", "p", "VIDEO", contentId = "nas", title = "Mondlandung",
                channelTitle = "NASA", approvalStatus = "approved"),
            // Nicht freigegeben: darf auch per T9 nie auftauchen.
            WhitelistItemEntity("pen", "p", "VIDEO", contentId = "pen", title = "Bunny Hop",
                approvalStatus = "pending")
        )
    }

    private fun KidViewModel.tippe(ziffern: String) = ziffern.forEach(::appendToQuery)
    /** Die Treffer; die Zeile „Wunsch an die Eltern" steht immer darunter (ADR 0001, eigener Test). */
    private fun KidViewModel.titles() = state.value.rows.filterNot { it.action.istWunschZeile }.map { it.title }

    @Test fun `bunny als 28669 findet Big Buck Bunny und sonst nichts`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = fixture().model()
        try {
            runCurrent()
            vm.onKey(KeyAction.Search)
            vm.tippe("28669")
            assertThat(vm.state.value.query).isEqualTo("28669")
            assertThat(vm.titles()).containsExactly("Big Buck Bunny")
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `schon eine Ziffer sucht, auch ueber den Kanalnamen`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = fixture().model()
        try {
            runCurrent()
            vm.onKey(KeyAction.Search)
            vm.tippe("2")   // Big/Buck/Bunny – und „Blender" als Kanal von Sintel
            assertThat(vm.titles()).containsExactly("Big Buck Bunny", "Sintel")
            vm.onKey(KeyAction.Back)
            vm.tippe("627")  // n-a-s: nur der Kanal NASA
            assertThat(vm.titles()).containsExactly("Mondlandung")
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `Zurueck loescht die letzte Ziffer, leer verlaesst es die Suche`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = fixture().model()
        try {
            runCurrent()
            vm.onKey(KeyAction.Search)
            vm.tippe("999")
            assertThat(vm.titles()).isEmpty()
            vm.onKey(KeyAction.Back); vm.onKey(KeyAction.Back)
            assertThat(vm.state.value.query).isEqualTo("9")
            assertThat(vm.state.value.screen).isEqualTo(KidScreen.Search)
            vm.onKey(KeyAction.Back)
            assertThat(vm.state.value.query).isEmpty()
            assertThat(vm.state.value.screen).isEqualTo(KidScreen.Search)
            vm.onKey(KeyAction.Back)
            assertThat(vm.state.value.screen).isEqualTo(KidScreen.Home)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `Buchstaben suchen weiter als Text`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = fixture().model()
        try {
            runCurrent()
            vm.onKey(KeyAction.Search)
            vm.setQuery("bunny")
            assertThat(vm.titles()).containsExactly("Big Buck Bunny")
            vm.setQuery("blender")
            assertThat(vm.titles()).containsExactly("Big Buck Bunny", "Sintel")
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
