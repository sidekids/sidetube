// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import xyz.steier.sidetube.core.curation.Einzelpruefungsgrund
import xyz.steier.sidetube.core.db.ReviewEventEntity
import xyz.steier.sidetube.core.model.AgeBand
import xyz.steier.sidetube.core.model.ContentCategory
import xyz.steier.sidetube.core.model.SourceTrust

/**
 * Deutsche Bezeichnungen fuer die Kennungen der Kuratierung. Die Kennungen selbst
 * (`trustedChildSource`, `deferred` ...) sind fuer Eltern nichtssagend; die Woerter folgen der
 * iOS-Fassung, damit beide Apps dasselbe sagen.
 */
internal object ParentLabels {

    fun trust(trust: SourceTrust): String = when (trust) {
        SourceTrust.TRUSTED_CHILD_SOURCE -> "Vertrauenswürdige Kinderquelle"
        SourceTrust.TRUSTED_SERIES -> "Vertrauenswürdige Reihe"
        SourceTrust.PER_VIDEO_REVIEW -> "Nur einzeln geprüfte Videos"
        SourceTrust.PARENT_ONLY -> "Nur für Eltern"
        SourceTrust.BLOCKED -> "Gesperrt"
    }

    /**
     * Ein Satz je Stufe, was das Kind danach sieht – beim Einstufen eines Kanals (ADR 0003). Die
     * Saetze folgen [xyz.steier.sidetube.core.curation.ContentPolicy]; wer die Regel aendert, aendert sie hier mit.
     */
    fun trustErklaerung(trust: SourceTrust): String = when (trust) {
        SourceTrust.TRUSTED_CHILD_SOURCE -> "Das Kind darf im ganzen Kanal stöbern, auch neue Videos ohne einzelne Freigabe."
        SourceTrust.TRUSTED_SERIES -> "Nur freigegebene Videos; neue Folgen sieht das Kind als Vorschau und kann sie sich wünschen."
        SourceTrust.PER_VIDEO_REVIEW -> "Das Kind sieht nur Videos, die einzeln freigegeben sind."
        SourceTrust.PARENT_ONLY -> "Bleibt in der Liste, aber das Kind sieht nichts aus diesem Kanal."
        SourceTrust.BLOCKED -> "Nichts aus diesem Kanal; auch künftige Links werden abgewiesen."
    }

    /** Deutsche Bezeichnung der Kategorie; die Kennung selbst ist fuer Eltern nichtssagend. */
    fun category(category: ContentCategory): String = when (category) {
        ContentCategory.KNOWLEDGE -> "Wissen"
        ContentCategory.MEDIA_LITERACY -> "Medienkompetenz"
        ContentCategory.NEWS -> "Nachrichten"
        ContentCategory.STORY -> "Geschichten"
        ContentCategory.MUSIC -> "Musik"
        ContentCategory.CRAFT -> "Basteln"
        ContentCategory.MANGA_DRAWING -> "Manga zeichnen"
        ContentCategory.ANIME_MANGA -> "Anime & Manga"
        ContentCategory.SPORT -> "Sport"
    }

    /** Kurzer Grund, warum ein Eintrag nicht in die Sammelpruefung darf (ADR 0004). */
    fun einzelpruefung(grund: Einzelpruefungsgrund): String = when (grund) {
        // Kurz: Die Woerter stehen auf dem SidePhone in einer Zeile neben Haekchen und Bild.
        Einzelpruefungsgrund.NICHT_OFFEN -> "nicht mehr offen"
        Einzelpruefungsgrund.QUELLE_GESPERRT -> "Quelle gesperrt"
        Einzelpruefungsgrund.QUELLE_NUR_ELTERN -> "nur für Eltern"
        Einzelpruefungsgrund.FILTERTREFFER -> "Filtertreffer"
        Einzelpruefungsgrund.NACHRICHT -> "Nachricht prüfen"
    }

    /**
     * Hinweis ueber der Auswahl: „3 brauchen eine Einzelprüfung: nur für Eltern (1), Filtertreffer (2)".
     * `null`, wenn alles sammelbar ist.
     */
    fun einzelpruefungHinweis(gruende: Collection<Einzelpruefungsgrund>): String? {
        if (gruende.isEmpty()) return null
        val anzahl = gruende.size
        val kopf = if (anzahl == 1) "1 braucht eine Einzelprüfung" else "$anzahl brauchen eine Einzelprüfung"
        val teile = gruende.groupingBy { it }.eachCount().entries.sortedBy { it.key.ordinal }
            .joinToString(", ") { (grund, n) -> "${einzelpruefung(grund)} ($n)" }
        return "$kopf: $teile"
    }

    /** Android fuehrt eigene Kennungen (`early`, `tween`); die Titel entsprechen iOS. */
    fun ageBand(band: AgeBand): String = when (band) {
        AgeBand.PRESCHOOL -> "Vorschule (3–5)"
        AgeBand.EARLY -> "Jüngere Kinder (6–8)"
        AgeBand.KIDS -> "Kinder (9–11)"
        AgeBand.TWEEN -> "Ab 12"
    }

    /**
     * Entscheidung im Verlauf. Unbekannte Kennungen bleiben lesbar stehen, statt zu verschwinden:
     * Ein Eintrag aus einer spaeteren Fassung soll im Verlauf nicht fehlen.
     */
    fun decision(id: String): String = when (id) {
        "discovered" -> "Aufgenommen"
        "approved" -> "Freigegeben"
        "rejected" -> "Abgelehnt"
        "deferred" -> "Zurückgestellt"
        "autoRejected" -> "Vom Filter abgelehnt"
        "blockedSource" -> "Quelle gesperrt"
        "trustChanged" -> "Stufe geändert"
        "statusExpired" -> "Prüfung fällig"
        "wished" -> "Gewünscht"
        "wishFulfilled" -> "Wunsch erfüllt"
        "wishRejected" -> "Wunsch: nicht jetzt"
        "wishDiscuss" -> "Wunsch: besprechen"
        "wishLinked" -> "Zum Wunsch aufgenommen"
        else -> id
    }

    private val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    /** Eine Zeile des Verlaufs: „Freigegeben · Eltern“ und darunter Zeitpunkt. */
    fun eventHeadline(event: ReviewEventEntity): String = "${decision(event.decision)} · ${event.actor}"

    fun eventTime(event: ReviewEventEntity, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTime.format(Instant.ofEpochMilli(event.at).atZone(zone))

    /** Minuten seit Mitternacht als „20:00“ – wie die Ruhezeit-Anzeige auf iOS. */
    fun clock(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)
}
