// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import xyz.steier.sidetube.R
import xyz.steier.sidetube.Texte
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import xyz.steier.sidetube.core.curation.Einzelpruefungsgrund
import xyz.steier.sidetube.core.db.ReviewEventEntity
import xyz.steier.sidetube.core.model.AgeBand
import xyz.steier.sidetube.core.model.ContentCategory
import xyz.steier.sidetube.core.model.SourceTrust

/**
 * Bezeichnungen fuer die Kennungen der Kuratierung. Die Kennungen selbst
 * (`trustedChildSource`, `deferred` ...) sind fuer Eltern nichtssagend; die Woerter folgen der
 * iOS-Fassung, damit beide Apps dasselbe sagen. Die `*Res`-Funktionen liefern die Ressource,
 * die Composable-Fassungen den Text; Code ohne Compose fragt `Texte`.
 */
internal object ParentLabels {

    fun trustRes(trust: SourceTrust): Int = when (trust) {
        SourceTrust.TRUSTED_CHILD_SOURCE -> R.string.trust_trusted_child_source
        SourceTrust.TRUSTED_SERIES -> R.string.trust_trusted_series
        SourceTrust.PER_VIDEO_REVIEW -> R.string.trust_per_video_review
        SourceTrust.PARENT_ONLY -> R.string.trust_parent_only
        SourceTrust.BLOCKED -> R.string.trust_blocked
    }

    @Composable fun trust(trust: SourceTrust): String = stringResource(trustRes(trust))

    /**
     * Ein Satz je Stufe, was das Kind danach sieht – beim Einstufen eines Kanals (ADR 0003). Die
     * Saetze folgen [xyz.steier.sidetube.core.curation.ContentPolicy]; wer die Regel aendert, aendert sie hier mit.
     */
    fun trustErklaerungRes(trust: SourceTrust): Int = when (trust) {
        SourceTrust.TRUSTED_CHILD_SOURCE -> R.string.trust_erklaerung_trusted_child_source
        SourceTrust.TRUSTED_SERIES -> R.string.trust_erklaerung_trusted_series
        SourceTrust.PER_VIDEO_REVIEW -> R.string.trust_erklaerung_per_video_review
        SourceTrust.PARENT_ONLY -> R.string.trust_erklaerung_parent_only
        SourceTrust.BLOCKED -> R.string.trust_erklaerung_blocked
    }

    @Composable fun trustErklaerung(trust: SourceTrust): String = stringResource(trustErklaerungRes(trust))

    /** Bezeichnung der Kategorie; die Kennung selbst ist fuer Eltern nichtssagend. */
    fun categoryRes(category: ContentCategory): Int = when (category) {
        ContentCategory.KNOWLEDGE -> R.string.kategorie_wissen
        ContentCategory.MEDIA_LITERACY -> R.string.kategorie_medienkompetenz
        ContentCategory.NEWS -> R.string.kategorie_nachrichten
        ContentCategory.STORY -> R.string.kategorie_geschichten
        ContentCategory.MUSIC -> R.string.kategorie_musik
        ContentCategory.CRAFT -> R.string.kategorie_basteln
        ContentCategory.MANGA_DRAWING -> R.string.kategorie_manga_zeichnen
        ContentCategory.ANIME_MANGA -> R.string.kategorie_anime_manga
        ContentCategory.SPORT -> R.string.kategorie_sport
    }

    @Composable fun category(category: ContentCategory): String = stringResource(categoryRes(category))

    /** Kurzer Grund, warum ein Eintrag nicht in die Sammelpruefung darf (ADR 0004). */
    fun einzelpruefungRes(grund: Einzelpruefungsgrund): Int = when (grund) {
        // Kurz: Die Woerter stehen auf dem SidePhone in einer Zeile neben Haekchen und Bild.
        Einzelpruefungsgrund.NICHT_OFFEN -> R.string.einzelpruefung_nicht_offen
        Einzelpruefungsgrund.QUELLE_GESPERRT -> R.string.einzelpruefung_quelle_gesperrt
        Einzelpruefungsgrund.QUELLE_NUR_ELTERN -> R.string.einzelpruefung_nur_eltern
        Einzelpruefungsgrund.FILTERTREFFER -> R.string.einzelpruefung_filtertreffer
        Einzelpruefungsgrund.NACHRICHT -> R.string.einzelpruefung_nachricht
    }

    @Composable fun einzelpruefung(grund: Einzelpruefungsgrund): String = stringResource(einzelpruefungRes(grund))

    /**
     * Hinweis ueber der Auswahl: „3 brauchen eine Einzelprüfung: nur für Eltern (1), Filtertreffer (2)".
     * `null`, wenn alles sammelbar ist.
     */
    fun einzelpruefungHinweis(gruende: Collection<Einzelpruefungsgrund>, texte: Texte): String? {
        if (gruende.isEmpty()) return null
        val kopf = texte.plural(R.plurals.einzelpruefung_kopf, gruende.size)
        val teile = gruende.groupingBy { it }.eachCount().entries.sortedBy { it.key.ordinal }
            .joinToString(", ") { (grund, n) -> texte.get(R.string.einzelpruefung_grund_mit_anzahl, texte.get(einzelpruefungRes(grund)), n) }
        return texte.get(R.string.einzelpruefung_hinweis, kopf, teile)
    }

    /** Android fuehrt eigene Kennungen (`early`, `tween`); die Titel entsprechen iOS. */
    fun ageBandRes(band: AgeBand): Int = when (band) {
        AgeBand.PRESCHOOL -> R.string.altersprofil_preschool
        AgeBand.EARLY -> R.string.altersprofil_early
        AgeBand.KIDS -> R.string.altersprofil_kids
        AgeBand.TWEEN -> R.string.altersprofil_tween
    }

    @Composable fun ageBand(band: AgeBand): String = stringResource(ageBandRes(band))

    /**
     * Entscheidung im Verlauf. Unbekannte Kennungen bleiben lesbar stehen, statt zu verschwinden:
     * Ein Eintrag aus einer spaeteren Fassung soll im Verlauf nicht fehlen.
     */
    fun decisionRes(id: String): Int? = when (id) {
        "discovered" -> R.string.entscheidung_discovered
        "approved" -> R.string.entscheidung_approved
        "rejected" -> R.string.entscheidung_rejected
        "deferred" -> R.string.entscheidung_deferred
        "autoRejected" -> R.string.entscheidung_auto_rejected
        "blockedSource" -> R.string.entscheidung_blocked_source
        "trustChanged" -> R.string.entscheidung_trust_changed
        "statusExpired" -> R.string.entscheidung_status_expired
        "wished" -> R.string.entscheidung_wished
        "wishFulfilled" -> R.string.entscheidung_wish_fulfilled
        "wishRejected" -> R.string.entscheidung_wish_rejected
        "wishDiscuss" -> R.string.entscheidung_wish_discuss
        "wishLinked" -> R.string.entscheidung_wish_linked
        else -> null
    }

    fun decision(id: String, texte: Texte): String = decisionRes(id)?.let(texte::get) ?: id

    private val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    /** Eine Zeile des Verlaufs: „Freigegeben · Eltern“ und darunter Zeitpunkt. */
    fun eventHeadline(event: ReviewEventEntity, texte: Texte): String =
        texte.get(R.string.verlauf_zeile, decision(event.decision, texte), event.actor)

    fun eventTime(event: ReviewEventEntity, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTime.format(Instant.ofEpochMilli(event.at).atZone(zone))

    /** Minuten seit Mitternacht als „20:00“ – wie die Ruhezeit-Anzeige auf iOS. */
    fun clock(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)
}
