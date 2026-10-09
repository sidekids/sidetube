// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import xyz.steier.sidetube.core.curation.*
import xyz.steier.sidetube.core.db.*
import xyz.steier.sidetube.core.net.*
import xyz.steier.sidetube.core.player.PlaybackModel
import xyz.steier.sidetube.core.provider.ChannelFeedSource
import xyz.steier.sidetube.core.provider.ChannelVideo
import xyz.steier.sidetube.core.repo.*
import xyz.steier.sidetube.TestTexte

/**
 * Gemeinsamer Aufbau fuer die ViewModel-Tests des Kindermodus: kontrollierte DAOs, keine Room-,
 * WebView- oder Geraeteanbindung. Die Uhr ist stellbar, damit Fristen ohne Warten pruefbar sind.
 */
internal class KidFixture(
    val profile: KidProfileEntity =
        KidProfileEntity("p", "Synthetic", dailyLimitMinutes = 10, bedtimeEnabled = false),
    /** Weitere Profile fuer den Umschalter-Test; die meisten Fixture-Nutzer brauchen nur eines. */
    val otherProfiles: List<KidProfileEntity> = emptyList()
) {
    val items = MutableStateFlow(listOf(
        WhitelistItemEntity("a", "p", "VIDEO", contentId = "a", title = "Video A", approvalStatus = "approved"),
        WhitelistItemEntity("b", "p", "VIDEO", contentId = "b", title = "Video B", approvalStatus = "approved")
    ))
    val sources = MutableStateFlow<List<CuratedSourceEntity>>(emptyList())
    val markers = mutableMapOf<String, PlaybackSessionEntity>()
    val commands = mutableListOf<PlaybackModel.Command>()
    var readGate: CompletableDeferred<Unit>? = null
    var beginGate: CompletableDeferred<Unit>? = null
    var readStarted = false
    var beginStarted = false
    val allProfiles = listOf(profile) + otherProfiles
    /** Profilstand wie aus Room: Aenderungen der Eltern (Limit, Ausnahme) kommen als neue Emission an. */
    val profileFlow = MutableStateFlow(allProfiles)
    /** Was `update` zuletzt geschrieben hat; `null`, wenn nichts. */
    var updatedProfile: KidProfileEntity? = null
    /** Gesehene Sekunden heute, fuer das Tageslimit. */
    var secondsToday = 0L
    val profilePrefs = object : xyz.steier.sidetube.ProfilePreferenceStore {
        override var lastProfileId: String? = null
    }
    val profiles = object : KidProfileDao {
        override fun observeAll() = profileFlow
        override suspend fun byId(id: String) = profileFlow.value.find { it.id == id }
        override suspend fun insert(profile: KidProfileEntity) = error("unexpected insert")
        override suspend fun update(profile: KidProfileEntity) {
            updatedProfile = profile
            profileFlow.value = profileFlow.value.map { if (it.id == profile.id) profile else it }
        }
        override suspend fun delete(profile: KidProfileEntity) = error("unexpected delete")
    }
    val whitelist = object : WhitelistDao {
        override fun observeByProfile(profileId: String) = items
        override fun observeApproved(profileId: String) = items.map { rows -> rows.filter { it.approvalStatus == "approved" } }
        override fun observePending(profileId: String) = items.map { rows -> rows.filter { it.approvalStatus != "approved" } }
        override suspend fun find(profileId: String, contentId: String) = items.value.firstOrNull { it.contentId == contentId }
        override suspend fun insert(item: WhitelistItemEntity) { items.value += item }
        override suspend fun update(item: WhitelistItemEntity) { items.value = items.value.map { if (it.id == item.id) item else it } }
        override suspend fun delete(item: WhitelistItemEntity) { items.value = items.value.filterNot { it.id == item.id } }
    }
    val sourceDao = object : CuratedSourceDao {
        override fun observeAll() = sources
        override suspend fun byChannel(channelId: String) = sources.value.firstOrNull { it.channelId == channelId }
        override suspend fun insertMissing(sources: List<CuratedSourceEntity>) { this@KidFixture.sources.value = sources }
        override suspend fun update(source: CuratedSourceEntity) { sources.value = listOf(source) }
    }
    /** Sehverlauf fuer „Zuletzt geschaut"; Buchungen der Wiedergabe landen bewusst nicht hier. */
    val historyEntries = mutableListOf<WatchHistoryEntity>()
    val history = object : WatchHistoryDao {
        override suspend fun insert(entry: WatchHistoryEntity) = Unit
        override suspend fun since(profileId: String, since: Long) =
            historyEntries.filter { it.profileId == profileId && it.watchedAt >= since }.sortedByDescending { it.watchedAt }
        override suspend fun secondsBetween(profileId: String, from: Long, until: Long): Long {
            readStarted = true
            readGate?.await()
            return secondsToday
        }
    }
    val sessions = object : PlaybackSessionDao {
        override suspend fun begin(session: PlaybackSessionEntity) {
            beginStarted = true
            // Model a database operation whose commit can outlive caller cancellation.
            withContext(NonCancellable) { beginGate?.await(); markers[session.profileId] = session }
        }
        override suspend fun pending(profileId: String) = markers[profileId]
        override fun observePending() = flowOf(markers.values.toList())
        override suspend fun finish(profileId: String, token: String) {
            if (markers[profileId]?.token == token) markers.remove(profileId)
        }
        override suspend fun acknowledgeByParent(profileId: String) { markers.remove(profileId) }
    }
    val events = mutableListOf<ReviewEventEntity>()
    val reviews = object : ReviewEventDao {
        override suspend fun insert(event: ReviewEventEntity) { events += event }
        override suspend fun forContent(contentId: String) = events.filter { it.contentId == contentId }
        override suspend fun deleteForProfile(profileId: String) { events.removeAll { it.profileId == profileId } }
    }
    /** Wuensche im Speicher (ADR 0001). */
    val wishes = xyz.steier.sidetube.FakeWishDao()
    /** Kanal-Feeds fuer „Neu bei deinen Kanaelen"; fehlt einer, gilt das als offline. */
    val feeds = mutableMapOf<String, String>()
    /** Zwischenspeicher je Schluessel (Kanal-ID oder `playlist:<id>`). */
    val cached = mutableMapOf<String, List<CachedChannelVideoEntity>>()
    val cache = object : ChannelVideoCacheDao {
        override suspend fun upsert(videos: List<CachedChannelVideoEntity>) {
            for ((key, neu) in videos.groupBy { it.channelId }) {
                cached[key] = (cached[key].orEmpty().filterNot { alt -> neu.any { it.videoId == alt.videoId } } + neu)
                    .sortedBy { it.position }
            }
        }
        override suspend fun byChannel(channelId: String) = cached[channelId].orEmpty()
        override suspend fun search(query: String, limit: Int) = emptyList<CachedChannelVideoEntity>()
        override suspend fun clear(channelId: String) { cached.remove(channelId) }
    }
    /** Feed-Inhalt je Playlist; fehlt sie, gilt das als Netzfehler. Abgefragte Playlists in [requested]. */
    val playlists = mutableMapOf<String, List<ChannelVideo>>()
    val requested = mutableListOf<String>()
    /** Wie oft den Eltern ein neuer Wunsch gemeldet wurde (ADR 0005). */
    var gemeldet = 0
    val content = ContentBundle(object : ContentSource {
        override fun read(path: String): String? = null
        override fun listLibraries() = emptyList<String>()
    })
    fun model(kanalbild: suspend (String) -> String? = { null }, now: () -> Instant = Instant::now) = KidViewModel(ProfileRepository(profiles, reviews), WhitelistRepository(whitelist, sourceDao),
        WatchTimeRepository(history), sessions,
        CurationRepository(whitelist, sourceDao, reviews, RiskScreen(content.riskTerms())),
        ChannelVideoCacheRepository(cache), content,
        ChannelFeedSource(object : HttpClient {
            override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
                feeds.entries.firstOrNull { url.endsWith(it.key) }?.let { HttpResponse(200, it.value) }
                    ?: error("Unexpected network request")
        }), now, profilePrefs, kanalbild,
        playlistHolen = { id -> requested += id; playlists[id] ?: error("offline") },
        wuensche = WunschRepository(wishes, reviews, now = now), neuerWunschGemeldet = { gemeldet++ }, texte = TestTexte).also { it.onPlaybackCommand = commands::add }
    fun rejectB() { items.value = items.value.map { if (it.id == "b") it.copy(approvalStatus = "rejected") else it } }
}
