// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.input

/** Ein Feld am Buchstabenrad der Suche. */
sealed interface RadTaste {
    data class Buchstabe(val zeichen: Radsuche.Zeichen) : RadTaste
    /** Wortgrenze – nur angeboten, wenn ein Name danach weitergeht. */
    data object Luecke : RadTaste
    /** Nimmt das letzte Zeichen weg – nur, wenn es etwas zu löschen gibt. */
    data object Loeschen : RadTaste
}

/**
 * Die Felder des Rades und wohin der Fokus nach einer Änderung geht.
 *
 * Reihenfolge: Buchstaben, dann Lücke, dann Löschen. Rein und ohne Zustand.
 */
object Radtasten {

    fun fuer(angebot: Radsuche.Angebot, eingabe: String): List<RadTaste> = buildList {
        angebot.zeichen.forEach { add(RadTaste.Buchstabe(it)) }
        if (angebot.luecke) add(RadTaste.Luecke)
        if (eingabe.isNotEmpty()) add(RadTaste.Loeschen)
    }

    /**
     * Wohin der Fokus nach dem Wechsel von [vorher] zu [nachher] geht.
     *
     * Er bleibt in der Nähe: auf demselben Feld, wenn es noch da ist, sonst beim nächsten
     * Buchstaben dahinter im Alphabet. So muss niemand nach jedem Schritt vom Anfang drehen.
     */
    fun fokusNach(vorher: RadTaste?, nachher: List<RadTaste>): Int {
        if (nachher.isEmpty()) return 0
        if (vorher != null) {
            val gleich = nachher.indexOfFirst { it.gleichesFeld(vorher) }
            if (gleich >= 0) return gleich
        }
        val buchstaben = nachher.withIndex().filter { it.value is RadTaste.Buchstabe }
        if (vorher is RadTaste.Buchstabe && buchstaben.isNotEmpty()) {
            val wert = vorher.zeichen.wert
            return (buchstaben.firstOrNull { (it.value as RadTaste.Buchstabe).zeichen.wert >= wert }
                ?: buchstaben.last()).index
        }
        return 0
    }

    /** Dasselbe Feld, auch wenn sich die Schreibweisen dahinter geändert haben. */
    private fun RadTaste.gleichesFeld(andere: RadTaste): Boolean = when (this) {
        is RadTaste.Buchstabe -> andere is RadTaste.Buchstabe && andere.zeichen.wert == zeichen.wert
        else -> this == andere
    }
}
