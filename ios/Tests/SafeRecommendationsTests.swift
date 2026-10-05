// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

struct SafeRecommendationsTests {
    private func fixture() throws -> (ModelContext, KidProfile) {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia")
        return (context, profile)
    }

    private func video(_ id: String, title: String? = nil, status: ApprovalStatus = .approved) -> WhitelistItem {
        WhitelistItem(type: .video, youtubeId: id, title: title ?? id, thumbnailUrl: "thumb-\(id)", approvalStatus: status)
    }

    @Test func onlyApprovedCandidatesAreVisible() throws {
        let (context, profile) = try fixture()
        let approved = video("approved")
        let pending = video("pending", status: .reviewRequired)
        approved.profile = profile; pending.profile = profile
        context.insert(approved); context.insert(pending); try context.save()
        let result = SafeRecommendationService.candidates(for: profile, currentVideoId: "current", contextQueue: [], context: context)
        #expect(result.map(\.videoId) == ["approved"])
    }

    @Test func policyRejectsReviewBlockedAndWrongAge() throws {
        let (context, profile) = try fixture()
        let review = video("review", status: .reviewRequired)
        let blocked = video("blocked"); blocked.containsSexualContent = true
        let tooOld = video("too-old"); tooOld.ageMin = 12
        for item in [review, blocked, tooOld] { item.profile = profile; context.insert(item) }
        try context.save()
        #expect(SafeRecommendationService.candidates(for: profile, currentVideoId: "current", contextQueue: [], context: context).isEmpty)
    }

    @Test func trustedSourceBrowsingIsAllowedWithoutApprovalButNotOtherTrustLevels() throws {
        let (context, profile) = try fixture()
        let curation = CurationRepository(context: context)
        try curation.ensureSources([
            SourceDefinition(channelId: "trusted", title: "Trusted", trust: .trustedChildSource),
            SourceDefinition(channelId: "reviewed", title: "Reviewed", trust: .perVideoReview),
        ])
        let trustedChannel = WhitelistItem(type: .channel, youtubeId: "trusted", title: "Trusted", thumbnailUrl: "", approvalStatus: .approved)
        trustedChannel.profile = profile; context.insert(trustedChannel)
        try ChannelVideoCacheRepository(context: context).upsert([
            PlaylistVideo(videoId: "dynamic", title: "Dynamic", thumbnailUrl: "", channelTitle: "Trusted", position: 0),
            PlaylistVideo(videoId: "duplicate", title: "Duplicate", thumbnailUrl: "", channelTitle: "Trusted", position: 1),
        ], channelId: "trusted")
        let result = SafeRecommendationService.candidates(for: profile, currentVideoId: "current", contextQueue: [], context: context)
        #expect(result.map(\.videoId) == ["dynamic", "duplicate"])
    }

    @Test func currentVideoAndDuplicatesAreRemovedAndPlaylistContextWins() throws {
        let (context, profile) = try fixture()
        let current = video("current"); current.profile = profile
        let next = video("next", title: "Next"); next.profile = profile
        let other = video("other", title: "Other"); other.profile = profile
        for item in [current, next, other] { context.insert(item) }
        try context.save()
        let queue = [PlayerModel.Item(videoId: "current", title: "Current"),
                     PlayerModel.Item(videoId: "next", title: "Next"),
                     PlayerModel.Item(videoId: "next", title: "Next duplicate"),
                     PlayerModel.Item(videoId: "other", title: "Other")]
        let result = SafeRecommendationService.candidates(for: profile, currentVideoId: "current", contextQueue: queue, context: context)
        #expect(result.map(\.videoId) == ["next", "other"])
        #expect(!result.contains { $0.videoId == "current" })
    }

    @Test func blockedQueueItemCannotReachPlayer() throws {
        let (context, profile) = try fixture()
        let item = video("allowed"); item.profile = profile; context.insert(item); try context.save()
        let coordinator = PlayerCoordinator()
        let engine = FakePlayerEngine()
        coordinator.makeEngine = { engine }
        coordinator.play(queue: [
            PlayerModel.Item(videoId: "allowed", title: "Allowed"),
            PlayerModel.Item(videoId: "not-approved", title: "Blocked")
        ], startIndex: 1, profile: profile, context: context)
        #expect(coordinator.player == nil)
        #expect(engine.loaded.isEmpty)
    }
}
