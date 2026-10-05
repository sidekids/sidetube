// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.curation

import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.ContentCategory
import xyz.steier.sidetube.core.model.SourceTrust
import xyz.steier.sidetube.core.repo.Approval

/**
 * Was Eltern beim Hinzufuegen eines Kanals entscheiden (ADR 0003): Vertrauensstufe, Mindestalter,
 * Kategorie. Reine Regel ohne Datenbank und ohne Oberflaeche, damit Vorschau-Dialog und Pruefliste
 * dasselbe rechnen und iOS dieselbe Regel nachbauen kann.
 */
data class Kanaleinstufung(
    val trust: SourceTrust,
    val ageMin: Int,
    val category: ContentCategory?
) {
    /** Was aus der Entscheidung folgt. */
    enum class Ergebnis {
        /** „Gesperrt": nichts in die Liste des Kindes; die Quelle merkt sich die Sperre. */
        NICHT_AUFNEHMEN,
        /** Jede andere Stufe: Die Entscheidung *ist* die Pruefung, der Kanal-Eintrag wird freigegeben. */
        AUFNEHMEN_UND_FREIGEBEN
    }

    val ergebnis: Ergebnis
        get() = if (trust == SourceTrust.BLOCKED) Ergebnis.NICHT_AUFNEHMEN else Ergebnis.AUFNEHMEN_UND_FREIGEBEN

    /**
     * Das hoehere von gewaehltem Alter und Kategorie-Mindestalter. Die Kategorie wuerde den Kanal
     * fuer juengere Kinder ohnehin ausblenden ([ContentPolicy]); gespeichert wird, was tatsaechlich gilt.
     */
    val effektivesMindestalter: Int
        get() = maxOf(ageMin, category?.minimumAge ?: 0)

    /** Ob die Ansicht sagen muss, dass die Kategorie das Alter anhebt. */
    val kategorieHebtAlterAn: Boolean
        get() = (category?.minimumAge ?: 0) > ageMin

    fun mitStufe(neu: SourceTrust) = copy(trust = neu)
    fun mitKategorie(neu: ContentCategory?) = copy(category = neu)
    fun aelter() = copy(ageMin = (ageMin + 1).coerceAtMost(MAX_ALTER))
    fun juenger() = copy(ageMin = (ageMin - 1).coerceAtLeast(MIN_ALTER))

    /**
     * Freigabe des Kanal-Eintrags; `null` bei „Gesperrt", denn dann gibt es nichts freizugeben.
     * Hoechstalter und Anmerkung kommen nur aus der Pruefmaske, die sie zusaetzlich fuehrt.
     */
    fun freigabe(ageMax: Int? = null, parentNotes: String? = null): Approval? =
        if (ergebnis == Ergebnis.NICHT_AUFNEHMEN) null
        else Approval(
            ageMin = effektivesMindestalter,
            ageMax = ageMax?.let { maxOf(it, effektivesMindestalter) },
            category = category?.id,
            parentNotes = parentNotes?.takeIf { it.isNotBlank() }
        )

    /** Fuer den Verlauf: „Nur einzeln geprüfte Videos · ab 8 · mangaDrawing". */
    fun verlaufsNotiz(titel: String): String = listOfNotNull(
        "$titel → ${trust.id}",
        "ab $effektivesMindestalter".takeIf { ergebnis == Ergebnis.AUFNEHMEN_UND_FREIGEBEN },
        category?.id?.takeIf { ergebnis == Ergebnis.AUFNEHMEN_UND_FREIGEBEN }
    ).joinToString(" · ")

    companion object {
        const val MIN_ALTER = 3
        const val MAX_ALTER = 16
        const val STANDARD_ALTER = 6

        /**
         * Vorauswahl: was die Quelle schon hat, sonst die vorsichtigste Stufe, die etwas zeigt
         * („Nur einzeln geprüfte Videos"), Alter 6, keine Kategorie.
         *
         * Liegt schon ein Eintrag vor (Kanal-Kandidat in der Pruefliste, freigegebener Kanal), gelten
         * dessen Alter und Kategorie – sie sind die juengere, genauere Entscheidung. Ein Alter unter 3
         * (Standard 0 bei Registerquellen und bei Kandidaten ohne Quelle) ist keine Entscheidung und
         * faellt auf die naechste Angabe zurueck.
         */
        fun vorauswahl(quelle: CuratedSourceEntity?, eintrag: WhitelistItemEntity? = null): Kanaleinstufung {
            val alter = listOfNotNull(eintrag?.ageMin, quelle?.defaultAgeMin)
                .firstOrNull { it >= MIN_ALTER }?.coerceAtMost(MAX_ALTER) ?: STANDARD_ALTER
            val kategorie = if (eintrag != null) eintrag.category else quelle?.defaultCategory
            return Kanaleinstufung(
                trust = SourceTrust.from(quelle?.trust) ?: SourceTrust.PER_VIDEO_REVIEW,
                ageMin = alter,
                category = ContentCategory.from(kategorie)
            )
        }
    }
}
