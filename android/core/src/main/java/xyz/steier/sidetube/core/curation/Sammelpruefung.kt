// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.curation

import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.ApprovalStatus
import xyz.steier.sidetube.core.model.ContentCategory
import xyz.steier.sidetube.core.model.NewsStatus
import xyz.steier.sidetube.core.model.SourceTrust
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.repo.Approval

/** Warum ein Eintrag nicht in die Sammelpruefung darf (ADR 0004), in der Reihenfolge der Pruefung. */
enum class Einzelpruefungsgrund {
    /** Steht nicht mehr zur Pruefung (inzwischen freigegeben oder abgelehnt, etwa hart vom Filter). */
    NICHT_OFFEN,
    QUELLE_GESPERRT,
    QUELLE_NUR_ELTERN,
    /** Themen- oder harter Treffer des Filters. */
    FILTERTREFFER,
    /** Nachricht mit `newsStatus` „Eltern prüfen" (oder „heikel"). */
    NACHRICHT
}

/** Wahl fuer die Kategorie in der Sammel-Maske: jeder Eintrag behaelt seine, oder eine fuer alle. */
sealed interface KategorieWahl {
    data object WieVorgeschlagen : KategorieWahl
    /** `null` heisst „keine Kategorie". */
    data class Gesetzt(val kategorie: ContentCategory?) : KategorieWahl
}

/**
 * Was die Sammel-Maske einmal fuer alle fragt (ADR 0004). `alter == null` heisst „wie vorgeschlagen".
 * Die Kanalstufe gilt nur fuer Kanaele in der Auswahl; „Gesperrt" ist keine Sammelentscheidung.
 */
data class Sammelwahl(
    val alter: Int? = null,
    val kategorie: KategorieWahl = KategorieWahl.WieVorgeschlagen,
    val kanalStufe: SourceTrust = SourceTrust.PER_VIDEO_REVIEW
) {
    init {
        require(kanalStufe != SourceTrust.BLOCKED) { "Sperren bleibt eine Einzelentscheidung" }
        require(alter == null || alter in Kanaleinstufung.MIN_ALTER..Kanaleinstufung.MAX_ALTER) { "Alter 3–16" }
    }
}

/** Freigabe eines einzelnen Eintrags aus der Sammelwahl – fuer Kanaele mit Stufe (ADR 0003). */
sealed interface Sammelfreigabe {
    data class Inhalt(val approval: Approval) : Sammelfreigabe
    data class Kanal(val einstufung: Kanaleinstufung, val ageMax: Int?, val parentNotes: String?) : Sammelfreigabe
}

/**
 * Regeln der Sammelpruefung (ADR 0004), ohne Datenbank und ohne Oberflaeche, damit Liste, ViewModel
 * und Repository dasselbe rechnen und iOS dieselbe Regel nachbauen kann.
 *
 * Eine Sammelfreigabe sieht sich den einzelnen Eintrag nicht an. Deshalb bleiben die Faelle, die
 * Aufmerksamkeit brauchen, draussen, und ohne bewusste Wahl gilt je Eintrag seine eigene Vorgabe.
 */
object Sammelpruefung {

    /** Vermerk im Verlauf jedes Eintrags, der ueber die Sammelpruefung entschieden wurde. */
    const val VERMERK = "Sammelprüfung"

    private val offen = setOf(ApprovalStatus.DISCOVERED, ApprovalStatus.REVIEW_REQUIRED, ApprovalStatus.EXPIRED_REVIEW)

    /**
     * `null`, wenn der Eintrag gesammelt entschieden werden darf; sonst der (erste) Grund fuer die
     * Einzelpruefung. [vorpruefung] ist eine frische Pruefung des Titels, falls vorhanden – sie faengt
     * harte Treffer, die am Eintrag selbst nicht mehr zu sehen sind.
     */
    fun grund(item: WhitelistItemEntity, quelle: CuratedSourceEntity?, vorpruefung: RiskAssessment? = null): Einzelpruefungsgrund? {
        val trust = SourceTrust.from(quelle?.trust)
        val news = NewsStatus.from(item.newsStatus)
        return when {
            ApprovalStatus.from(item.approvalStatus) !in offen -> Einzelpruefungsgrund.NICHT_OFFEN
            trust == SourceTrust.BLOCKED -> Einzelpruefungsgrund.QUELLE_GESPERRT
            trust == SourceTrust.PARENT_ONLY -> Einzelpruefungsgrund.QUELLE_NUR_ELTERN
            item.sensitiveTopics.isNotBlank() || !item.editorialNotes.isNullOrBlank() ||
                vorpruefung?.isHardBlocked == true || vorpruefung?.topics?.isNotEmpty() == true -> Einzelpruefungsgrund.FILTERTREFFER
            item.isNews && (news == NewsStatus.PARENT_REVIEW || news == NewsStatus.SENSITIVE) -> Einzelpruefungsgrund.NACHRICHT
            else -> null
        }
    }

    fun istKanal(item: WhitelistItemEntity) = item.type == WhitelistItemType.CHANNEL.name

    /**
     * Freigabe dieses Eintrags aus der Sammelwahl. „Wie vorgeschlagen" heisst: was der Eintrag
     * mitbringt – dasselbe, was ein „Freigeben" ohne Aenderung in der Einzelmaske speicherte. Hebt die
     * Kategorie das Alter an, gilt je Eintrag das hoehere; Hoechstalter und Anmerkung bleiben stehen.
     */
    fun freigabe(item: WhitelistItemEntity, quelle: CuratedSourceEntity?, wahl: Sammelwahl): Sammelfreigabe {
        if (istKanal(item)) {
            val vorschlag = Kanaleinstufung.vorauswahl(quelle, item)
            val einstufung = Kanaleinstufung(
                trust = wahl.kanalStufe,
                ageMin = wahl.alter ?: vorschlag.ageMin,
                category = when (val k = wahl.kategorie) {
                    KategorieWahl.WieVorgeschlagen -> vorschlag.category
                    is KategorieWahl.Gesetzt -> k.kategorie
                }
            )
            return Sammelfreigabe.Kanal(einstufung, item.ageMax, item.parentNotes)
        }
        val kategorie = when (val k = wahl.kategorie) {
            KategorieWahl.WieVorgeschlagen -> item.category
            is KategorieWahl.Gesetzt -> k.kategorie?.id
        }
        val mindestalter = maxOf(wahl.alter ?: item.ageMin, ContentCategory.from(kategorie)?.minimumAge ?: 0)
        return Sammelfreigabe.Inhalt(Approval(
            ageMin = mindestalter,
            ageMax = item.ageMax?.let { maxOf(it, mindestalter) },
            category = kategorie,
            parentNotes = item.parentNotes?.takeIf { it.isNotBlank() }
        ))
    }
}
