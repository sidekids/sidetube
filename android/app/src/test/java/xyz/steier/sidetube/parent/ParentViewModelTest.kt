// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import xyz.steier.sidetube.core.curation.ContentBundle
import xyz.steier.sidetube.core.curation.ContentSource
import xyz.steier.sidetube.core.curation.RiskScreen
import xyz.steier.sidetube.core.db.*
import xyz.steier.sidetube.core.model.AgeBand
import xyz.steier.sidetube.core.net.HttpClient
import xyz.steier.sidetube.core.net.HttpResponse
import xyz.steier.sidetube.core.provider.ChannelPageSource
import xyz.steier.sidetube.core.provider.OEmbedSource
import xyz.steier.sidetube.core.provider.YouTubeResolver
import xyz.steier.sidetube.core.repo.CurationRepository
import xyz.steier.sidetube.core.repo.ProfileRepository
import xyz.steier.sidetube.core.repo.StarterPackService
import xyz.steier.sidetube.core.repo.WhitelistRepository

/** Elternbereich ohne Room und ohne Geraet: kontrollierte DAOs, die mitschreiben. */
private class ParentFixture {
    val profiles = MutableStateFlow(listOf(KidProfileEntity("p", "Mila", dailyLimitMinutes = null)))
    val items = MutableStateFlow(listOf(
        WhitelistItemEntity("a", "p", "VIDEO", contentId = "vid", title = "Video A")
    ))
    val events = mutableListOf<ReviewEventEntity>()

    private val profileDao = object : KidProfileDao {
        override fun observeAll() = profiles
        override suspend fun byId(id: String) = profiles.value.find { it.id == id }
        override suspend fun insert(profile: KidProfileEntity) { profiles.value += profile }
        override suspend fun update(profile: KidProfileEntity) {
            profiles.value = profiles.value.map { if (it.id == profile.id) profile else it }
        }
        override suspend fun delete(profile: KidProfileEntity) { profiles.value -= profile }
    }
    private val whitelistDao = object : WhitelistDao {
        override fun observeByProfile(profileId: String) = items
        override fun observeApproved(profileId: String) = items.map { r -> r.filter { it.approvalStatus == "approved" } }
        override fun observePending(profileId: String) = items.map { r -> r.filter { it.approvalStatus != "approved" } }
        override suspend fun find(profileId: String, contentId: String) = items.value.find { it.contentId == contentId }
        override suspend fun insert(item: WhitelistItemEntity) { items.value += item }
        override suspend fun update(item: WhitelistItemEntity) { items.value = items.value.map { if (it.id == item.id) item else it } }
        override suspend fun delete(item: WhitelistItemEntity) { items.value = items.value.filterNot { it.id == item.id } }
    }
    private val sourceDao = object : CuratedSourceDao {
        override fun observeAll() = flowOf(emptyList<CuratedSourceEntity>())
        override suspend fun byChannel(channelId: String): CuratedSourceEntity? = null
        override suspend fun insertMissing(sources: List<CuratedSourceEntity>) { sourcesAdded += sources }
        override suspend fun update(source: CuratedSourceEntity) = Unit
    }
    private val reviewDao = object : ReviewEventDao {
        override suspend fun insert(event: ReviewEventEntity) { events += event }
        override suspend fun forContent(contentId: String) =
            events.filter { it.contentId == contentId }.sortedByDescending { it.at }
        override suspend fun deleteForProfile(profileId: String) { events.removeAll { it.profileId == profileId } }
    }
    private val sessions = object : PlaybackSessionDao {
        override suspend fun begin(session: PlaybackSessionEntity) = Unit
        override suspend fun pending(profileId: String): PlaybackSessionEntity? = null
        override fun observePending() = flowOf(emptyList<PlaybackSessionEntity>())
        override suspend fun finish(profileId: String, token: String) = Unit
        override suspend fun acknowledgeByParent(profileId: String) = Unit
    }
    private val content = ContentBundle(object : ContentSource {
        override fun read(path: String): String? = null
        override fun listLibraries() = emptyList<String>()
    })
    /** oEmbed-Antwort fuer den Link-Ablauf; alles andere gilt als offline. */
    var oembed: String? = null
    /** Kanalseite fuer den Kanal-Link (ADR 0003). */
    var kanalseite: String? = null
    private val offline = object : HttpClient {
        override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
            oembed?.takeIf { url.contains("oembed") }?.let { HttpResponse(200, it) }
                ?: kanalseite?.takeIf { url.contains("youtube.com/@") || url.contains("/channel/") }?.let { HttpResponse(200, it) }
                ?: error("kein Netz im Test")
    }
    val wishes = xyz.steier.sidetube.FakeWishDao()
    val sourcesAdded = mutableListOf<CuratedSourceEntity>()
    private var clock = 1_000L
    val curation = CurationRepository(whitelistDao, sourceDao, reviewDao, RiskScreen(content.riskTerms()), now = { clock++ })
    private val profileRepo = ProfileRepository(profileDao, reviewDao)

