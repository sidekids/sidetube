// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.steier.sidetube.core.curation.ContentBundle
import xyz.steier.sidetube.core.curation.ContentSource
import xyz.steier.sidetube.core.curation.Einzelpruefungsgrund
import xyz.steier.sidetube.core.curation.Kanaleinstufung
import xyz.steier.sidetube.core.curation.KategorieWahl
import xyz.steier.sidetube.core.curation.Sammelwahl
import xyz.steier.sidetube.core.curation.RiskScreen
import xyz.steier.sidetube.core.model.ContentCategory
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.SideTubeDatabase
import xyz.steier.sidetube.core.model.SourceTrust
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.provider.ContentDraft

internal object Content : ContentSource {
    override fun read(path: String): String? =
        Content::class.java.classLoader?.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
    override fun listLibraries() = listOf("general.json", "alter-9-11.json")
}

@RunWith(RobolectricTestRunner::class)
class CurationRepositoryTest {

    private lateinit var db: SideTubeDatabase
    private lateinit var repo: CurationRepository
    private val profile = KidProfileEntity(id = "p1", name = "Mira")

    private fun draft(id: String = "v1", title: String = "Wie entsteht ein Regenbogen", channel: String? = "UC1") =
        ContentDraft(
            type = WhitelistItemType.VIDEO, contentId = id, title = title,
            channelTitle = "Ein Kanal", sourceChannelId = channel
        )

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), SideTubeDatabase::class.java
        ).build()
        db.kidProfiles().insert(profile)
        repo = CurationRepository(
            db.whitelist(), db.sources(), db.reviewEvents(),
            RiskScreen(ContentBundle(Content).riskTerms())
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `ein neuer Inhalt kommt zur Pruefung, nicht freigegeben`() = runTest {
        val item = repo.discover(draft(), profile.id)

        assertThat(item.approvalStatus).isEqualTo("reviewRequired")
        assertThat(db.whitelist().observeApproved(profile.id).first()).isEmpty()
        assertThat(repo.observePending(profile.id).first()).hasSize(1)
    }

    @Test
    fun `ein harter Treffer wird sofort abgelehnt`() = runTest {
        val item = repo.discover(draft(title = "Hentai Compilation"), profile.id)

        assertThat(item.approvalStatus).isEqualTo("rejected")
        assertThat(repo.history("v1").first().decision).isEqualTo("autoRejected")
    }

    @Test
    fun `aus einer gesperrten Quelle kommt nichts herein`() = runTest {
        repo.ensureSources(emptyList())
        db.sources().insertMissing(listOf(CuratedSourceEntity(channelId = "UC1", title = "X", trust = "blocked")))

        val fehler = runCatching { repo.discover(draft(), profile.id) }.exceptionOrNull()

        assertThat(fehler).isInstanceOf(DiscoverError.BlockedSource::class.java)
    }

    @Test
    fun `dasselbe Video wird nicht zweimal aufgenommen`() = runTest {
        repo.discover(draft(), profile.id)

        val fehler = runCatching { repo.discover(draft(), profile.id) }.exceptionOrNull()

        assertThat(fehler).isInstanceOf(DiscoverError.Duplicate::class.java)
    }

    @Test
    fun `die Vorgaben der Quelle werden uebernommen`() = runTest {
        db.sources().insertMissing(listOf(CuratedSourceEntity(
            channelId = "UC1", title = "Die Maus", trust = "trustedChildSource",
            defaultAgeMin = 5, defaultCategory = "knowledge"
        )))

        val item = repo.discover(draft(), profile.id)

        assertThat(item.ageMin).isEqualTo(5)
        assertThat(item.category).isEqualTo("knowledge")
    }

    @Test
    fun `erst die Freigabe macht sichtbar, und sie steht im Verlauf`() = runTest {
        val item = repo.discover(draft(), profile.id)

        repo.approve(item, Approval(ageMin = 6, category = "knowledge", parentNotes = "geprüft"), actor = "Eltern")

        val sichtbar = db.whitelist().observeApproved(profile.id).first()
        assertThat(sichtbar).hasSize(1)
        assertThat(sichtbar.single().ageMin).isEqualTo(6)
        assertThat(sichtbar.single().approvedBy).isEqualTo("Eltern")
        assertThat(repo.history("v1").map { it.decision }).containsExactly("approved", "discovered").inOrder()
    }

    @Test
    fun `Freigegebenes laesst sich zurueck in die Pruefung geben`() = runTest {
        val item = repo.discover(draft(), profile.id)
        repo.approve(item, Approval(ageMin = 6), actor = "Eltern")
        val freigegeben = db.whitelist().observeApproved(profile.id).first().single()

        repo.defer(freigegeben, actor = "Eltern")

        assertThat(db.whitelist().observeApproved(profile.id).first()).isEmpty()
        assertThat(repo.observePending(profile.id).first()).hasSize(1)
    }

    @Test
    fun `eine Elternentscheidung ueberlebt ein spaeteres Quellenregister`() = runTest {
        val register = ContentBundle(Content).sources()
        repo.ensureSources(register)
        val quelle = checkNotNull(repo.source(register.first().channelId))

        repo.setTrust(quelle, SourceTrust.BLOCKED, actor = "Eltern")
        repo.ensureSources(register)   // Register wird erneut eingespielt

        assertThat(repo.source(quelle.channelId)?.trust).isEqualTo("blocked")
    }

    @Test
    fun `das Quellenregister aus content laesst sich einspielen`() = runTest {
        val register = ContentBundle(Content).sources()

        repo.ensureSources(register)

        assertThat(repo.observeSources().first()).hasSize(register.size)
    }

    // ── ADR 0003: Kanal beim Hinzufuegen einstufen ────────────────────────────────────────

    private fun kanal(id: String = "UCk", title: String = "Ein Kanal") = ContentDraft(
        type = WhitelistItemType.CHANNEL, contentId = id, title = title, channelTitle = title, sourceChannelId = id
    )

    @Test
    fun `ein eingestufter Kanal ist gleich freigegeben, die Quelle traegt die Wahl`() = runTest {
        val wahl = Kanaleinstufung(SourceTrust.TRUSTED_SERIES, 6, ContentCategory.MANGA_DRAWING)

        val ergebnis = repo.nimmKanalAuf(kanal(), profile.id, wahl, actor = "Eltern")

        val item = (ergebnis as KanalErgebnis.Freigegeben).item
        assertThat(item.approvalStatus).isEqualTo("approved")
        assertThat(item.ageMin).isEqualTo(8)          // Kategorie „Manga zeichnen" ab 8
        assertThat(item.category).isEqualTo("mangaDrawing")
        val quelle = checkNotNull(repo.source("UCk"))
        assertThat(quelle.trust).isEqualTo("trustedSeries")
        assertThat(quelle.defaultAgeMin).isEqualTo(8)
        assertThat(quelle.defaultCategory).isEqualTo("mangaDrawing")
        assertThat(repo.observePending(profile.id).first()).isEmpty()
        val verlauf = repo.history("source:UCk").single()
        assertThat(verlauf.decision).isEqualTo("trustChanged")
        assertThat(verlauf.actor).isEqualTo("Eltern")
        assertThat(repo.history("UCk").map { it.decision }).containsExactly("approved", "discovered").inOrder()
    }

    @Test
    fun `gesperrt legt keinen Eintrag an, merkt sich aber die Sperre`() = runTest {
        val ergebnis = repo.nimmKanalAuf(kanal(), profile.id, Kanaleinstufung(SourceTrust.BLOCKED, 6, null), actor = "Eltern")

        assertThat(ergebnis).isEqualTo(KanalErgebnis.Gesperrt)
        assertThat(db.whitelist().find(profile.id, "UCk")).isNull()
        assertThat(repo.source("UCk")?.trust).isEqualTo("blocked")
        assertThat(repo.history("source:UCk").single().decision).isEqualTo("blockedSource")
        // Kuenftige Links dieses Kanals werden abgewiesen.
        val fehler = runCatching { repo.discover(draft(channel = "UCk"), profile.id) }.exceptionOrNull()
        assertThat(fehler).isInstanceOf(DiscoverError.BlockedSource::class.java)
    }

    @Test
    fun `eine vorhandene Quelle wird aktualisiert, nicht verdoppelt`() = runTest {
        db.sources().insertMissing(listOf(CuratedSourceEntity(channelId = "UCk", title = "Register-Titel", trust = "perVideoReview")))

        repo.nimmKanalAuf(kanal(), profile.id, Kanaleinstufung(SourceTrust.TRUSTED_CHILD_SOURCE, 9, null), actor = "Eltern")

        val quellen = repo.observeSources().first()
        assertThat(quellen).hasSize(1)
        assertThat(quellen.single().title).isEqualTo("Register-Titel")
        assertThat(quellen.single().trust).isEqualTo("trustedChildSource")
        assertThat(quellen.single().defaultAgeMin).isEqualTo(9)
    }

    @Test
    fun `sperren laesst Alter und Kategorie der Quelle stehen`() = runTest {
        db.sources().insertMissing(listOf(CuratedSourceEntity(channelId = "UCk", title = "K", defaultAgeMin = 9, defaultCategory = "knowledge")))

        repo.nimmKanalAuf(kanal(), profile.id, Kanaleinstufung(SourceTrust.BLOCKED, 3, null), actor = "Eltern")

        val quelle = checkNotNull(repo.source("UCk"))
        assertThat(quelle.trust).isEqualTo("blocked")
        assertThat(quelle.defaultAgeMin).isEqualTo(9)
        assertThat(quelle.defaultCategory).isEqualTo("knowledge")
    }

    @Test
    fun `ein Kanal-Kandidat aus der Pruefliste wird mit Stufe freigegeben oder abgelehnt`() = runTest {
        val kandidat = repo.discover(kanal(), profile.id)
        val zweiter = repo.discover(kanal("UCz", "Zweiter"), profile.id)

        val frei = repo.stufeKanalEin(kandidat, Kanaleinstufung(SourceTrust.PER_VIDEO_REVIEW, 7, null), "Eltern",
            ageMax = 10, parentNotes = "ok")
        val gesperrt = repo.stufeKanalEin(zweiter, Kanaleinstufung(SourceTrust.BLOCKED, 6, null), "Eltern")

        val item = (frei as KanalErgebnis.Freigegeben).item
        assertThat(item.approvalStatus).isEqualTo("approved")
        assertThat(item.ageMin).isEqualTo(7)
        assertThat(item.ageMax).isEqualTo(10)
        assertThat(item.parentNotes).isEqualTo("ok")
        assertThat(gesperrt).isEqualTo(KanalErgebnis.Gesperrt)
        assertThat(db.whitelist().find(profile.id, "UCz")?.approvalStatus).isEqualTo("rejected")
        assertThat(repo.observePending(profile.id).first()).isEmpty()
        assertThat(repo.source("UCz")?.trust).isEqualTo("blocked")
    }

    @Test
    fun `ein abgelehnter Kanal wird per Link nicht still freigegeben`() = runTest {
        val vorhanden = repo.discover(kanal(), profile.id)
        repo.reject(vorhanden, actor = "Eltern")

        val ergebnis = repo.nimmKanalAuf(kanal(), profile.id, Kanaleinstufung(SourceTrust.TRUSTED_SERIES, 6, null), "Eltern")

        assertThat(ergebnis).isInstanceOf(KanalErgebnis.SchonAbgelehnt::class.java)
        assertThat(db.whitelist().find(profile.id, "UCk")?.approvalStatus).isEqualTo("rejected")
    }

    @Test
    fun `ein Kanal mit hartem Filtertreffer wird nicht freigegeben`() = runTest {
        val ergebnis = repo.nimmKanalAuf(kanal(title = "Hentai Compilation"), profile.id,
            Kanaleinstufung(SourceTrust.TRUSTED_SERIES, 6, null), "Eltern")

        assertThat(ergebnis).isInstanceOf(KanalErgebnis.VomFilterAbgelehnt::class.java)
        assertThat(db.whitelist().observeApproved(profile.id).first()).isEmpty()
    }

    // ── Sammelpruefung (ADR 0004) ──

    @Test
    fun `die Sammelfreigabe gibt jeden sammelbaren Eintrag mit eigenem Verlaufseintrag frei`() = runTest {
        val a = repo.discover(draft("v1", "Wie entsteht ein Regenbogen"), profile.id)
        val b = repo.discover(draft("v2", "Wie baut man ein Vogelhaus"), profile.id)
        val riskant = repo.discover(draft("v3", "Krieg erklärt"), profile.id)

        val ergebnis = repo.sammelFreigeben(listOf(a, b, riskant),
            Sammelwahl(kategorie = KategorieWahl.Gesetzt(ContentCategory.MANGA_DRAWING)), "Eltern")

        assertThat(ergebnis.entschieden.map { it.contentId }).containsExactly("v1", "v2")
        assertThat(ergebnis.einzeln.values).containsExactly(Einzelpruefungsgrund.FILTERTREFFER)
        val frei = db.whitelist().observeApproved(profile.id).first()
        assertThat(frei.map { it.contentId }).containsExactly("v1", "v2")
        assertThat(frei.map { it.ageMin }.toSet()).containsExactly(8)
        assertThat(frei.map { it.category }.toSet()).containsExactly("mangaDrawing")
        listOf("v1", "v2").forEach { id ->
            val event = repo.history(id).single { it.decision == "approved" }
            assertThat(event.actor).isEqualTo("Eltern")
            assertThat(event.note).isEqualTo("Sammelprüfung")
        }
        assertThat(db.whitelist().find(profile.id, "v3")?.approvalStatus).isEqualTo("reviewRequired")
    }

    @Test
    fun `Kanaele in der Sammelfreigabe bekommen die gewaehlte Stufe in ihrer Quelle`() = runTest {
        val k = repo.discover(kanal(), profile.id)

        repo.sammelFreigeben(listOf(k), Sammelwahl(kanalStufe = SourceTrust.TRUSTED_SERIES), "Eltern")

        assertThat(db.whitelist().find(profile.id, "UCk")?.approvalStatus).isEqualTo("approved")
        assertThat(repo.source("UCk")?.trust).isEqualTo("trustedSeries")
        assertThat(repo.history("UCk").single { it.decision == "approved" }.note).isEqualTo("Sammelprüfung")
    }

    @Test
    fun `Eintraege aus nur-fuer-Eltern-Quellen bleiben bei der Sammelpruefung stehen`() = runTest {
        db.sources().insertMissing(listOf(CuratedSourceEntity(channelId = "UC1", title = "X", trust = "parentOnly")))
        val item = repo.discover(draft(), profile.id)

        val frei = repo.sammelFreigeben(listOf(item), Sammelwahl(), "Eltern")
        val ab = repo.sammelAblehnen(listOf(item), "Eltern")

        assertThat(frei.einzeln.values).containsExactly(Einzelpruefungsgrund.QUELLE_NUR_ELTERN)
        assertThat(ab.entschieden).isEmpty()
        assertThat(db.whitelist().find(profile.id, "v1")?.approvalStatus).isEqualTo("reviewRequired")
    }

    @Test
    fun `ein harter Treffer wird auch zurueckgestellt nicht gesammelt freigegeben`() = runTest {
        val item = repo.discover(draft(title = "Hentai Compilation"), profile.id)
        repo.defer(item, "Eltern")

        val ergebnis = repo.sammelFreigeben(listOf(item), Sammelwahl(), "Eltern")

        assertThat(ergebnis.einzeln.values).containsExactly(Einzelpruefungsgrund.FILTERTREFFER)
        assertThat(db.whitelist().observeApproved(profile.id).first()).isEmpty()
    }

    @Test
    fun `die Sammelablehnung lehnt jeden Eintrag einzeln ab, mit Vermerk`() = runTest {
        val a = repo.discover(draft("v1"), profile.id)
        val b = repo.discover(draft("v2", "Wie baut man ein Vogelhaus"), profile.id)

        val ergebnis = repo.sammelAblehnen(listOf(a, b), "Eltern")

        assertThat(ergebnis.entschieden).hasSize(2)
        assertThat(repo.observePending(profile.id).first()).isEmpty()
        listOf("v1", "v2").forEach { id ->
            val event = repo.history(id).single { it.decision == "rejected" }
            assertThat(event.note).isEqualTo("Sammelprüfung")
        }
    }
}
