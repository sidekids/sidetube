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
import xyz.steier.sidetube.core.db.PlaybackSessionEntity
import xyz.steier.sidetube.core.input.KeyAction

/**
 * Die Vollbild-Sperre des Kindermodus (iOS: `KidOverlay`): Sie ersetzt den wegtippbaren Hinweis.
 * Nur die PIN der Eltern fuehrt heraus; Regeln, die nicht mehr greifen, heben sie von selbst auf.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidSperreTest {
    /** Ein Montag: kein Wochenend-Versatz auf den Beginn der Ruhezeit (20:00-06:30). */
    private fun at(hour: Int, minute: Int, day: Int = 14): Instant =
        LocalDate.of(2026, 9, day).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant()

    private var clock: Instant = at(19, 0)

    private fun TestScope.jump(seconds: Long) {
        clock = clock.plusSeconds(seconds)
        advanceTimeBy(seconds * 1000)
        runCurrent()
    }

    private fun TestScope.tick(seconds: Int) = repeat(seconds) { jump(1) }

    private fun with(profile: KidProfileEntity, body: suspend TestScope.(KidFixture, KidViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = KidFixture(profile)
        val vm = f.model { clock }
        try { runCurrent(); body(f, vm) } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    private val ohneRegeln = KidProfileEntity("p", "Synthetic", dailyLimitMinutes = null, bedtimeEnabled = false)
    private val mitRuhezeit = KidProfileEntity("p", "Synthetic", dailyLimitMinutes = null, bedtimeEnabled = true)
    private val mitLimit = KidProfileEntity("p", "Synthetic", dailyLimitMinutes = 10, bedtimeEnabled = false)

    // ---- Schlaf-Timer --------------------------------------------------------------------------

    @Test fun `abgelaufener Schlaf-Timer sperrt, Tasten und Tippen wirken nicht, kein Hinweis daneben`() =
        with(ohneRegeln) { f, vm ->
            vm.activateRow(0); runCurrent()
            vm.startSleepTimer(2)
            jump(60); tick(60)
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.GUTE_NACHT)
            assertThat(vm.state.value.playback).isNull()
            assertThat(vm.state.value.hint).isNull()

            val fokus = vm.state.value.focusIndex
            vm.onKey(KeyAction.FocusNext); vm.onKey(KeyAction.Select); runCurrent()
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.focusIndex).isEqualTo(fokus)
            assertThat(vm.state.value.playback).isNull()
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.GUTE_NACHT)
        }

    @Test fun `die Eltern heben den Schlaf-Timer auf der Sperre auf, danach startet ein Video wieder`() =
        with(ohneRegeln) { _, vm ->
            vm.activateRow(0); runCurrent()
            vm.startSleepTimer(1); jump(60); runCurrent()
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.GUTE_NACHT)

            vm.elternHebenSperreAuf(); runCurrent()
            assertThat(vm.state.value.sperre).isNull()
            assertThat(vm.state.value.sleepRemainingSeconds).isNull()
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNotNull()
        }

    @Test fun `stoppen die Eltern den Timer im Elternbereich, faellt die Sperre ebenfalls`() =
        with(ohneRegeln) { _, vm ->
            vm.startSleepTimer(1); jump(60); runCurrent()
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.GUTE_NACHT)
            vm.stopSleepTimer(); runCurrent()
            assertThat(vm.state.value.sperre).isNull()
        }

    // ---- Ruhezeit -------------------------------------------------------------------------------

    @Test fun `die Ruhezeit sperrt auch ohne laufendes Video und hebt sich am Ende des Fensters auf`() =
        with(mitRuhezeit) { _, vm ->
            assertThat(vm.state.value.sperre).isNull()
            jump(60 * 60)   // 20:00
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.RUHEZEIT)
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNull()

            jump(10 * 60 * 60 + 29 * 60)   // 06:29
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.RUHEZEIT)
            jump(60)   // 06:30 - Ende (exklusiv)
            assertThat(vm.state.value.sperre).isNull()
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNotNull()
        }

    @Test fun `die Ruhezeit beendet ein laufendes Video und sperrt`() =
        with(mitRuhezeit) { _, vm ->
            vm.activateRow(0); runCurrent()
            jump(60 * 60)   // 20:00
            assertThat(vm.state.value.playback).isNull()
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.RUHEZEIT)
        }

    @Test fun `die PIN auf der Ruhezeit-Sperre setzt eine Ausnahme bis zum Ende des Fensters`() =
        with(mitRuhezeit) { f, vm ->
            jump(60 * 60)   // 20:00
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.RUHEZEIT)

            vm.elternHebenSperreAuf(); runCurrent()
            assertThat(vm.state.value.sperre).isNull()
            assertThat(f.updatedProfile?.bedtimeSkipUntil).isEqualTo(at(6, 30, day = 15).toEpochMilli())
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNotNull()

            // Nach dem Fenster gilt die Regel wieder: am naechsten Abend sperrt sie erneut.
            jump(24 * 60 * 60)   // Dienstag 20:00
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.RUHEZEIT)
        }

    @Test fun `eine Ausnahme aus dem Elternbereich hebt die Sperre ueber den Profilstand auf`() =
        with(mitRuhezeit) { f, vm ->
            jump(60 * 60)   // 20:00
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.RUHEZEIT)
            f.profileFlow.value = listOf(mitRuhezeit.copy(bedtimeSkipUntil = at(6, 30, day = 15).toEpochMilli()))
            runCurrent()
            assertThat(vm.state.value.sperre).isNull()
        }

    @Test fun `die Sperre nennt die Uhrzeit, ab der es weitergeht`() {
        assertThat(KidSperreText.weiterAb(mitRuhezeit)).isEqualTo("6:30")
        assertThat(KidSperreText.weiterAb(ohneRegeln)).isNull()
        assertThat(KidSperreText.weiterAb(mitRuhezeit.copy(bedtimeEndMinutes = 99999))).isNull()
        assertThat(xyz.steier.sidetube.TestTexte.get(KidSperreText.textRes(KidSperre.RUHEZEIT, "6:30"), "6:30")).contains("Ab 6:30 Uhr")
    }

    // ---- Tageslimit -----------------------------------------------------------------------------

    @Test fun `aufgebrauchte Sehzeit sperrt schon beim Start und hebt sich mit einem hoeheren Limit auf`() =
        with(mitLimit) { f, vm ->
            f.secondsToday = 10 * 60
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNull()
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.ZEIT_UM)
            assertThat(vm.state.value.remainingMinutes).isEqualTo(0)

            f.profileFlow.value = listOf(mitLimit.copy(dailyLimitMinutes = 20)); runCurrent()
            assertThat(vm.state.value.sperre).isNull()
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNotNull()
        }

    @Test fun `die Zeit-um-Sperre faellt um Mitternacht`() =
        with(mitLimit) { f, vm ->
            f.secondsToday = 10 * 60
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.ZEIT_UM)
            f.secondsToday = 0   // der neue Tag beginnt ohne Sehzeit
            jump(4 * 60 * 60 + 59 * 60)   // 23:59
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.ZEIT_UM)
            jump(60)   // 00:00
            assertThat(vm.state.value.sperre).isNull()
        }

    @Test fun `die PIN auf Zeit-um hebt nur die Sperre, beim Rueckweg greift die Regel wieder`() =
        with(mitLimit) { f, vm ->
            f.secondsToday = 10 * 60
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.ZEIT_UM)
            assertThat(KidSperre.ZEIT_UM.zurueckZumKind).isFalse()
            vm.elternHebenSperreAuf(); runCurrent()
            assertThat(vm.state.value.sperre).isNull()
            vm.pruefeSperre(); runCurrent()   // wie beim Rueckweg in den Kindermodus
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.ZEIT_UM)
        }

    // ---- Unterbrochene Wiedergabe, Speicherfehler ------------------------------------------------

    @Test fun `ein Marker der letzten Wiedergabe sperrt bis zur Freigabe der Eltern`() =
        with(ohneRegeln) { f, vm ->
            f.markers["p"] = PlaybackSessionEntity("p", "alt", 0L)
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNull()
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.UNTERBROCHEN)

            // Zeit und Profilstand heben diese Sperre nicht auf.
            jump(60 * 60); f.profileFlow.value = listOf(ohneRegeln.copy(name = "Neu")); runCurrent()
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.UNTERBROCHEN)

            vm.acknowledgeInterruptedPlaybackByParent("p"); runCurrent()
            assertThat(vm.state.value.sperre).isNull()
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNotNull()
        }

    @Test fun `ein Lesefehler der Sehzeit sperrt statt zu starten`() =
        with(mitLimit) { f, vm ->
            f.readGate = CompletableDeferred<Unit>().also { it.completeExceptionally(IllegalStateException("disk")) }
            vm.activateRow(0); runCurrent()
            assertThat(vm.state.value.playback).isNull()
            assertThat(vm.state.value.sperre).isEqualTo(KidSperre.SPEICHERFEHLER)
        }
}
