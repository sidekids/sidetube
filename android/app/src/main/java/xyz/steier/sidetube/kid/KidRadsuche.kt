// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.input.RadTaste
import xyz.steier.sidetube.core.input.Radsuche
import xyz.steier.sidetube.core.input.Radtasten

/**
 * Das Buchstabenrad der Kinder-Suche, wie es die Oberfläche zeigt.
 *
 * [aktiv]: Der Fokus steht im Rad (links/rechts wählen einen Buchstaben, Mitte nimmt ihn). Sonst
 * steht er in der Trefferliste darunter. [t9]: Die Eingabe kam über Zifferntasten – dann gilt die
 * alte T9-Suche, und das Rad bleibt leer (siehe `core/input/T9`).
 */
data class RadZustand(
    /** Wie die Eingabe angezeigt wird – mit Umlauten, sobald die Treffer sie eindeutig machen. */
    val anzeige: String = "",
    /** Was gewählt oder getippt wurde, in derselben Länge wie die Eingabe. */
    val getippt: String = "",
    val tasten: List<RadTaste> = emptyList(),
    val fokus: Int = 0,
    val aktiv: Boolean = true,
    val t9: Boolean = false
)

/**
 * Die Rad-Suche über den Freigaben eines Profils: welche Einträge passen, welche Felder das Rad
 * anbietet. Gesucht wird in Titel und Kanalname. Die Regeln selbst stehen in [Radsuche].
 *
 * Merkt sich den Bestand, aus dem sie gebaut ist, und baut nur neu, wenn er sich ändert.
 */
internal class KidRadsuche {
    private var bestand: List<WhitelistItemEntity>? = null
    private var suche = Radsuche(emptyList())

    private fun fuer(items: List<WhitelistItemEntity>): Radsuche {
        if (bestand !== items) {
            suche = Radsuche(items.map { listOfNotNull(it.title, it.channelTitle) })
            bestand = items
        }
        return suche
    }

    fun treffer(items: List<WhitelistItemEntity>, eingabe: String): List<WhitelistItemEntity> =
        fuer(items).treffer(eingabe).map(items::get)

    /** Das Rad nach einer Änderung; der Fokus bleibt in der Nähe des vorigen Feldes. */
    fun rad(items: List<WhitelistItemEntity>, eingabe: String, vorher: RadZustand): RadZustand {
        if (vorher.t9) return vorher.copy(tasten = emptyList(), fokus = 0, anzeige = vorher.getippt)
        val suche = fuer(items)
        val tasten = Radtasten.fuer(suche.angebot(eingabe), eingabe)
        val fokus = Radtasten.fokusNach(vorher.tasten.getOrNull(vorher.fokus), tasten)
        return vorher.copy(tasten = tasten, fokus = fokus, anzeige = suche.schreibweise(eingabe, vorher.getippt))
    }
}
