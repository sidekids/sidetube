// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

/// Aufgelöster Inhalt, bereit zum Eintragen (kommt später aus oEmbed/Data API).
struct WhitelistItemDraft: Equatable, Sendable {
    var type: YouTubeContentType
    var youtubeId: String
    var title: String
    var thumbnailUrl: String
    var channelTitle: String?
    var provider: ContentProvider = .youtube
    var sourceChannelId: String?
    var sourceUrl: String?
    var description: String?
    var durationSeconds: Int?
    var isNSFW: Bool = false
    var isLive: Bool = false
}

struct WhitelistRepository {
    let context: ModelContext

    enum AddError: Error, Equatable { case duplicate }

   /// Alle Einträge (Elternbereich).
    func items(of profile: KidProfile, type: YouTubeContentType? = nil) -> [WhitelistItem] {
        profile.whitelistItems
            .filter { type == nil || $0.typeRaw == type!.rawValue }
            .sorted { $0.addedAt > $1.addedAt }
    }

   /// Nur im Kinderprofil sichtbare Einträge: freigegeben, altersgerecht, Quelle nicht gesperrt.
    func visibleItems(of profile: KidProfile, type: YouTubeContentType? = nil) -> [WhitelistItem] {
        let curation = CurationRepository(context: context)
        return items(of: profile, type: type).filter { item in
            ContentPolicy.isVisible(item, for: profile, source: curation.effectiveSource(channelId: item.sourceChannelId ?? (item.type == .channel ? item.youtubeId : nil)))
        }
    }

   /// Dubletten-Check pro Profil und YouTube-ID – vor jedem API-Aufruf nutzbar.
    func contains(youtubeId: String, in profile: KidProfile) -> Bool {
        profile.whitelistItems.contains { $0.youtubeId == youtubeId }
    }

    @discardableResult
    func add(_ draft: WhitelistItemDraft, to profile: KidProfile) throws -> WhitelistItem {
        guard !contains(youtubeId: draft.youtubeId, in: profile) else { throw AddError.duplicate }
        let item = WhitelistItem(type: draft.type, youtubeId: draft.youtubeId, title: draft.title,
                                 thumbnailUrl: draft.thumbnailUrl, channelTitle: draft.channelTitle)
        item.provider = draft.provider
        item.sourceChannelId = draft.sourceChannelId
        item.sourceUrl = draft.sourceUrl
        item.profile = profile
        context.insert(item)
        try context.save()
        return item
    }

    func remove(_ item: WhitelistItem) throws {
        context.delete(item)
        try context.save()
    }
}
