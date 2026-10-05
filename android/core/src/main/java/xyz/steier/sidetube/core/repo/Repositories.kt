// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import xyz.steier.sidetube.core.curation.ContentPolicy
import xyz.steier.sidetube.core.db.CuratedSourceDao
import xyz.steier.sidetube.core.db.KidProfileDao
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.ReviewEventDao
import xyz.steier.sidetube.core.db.WhitelistDao
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.db.WatchHistoryDao
import xyz.steier.sidetube.core.db.WatchHistoryEntity
import java.util.Calendar
import java.util.UUID

class ProfileRepository(
    private val dao: KidProfileDao,
    private val events: ReviewEventDao,
    /** Fuehrt beide Loeschungen gemeinsam aus; in der App eine Room-Transaktion ([SideTubeDatabase.inTransaction]). */
    private val transaction: suspend (suspend () -> Unit) -> Unit = { it() }
) {

    fun observeAll(): Flow<List<KidProfileEntity>> = dao.observeAll()

    suspend fun byId(id: String): KidProfileEntity? = dao.byId(id)

    suspend fun create(name: String, dailyLimitMinutes: Int? = null): KidProfileEntity {
        val profile = KidProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            dailyLimitMinutes = dailyLimitMinutes
        )
        dao.insert(profile)
        return profile
    }

    suspend fun update(profile: KidProfileEntity) = dao.update(profile)

    /**
     * Loescht das Profil; Freigaben, Sehverlauf, Wuensche und Wiedergabe-Merker folgen per
     * Fremdschluessel (CASCADE). Die Eintraege des Profils im Verlauf der Freigaben – darunter die
     * Wunschtexte des Kindes – haben keinen Fremdschluessel und werden hier mit entfernt.
     * Profilunabhaengige Ereignisse (Stufe einer Quelle) bleiben.
     */
    suspend fun delete(profile: KidProfileEntity) = transaction {
        events.deleteForProfile(profile.id)
        dao.delete(profile)
    }
}

class WhitelistRepository(
    private val dao: WhitelistDao,
    private val sources: CuratedSourceDao
) {

    fun observeAll(profileId: String): Flow<List<WhitelistItemEntity>> = dao.observeByProfile(profileId)

    /**
     * Was das Kind sehen darf. Die Datenbank liefert Freigegebenes, die Regel entfernt daraus,
     * was Altersstufe, Kategorie oder Quellenstufe verbieten – beides zusammen, nie nur eines.
     */
    fun observeVisible(profile: KidProfileEntity): Flow<List<WhitelistItemEntity>> =
        combine(dao.observeApproved(profile.id), sources.observeAll()) { items, allSources ->
            val byChannel = allSources.associateBy { it.channelId }
            items.filter { ContentPolicy.isVisible(it, profile, byChannel[it.sourceChannelId]) }
        }

    suspend fun find(profileId: String, contentId: String): WhitelistItemEntity? = dao.find(profileId, contentId)

    suspend fun remove(item: WhitelistItemEntity) = dao.delete(item)
}

/** Zwischenspeicher der Kanalvideos: traegt die Kindersuche, die deshalb ohne Netz auskommt. */
class ChannelVideoCacheRepository(private val dao: xyz.steier.sidetube.core.db.ChannelVideoCacheDao) {

    suspend fun videos(channelId: String) = dao.byChannel(channelId)

    suspend fun store(channelId: String, videos: List<xyz.steier.sidetube.core.provider.ChannelVideo>) {
        dao.upsert(videos.map {
            xyz.steier.sidetube.core.db.CachedChannelVideoEntity(
                channelId = channelId, videoId = it.videoId, title = it.title,
                thumbnailUrl = it.thumbnailUrl, channelTitle = it.channelTitle, position = it.position,
                publishedAt = it.publishedAt, isShort = it.isShort, isUpcoming = it.isUpcoming,
                videoChannelId = it.channelId
            )
        })
    }

    suspend fun search(query: String) = dao.search(query)

    /**
     * Playlists teilen sich die Tabelle mit den Kanaelen, unter dem Schluessel `playlist:<id>`.
     * Eine eigene Tabelle braeuchte eine Migration fuer dieselben Spalten; der Vorsatz schliesst
     * aus, dass eine Playlist-Kennung je mit einer Kanal-Kennung (`UC…`) zusammenfaellt.
     */
    suspend fun playlistVideos(playlistId: String) = dao.byChannel(playlistKey(playlistId))

    /** Ersetzt den Stand der Playlist: Was dort entfernt wurde, soll auch hier verschwinden. */
    suspend fun storePlaylist(playlistId: String, videos: List<xyz.steier.sidetube.core.provider.ChannelVideo>) {
        dao.clear(playlistKey(playlistId))
        store(playlistKey(playlistId), videos)
    }

    companion object {
        const val PLAYLIST_PREFIX = "playlist:"
        fun playlistKey(playlistId: String) = PLAYLIST_PREFIX + playlistId
    }
}

class WatchTimeRepository(
    private val dao: WatchHistoryDao,
    private val now: () -> Long = System::currentTimeMillis
) {

    suspend fun record(profileId: String, videoId: String, title: String, seconds: Int, at: Long = now()) {
        if (seconds <= 0) return
        dao.insert(WatchHistoryEntity(
            profileId = profileId, videoId = videoId, videoTitle = title,
            watchedSeconds = seconds, watchedAt = at
        ))
    }

    /** Sehzeit des Kalendertags – Grundlage fuer das Tageslimit. */
    suspend fun secondsToday(profileId: String, at: Long = now()): Int {
        val (from, until) = dayBounds(at)
        val seconds = dao.secondsBetween(profileId, from, until)
        return if (seconds < 0) Int.MAX_VALUE else seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Verbleibende Sekunden heute; `null` heisst unbegrenzt. */
    suspend fun remainingSeconds(profile: KidProfileEntity, at: Long = now()): Int? {
        val limit = profile.dailyLimitMinutes ?: return null
        if (limit <= 0 || limit > Int.MAX_VALUE / 60) return 0
        return (limit * 60 - secondsToday(profile.id, at)).coerceAtLeast(0)
    }

    suspend fun since(profileId: String, since: Long): List<WatchHistoryEntity> = dao.since(profileId, since)

    private fun dayBounds(at: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = at
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return start to calendar.timeInMillis
    }
}