    fun model() = ParentViewModel(
        profileRepo, WhitelistRepository(whitelistDao, sourceDao), curation, sessions,
        StarterPackService(content, curation, profileRepo),
        YouTubeResolver(OEmbedSource(offline), ChannelPageSource(offline)),
        seedSources = {},
        wuensche = wunschRepo,
        texte = xyz.steier.sidetube.TestTexte
    )
    val wunschRepo = xyz.steier.sidetube.core.repo.WunschRepository(wishes, reviewDao,
        now = { java.time.Instant.ofEpochMilli(clock) })
}

@OptIn(ExperimentalCoroutinesApi::class)
class ParentViewModelTest {

    private suspend fun TestScope.withModel(block: suspend TestScope.(ParentFixture, ParentViewModel) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val f = ParentFixture()
        val vm = f.model()
        try {
            runCurrent()
            this.block(f, vm)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun `saving the editor writes limit, bedtime and switches to the profile`() = runTest {
        withModel { f, vm ->
            val draft = ProfileDraft.from(f.profiles.value.single())
                .copy(name = "  Mila B ", limitEnabled = true, limitMinutes = 60, ageBand = AgeBand.TWEEN,
                    allowShorts = true, autoplayNext = true)
                .stepLimit(-1).stepLimit(-1).stepLimit(-1).stepLimit(-1).stepLimit(-1).stepLimit(-1)
                .stepBedtimeStart(1)

            vm.saveProfile("p", draft); runCurrent()

            val saved = f.profiles.value.single()
            assertThat(saved.name).isEqualTo("Mila B")
            assertThat(saved.dailyLimitMinutes).isEqualTo(30)
            assertThat(saved.ageBand).isEqualTo("tween")
            assertThat(saved.allowShorts).isTrue()
            assertThat(saved.autoplayNext).isTrue()
            assertThat(saved.bedtimeStartMinutes).isEqualTo(20 * 60 + 15)
            assertThat(vm.state.value.message).isEqualTo("Profil „Mila B“ gesichert.")
        }
    }

    @Test fun `switching the limit off stores no limit`() = runTest {
        withModel { f, vm ->
            f.profiles.value = listOf(f.profiles.value.single().copy(dailyLimitMinutes = 45))
            vm.saveProfile("p", ProfileDraft.from(f.profiles.value.single()).copy(limitEnabled = false)); runCurrent()
            assertThat(f.profiles.value.single().dailyLimitMinutes).isNull()
        }
    }

    @Test fun `saving keeps a bedtime exception set after the editor was opened`() = runTest {
        withModel { f, vm ->
            val draft = ProfileDraft.from(f.profiles.value.single())
            f.profiles.value = listOf(f.profiles.value.single().copy(bedtimeSkipUntil = 99_999L))
            vm.saveProfile("p", draft); runCurrent()
            assertThat(f.profiles.value.single().bedtimeSkipUntil).isEqualTo(99_999L)
        }
    }

    @Test fun `an empty name is refused and nothing is written`() = runTest {
        withModel { f, vm ->
            vm.saveProfile("p", ProfileDraft.from(f.profiles.value.single()).copy(name = "  ")); runCurrent()
            assertThat(f.profiles.value.single().name).isEqualTo("Mila")
            assertThat(vm.state.value.message).isEqualTo("Bitte einen Namen eingeben.")
        }
    }

    @Test fun `later keeps the item pending and records a deferred event`() = runTest {
        withModel { f, vm ->
            vm.later(f.items.value.single()); runCurrent()
            assertThat(f.items.value.single().approvalStatus).isEqualTo("reviewRequired")
            assertThat(f.events.map { it.decision }).containsExactly("deferred")
            assertThat(f.events.single().actor).isEqualTo("Eltern")
            assertThat(vm.state.value.message).isEqualTo("„Video A“ zurückgestellt.")
        }
    }

    @Test fun `history shows this profile's events newest first, not other profiles'`() = runTest {
        withModel { f, vm ->
            val item = f.items.value.single()
            f.curation.approve(item, xyz.steier.sidetube.core.repo.Approval(ageMin = 6), actor = "Eltern")
            f.curation.defer(item, actor = "Eltern")
            f.events += ReviewEventEntity(contentId = "vid", profileId = "anderes", decision = "rejected",
                actor = "Eltern", at = 5_000L)

            vm.loadHistory(item); runCurrent()

            assertThat(vm.state.value.verlauf.map { it.decision }).containsExactly("deferred", "approved").inOrder()
        }
    }

    @Test fun `pin change is confirmed with a message`() = runTest {
        withModel { _, vm ->
            vm.pinChanged()
            assertThat(vm.state.value.message).isEqualTo("Die PIN ist geändert.")
        }
    }

    // ── Wuensche (ADR 0001) ───────────────────────────────────────────────────────────────

    private suspend fun ParentFixture.wunsch(entwurf: xyz.steier.sidetube.core.curation.WunschEntwurf) =
        (wunschRepo.wuensche(entwurf, "p", "Mila") as xyz.steier.sidetube.core.repo.WunschErgebnis.Geschickt).wunsch

    @Test fun `open wishes come first in the review list`() = runTest {
        withModel { f, vm ->
            f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.Thema("Dinos"))
            vm.openProfile(f.profiles.value.single()); runCurrent()
            assertThat(vm.state.value.wuensche.map { it.topic }).containsExactly("Dinos")
        }
    }

    @Test fun `approving a new episode creates an approved entry and fulfils the wish with a reply`() = runTest {
        withModel { f, vm ->
            val w = f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.NeueFolge("fol12345678", "Mondflug", "UCn", "NASA"))
            vm.wunschFreigeben(w, "Viel Spaß!"); runCurrent()
            val item = f.items.value.single { it.contentId == "fol12345678" }
            assertThat(item.approvalStatus).isEqualTo("approved")
            assertThat(item.sourceChannelId).isEqualTo("UCn")
            val danach = f.wishes.rows.value.single()
            assertThat(danach.status).isEqualTo("erfuellt")
            assertThat(danach.parentReply).isEqualTo("Viel Spaß!")
            assertThat(danach.resultContentId).isEqualTo("fol12345678")
            assertThat(f.events.filter { it.contentId == "fol12345678" }.map { it.decision })
                .containsExactly("wished", "discovered", "approved", "wishFulfilled").inOrder()
        }
    }

