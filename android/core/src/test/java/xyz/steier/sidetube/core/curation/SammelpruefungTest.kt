// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.curation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.ContentCategory
import xyz.steier.sidetube.core.model.SensitiveTopic
import xyz.steier.sidetube.core.model.SourceTrust
import xyz.steier.sidetube.core.repo.Approval

/** ADR 0004: welche Eintraege gesammelt entschieden werden duerfen und was die Sammelwahl je Eintrag ergibt. */
class SammelpruefungTest {

    private fun video(
        ageMin: Int = 5, category: String? = "knowledge", ageMax: Int? = null, notes: String? = null,
        status: String = "reviewRequired", topics: String = "", editorial: String? = null,
        isNews: Boolean = false, newsStatus: String? = null
    ) = WhitelistItemEntity(
        "v", "p", "VIDEO", contentId = "v1", title = "Video", sourceChannelId = "UC1", approvalStatus = status,
        ageMin = ageMin, category = category, ageMax = ageMax, parentNotes = notes, sensitiveTopics = topics,
        editorialNotes = editorial, isNews = isNews, newsStatus = newsStatus
    )

    private fun kanal(ageMin: Int = 0, category: String? = null) =
        WhitelistItemEntity("k", "p", "CHANNEL", contentId = "UCk", title = "Kanal", ageMin = ageMin, category = category)

    private fun quelle(trust: String) = CuratedSourceEntity(channelId = "UC1", title = "Quelle", trust = trust)

    // ── sammelbar ja/nein ──

    @Test fun `ein unauffaelliger offener Eintrag ist sammelbar`() {
        assertThat(Sammelpruefung.grund(video(), quelle("perVideoReview"))).isNull()
        assertThat(Sammelpruefung.grund(video(), null)).isNull()
        assertThat(Sammelpruefung.grund(video(status = "expiredReview"), null)).isNull()
    }

    @Test fun `Risikotreffer des Filters brauchen die Einzelpruefung`() {
        assertThat(Sammelpruefung.grund(video(topics = "war"), null)).isEqualTo(Einzelpruefungsgrund.FILTERTREFFER)
        assertThat(Sammelpruefung.grund(video(editorial = "krieg"), null)).isEqualTo(Einzelpruefungsgrund.FILTERTREFFER)
    }

    @Test fun `eine frische Vorpruefung mit hartem oder Themen-Treffer zaehlt auch`() {
        assertThat(Sammelpruefung.grund(video(), null, RiskAssessment(hardBlockTerms = listOf("nsfw"))))
            .isEqualTo(Einzelpruefungsgrund.FILTERTREFFER)
        assertThat(Sammelpruefung.grund(video(), null, RiskAssessment(topics = setOf(SensitiveTopic.FEAR))))
            .isEqualTo(Einzelpruefungsgrund.FILTERTREFFER)
        assertThat(Sammelpruefung.grund(video(), null, RiskAssessment(isShort = true))).isNull()
    }

    @Test fun `Nachrichten mit Elternpruefung bleiben draussen, unbedenkliche nicht`() {
        assertThat(Sammelpruefung.grund(video(isNews = true, newsStatus = "parentReview"), null))
            .isEqualTo(Einzelpruefungsgrund.NACHRICHT)
        assertThat(Sammelpruefung.grund(video(isNews = true, newsStatus = "sensitive"), null))
            .isEqualTo(Einzelpruefungsgrund.NACHRICHT)
        assertThat(Sammelpruefung.grund(video(isNews = true, newsStatus = "safe"), null)).isNull()
    }

    @Test fun `gesperrte und nur-fuer-Eltern-Quellen bleiben draussen`() {
        assertThat(Sammelpruefung.grund(video(), quelle("blocked"))).isEqualTo(Einzelpruefungsgrund.QUELLE_GESPERRT)
        assertThat(Sammelpruefung.grund(video(), quelle("parentOnly"))).isEqualTo(Einzelpruefungsgrund.QUELLE_NUR_ELTERN)
    }

    @Test fun `was nicht mehr offen ist, wird nicht angefasst`() {
        assertThat(Sammelpruefung.grund(video(status = "rejected"), null)).isEqualTo(Einzelpruefungsgrund.NICHT_OFFEN)
        assertThat(Sammelpruefung.grund(video(status = "approved"), null)).isEqualTo(Einzelpruefungsgrund.NICHT_OFFEN)
    }

    @Test fun `die Quelle geht dem Filtertreffer als Grund vor`() {
        assertThat(Sammelpruefung.grund(video(topics = "war"), quelle("blocked"))).isEqualTo(Einzelpruefungsgrund.QUELLE_GESPERRT)
    }

    // ── Freigabe je Eintrag ──

