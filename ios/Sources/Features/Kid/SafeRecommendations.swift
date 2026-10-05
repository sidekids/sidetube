// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

/// Herkunft eines Kandidaten. Herkunft beeinflusst nur die Reihenfolge, nie die Freigabe.
enum RecommendationSourceType: String, Sendable {
    case playlist, sameChannel, category, recentlyApproved, related
}

struct RecommendationCandidate: Equatable, Sendable, Identifiable {
    let id: String
    let videoId: String
    let title: String
    let thumbnailURL: String
    let channelId: String?
    let channelTitle: String?
    let sourceType: RecommendationSourceType
    let reason: String
    let priority: Int
    let order: Int
}

/// Builds the child-facing list from SideTube-owned data only.
/// Provider related data can be added later as `.related`, but must enter this type
/// and pass the same policy before it can become visible.
enum SafeRecommendationService {
    static func candidates(for profile: KidProfile, currentVideoId: String,
                           contextQueue: [PlayerModel.Item], context: ModelContext,
                           limit: Int = 12) -> [RecommendationCandidate] {
        let curation = CurationRepository(context: context)
        let whitelist = WhitelistRepository(context: context)
        let visible = whitelist.visibleItems(of: profile, type: .video)
        let current = visible.first { $0.youtubeId == currentVideoId }
        let currentChannel = current?.sourceChannelId
        let currentCategory = current?.category
        let contextIDs = Set(contextQueue.map(\.videoId))
        var candidates: [RecommendationCandidate] = []
        var seen = Set<String>([currentVideoId])

        func append(_ item: WhitelistItem, sourceType: RecommendationSourceType, reason: String, priority: Int, order: Int = Int.max) {
            guard !seen.contains(item.youtubeId),
                  ContentPolicy.isVisible(item, for: profile, source: curation.effectiveSource(channelId: item.sourceChannelId)) else { return }
            seen.insert(item.youtubeId)
            candidates.append(RecommendationCandidate(id: item.youtubeId, videoId: item.youtubeId,
                title: item.title, thumbnailURL: item.thumbnailUrl, channelId: item.sourceChannelId,
                channelTitle: item.channelTitle, sourceType: sourceType, reason: reason, priority: priority, order: order))
        }

        // The caller's ordering is the strongest context (playlist/list “Als Nächstes”).
        for (order, id) in contextQueue.map(\.videoId).enumerated() where id != currentVideoId {
            if let item = visible.first(where: { $0.youtubeId == id }) {
                append(item, sourceType: .playlist, reason: "Kontext der aktuellen Liste", priority: 0, order: order)
            }
        }
        for item in visible where item.sourceChannelId == currentChannel {
            append(item, sourceType: .sameChannel, reason: "Freigegebenes Video desselben Kanals", priority: 1)
        }
        for item in visible where currentCategory != nil && item.category == currentCategory {
            append(item, sourceType: .category, reason: "Freigegebene Kategorie", priority: 2)
        }
        for item in visible where !contextIDs.contains(item.youtubeId) {
            append(item, sourceType: .recentlyApproved, reason: "Weitere freigegebene Inhalte", priority: 3)
        }

        // Trusted channel browsing is an existing explicit source rule, not approval.
        let trustedChannels = whitelist.visibleItems(of: profile, type: .channel)
            .compactMap(\.youtubeId)
            .filter { curation.effectiveSource(channelId: $0)?.trust.allowsChannelBrowsing == true }
        for channelId in trustedChannels {
            guard let source = curation.effectiveSource(channelId: channelId) else { continue }
            for cached in ChannelVideoCacheRepository(context: context).videos(channelId: channelId) {
                guard !seen.contains(cached.videoId) else { continue }
                let item = WhitelistItem(type: .video, youtubeId: cached.videoId, title: cached.title,
                                         thumbnailUrl: cached.thumbnailUrl, channelTitle: cached.channelTitle,
                                         approvalStatus: .reviewRequired)
                item.sourceChannelId = channelId
                item.category = source.defaultCategory
                item.ageMin = source.defaultAgeMin
                let risk = RiskScreen.assess(title: cached.title)
                item.isShort = risk.isShort
                item.isLive = risk.isLive
                item.containsSexualContent = risk.topics.contains(.sexual)
                guard ContentPolicy.evaluate(item, for: profile, source: source, allowTrustedBrowsing: true).visible else { continue }
                seen.insert(cached.videoId)
                candidates.append(RecommendationCandidate(id: cached.videoId, videoId: cached.videoId,
                    title: cached.title, thumbnailURL: cached.thumbnailUrl, channelId: channelId,
                    channelTitle: cached.channelTitle, sourceType: .sameChannel,
                    reason: "Vertrauenswürdige Kinderquelle", priority: 1, order: cached.position))
            }
        }
        return candidates.sorted {
            if $0.priority != $1.priority { return $0.priority < $1.priority }
            if $0.order != $1.order { return $0.order < $1.order }
            return $0.title.localizedStandardCompare($1.title) == .orderedAscending
        }
            .prefix(limit).map { $0 }
    }

    /// Final enforcement boundary before an embedded player receives a video ID.
    static func allowed(_ item: PlayerModel.Item, for profile: KidProfile, context: ModelContext) -> Bool {
        let curation = CurationRepository(context: context)
        if let approved = profile.whitelistItems.first(where: { $0.youtubeId == item.videoId }) {
            guard approved.type == .video, approved.provider.isPlayable else { return false }
            return ContentPolicy.isVisible(approved, for: profile, source: curation.effectiveSource(channelId: approved.sourceChannelId))
        }
        // Aus einer freigegebenen Playlist: deren Regel, sonst weiter wie gewohnt (vertrauenswürdige Kanäle).
        if let playlistId = item.sourcePlaylistId,
           PlaylistPlayability.allows(item, playlistId: playlistId, profile: profile, context: context) {
            return true
        }
        guard let channelId = item.sourceChannelId,
              let source = curation.effectiveSource(channelId: channelId),
              source.trust.allowsChannelBrowsing else { return false }
        let dynamic = WhitelistItem(type: .video, youtubeId: item.videoId, title: item.title,
                                    thumbnailUrl: item.thumbnailURL ?? YouTubeIDs.defaultThumbnail(videoId: item.videoId),
                                    channelTitle: item.channelTitle, approvalStatus: .reviewRequired)
        dynamic.sourceChannelId = channelId
        dynamic.category = source.defaultCategory
        dynamic.ageMin = source.defaultAgeMin
        let risk = RiskScreen.assess(title: item.title)
        dynamic.isShort = risk.isShort
        dynamic.isLive = risk.isLive
        dynamic.containsSexualContent = risk.topics.contains(.sexual)
        return ContentPolicy.evaluate(dynamic, for: profile, source: source, allowTrustedBrowsing: true).visible
    }
}
