// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeyMapTest {

    @Test
    fun `die Mitte oeffnet mit jedem moeglichen Code`() {
        // Ungemessen unter Firmware 2.0.0: Die Messtabelle nennt KEY_PLAYPAUSE, eine fruehere
        // Messung ENTER. Bis zur Neumessung oeffnen alle (SideUI ADR 0012).
        assertThat(KeyMap.action(KeyMap.ENTER)).isEqualTo(KeyAction.Select)
        assertThat(KeyMap.action(KeyMap.DPAD_CENTER)).isEqualTo(KeyAction.Select)
        assertThat(KeyMap.action(KeyMap.NUMPAD_ENTER)).isEqualTo(KeyAction.Select)
        assertThat(KeyMap.action(KeyMap.MEDIA_PLAY_PAUSE)).isEqualTo(KeyAction.Select)
    }

    @Test
    fun `ENTER lang ist das Kontextmenue von aussen unten rechts`() {
        assertThat(KeyMap.action(KeyMap.ENTER, longPress = true)).isEqualTo(KeyAction.ContextMenu)
        assertThat(KeyMap.isHoldKey(KeyMap.ENTER)).isTrue()
    }

    @Test
    fun `aussen oben rechts kurz Suche, lang Einstellungen`() {
        assertThat(KeyMap.action(KeyMap.DPAD_RIGHT)).isEqualTo(KeyAction.Search)
        assertThat(KeyMap.action(KeyMap.DPAD_RIGHT, longPress = true)).isEqualTo(KeyAction.Settings)
        assertThat(KeyMap.isHoldKey(KeyMap.DPAD_RIGHT)).isTrue()
    }

    @Test
    fun `alle Ruecktasten warten auf kurz oder lang, die Bewegungstasten nicht`() {
        for (key in listOf(KeyMap.DPAD_LEFT, KeyMap.BACK, KeyMap.TAB, KeyMap.ESCAPE, KeyMap.STAR, KeyMap.NUMPAD_STAR)) {
            assertThat(KeyMap.isHoldKey(key)).isTrue()
        }
        for (key in listOf(KeyMap.DPAD_UP, KeyMap.DPAD_DOWN, KeyMap.MEDIA_NEXT, KeyMap.MEDIA_PREVIOUS,
                KeyMap.DPAD_CENTER, KeyMap.MEDIA_PLAY_PAUSE)) {
            assertThat(KeyMap.isHoldKey(key)).isFalse()
        }
    }

    @Test
    fun `oben und unten bewegen die Auswahl`() {
        assertThat(KeyMap.action(KeyMap.DPAD_UP)).isEqualTo(KeyAction.FocusPrevious)
        assertThat(KeyMap.action(KeyMap.DPAD_DOWN)).isEqualTo(KeyAction.FocusNext)
    }

    @Test
    fun `alle Ruecktasten des Geraets fuehren eine Ebene zurueck`() {
        for (key in listOf(KeyMap.DPAD_LEFT, KeyMap.BACK, KeyMap.TAB, KeyMap.ESCAPE, KeyMap.STAR, KeyMap.NUMPAD_STAR)) {
            assertThat(KeyMap.action(key)).isEqualTo(KeyAction.Back)
        }
    }

    @Test
    fun `lang gedrueckt fuehrt bis zum Start`() {
        assertThat(KeyMap.action(KeyMap.BACK, longPress = true)).isEqualTo(KeyAction.Home)
        assertThat(KeyMap.action(KeyMap.DPAD_LEFT, longPress = true)).isEqualTo(KeyAction.Home)
    }

    @Test
    fun `rechts oeffnet die Suche`() {
        assertThat(KeyMap.action(KeyMap.DPAD_RIGHT)).isEqualTo(KeyAction.Search)
    }

    @Test
    fun `waehrend einer Texteingabe sind Ziffern keine Navigation`() {
        val eins = 8   // KEYCODE_1
        assertThat(KeyMap.action(eins, typing = false)).isNull()
        assertThat(KeyMap.action(eins, typing = true)).isNull()
        assertThat(KeyMap.digit(eins)).isEqualTo('1')
        assertThat(KeyMap.digit(7)).isEqualTo('0')
        assertThat(KeyMap.digit(16)).isEqualTo('9')
        assertThat(KeyMap.digit(KeyMap.ENTER)).isNull()
    }

    @Test
    fun `unbekannte Tasten bleiben dem System ueberlassen`() {
        assertThat(KeyMap.action(999)).isNull()
        assertThat(KeyMap.action(KeyMap.HOME)).isNull()
    }
}

class FocusModelTest {

    @Test
    fun `die Auswahl bleibt am Ende stehen statt umzuspringen`() {
        val focus = FocusModel(3)

        assertThat(focus.move(1)).isTrue()
        assertThat(focus.move(1)).isTrue()
        assertThat(focus.index).isEqualTo(2)
        assertThat(focus.move(1)).isFalse()   // nichts bewegt sich
        assertThat(focus.index).isEqualTo(2)
    }

    @Test
    fun `am Anfang geht es nicht weiter zurueck`() {
        val focus = FocusModel(3)
        assertThat(focus.move(-1)).isFalse()
        assertThat(focus.index).isEqualTo(0)
    }

    @Test
    fun `eine kuerzere Liste zieht die Auswahl mit`() {
        val focus = FocusModel(5)
        focus.focus(4)

        focus.setCount(2)

        assertThat(focus.index).isEqualTo(1)
        assertThat(focus.hasSelection).isTrue()
    }

    @Test
    fun `eine leere Liste hat keine Auswahl`() {
        val focus = FocusModel(0)

        assertThat(focus.hasSelection).isFalse()
        assertThat(focus.move(1)).isFalse()
    }
}

/** Kurz oder lang – der Fehler „langes Zurück löscht nur ein Zeichen" (SideUI ADR 0007). */
class HoldKeyTest {

    private val key = HoldKey()

    @Test
    fun `der erste Druck entscheidet nichts, kurz faellt beim Loslassen`() {
        assertThat(key.down(0, false)).isNull()
        assertThat(key.up()).isEqualTo(HoldKey.Press.Short)
    }

    @Test
    fun `Android meldet lang erst mit der Wiederholung - dann lang und kein kurz`() {
        // Genau diese Folge schickt auch `input keyevent --longpress`. Vorher wurde nur der erste
        // Druck (repeatCount 0) ausgewertet, und der war immer kurz.
        assertThat(key.down(0, false)).isNull()
        assertThat(key.down(1, true)).isEqualTo(HoldKey.Press.Long)
        assertThat(key.down(2, false)).isNull()
        assertThat(key.up()).isNull()
    }

    @Test
    fun `ohne Wiederholung entscheidet die Uhr`() {
        key.down(0, false)
        assertThat(key.timeout()).isEqualTo(HoldKey.Press.Long)
        assertThat(key.up()).isNull()
    }

    @Test
    fun `Uhr nach dem Loslassen und Loslassen ohne Druck bewirken nichts`() {
        key.down(0, false)
        assertThat(key.up()).isEqualTo(HoldKey.Press.Short)
        assertThat(key.timeout()).isNull()
        assertThat(key.up()).isNull()
    }

    @Test
    fun `gehalten ist lang, auch wenn der erste Druck fehlt`() {
        // Im Touch-Modus verbraucht Android den ersten DOWN einer Richtungstaste.
        assertThat(key.down(1, true)).isEqualTo(HoldKey.Press.Long)
        assertThat(key.up()).isNull()
    }
}
