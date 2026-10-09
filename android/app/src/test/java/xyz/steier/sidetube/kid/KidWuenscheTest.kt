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
import xyz.steier.sidetube.core.db.CachedChannelVideoEntity
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.input.KeyAction
import xyz.steier.sidetube.core.input.RadTaste
import xyz.steier.sidetube.core.player.PlaybackModel
import java.time.Duration
import java.time.Instant

/**
 * Die drei Wege eines Wunsches (ADR 0001) mit denselben Tasten wie am SidePhone: Ring
 * hoch/runter (Focus), links/rechts (Media), Mitte (Select), Zurück, außen oben rechts (Search).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidWuenscheTest {
    private val jetzt = Instant.parse("2026-10-02T10:00:00Z")
    private val nasa = "UCLA_DiR1FfKNvjuUpBHmylQ"

    private fun approved(id: String, type: String, title: String, channel: String? = null, sourceChannelId: String? = null) =
        WhitelistItemEntity(id, "p", type, contentId = id, title = title, channelTitle = channel,
            sourceChannelId = sourceChannelId, approvalStatus = "approved")

    private fun fixture() = KidFixture().apply {
        items.value = listOf(
            approved(nasa, "CHANNEL", "NASA"),
            approved("bbb", "VIDEO", "Big Buck Bunny", "Blender", "UCblender")
        )
        sources.value = listOf(CuratedSourceEntity(channelId = nasa, title = "NASA", trust = "trustedSeries"))
        cached[nasa] = (1..8).map { i ->
            CachedChannelVideoEntity(nasa, "f$i", "Folge $i", "", "NASA", i,
                publishedAt = jetzt.minus(Duration.ofDays(i.toLong())).toEpochMilli())
        }
    }

    private fun test(block: suspend TestScope.(KidFixture, KidViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fx = fixture()
        val vm = fx.model(now = { jetzt })
        try {
            runCurrent()
            block(fx, vm)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    private fun KidViewModel.rows() = state.value.rows
    private fun KidViewModel.gehZu(id: String) {
        val ziel = rows().indexOfFirst { it.id == id }
        check(ziel >= 0) { "$id fehlt: ${rows().map { it.id }}" }
        while (state.value.focusIndex < ziel) onKey(KeyAction.FocusNext)
        while (state.value.focusIndex > ziel) onKey(KeyAction.FocusPrevious)
    }
    private fun KidViewModel.rad(feld: Char) {
        val felder = state.value.rad.tasten
        val ziel = felder.indexOfFirst { it is RadTaste.Buchstabe && it.zeichen.wert == feld }
        check(ziel >= 0) { "$feld nicht am Rad" }
        while (state.value.rad.fokus < ziel) onKey(KeyAction.MediaNext)
        while (state.value.rad.fokus > ziel) onKey(KeyAction.MediaPrevious)
        onKey(KeyAction.Select)
    }

    @Test fun `Startseite - gesperrte neue Folgen der Reihe, hoechstens sechs, und Meine Wuensche`() = test { _, vm ->
        val neu = vm.rows().filter { it.section == KidWunschRows(xyz.steier.sidetube.TestTexte).NEU }
        assertThat(neu.map { it.title }).containsExactly("Folge 1", "Folge 2", "Folge 3", "Folge 4", "Folge 5", "Folge 6").inOrder()
        // Nie abspielbar, nur wünschbar.
        assertThat(neu.all { it.action is KidAction.OpenNeueFolge }).isTrue()
        assertThat(vm.rows().last().title).isEqualTo("Meine Wünsche")
        assertThat(vm.rows().last().subtitle).isEqualTo("Heute noch 3 Wünsche")
    }

    @Test fun `Weg 3 - Folge oeffnen, Wuenschen, danach als gewuenscht markiert`() = test { fx, vm ->
        vm.gehZu("neu-f2")
        vm.onKey(KeyAction.Select)
        val ansicht = vm.state.value.screen as KidScreen.NeueFolgeAnsicht
        assertThat(ansicht.folge.videoId).isEqualTo("f2")
        assertThat(vm.rows().map { it.title }).containsExactly("Wünschen", "Zurück").inOrder()
        assertThat(fx.commands).isEmpty()

        vm.onKey(KeyAction.Select); runCurrent()   // „Wünschen"

        val wunsch = fx.wishes.rows.value.single()
        assertThat(wunsch.kind).isEqualTo("neueFolge")
        assertThat(wunsch.videoId).isEqualTo("f2")
        assertThat(wunsch.channelId).isEqualTo(nasa)
        assertThat(vm.state.value.hint).isEqualTo("Dein Wunsch ist bei den Eltern. Heute noch 2 Wünsche.")
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Home)
        assertThat(vm.rows().first { it.id == "neu-f2" }.subtitle).startsWith("✓ Gewünscht")
        assertThat(fx.commands.filterIsInstance<PlaybackModel.Command.Load>()).isEmpty()
        assertThat(fx.events.single().decision).isEqualTo("wished")
        // Im Verlauf steht „Kind", nicht der Name des Profils (wie iOS).
        assertThat(fx.events.single().actor).isEqualTo("Kind")
        // Den Eltern gemeldet (ADR 0005).
        assertThat(fx.gemeldet).isEqualTo(1)

        // Noch einmal: kein zweiter Wunsch.
        vm.gehZu("neu-f2"); vm.onKey(KeyAction.Select)
        assertThat(vm.rows().first().title).isEqualTo("✓ Schon gewünscht")
        vm.onKey(KeyAction.Select); runCurrent()
        assertThat(fx.wishes.rows.value).hasSize(1)
        // Die Dublette wird nicht noch einmal gemeldet.
        assertThat(fx.gemeldet).isEqualTo(1)
    }

    @Test fun `Ueber der Tagesgrenze wird nichts gemeldet`() = test { fx, vm ->
        listOf("f1", "f2", "f3", "f4").forEach { id ->
            vm.gehZu("neu-$id"); vm.onKey(KeyAction.Select)
            vm.onKey(KeyAction.Select); runCurrent()
        }
        assertThat(fx.wishes.rows.value).hasSize(3)
        assertThat(fx.gemeldet).isEqualTo(3)
    }

    @Test fun `Weg 1 - aus der Suche ohne Treffer ein Thema wuenschen`() = test { fx, vm ->
        vm.onKey(KeyAction.Search)
        // Ohne Eingabe gibt es keine Treffer: Der Wunsch ist die einzige Zeile.
        assertThat(vm.rows().map { it.id }).containsExactly(KidWunschRows.WUNSCH_ZEILE_SUCHE)
        vm.onKey(KeyAction.FocusNext)              // ▼ aus dem Rad zum Wunsch
        vm.onKey(KeyAction.Select)
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.ThemaWunsch)
        // Das freie Rad bietet alle Buchstaben, auch solche ohne Treffer.
        assertThat(vm.state.value.rad.tasten.filterIsInstance<RadTaste.Buchstabe>()).hasSize(30)
        assertThat(vm.rows().single().action).isEqualTo(KidAction.Info)

        "dino".forEach { vm.rad(it) }
        assertThat(vm.state.value.rad.anzeige).isEqualTo("dino")
        vm.onKey(KeyAction.FocusNext)              // ▼ zu „Wunsch schicken"
        assertThat(vm.rows().single().title).isEqualTo("Wunsch schicken: „Dino“")
        vm.onKey(KeyAction.Select); runCurrent()

        val wunsch = fx.wishes.rows.value.single()
        assertThat(wunsch.kind).isEqualTo("thema")
        assertThat(wunsch.topic).isEqualTo("dino")
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Search)   // zurück, woher es kam
        assertThat(vm.state.value.wuenscheHeute).isEqualTo(2)
    }

    @Test fun `Themenwunsch uebernimmt die Eingabe der Suche, Zurueck loescht erst`() = test { _, vm ->
        vm.onKey(KeyAction.Search)
        vm.rad('b'); vm.rad('i')                   // „Bi" – Big Buck Bunny
        vm.gehZuWunsch()
        vm.onKey(KeyAction.Select)
        assertThat(vm.state.value.rad.anzeige).isEqualTo("bi")
        vm.onKey(KeyAction.Back)
        assertThat(vm.state.value.rad.anzeige).isEqualTo("b")
        vm.onKey(KeyAction.Back); vm.onKey(KeyAction.Back)
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Search)
    }

    private fun KidViewModel.gehZuWunsch() {
        onKey(KeyAction.FocusNext)
        gehZu(KidWunschRows.WUNSCH_ZEILE_SUCHE)
    }

    @Test fun `Tagesgrenze - nach drei Wuenschen geht keiner mehr, ueberall sichtbar`() = test { fx, vm ->
        for (id in listOf("f1", "f2", "f3")) {
            vm.gehZu("neu-$id"); vm.onKey(KeyAction.Select); vm.onKey(KeyAction.Select); runCurrent()
        }
        assertThat(fx.wishes.rows.value).hasSize(3)
        assertThat(vm.state.value.wuenscheHeute).isEqualTo(0)
        assertThat(vm.rows().last().subtitle).startsWith("Heute keine Wünsche mehr")
        vm.gehZu("neu-f4"); vm.onKey(KeyAction.Select)
        assertThat(vm.rows().first().action).isEqualTo(KidAction.Info)
        vm.onKey(KeyAction.Select); runCurrent()
        assertThat(fx.wishes.rows.value).hasSize(3)
    }

    @Test fun `Tagesgrenze ueber Mitternacht - ohne neue Emission gehen am naechsten Tag wieder Wuensche`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fx = fixture()
        var uhr = jetzt
        val vm = fx.model(now = { uhr })
        try {
            runCurrent()
            for (id in listOf("f1", "f2", "f3")) {
                vm.gehZu("neu-$id"); vm.onKey(KeyAction.Select); vm.onKey(KeyAction.Select); runCurrent()
            }
            assertThat(vm.state.value.wuenscheHeute).isEqualTo(0)
            // Ein Tag spaeter, ohne dass sich an den Wuenschen etwas aendert (keine Room-Emission).
            uhr = jetzt.plus(Duration.ofHours(25))
            vm.gehZu("neu-f4"); vm.onKey(KeyAction.Select)
            assertThat(vm.rows().first().title).isEqualTo("Wünschen")
            vm.onKey(KeyAction.Select); runCurrent()
            assertThat(fx.wishes.rows.value).hasSize(4)
            assertThat(vm.state.value.hint).isEqualTo("Dein Wunsch ist bei den Eltern. Heute noch 2 Wünsche.")
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `langsames Speichern - ist das Kind schon woanders, bleibt es dort`() = test { fx, vm ->
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        fx.wishes.insertGate = gate
        vm.gehZu("neu-f2"); vm.onKey(KeyAction.Select)
        vm.onKey(KeyAction.Select); runCurrent()   // „Wünschen" – Speichern haengt
        vm.onKey(KeyAction.Back)                   // zurück zur Startseite …
        vm.onKey(KeyAction.Search)                 // … und weiter in die Suche
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Search)
        gate.complete(Unit); runCurrent()
        assertThat(fx.wishes.rows.value).hasSize(1)
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Search)
    }

    @Test fun `Meine Wuensche - nach Ablehnung oder Sperre der Quelle kein Bild und Titel des fremden Videos`() = test { fx, vm ->
        for (id in listOf("f2", "f3")) {
            vm.gehZu("neu-$id"); vm.onKey(KeyAction.Select); vm.onKey(KeyAction.Select); runCurrent()
        }
        val f2 = fx.wishes.rows.value.single { it.videoId == "f2" }
        val f3 = fx.wishes.rows.value.single { it.videoId == "f3" }
        fx.wishes.rows.value = fx.wishes.rows.value.map { if (it.id == f2.id) it.copy(status = "abgelehnt", parentReply = "Nicht jetzt") else it }
        runCurrent()
        vm.gehZu(KidWunschRows.MEINE_WUENSCHE); vm.onKey(KeyAction.Select); runCurrent()
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Wishes)
        val abgelehnt = vm.rows().single { it.id == "wish-" + f2.id }
        assertThat(abgelehnt.title).isEqualTo("Eine neue Folge")
        assertThat(abgelehnt.thumbnailUrl).isNull()
        assertThat(abgelehnt.subtitle).startsWith("Nicht jetzt")
        assertThat(abgelehnt.detail).isEqualTo("Eltern: „Nicht jetzt“")
        assertThat(vm.rows().single { it.id == "wish-" + f3.id }.title).isEqualTo("Folge 3")

        // Die Eltern sperren den Kanal: auch der offene Wunsch zeigt nichts Fremdes mehr.
        fx.sources.value = listOf(CuratedSourceEntity(channelId = nasa, title = "NASA", trust = "blocked"))
        runCurrent()
        val offen = vm.rows().single { it.id == "wish-" + f3.id }
        assertThat(offen.title).isEqualTo("Eine neue Folge")
        assertThat(offen.thumbnailUrl).isNull()
    }

    @Test fun `Weg 2 - Mehr davon auf der Endkarte, mit Kanal als Anlass`() = test { fx, vm ->
        vm.gehZu("bbb"); vm.onKey(KeyAction.Select); runCurrent()
        vm.onPlayerEvent(PlayerEventKind.State, 1)
        vm.onPlayerEvent(PlayerEventKind.State, 0)   // zu Ende
        vm.onKey(KeyAction.FocusNext)
        assertThat(vm.state.value.endFocus).isEqualTo(1)
        vm.onKey(KeyAction.Select); runCurrent()

        val wunsch = fx.wishes.rows.value.single()
        assertThat(wunsch.kind).isEqualTo("mehrDavon")
        assertThat(wunsch.videoId).isEqualTo("bbb")
        assertThat(wunsch.channelId).isEqualTo("UCblender")
        assertThat(vm.state.value.mehrDavon).isEqualTo(WunschMoeglich.SCHON_GEWUENSCHT)
        // Geht nicht mehr: hoch/runter überspringt den Knopf.
        assertThat(vm.state.value.endFocus).isEqualTo(2)
        vm.onKey(KeyAction.FocusPrevious)
        assertThat(vm.state.value.endFocus).isEqualTo(0)
        vm.onKey(KeyAction.FocusNext)
        assertThat(vm.state.value.endFocus).isEqualTo(2)
    }

    @Test fun `Weg 2 - Player-Menue mit aussen unten rechts lang, haelt an und spielt weiter`() = test { fx, vm ->
        // Seit SideUI ADR 0013: oben rechts ist überall die Suche, das Menü liegt auf unten rechts lang.
        vm.gehZu("bbb"); vm.onKey(KeyAction.Select); runCurrent()
        vm.onPlayerEvent(PlayerEventKind.State, 1)
        fx.commands.clear()
        vm.onKey(KeyAction.ContextMenu)
        assertThat(vm.state.value.playerMenu).isEqualTo(0)
        assertThat(fx.commands).containsExactly(PlaybackModel.Command.Pause)
        vm.onKey(KeyAction.Select); runCurrent()   // „Mehr davon wünschen"
        assertThat(fx.wishes.rows.value.single().kind).isEqualTo("mehrDavon")
        assertThat(vm.state.value.playerMenu).isNull()
        assertThat(fx.commands).containsExactly(PlaybackModel.Command.Pause, PlaybackModel.Command.Resume).inOrder()

        // Zweites Öffnen: „Mehr davon" geht nicht mehr, der Fokus steht auf „Weiterschauen"; Zurück schließt.
        vm.onKey(KeyAction.ContextMenu)
        assertThat(vm.state.value.playerMenu).isEqualTo(1)
        vm.onKey(KeyAction.FocusPrevious)
        assertThat(vm.state.value.playerMenu).isEqualTo(1)
        vm.onKey(KeyAction.Back)
        assertThat(vm.state.value.playerMenu).isNull()
        assertThat(vm.state.value.playback).isNotNull()   // nur das Menü, nicht der Player
    }

    @Test fun `Meine Wuensche - Antwort der Eltern sichtbar, erfuellter Wunsch fuehrt zum Video`() = test { fx, vm ->
        fx.wishes.insert(xyz.steier.sidetube.core.db.WishEntity("w1", "p", "thema", "thema:dinos", topic = "Dinos",
            status = "abgelehnt", parentReply = "Am Wochenende zusammen", createdAt = 1))
        fx.wishes.insert(xyz.steier.sidetube.core.db.WishEntity("w2", "p", "mehrDavon", "mehrDavon:bbb", videoId = "bbb",
            videoTitle = "Big Buck Bunny", status = "erfuellt", resultContentId = "bbb", createdAt = 2))
        runCurrent()
        vm.gehZu(KidWunschRows.MEINE_WUENSCHE); vm.onKey(KeyAction.Select)
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Wishes)
        val zeilen = vm.rows()
        assertThat(zeilen.map { it.title }).containsExactly("Etwas wünschen", "Big Buck Bunny", "„Dinos“").inOrder()
        assertThat(zeilen[2].subtitle).startsWith("Nicht jetzt")
        assertThat(zeilen[2].detail).isEqualTo("Eltern: „Am Wochenende zusammen“")
        assertThat(zeilen[1].subtitle).startsWith("Freigegeben ▶")

        vm.gehZu("wish-w2"); vm.onKey(KeyAction.Select); runCurrent()
        assertThat(vm.state.value.playback?.queue?.map { it.videoId }).containsExactly("bbb")
    }

    @Test fun `ein anderes Profil sieht diese Wuensche nicht`() = test { fx, vm ->
        fx.wishes.insert(xyz.steier.sidetube.core.db.WishEntity("w1", "anderes", "thema", "thema:dinos", topic = "Dinos", createdAt = 1))
        runCurrent()
        vm.gehZu(KidWunschRows.MEINE_WUENSCHE); vm.onKey(KeyAction.Select)
        assertThat(vm.rows().map { it.title }).containsExactly("Etwas wünschen")
        assertThat(vm.state.value.wuenscheHeute).isEqualTo(3)
    }
}
