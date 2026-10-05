// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

/// Cache der Kanalvideos als „Single Source of Truth" für die Kanalansicht. Suche = lokale Abfrage, 0 Quota.
struct ChannelVideoCacheRepository {
    let context: ModelContext

    func videos(channelId: String) -> [CachedChannelVideo] {
        let descriptor = FetchDescriptor<CachedChannelVideo>(
            predicate: #Predicate { $0.channelId == channelId },
            sortBy: [SortDescriptor(\.position), SortDescriptor(\.title)])
        return (try? context.fetch(descriptor)) ?? []
    }

    /// Resolves the source of a previously watched dynamically browsed video.
    func video(videoId: String) -> CachedChannelVideo? {
        let descriptor = FetchDescriptor<CachedChannelVideo>(predicate: #Predicate { $0.videoId == videoId })
        return try? context.fetch(descriptor).first
    }

    func search(channelId: String, query: String) -> [CachedChannelVideo] {
        let needle = query.trimmingCharacters(in: .whitespaces)
        guard !needle.isEmpty else { return videos(channelId: channelId) }
        let descriptor = FetchDescriptor<CachedChannelVideo>(
            predicate: #Predicate { $0.channelId == channelId && $0.title.localizedStandardContains(needle) },
            sortBy: [SortDescriptor(\.position)])
        return (try? context.fetch(descriptor)) ?? []
    }

   /// Alle gecachten Videos aller Kanäle, deren Titel passt (für die Kindersuche).
    func searchAll(query: String) -> [CachedChannelVideo] {
        let needle = query.trimmingCharacters(in: .whitespaces)
        guard !needle.isEmpty else { return [] }
        let descriptor = FetchDescriptor<CachedChannelVideo>(
            predicate: #Predicate { $0.title.localizedStandardContains(needle) },
            sortBy: [SortDescriptor(\.channelTitle), SortDescriptor(\.position)])
        return (try? context.fetch(descriptor)) ?? []
    }

   /// Einfügen oder aktualisieren nach (channelId, videoId) – ersetzt das zusammengesetzte @Upsert der Android-App.
   /// Steht ein Video mehrfach in `incoming`, zählt der erste Eintrag.
    func upsert(_ incoming: [PlaylistVideo], channelId: String) throws {
        var existing = Dictionary(videos(channelId: channelId).map { ($0.videoId, $0) }, uniquingKeysWith: { first, _ in first })
        var written: Set<String> = []
        for video in incoming where written.insert(video.videoId).inserted {
            let cached: CachedChannelVideo
            if let found = existing[video.videoId] {
                cached = found
                cached.title = video.title
                cached.thumbnailUrl = video.thumbnailUrl
                cached.channelTitle = video.channelTitle
                cached.position = video.position
            } else {
                cached = CachedChannelVideo(channelId: channelId, videoId: video.videoId, title: video.title,
                                            thumbnailUrl: video.thumbnailUrl, channelTitle: video.channelTitle,
                                            position: video.position)
                context.insert(cached)
                existing[video.videoId] = cached
            }
            // Ein Kanal-Feed nennt seinen Kanal; ohne Angabe bleibt eine schon bekannte Kennung stehen.
            if let videoChannel = video.channelId { cached.videoChannelId = videoChannel }
            cached.isShort = video.isShort
            cached.isUpcoming = video.isUpcoming
        }
        try context.save()
    }

   // MARK: Playlists

   /// Playlists teilen sich den Speicher mit den Kanälen, unter dem Schlüssel `playlist:<id>` (wie Android).
   /// Eine eigene Tabelle bräuchte eine Schemaänderung für dieselben Spalten; der Vorsatz schließt aus,
   /// dass eine Playlist-Kennung je mit einer Kanal-Kennung (`UC…`) zusammenfällt. Die Kanal-ID jedes
   /// Videos steht in `videoChannelId`; fehlt sie (Eintrag von vor dieser Spalte), gilt der Kanal als unbekannt.
    static let playlistPrefix = "playlist:"
    static func playlistKey(_ playlistId: String) -> String { playlistPrefix + playlistId }
    static func playlistId(fromKey key: String) -> String? {
        key.hasPrefix(playlistPrefix) ? String(key.dropFirst(playlistPrefix.count)) : nil
    }

    func playlistVideos(playlistId: String) -> [PlaylistVideo] {
        videos(channelId: Self.playlistKey(playlistId)).map {
            PlaylistVideo(videoId: $0.videoId, title: $0.title, thumbnailUrl: $0.thumbnailUrl,
                          channelTitle: $0.channelTitle, position: $0.position, channelId: $0.videoChannelId,
                          isShort: $0.isShort, isUpcoming: $0.isUpcoming)
        }
    }

   /// Ersetzt den Stand der Playlist: Was dort entfernt wurde, soll auch hier verschwinden.
    func storePlaylist(playlistId: String, videos: [PlaylistVideo]) throws {
        let key = Self.playlistKey(playlistId)
        try context.delete(model: CachedChannelVideo.self, where: #Predicate { $0.channelId == key })
        try upsert(videos, channelId: key)
    }

    func clear(channelId: String) throws {
        try context.delete(model: CachedChannelVideo.self, where: #Predicate { $0.channelId == channelId })
        try context.save()
    }
}
