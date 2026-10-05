// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.input

/**
 * Kurz oder lang? – die Entscheidung fuer eine Taste mit zwei Bedeutungen (SideUI ADR 0007, 0013).
 *
 * Der Fehler, den sie behebt: `MainActivity` wertete nur den ersten Druck aus
 * (`repeatCount == 0`). Android setzt `isLongPress` aber erst bei der **Wiederholung** – der
 * erste Druck war damit immer „kurz". Langes Zurueck loeschte in der Suche nur ein Zeichen,
 * statt zum Start zu fuehren.
 *
 * Jetzt faellt „kurz" erst beim Loslassen, „lang" bei der ersten Wiederholung, beim
 * Long-Press-Flag (so schickt es auch `input keyevent --longpress`) oder, wenn das Geraet keine
 * Wiederholung schickt, nach Ablauf einer Uhr, die der Aufrufer fuehrt ([timeout]).
 * Rein und ohne Android, damit es ohne Geraet pruefbar ist.
 */
class HoldKey {

    enum class Press { Short, Long }

    private var down = false
    private var longDone = false

    /** Eine DOWN-Meldung; liefert [Press.Long], wenn sie das Halten belegt. */
    fun down(repeatCount: Int, longPressFlag: Boolean): Press? {
        if (repeatCount == 0 && !longPressFlag) {
            down = true
            longDone = false
            return null
        }
        // Auch ohne gesehenen ersten Druck: Im Touch-Modus verbraucht Android den ersten DOWN einer
        // Richtungstaste, um den Modus zu verlassen. Gehalten heisst trotzdem lang.
        if (longDone) return null
        down = true
        longDone = true
        return Press.Long
    }

    /** Die Uhr des Aufrufers ist abgelaufen, waehrend die Taste noch unten ist. */
    fun timeout(): Press? {
        if (!down || longDone) return null
        longDone = true
        return Press.Long
    }

    /** Die UP-Meldung: kurz, wenn noch nichts Langes ausgeloest wurde. */
    fun up(): Press? {
        val result = if (down && !longDone) Press.Short else null
        down = false
        longDone = false
        return result
    }

    companion object {
        /** Ab hier gilt ein Druck als lang – derselbe Wert wie in SidePlay. */
        const val LONG_MS = 500L
    }
}