    @Test fun `wie vorgeschlagen behaelt jeder Eintrag seine Vorgaben`() {
        val f = Sammelpruefung.freigabe(video(ageMin = 5, category = "knowledge", ageMax = 10, notes = "gut"), null, Sammelwahl())
        assertThat(f).isEqualTo(Sammelfreigabe.Inhalt(Approval(ageMin = 5, ageMax = 10, category = "knowledge", parentNotes = "gut")))
    }

    @Test fun `ein gesetztes Alter und eine gesetzte Kategorie gelten fuer alle`() {
        val wahl = Sammelwahl(alter = 7, kategorie = KategorieWahl.Gesetzt(ContentCategory.MUSIC))
        val f = Sammelpruefung.freigabe(video(ageMin = 5, category = "knowledge"), null, wahl) as Sammelfreigabe.Inhalt
        assertThat(f.approval.ageMin).isEqualTo(7)
        assertThat(f.approval.category).isEqualTo("music")
    }

    @Test fun `keine Kategorie ist eine bewusste Wahl`() {
        val f = Sammelpruefung.freigabe(video(category = "knowledge"), null, Sammelwahl(kategorie = KategorieWahl.Gesetzt(null)))
        assertThat((f as Sammelfreigabe.Inhalt).approval.category).isNull()
    }

    @Test fun `das Kategorie-Mindestalter hebt je Eintrag an`() {
        val wahl = Sammelwahl(kategorie = KategorieWahl.Gesetzt(ContentCategory.ANIME_MANGA))
        val jung = Sammelpruefung.freigabe(video(ageMin = 6), null, wahl) as Sammelfreigabe.Inhalt
        val alt = Sammelpruefung.freigabe(video(ageMin = 14), null, wahl) as Sammelfreigabe.Inhalt
        assertThat(jung.approval.ageMin).isEqualTo(12)
        assertThat(alt.approval.ageMin).isEqualTo(14)
    }

    @Test fun `die vorgeschlagene Kategorie hebt ein gesetztes Alter ebenfalls an`() {
        val f = Sammelpruefung.freigabe(video(category = "mangaDrawing"), null, Sammelwahl(alter = 4)) as Sammelfreigabe.Inhalt
        assertThat(f.approval.ageMin).isEqualTo(8)
    }

    @Test fun `ein Hoechstalter faellt nie unter das angehobene Mindestalter`() {
        val f = Sammelpruefung.freigabe(video(ageMax = 8), null, Sammelwahl(alter = 10)) as Sammelfreigabe.Inhalt
        assertThat(f.approval.ageMax).isEqualTo(10)
    }

    @Test fun `Kanaele bekommen die gewaehlte Stufe und ihre eigene Vorauswahl`() {
        // Wie im Einzelweg (Kanaleinstufung.vorauswahl): Alter 0 am Kandidaten faellt auf die Quelle zurueck,
        // die Kategorie des Kandidaten gilt.
        val q = CuratedSourceEntity(channelId = "UCk", title = "Kanal", trust = "perVideoReview", defaultAgeMin = 8, defaultCategory = "music")
        val f = Sammelpruefung.freigabe(kanal(category = "knowledge"), q, Sammelwahl(kanalStufe = SourceTrust.TRUSTED_SERIES)) as Sammelfreigabe.Kanal
        assertThat(f.einstufung).isEqualTo(Kanaleinstufung(SourceTrust.TRUSTED_SERIES, 8, ContentCategory.KNOWLEDGE))
    }

    @Test fun `ein Kanal ohne Vorgaben bekommt Alter 6 und gesetzte Werte gelten auch fuer ihn`() {
        val ohne = Sammelpruefung.freigabe(kanal(), null, Sammelwahl()) as Sammelfreigabe.Kanal
        assertThat(ohne.einstufung).isEqualTo(Kanaleinstufung(SourceTrust.PER_VIDEO_REVIEW, 6, null))
        val gesetzt = Sammelpruefung.freigabe(kanal(), null,
            Sammelwahl(alter = 9, kategorie = KategorieWahl.Gesetzt(ContentCategory.ANIME_MANGA))) as Sammelfreigabe.Kanal
        assertThat(gesetzt.einstufung.freigabe()?.ageMin).isEqualTo(12)
    }

    @Test fun `Gesperrt ist in der Sammelwahl nicht waehlbar`() {
        val fehler = runCatching { Sammelwahl(kanalStufe = SourceTrust.BLOCKED) }.exceptionOrNull()
        assertThat(fehler).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test fun `ein Alter ausserhalb von 3 bis 16 ist nicht waehlbar`() {
        assertThat(runCatching { Sammelwahl(alter = 2) }.isFailure).isTrue()
        assertThat(runCatching { Sammelwahl(alter = 17) }.isFailure).isTrue()
    }
}
