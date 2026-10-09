// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import xyz.steier.sidetube.R
import xyz.steier.sidetube.core.db.KidProfileEntity

/**
 * Warum der Kindermodus gesperrt ist. Die Sperre liegt als Vollbild über allem und kennt nur
 * einen Ausgang: die PIN der Eltern – wie `KidOverlay` auf iOS. Ein wegtippbarer Hinweis reicht
 * hier nicht: Was das Kind selbst wegdrücken kann, ist kein Schutz.
 */
enum class KidSperre {
    /** Der Schlaf-Timer ist abgelaufen; nur die Eltern heben ihn auf. */
    GUTE_NACHT,
    /** Die Sehzeit des Tages ist aufgebraucht; endet um Mitternacht oder mit einem höheren Limit. */
    ZEIT_UM,
    /** Ruhezeit; endet mit dem Fenster oder mit einer Ausnahme der Eltern. */
    RUHEZEIT,
    /** Die Sehzeit konnte nicht gespeichert werden; die Eltern müssen es ansehen. */
    SPEICHERFEHLER,
    /** Die letzte Wiedergabe wurde nicht sauber beendet; die Eltern geben sie frei. */
    UNTERBROCHEN;

    /**
     * Diese Sperren folgen aus Regeln, die der Kindermodus selbst nachprüfen kann; sie heben sich
     * auf, sobald die Regel nicht mehr greift. Die anderen beiden bleiben bis zu den Eltern.
     */
    val automatisch: Boolean get() = this == GUTE_NACHT || this == ZEIT_UM || this == RUHEZEIT

    /** Nach der PIN zurück in den Kindermodus – oder in den Elternbereich, wo etwas zu tun ist. */
    val zurueckZumKind: Boolean get() = this == GUTE_NACHT || this == RUHEZEIT
}

/** Texte der Sperre als Ressourcen, wörtlich wie auf iOS (`KidOverlayView`), damit beide Kinder dasselbe lesen. */
object KidSperreText {
    fun titelRes(sperre: KidSperre): Int = when (sperre) {
        KidSperre.GUTE_NACHT -> R.string.sperre_gute_nacht_titel
        KidSperre.ZEIT_UM -> R.string.sperre_zeit_um_titel
        KidSperre.RUHEZEIT -> R.string.sperre_ruhezeit_titel
        KidSperre.SPEICHERFEHLER -> R.string.sperre_speicherfehler_titel
        KidSperre.UNTERBROCHEN -> R.string.sperre_unterbrochen_titel
    }

    /**
     * [weiterAb] ist die Uhrzeit, ab der es nach der Ruhezeit weitergeht („6:30“) – nimmt der Sperre das
     * Endgültige; der Text dazu nimmt sie als erstes Argument.
     */
    fun textRes(sperre: KidSperre, weiterAb: String?): Int = when (sperre) {
        KidSperre.GUTE_NACHT -> R.string.sperre_gute_nacht_text
        KidSperre.ZEIT_UM -> R.string.sperre_zeit_um_text
        KidSperre.RUHEZEIT -> if (weiterAb != null) R.string.sperre_ruhezeit_text_ab else R.string.sperre_ruhezeit_text
        KidSperre.SPEICHERFEHLER -> R.string.sperre_speicherfehler_text
        KidSperre.UNTERBROCHEN -> R.string.sperre_unterbrochen_text
    }

    /** „6:30“ aus dem Ende der Ruhezeit; `null`, wenn keine eingestellt oder der Wert unplausibel ist. */
    fun weiterAb(profile: KidProfileEntity?): String? {
        if (profile == null || !profile.bedtimeEnabled) return null
        val minuten = profile.bedtimeEndMinutes
        if (minuten !in 0..1439) return null
        return "%d:%02d".format(minuten / 60, minuten % 60)
    }
}