    @Test fun `reject, discuss and done keep an optional reply`() = runTest {
        withModel { f, vm ->
            val a = f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.Thema("Dinos"))
            val b = f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.Thema("Pferde"))
            vm.wunschEntscheiden(a, xyz.steier.sidetube.core.curation.WunschStatus.BESPRECHEN, "Erzähl mir mehr"); runCurrent()
            vm.wunschEntscheiden(b, xyz.steier.sidetube.core.curation.WunschStatus.ABGELEHNT, null); runCurrent()
            val nachher = f.wishes.rows.value.associateBy { it.topic }
            assertThat(nachher["Dinos"]?.status).isEqualTo("besprechen")
            assertThat(nachher["Dinos"]?.parentReply).isEqualTo("Erzähl mir mehr")
            assertThat(nachher["Pferde"]?.status).isEqualTo("abgelehnt")
            assertThat(nachher["Pferde"]?.parentReply).isNull()
            // „Besprechen" bleibt offen und kann danach erledigt werden; „abgelehnt" nicht mehr.
            vm.wunschEntscheiden(nachher["Dinos"]!!, xyz.steier.sidetube.core.curation.WunschStatus.ERFUELLT, null); runCurrent()
            vm.wunschEntscheiden(nachher["Pferde"]!!, xyz.steier.sidetube.core.curation.WunschStatus.ERFUELLT, null); runCurrent()
            assertThat(f.wishes.rows.value.associateBy { it.topic }.mapValues { it.value.status })
                .containsExactly("Dinos", "erfuellt", "Pferde", "abgelehnt")
            assertThat(vm.state.value.message).isEqualTo("Der Wunsch ist schon entschieden.")
        }
    }

    @Test fun `a link for a topic wish goes through preview and fulfils the wish on approval`() = runTest {
        withModel { f, vm ->
            f.oembed = """{"title":"Dinos für Kinder","author_name":"Ein Kanal"}"""
            val w = f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.Thema("Dinos"))
            vm.wunschLink(w, "https://www.youtube.com/watch?v=Z4C82eyhwgU"); runCurrent()
            assertThat(vm.state.value.vorschau?.title).isEqualTo("Dinos für Kinder")
            vm.nimmVorschauAuf("p"); runCurrent()
            val item = f.items.value.single { it.contentId == "Z4C82eyhwgU" }
            assertThat(item.approvalStatus).isEqualTo("reviewRequired")
            assertThat(f.wishes.rows.value.single().status).isEqualTo("offen")
            assertThat(f.wishes.rows.value.single().resultContentId).isEqualTo("Z4C82eyhwgU")

            vm.approve(item, xyz.steier.sidetube.core.repo.Approval(ageMin = 6)); runCurrent()
            assertThat(f.wishes.rows.value.single().status).isEqualTo("erfuellt")
            assertThat(vm.state.value.message).isEqualTo("„Dinos für Kinder“ ist freigegeben – Wunsch erfüllt.")
        }
    }

    @Test fun `check channel adds a candidate, or opens the trust levels when it is already listed`() = runTest {
        withModel { f, vm ->
            val w = f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.MehrDavon("vid", "Video A", "UCk", "Ein Kanal"))
            var quellen = 0
            vm.wunschKanalPruefen(w) { quellen++ }; runCurrent()
            val kanal = f.items.value.single { it.contentId == "UCk" }
            assertThat(kanal.type).isEqualTo("CHANNEL")
            assertThat(kanal.approvalStatus).isEqualTo("reviewRequired")
            assertThat(quellen).isEqualTo(0)

            vm.wunschKanalPruefen(w) { quellen++ }; runCurrent()
            assertThat(quellen).isEqualTo(1)
            assertThat(f.sourcesAdded.map { it.channelId to it.trust }).containsExactly("UCk" to "perVideoReview")
            assertThat(f.wishes.rows.value.single().status).isEqualTo("offen")   // erledigt erst ausdrücklich
        }
    }

    @Test fun `check channel without a known channel says so instead of guessing`() = runTest {
        withModel { f, vm ->
            val w = f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.MehrDavon("vid", "Video A", null, "Ein Kanal"))
            vm.wunschKanalPruefen(w) { error("keine Stufe ohne Kanal") }; runCurrent()
            assertThat(vm.state.value.message).startsWith("Der Kanal zu diesem Video ließ sich nicht finden")
            assertThat(f.items.value.none { it.type == "CHANNEL" }).isTrue()
        }
    }

    @Test fun `approve on a wish does not approve an entry that is already rejected`() = runTest {
        withModel { f, vm ->
            f.items.value += WhitelistItemEntity("r", "p", "VIDEO", contentId = "fol12345678", title = "Mondflug",
                approvalStatus = "rejected", editorialNotes = "Filter: Krieg")
            val w = f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.NeueFolge("fol12345678", "Mondflug", "UCn", "NASA"))
            vm.wunschFreigeben(w, null); runCurrent()
            assertThat(f.items.value.single { it.contentId == "fol12345678" }.approvalStatus).isEqualTo("rejected")
            assertThat(f.wishes.rows.value.single().status).isEqualTo("offen")
            assertThat(vm.state.value.message).startsWith("„Mondflug“ ist schon abgelehnt (Filter: Krieg)")

            // Wartet der vorhandene Eintrag nur auf Pruefung, gibt der Knopf ihn frei – wie ohne Eintrag.
            f.items.value = f.items.value.map { if (it.id == "r") it.copy(approvalStatus = "reviewRequired") else it }
            vm.wunschFreigeben(w, null); runCurrent()
            assertThat(f.items.value.single { it.contentId == "fol12345678" }.approvalStatus).isEqualTo("approved")
            assertThat(f.wishes.rows.value.single().status).isEqualTo("erfuellt")
        }
    }

    // ── Kanal beim Hinzufuegen einstufen (ADR 0003) ──────────────────────────────────────────

    private val kanalHtml = """<meta itemprop="identifier" content="UCmaus"><meta property="og:title" content="Die Maus">"""

    @Test fun `a channel link is classified in the preview and approved without the review list`() = runTest {
        withModel { f, vm ->
            f.kanalseite = kanalHtml
            val w = f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.Thema("Maus"))
            vm.wunschLink(w, "https://www.youtube.com/@diemaus"); runCurrent()
            assertThat(vm.state.value.vorschau?.type).isEqualTo(xyz.steier.sidetube.core.model.WhitelistItemType.CHANNEL)

            vm.nimmKanalAuf("p", xyz.steier.sidetube.core.curation.Kanaleinstufung(
                xyz.steier.sidetube.core.model.SourceTrust.TRUSTED_SERIES, 6,
                xyz.steier.sidetube.core.model.ContentCategory.MANGA_DRAWING)); runCurrent()

            val kanal = f.items.value.single { it.contentId == "UCmaus" }
            assertThat(kanal.approvalStatus).isEqualTo("approved")
            assertThat(kanal.ageMin).isEqualTo(8)
            assertThat(kanal.category).isEqualTo("mangaDrawing")
            assertThat(f.sourcesAdded.map { Triple(it.channelId, it.trust, it.defaultAgeMin) })
                .containsExactly(Triple("UCmaus", "trustedSeries", 8))
            assertThat(f.events.single { it.contentId == "source:UCmaus" }.actor).isEqualTo("Eltern")
            assertThat(f.wishes.rows.value.single().status).isEqualTo("erfuellt")
            assertThat(f.wishes.rows.value.single().resultContentId).isEqualTo("UCmaus")
            assertThat(vm.state.value.vorschau).isNull()
            assertThat(vm.state.value.message).isEqualTo("„Die Maus“ ist eingestuft und freigegeben – Wunsch erfüllt.")
        }
    }

    @Test fun `blocking a channel in the preview adds nothing and leaves the wish open`() = runTest {
        withModel { f, vm ->
            f.kanalseite = kanalHtml
            val w = f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.Thema("Maus"))
            vm.wunschLink(w, "https://www.youtube.com/@diemaus"); runCurrent()

            vm.nimmKanalAuf("p", xyz.steier.sidetube.core.curation.Kanaleinstufung(
                xyz.steier.sidetube.core.model.SourceTrust.BLOCKED, 6, null)); runCurrent()

            assertThat(f.items.value.none { it.contentId == "UCmaus" }).isTrue()
            assertThat(f.sourcesAdded.single().trust).isEqualTo("blocked")
            assertThat(f.wishes.rows.value.single().status).isEqualTo("offen")
            assertThat(f.wishes.rows.value.single().resultContentId).isNull()
            assertThat(vm.state.value.message).startsWith("„Die Maus“ ist gesperrt")
        }
    }

    @Test fun `a channel candidate from the review list is approved with its trust level`() = runTest {
        withModel { f, vm ->
            val w = f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.MehrDavon("vid", "Video A", "UCk", "Ein Kanal"))
            vm.wunschKanalPruefen(w) {}; runCurrent()
            val kandidat = f.items.value.single { it.contentId == "UCk" }

            vm.stufeKanalEin(kandidat, xyz.steier.sidetube.core.curation.Kanaleinstufung(
                xyz.steier.sidetube.core.model.SourceTrust.TRUSTED_CHILD_SOURCE, 9, null), ageMax = null, notes = null); runCurrent()

            assertThat(f.items.value.single { it.contentId == "UCk" }.approvalStatus).isEqualTo("approved")
            assertThat(f.items.value.single { it.contentId == "UCk" }.ageMin).isEqualTo(9)
            assertThat(f.sourcesAdded.single().trust).isEqualTo("trustedChildSource")
            assertThat(vm.state.value.message).isEqualTo("„Ein Kanal“ ist eingestuft und freigegeben.")
        }
    }

    @Test fun `deleting a profile removes its history entries including wish texts`() = runTest {
        withModel { f, vm ->
            f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.Thema("Geheimes Thema"))
            f.events += ReviewEventEntity(contentId = "source:UC1", profileId = null, decision = "trustChanged", actor = "Eltern", at = 1)
            vm.deleteProfile(f.profiles.value.single()); runCurrent()
            assertThat(f.profiles.value).isEmpty()
            assertThat(f.events.map { it.contentId }).containsExactly("source:UC1")
        }
    }

    // ── Sammelpruefung (ADR 0004) ────────────────────────────────────────────────────────────

    @Test fun `bulk approval fulfils wishes and leaves risky entries for a single review`() = runTest {
        withModel { f, vm ->
            f.items.value += WhitelistItemEntity("b", "p", "VIDEO", contentId = "vidB", title = "Video B", ageMin = 7)
            f.items.value += WhitelistItemEntity("c", "p", "VIDEO", contentId = "vidC", title = "Video C", sensitiveTopics = "war")
            val w = f.wunsch(xyz.steier.sidetube.core.curation.WunschEntwurf.Thema("Dinos"))
            f.wunschRepo.verknuepfe(w, "vidB", "Eltern")
            assertThat(vm.sammelGrund(f.items.value.single { it.id == "c" }))
                .isEqualTo(xyz.steier.sidetube.core.curation.Einzelpruefungsgrund.FILTERTREFFER)

            vm.sammelFreigeben(f.items.value, xyz.steier.sidetube.core.curation.Sammelwahl()); runCurrent()

            val nachher = f.items.value.associateBy { it.id }
            assertThat(nachher["a"]?.approvalStatus).isEqualTo("approved")
            assertThat(nachher["b"]?.approvalStatus).isEqualTo("approved")
            assertThat(nachher["b"]?.ageMin).isEqualTo(7)
            assertThat(nachher["c"]?.approvalStatus).isEqualTo("reviewRequired")
            assertThat(f.wishes.rows.value.single().status).isEqualTo("erfuellt")
            assertThat(f.events.filter { it.decision == "approved" }.map { it.actor to it.note })
                .containsExactly("Eltern" to "Sammelprüfung", "Eltern" to "Sammelprüfung")
            assertThat(vm.state.value.message).isEqualTo("2 Einträge freigegeben – 1 Wunsch erfüllt. 1 braucht eine Einzelprüfung.")
        }
    }

    @Test fun `bulk rejection rejects each entry`() = runTest {
        withModel { f, vm ->
            vm.sammelAblehnen(f.items.value); runCurrent()
            assertThat(f.items.value.single().approvalStatus).isEqualTo("rejected")
            assertThat(vm.state.value.message).isEqualTo("1 Eintrag abgelehnt.")
        }
    }

    @Test fun `the hint names how many need a single review and why`() {
        val g = xyz.steier.sidetube.core.curation.Einzelpruefungsgrund.FILTERTREFFER
        val n = xyz.steier.sidetube.core.curation.Einzelpruefungsgrund.QUELLE_NUR_ELTERN
        assertThat(ParentLabels.einzelpruefungHinweis(emptyList(), xyz.steier.sidetube.TestTexte)).isNull()
        assertThat(ParentLabels.einzelpruefungHinweis(listOf(g, n, g), xyz.steier.sidetube.TestTexte))
            .isEqualTo("3 brauchen eine Einzelprüfung: nur für Eltern (1), Filtertreffer (2)")
    }

    @Test fun `the bulk dialog never offers blocked`() {
        assertThat(SAMMEL_STUFEN).doesNotContain(xyz.steier.sidetube.core.model.SourceTrust.BLOCKED)
        assertThat(SAMMEL_STUFEN).hasSize(4)
    }
}
