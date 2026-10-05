// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.input

/**
 * Was eine Taste bedeutet – unabhaengig davon, welchen Code das Geraet meldet.
 *
 * Die Bildschirme kennen nur diese Aktionen; Android-Keycodes werden ausschliesslich hier
 * uebersetzt. Sonst muesste jede neue Ansicht die Eigenheiten der Hardware erneut kennen.
 */
enum class KeyAction {
    /** Auswahl eine Position zurueck (visuelle Reihenfolge, kein Umlauf). */
    FocusPrevious,
    /** Auswahl eine Position weiter; am Ende bleibt sie stehen. */
    FocusNext,
    /** Oeffnen beziehungsweise im Player Wiedergabe und Pause. */
    Select,
    /** Eine Ebene zurueck; lang gedrueckt bis zum Start. */
    Back,
    /** Zum Start, ohne Umweg. */
    Home,
    /** Suche oeffnen – Abkuerzung (aussen oben rechts, kurz), ersetzt keine Auswahl. */
    Search,
    /** Einstellungen = Elternbereich hinter der PIN (aussen oben rechts, lang). */
    Settings,
    /** Kontextmenue (aussen unten rechts, lang): im Player das Menue mit „Mehr davon". */
    ContextMenu,
    /** Voriger Titel im Player. */
    MediaPrevious,
    /** Naechster Titel im Player. */
    MediaNext
}

/**
 * Uebersetzt Hardware-Tasten des Sidephone SP-01 in Aktionen (SideUI ADR 0005, 0012, 0013).
 *
 * Grundlage ist die gemeinsame Messtabelle (SidePlay `docs/design/sideui/clickwheel.md`):
 * innen hoch/runter = DPAD_UP/DOWN, innen links/rechts = MEDIA_PREVIOUS/NEXT, aussen links =
 * DPAD_LEFT oder TAB (zurueck), aussen oben rechts = DPAD_RIGHT.
 *
 * **Die Mitte ist ungemessen:** Die Tabelle nennt `KEY_PLAYPAUSE`, eine fruehere Messung fuer
 * SideTube `ENTER`; unter Firmware 2.0.0 wird neu gemessen. Bis dahin oeffnen beide (und
 * DPAD_CENTER). Die Tabelle legt `ENTER` zugleich auf aussen unten rechts – eine Taste kann
 * nicht beides sein. Kurz gilt deshalb die Mitte (die Grundbedienung geht vor einer Abkuerzung,
 * ADR 0004), lang das Kontextmenue von aussen unten rechts. Die kurze Abkuerzung „Favoriten"
 * entfaellt: SideTube hat keine Favoriten (Auslegung in `docs/design/sideui.md`).
 */
object KeyMap {

    // Android-Keycodes, hier bewusst als Zahlen: Der Kern soll ohne Android-Abhaengigkeit
    // pruefbar bleiben. Die Namen stehen daneben.
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val ENTER = 66
    const val BACK = 4
    const val TAB = 61
    const val ESCAPE = 111
    const val NUMPAD_ENTER = 160
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88
    const val NUMPAD_STAR = 155
    const val STAR = 17
    const val HOME = 3
    const val DEL = 67

    /**
     * Die Aktion einer Taste.
     *
     * @param longPress ob der Druck lang war – nur fuer [isHoldKey]-Tasten bedeutsam, und nur,
     *   wenn [HoldKey] es entschieden hat. Das Flag eines einzelnen KeyEvents reicht nicht: Android
     *   setzt es erst bei der Wiederholung, nie beim ersten Druck.
     * @param typing In einem Textfeld duerfen Ziffern nicht als Navigation gelten.
     */
    fun action(keyCode: Int, longPress: Boolean = false, typing: Boolean = false): KeyAction? {
        if (typing && keyCode in DIGITS) return null

        return when (keyCode) {
            DPAD_UP -> KeyAction.FocusPrevious
            DPAD_DOWN -> KeyAction.FocusNext
            MEDIA_PREVIOUS -> KeyAction.MediaPrevious
            MEDIA_NEXT -> KeyAction.MediaNext
            DPAD_CENTER, MEDIA_PLAY_PAUSE -> KeyAction.Select
            ENTER, NUMPAD_ENTER -> if (longPress) KeyAction.ContextMenu else KeyAction.Select
            DPAD_LEFT, BACK, TAB, ESCAPE, STAR, NUMPAD_STAR ->
                if (longPress) KeyAction.Home else KeyAction.Back
            DPAD_RIGHT -> if (longPress) KeyAction.Settings else KeyAction.Search
            else -> null
        }
    }

    /** Tasten mit zwei Bedeutungen: Sie wirken erst beim Loslassen (kurz) oder beim Halten (lang). */
    fun isHoldKey(keyCode: Int): Boolean = keyCode in HOLD_KEYS

    private val HOLD_KEYS = setOf(DPAD_LEFT, BACK, TAB, ESCAPE, STAR, NUMPAD_STAR, DPAD_RIGHT, ENTER, NUMPAD_ENTER)

    /** Loescht das letzte Zeichen der Sucheingabe. */
    fun isBackspace(keyCode: Int): Boolean = keyCode == DEL

    /** Ziffer der Taste oder `null` – fuer die Sucheingabe. */
    fun digit(keyCode: Int): Char? = when (keyCode) {
        in DIGITS -> ('0' + (keyCode - 7))
        in NUMPAD_DIGITS -> ('0' + (keyCode - 144))
        else -> null
    }

    private val DIGITS = 7..16          // KEYCODE_0 bis KEYCODE_9
    private val NUMPAD_DIGITS = 144..153
}
