// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import kotlinx.coroutines.flow.first
import xyz.steier.sidetube.core.curation.ContentBundle
import xyz.steier.sidetube.core.curation.ContentPolicy
import xyz.steier.sidetube.core.curation.RiskScreen
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.provider.ChannelVideo
import xyz.steier.sidetube.core.repo.ChannelVideoCacheRepository
import xyz.steier.sidetube.core.repo.CurationRepository
import xyz.steier.sidetube.core.repo.WatchTimeRepository
import xyz.steier.sidetube.core.repo.WhitelistRepository
import java.time.Duration
import java.time.Instant

/**
 * Was aus Kanälen, Playlists und dem Verlauf abgespielt werden darf. Die Regeln stehen in
 * [ContentPolicy]; hier werden nur die Daten dafür zusammengetragen – an einer Stelle, damit
 * Kanalansicht, Playlist und „Zuletzt geschaut" dieselbe Antwort geben.
 */
internal class KidAbspielbar(
    private val whitelist: WhitelistRepository,
    private val curation: CurationRepository,
    private val cache: ChannelVideoCacheRepository,
    private val content: ContentBundle,
    private val watchTime: WatchTimeRepository
) {

    /** Kanalvideos, die ohne eigene Freigabe gezeigt werden dürfen (nur stöberbare Kinderquellen). */
    suspend fun channelRows(profile: KidProfileEntity, channelId: String, rows: List<KidRow>): List<KidRow> {
        val source = curation.source(channelId)
        val decisions = whitelist.observeAll(profile.id).first().associateBy { it.contentId }
        val riskScreen = RiskScreen(content.riskTerms())
        return rows.filter { row ->
            val video = row.action as? KidAction.Play ?: return@filter false
            val candidate = WhitelistItemEntity(id = video.videoId, profileId = profile.id,
                type = "VIDEO", contentId = video.videoId, title = video.title, sourceChannelId = channelId)
            ContentPolicy.canBrowseCandidate(candidate, profile, source,
                decisions[video.videoId], riskScreen.assess(video.title))
        }
    }

    /**
     * Videos einer freigegebenen Playlist nach [ContentPolicy.canPlayFromPlaylist]. Jedes Video
     * einmal: Steht es mehrfach in der Playlist, zaehlt der erste Platz (die Liste braucht
     * eindeutige Schluessel).
     */
    suspend fun playlistVideos(profile: KidProfileEntity, playlist: WhitelistItemEntity, videos: List<ChannelVideo>): List<ChannelVideo> {
        if (videos.isEmpty()) return emptyList()
        val decisions = whitelist.observeAll(profile.id).first().associateBy { it.contentId }
        val byId = curation.observeSources().first().associateBy { it.channelId }
        val playlistSource = playlist.sourceChannelId?.let(byId::get)
        val riskScreen = RiskScreen(content.riskTerms())
        return videos.distinctBy { it.videoId }.filter { video ->
            // Die Quelle nur ueber die Kanal-ID, nie ueber den Namen: Ein Name ist nicht eindeutig und
            // kann sich aendern. Ohne Kanal-ID (Zwischenspeicher von vor Schema 3) laesst die Regel
            // das Video nur mit eigener Freigabe durch.
            val videoSource = video.channelId?.let(byId::get)
            val candidate = WhitelistItemEntity(id = video.videoId, profileId = profile.id, type = WhitelistItemType.VIDEO.name,
                contentId = video.videoId, title = video.title, channelTitle = video.channelTitle,
                sourceChannelId = video.channelId, isShort = video.isShort, isLive = video.isUpcoming)
            ContentPolicy.canPlayFromPlaylist(candidate, profile, playlist, playlistSource, videoSource,
                decisions[video.videoId], riskScreen.assess(video.title))
        }
    }

    suspend fun cachedPlaylist(playlistId: String): List<ChannelVideo> =
        cache.playlistVideos(playlistId).map {
            ChannelVideo(it.videoId, it.title, it.thumbnailUrl, it.channelTitle, it.position,
                channelId = it.videoChannelId, publishedAt = it.publishedAt, isShort = it.isShort, isUpcoming = it.isUpcoming)
        }

    /**
     * Alles, was das Kind gerade abspielen darf: sichtbare Einzelvideos, die zwischengespeicherten
     * Videos stöberbarer Kanäle und freigegebener Playlists – jeweils nach denselben Regeln wie
     * beim Öffnen. Ein einmal gesehenes, später entzogenes Video ist über den Verlauf nicht mehr
     * erreichbar (wie iOS `KidRows.playableVideoIds`).
     */
    suspend fun playableVideoIds(profile: KidProfileEntity, visible: List<WhitelistItemEntity>): Set<String> {
        val ids = visible.filter { it.type == WhitelistItemType.VIDEO.name }.map { it.contentId }.toMutableSet()
        for (channel in visible.filter { it.type == WhitelistItemType.CHANNEL.name }) {
            if (!ContentPolicy.allowsChannelBrowsing(curation.source(channel.contentId))) continue
            val cached = cache.videos(channel.contentId)
            if (cached.isEmpty()) continue
            ids += channelRows(profile, channel.contentId, cached.map { KidRows.video(it.videoId, it.title, it.channelTitle) })
                .mapNotNull { (it.action as? KidAction.Play)?.videoId }
        }
        for (playlist in visible.filter { it.type == WhitelistItemType.PLAYLIST.name }) {
            ids += playlistVideos(profile, playlist, cachedPlaylist(playlist.contentId)).map { it.videoId }
        }
        return ids
    }

    /**
     * „Zuletzt geschaut": neueste zuerst, jedes Video einmal, nur Erlaubtes. Android speichert
     * keine Abspielposition, deshalb kein „Weiterschauen" mit Fortschritt wie auf iOS.
     */
    suspend fun zuletztGeschaut(profile: KidProfileEntity, visible: List<WhitelistItemEntity>, now: Instant): List<KidRow> {
        val allowed = playableVideoIds(profile, visible)
        val seen = mutableSetOf<String>()
        return watchTime.since(profile.id, now.minus(RECENT_DAYS).toEpochMilli())
            .sortedByDescending { it.watchedAt }
            .filter { it.videoId in allowed && seen.add(it.videoId) }
            .take(KidRows.RECENT_LIMIT)
            .map { KidRows.video(it.videoId, it.videoTitle, null, idPrefix = "recent-") }
    }

    private companion object {
        /** So weit reicht „Zuletzt geschaut" zurück. */
        val RECENT_DAYS: Duration = Duration.ofDays(60)
    }
}
