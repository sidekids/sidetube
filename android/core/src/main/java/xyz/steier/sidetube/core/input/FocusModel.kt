// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.input

/**
 * Wo die Auswahl gerade steht.
 *
 * Bewusst ohne Umlauf: Ein Kind, das die Taste gedrueckt haelt, soll am Ende der Liste
 * stehen bleiben und nicht wieder oben landen – sonst ist unklar, ob man sich bewegt hat.
 * Der Fokus bleibt immer sichtbar, auch nach Touchbedienung.
 */
class FocusModel(initialCount: Int = 0) {

    var count: Int = initialCount
        private set

    var index: Int = 0
        private set

    val hasSelection: Boolean get() = count > 0 && index in 0 until count

    /** Neue Anzahl setzen und die Auswahl im gueltigen Bereich halten. */
    fun setCount(newCount: Int) {
        count = newCount.coerceAtLeast(0)
        index = index.coerceIn(0, (count - 1).coerceAtLeast(0))
    }

    /** Gibt zurueck, ob sich etwas bewegt hat – daran haengt die Rueckmeldung. */
    fun move(by: Int): Boolean {
        if (count == 0) return false
        val target = (index + by).coerceIn(0, count - 1)
        if (target == index) return false
        index = target
        return true
    }

    fun focus(newIndex: Int) {
        if (count == 0) return
        index = newIndex.coerceIn(0, count - 1)
    }

    fun reset() { index = 0 }
}
