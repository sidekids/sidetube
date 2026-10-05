// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.curation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.ContentCategory
import xyz.steier.sidetube.core.model.SourceTrust

/** ADR 0003: die Regel beim Einstufen eines Kanals, ohne Datenbank. */
class KanaleinstufungTest {

    private fun quelle(trust: String = "trustedSeries", alter: Int = 9, kategorie: String? = "knowledge") =
        CuratedSourceEntity(channelId = "UC1", title = "Kanal", trust = trust, defaultAgeMin = alter, defaultCategory = kategorie)

    @Test fun `ohne Quelle die vorsichtige Stufe, Alter 6, keine Kategorie`() {
        assertThat(Kanaleinstufung.vorauswahl(null))
            .isEqualTo(Kanaleinstufung(SourceTrust.PER_VIDEO_REVIEW, 6, null))
    }

    @Test fun `mit Quelle gilt, was sie schon hat`() {
        assertThat(Kanaleinstufung.vorauswahl(quelle()))
            .isEqualTo(Kanaleinstufung(SourceTrust.TRUSTED_SERIES, 9, ContentCategory.KNOWLEDGE))
    }

    @Test fun `eine gesperrte Quelle bleibt vorausgewaehlt gesperrt`() {
        assertThat(Kanaleinstufung.vorauswahl(quelle(trust = "blocked")).trust).isEqualTo(SourceTrust.BLOCKED)
    }

    @Test fun `Alter 0 aus dem Register ist keine Entscheidung und wird 6`() {
        assertThat(Kanaleinstufung.vorauswahl(quelle(alter = 0)).ageMin).isEqualTo(6)
    }

    @Test fun `ein Alter ueber 16 wird auf 16 begrenzt`() {
        assertThat(Kanaleinstufung.vorauswahl(quelle(alter = 18)).ageMin).isEqualTo(16)
    }

    @Test fun `ein vorhandener Eintrag geht der Quelle bei Alter und Kategorie vor`() {
        val eintrag = WhitelistItemEntity("k", "p", "CHANNEL", contentId = "UC1", title = "Kanal", ageMin = 12, category = null)
        val wahl = Kanaleinstufung.vorauswahl(quelle(), eintrag)
        assertThat(wahl.ageMin).isEqualTo(12)
        assertThat(wahl.category).isNull()
        assertThat(wahl.trust).isEqualTo(SourceTrust.TRUSTED_SERIES)
    }

    @Test fun `ein Kandidat mit Alter 0 nimmt das der Quelle`() {
        val eintrag = WhitelistItemEntity("k", "p", "CHANNEL", contentId = "UC1", title = "Kanal", ageMin = 0, category = "knowledge")
        assertThat(Kanaleinstufung.vorauswahl(quelle(alter = 8), eintrag).ageMin).isEqualTo(8)
    }

    @Test fun `die Kategorie hebt das Mindestalter an`() {
        val wahl = Kanaleinstufung(SourceTrust.PER_VIDEO_REVIEW, 6, ContentCategory.ANIME_MANGA)
        assertThat(wahl.kategorieHebtAlterAn).isTrue()
        assertThat(wahl.effektivesMindestalter).isEqualTo(12)
        assertThat(wahl.freigabe()?.ageMin).isEqualTo(12)
        assertThat(wahl.freigabe()?.category).isEqualTo("animeManga")
    }

    @Test fun `ein hoeheres eigenes Alter bleibt stehen`() {
        val wahl = Kanaleinstufung(SourceTrust.PER_VIDEO_REVIEW, 10, ContentCategory.MANGA_DRAWING)
        assertThat(wahl.kategorieHebtAlterAn).isFalse()
        assertThat(wahl.effektivesMindestalter).isEqualTo(10)
    }

    @Test fun `gesperrt heisst nicht aufnehmen, ohne Freigabe`() {
        val wahl = Kanaleinstufung(SourceTrust.BLOCKED, 6, null)
        assertThat(wahl.ergebnis).isEqualTo(Kanaleinstufung.Ergebnis.NICHT_AUFNEHMEN)
        assertThat(wahl.freigabe()).isNull()
    }

    @Test fun `jede andere Stufe heisst aufnehmen und freigeben`() {
        SourceTrust.entries.filter { it != SourceTrust.BLOCKED }.forEach {
            assertThat(Kanaleinstufung(it, 6, null).ergebnis).isEqualTo(Kanaleinstufung.Ergebnis.AUFNEHMEN_UND_FREIGEBEN)
        }
    }

    @Test fun `das Alter bleibt zwischen 3 und 16`() {
        var wahl = Kanaleinstufung(SourceTrust.PER_VIDEO_REVIEW, 4, null)
        repeat(5) { wahl = wahl.juenger() }
        assertThat(wahl.ageMin).isEqualTo(3)
        repeat(20) { wahl = wahl.aelter() }
        assertThat(wahl.ageMin).isEqualTo(16)
    }

    @Test fun `ein Hoechstalter liegt nie unter dem geltenden Mindestalter`() {
        val wahl = Kanaleinstufung(SourceTrust.TRUSTED_SERIES, 6, ContentCategory.NEWS)
        assertThat(wahl.freigabe(ageMax = 7, parentNotes = " ")?.ageMax).isEqualTo(8)
        assertThat(wahl.freigabe(ageMax = 7, parentNotes = " ")?.parentNotes).isNull()
    }
}
