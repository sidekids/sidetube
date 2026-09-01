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
    func upsert(_ incoming: [PlaylistVideo], channelId: String) throws {
        let existing = Dictionary(videos(channelId: channelId).map { ($0.videoId, $0) }, uniquingKeysWith: { first, _ in first })
        for video in incoming {
            if let cached = existing[video.videoId] {
                cached.title = video.title
                cached.thumbnailUrl = video.thumbnailUrl
                cached.channelTitle = video.channelTitle
                cached.position = video.position
            } else {
                context.insert(CachedChannelVideo(channelId: channelId, videoId: video.videoId, title: video.title,
                                                  thumbnailUrl: video.thumbnailUrl, channelTitle: video.channelTitle,
                                                  position: video.position))
            }
        }
        try context.save()
    }

    func clear(channelId: String) throws {
        try context.delete(model: CachedChannelVideo.self, where: #Predicate { $0.channelId == channelId })
        try context.save()
    }
}
