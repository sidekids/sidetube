// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

/// Trägt die Daten für `ContentPolicy.canPlayFromPlaylist` zusammen – an einer Stelle, damit
/// Playlist-Ansicht, Player-Grenze und „Zuletzt geschaut" dieselbe Antwort geben (wie Android `KidAbspielbar`).
enum PlaylistPlayability {
   /// Die Freigabe, unter der eine Playlist steht: der Whitelist-Eintrag des Profils, sonst die
   /// Schlaf-Playlist, die Eltern im Profil hinter der PIN eingetragen haben (ohne Alters-/Kategorieangaben).
   /// Gibt es einen Whitelist-Eintrag, gilt nur er – auch wenn er abgelehnt ist.
    static func playlistItem(id playlistId: String, profile: KidProfile) -> WhitelistItem? {
        if let item = profile.whitelistItems.first(where: { $0.type == .playlist && $0.youtubeId == playlistId }) {
            return item
        }
        guard profile.sleepPlaylistId == playlistId else { return nil }
        // Nur ein Prüfling für die Regel – wird nie in den Store eingefügt.
        return WhitelistItem(type: .playlist, youtubeId: playlistId, title: "Schlaf-Playlist",
                             thumbnailUrl: "", approvalStatus: .approved)
    }

   /// Die Videos, die aus `playlist` gezeigt und abgespielt werden dürfen, in ihrer Reihenfolge.
   /// Jedes Video einmal: Steht es mehrfach in der Playlist, zählt der erste Platz (die Zeilen brauchen
   /// eindeutige IDs).
    static func allowedVideos(_ videos: [PlaylistVideo], profile: KidProfile, playlist: WhitelistItem,
                              context: ModelContext) -> [PlaylistVideo] {
        guard !videos.isEmpty else { return [] }
        let curation = CurationRepository(context: context)
        let playlistSource = curation.effectiveSource(channelId: playlist.sourceChannelId)
        var decisions: [String: WhitelistItem] = [:]
        for item in profile.whitelistItems where decisions[item.youtubeId] == nil { decisions[item.youtubeId] = item }
        var seen: Set<String> = []
        return videos.filter { video in
            guard seen.insert(video.videoId).inserted else { return false }
            // Die Quelle nur über die Kanal-ID, nie über den Namen: Ein Name ist nicht eindeutig und kann
            // sich ändern. Ohne Kanal-ID lässt die Regel das Video nur mit eigener Freigabe durch.
            let videoSource = curation.effectiveSource(channelId: video.channelId)
            return ContentPolicy.canPlayFromPlaylist(video, for: profile, playlist: playlist,
                                                     playlistSource: playlistSource, videoSource: videoSource,
                                                     priorDecision: decisions[video.videoId],
                                                     risk: RiskScreen.assess(title: video.title))
        }
    }

   /// Einzelprüfung an der Player-Grenze.
   /// Feed-Kennzeichen (Short, angekündigt) kennt nur der Zwischenspeicher der Playlist; sie gelten auch hier.
    static func allows(_ item: PlayerModel.Item, playlistId: String, profile: KidProfile, context: ModelContext) -> Bool {
        guard let playlist = playlistItem(id: playlistId, profile: profile) else { return false }
        let cached = ChannelVideoCacheRepository(context: context).playlistVideos(playlistId: playlistId)
            .first { $0.videoId == item.videoId }
        let video = PlaylistVideo(videoId: item.videoId, title: item.title,
                                  thumbnailUrl: item.thumbnailURL ?? YouTubeIDs.defaultThumbnail(videoId: item.videoId),
                                  channelTitle: item.channelTitle ?? "", position: 0,
                                  channelId: item.sourceChannelId ?? cached?.channelId,
                                  isShort: cached?.isShort ?? false, isUpcoming: cached?.isUpcoming ?? false)
        return !allowedVideos([video], profile: profile, playlist: playlist, context: context).isEmpty
    }

   /// Abspielbare Videos aller Playlists des Profils aus dem Zwischenspeicher (für „Zuletzt geschaut").
    static func cachedPlayableVideoIds(profile: KidProfile, context: ModelContext) -> Set<String> {
        let cache = ChannelVideoCacheRepository(context: context)
        var playlistIds = Set(profile.whitelistItems.filter { $0.type == .playlist }.map(\.youtubeId))
        if let sleep = profile.sleepPlaylistId { playlistIds.insert(sleep) }
        var ids: Set<String> = []
        for playlistId in playlistIds {
            guard let playlist = playlistItem(id: playlistId, profile: profile) else { continue }
            ids.formUnion(allowedVideos(cache.playlistVideos(playlistId: playlistId), profile: profile,
                                        playlist: playlist, context: context).map(\.videoId))
        }
        return ids
    }
}
