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
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.input.KeyAction
import xyz.steier.sidetube.core.input.RadTaste

/**
 * Die Kinder-Suche mit dem Rad: Das SP-01 hat keine Zifferntasten, nur den Ring. Angeboten wird
 * nur, womit ein freigegebener Name weitergeht.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidRadSearchTest {

    private fun fixture() = KidFixture().apply {
        items.value = listOf(
            WhitelistItemEntity("bbb", "p", "VIDEO", contentId = "bbb", title = "Big Buck Bunny",
                channelTitle = "Blender", approvalStatus = "approved"),
            WhitelistItemEntity("sin", "p", "VIDEO", contentId = "sin", title = "Sintel",
                channelTitle = "Blender", approvalStatus = "approved"),
            WhitelistItemEntity("mae", "p", "VIDEO", contentId = "mae", title = "Märchenwald",
                channelTitle = "NASA", approvalStatus = "approved"),
            // Nicht freigegeben: darf weder als Treffer noch als Buchstabe auftauchen.
            WhitelistItemEntity("pen", "p", "VIDEO", contentId = "pen", title = "Zebra Hop",
                approvalStatus = "pending")
        )
    }

    private fun mitModell(test: suspend TestScope.(KidViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = fixture().model()
        try {
            runCurrent()
            vm.onKey(KeyAction.Search)
            test(vm)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    private fun KidViewModel.felder() = state.value.rad.tasten.map {
        when (it) {
            is RadTaste.Buchstabe -> it.zeichen.wert.toString()
            RadTaste.Luecke -> "␣"
            RadTaste.Loeschen -> "⌫"
        }
    }

    /** Wie ein Kind: mit links/rechts zum Feld, dann Mitte. */
    private fun KidViewModel.rad(feld: String) {
        val ziel = felder().indexOf(feld)
        check(ziel >= 0) { "$feld nicht am Rad: ${felder()}" }
        while (state.value.rad.fokus < ziel) onKey(KeyAction.MediaNext)
        while (state.value.rad.fokus > ziel) onKey(KeyAction.MediaPrevious)
        onKey(KeyAction.Select)
    }

    /** Die Treffer; die Zeile „Wunsch an die Eltern" steht immer darunter (ADR 0001, eigener Test). */
    private fun KidViewModel.titles() = state.value.rows.filterNot { it.action.istWunschZeile }.map { it.title }

    @Test fun `das Rad bietet nur Anfaenge freigegebener Namen`() = mitModell { vm ->
        // big buck bunny blender sintel maerchenwald nasa – kein z von „Zebra Hop"
        assertThat(vm.felder()).containsExactly("b", "m", "n", "s").inOrder()
        assertThat(vm.state.value.rad.aktiv).isTrue()
        assertThat(vm.titles()).isEmpty()
    }

    @Test fun `b-u-n-n-y mit dem Rad, dann runter und Mitte`() = mitModell { vm ->
        vm.rad("b")
        assertThat(vm.titles()).containsExactly("Big Buck Bunny", "Sintel")   // Sintel über „Blender"
        assertThat(vm.felder()).containsExactly("i", "l", "u", "⌫").inOrder()
        "unny".forEach { vm.rad(it.toString()) }
        assertThat(vm.state.value.rad.anzeige).isEqualTo("bunny")
        assertThat(vm.titles()).containsExactly("Big Buck Bunny")
        assertThat(vm.felder()).containsExactly("⌫")
        // Fertig: Der Fokus springt auf den Treffer, damit die naechste Mitte abspielt statt loescht.
        assertThat(vm.state.value.rad.aktiv).isFalse()
        assertThat(vm.state.value.focusIndex).isEqualTo(0)

        vm.onKey(KeyAction.FocusPrevious)
        assertThat(vm.state.value.rad.aktiv).isTrue()
        vm.onKey(KeyAction.FocusNext)
        assertThat(vm.state.value.rad.aktiv).isFalse()
        // Zurueck loescht und holt den Fokus ins Rad.
        vm.onKey(KeyAction.Back)
        assertThat(vm.state.value.rad.anzeige).isEqualTo("bunn")
        assertThat(vm.state.value.rad.aktiv).isTrue()
    }

    @Test fun `kein Umlauf am Rand des Rades`() = mitModell { vm ->
        vm.onKey(KeyAction.MediaPrevious)
        assertThat(vm.state.value.rad.fokus).isEqualTo(0)
        repeat(10) { vm.onKey(KeyAction.MediaNext) }
        assertThat(vm.state.value.rad.fokus).isEqualTo(vm.felder().lastIndex)
    }

    @Test fun `Luecke nur nach einem fertigen Wort, Loeschen und Zurueck nehmen weg`() = mitModell { vm ->
        "big".forEach { vm.rad(it.toString()) }
        assertThat(vm.felder()).containsExactly("␣", "⌫").inOrder()
        vm.rad("␣")
        assertThat(vm.felder()).containsExactly("b", "⌫").inOrder()
        vm.rad("⌫")
        assertThat(vm.state.value.query).isEqualTo("big")
        vm.onKey(KeyAction.Back)
        assertThat(vm.state.value.query).isEqualTo("bi")
        assertThat(vm.state.value.rad.anzeige).isEqualTo("bi")
    }

    @Test fun `Zurueck lang fuehrt aus der Suche zum Start, statt ein Zeichen zu loeschen`() = mitModell { vm ->
        // Der bekannte Fehler: Langes Zurück kam als kurzes an und nahm nur einen Buchstaben weg.
        // Die Tastenseite prüft HoldKeyTest; hier die Bedeutung von „lang".
        "big".forEach { vm.rad(it.toString()) }
        vm.onKey(xyz.steier.sidetube.core.input.KeyMap.action(xyz.steier.sidetube.core.input.KeyMap.DPAD_LEFT, longPress = true)!!)
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Home)
        assertThat(vm.state.value.query).isEmpty()
    }

    @Test fun `Umlaut ist ein a am Rad, die Anzeige schreibt ihn richtig`() = mitModell { vm ->
        vm.rad("m")
        vm.rad("a")
        assertThat(vm.state.value.rad.anzeige).isEqualTo("mä")
        assertThat(vm.titles()).containsExactly("Märchenwald")
    }

    @Test fun `Antippen nimmt den Buchstaben, Tastaturzeichen gehen weiter`() = mitModell { vm ->
        vm.tapWheelKey(vm.felder().indexOf("s"))
        assertThat(vm.titles()).containsExactly("Sintel")
        vm.onKey(KeyAction.Back)
        vm.appendToQuery('M'); vm.appendToQuery('ä')
        assertThat(vm.state.value.query).isEqualTo("ma")
        assertThat(vm.titles()).containsExactly("Märchenwald")
    }

    @Test fun `Ziffern bleiben T9, das Rad ist dann leer`() = mitModell { vm ->
        "28669".forEach(vm::appendToQuery)
        assertThat(vm.state.value.rad.t9).isTrue()
        assertThat(vm.felder()).isEmpty()
        assertThat(vm.titles()).containsExactly("Big Buck Bunny")
        repeat(5) { vm.onKey(KeyAction.Back) }
        assertThat(vm.state.value.rad.t9).isFalse()
        assertThat(vm.felder()).containsExactly("b", "m", "n", "s").inOrder()
    }
}
