// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

/// Auch Startvorschläge brauchen eine ausdrückliche Elternfreigabe.
struct WelcomeLibraryTests {
    private var appBundle: Bundle {
        Bundle(identifier: "xyz.steier.sidetube") ?? .main
    }

    private func makeWorld() throws -> (ModelContext, KidProfile) {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia")
        return (context, profile)
    }

    @Test func seedsPendingVideosThatTheChildCannotSee() throws {
        let (context, profile) = try makeWorld()
        #expect(WhitelistRepository(context: context).visibleItems(of: profile).isEmpty)

        let added = WelcomeLibrary.seed(into: profile, context: context, bundle: appBundle)
        #expect(added > 0, "content/welcome.json liefert mindestens ein Video")

        let visible = WhitelistRepository(context: context).visibleItems(of: profile, type: .video)
        #expect(visible.isEmpty)
        let pending = WhitelistRepository(context: context).items(of: profile, type: .video)
        #expect(pending.count == libraryVideoCount(in: appBundle))
        let first = try #require(pending.first)
        #expect(first.approvalStatus == .reviewRequired)
        #expect(first.approvedBy == nil)
        #expect(!first.thumbnailUrl.isEmpty)
        #expect(first.sourceChannelId != nil, "die Quelle ist bekannt, auch wenn sie keine Vertrauensstufe hat")
    }

    @Test func seedingTwiceAddsNothingAndDoesNotDuplicate() throws {
        let (context, profile) = try makeWorld()
        let first = WelcomeLibrary.seed(into: profile, context: context, bundle: appBundle)
        let second = WelcomeLibrary.seed(into: profile, context: context, bundle: appBundle)
        #expect(second == 0)
        #expect(WhitelistRepository(context: context).items(of: profile, type: .video).count == first - libraryChannelCount(in: appBundle))
    }

    private func libraryVideoCount(in bundle: Bundle) -> Int {
        (try? ContentBundle.load(SeedLibraryImporter.SeedLibrary.self, "welcome", bundle: bundle).videos.count) ?? 0
    }

    private func libraryChannelCount(in bundle: Bundle) -> Int {
        (try? ContentBundle.load(SeedLibraryImporter.SeedLibrary.self, "welcome", bundle: bundle).channels?.count) ?? 0
    }

    @Test func theWelcomeVideoRespectsTheAgeOfTheProfile() throws {
        let (context, profile) = try makeWorld()
        WelcomeLibrary.seed(into: profile, context: context, bundle: appBundle)
        let whitelist = WhitelistRepository(context: context)
        for item in whitelist.items(of: profile, type: .video) { item.approvalStatus = .approved }
        try context.save()
        #expect(!whitelist.visibleItems(of: profile, type: .video).isEmpty, "Standardprofil 9–11 sieht es")

        profile.ageBand = .preschool
        try context.save()
        #expect(whitelist.visibleItems(of: profile, type: .video).isEmpty, "für die Kleinsten greift das Mindestalter")
    }

    @Test func migrationPreservesParentDecisionsAndDoesNotRestoreDeletedSuggestions() throws {
        let (context, profile) = try makeWorld()
        WelcomeLibrary.seed(into: profile, context: context, bundle: appBundle)
        let items = WhitelistRepository(context: context).items(of: profile, type: .video)
        let legacy = try #require(items.first)
        legacy.approvalStatus = .approved
        legacy.approvedBy = "SideTube"
        let parent = WhitelistItem(type: .video, youtubeId: "parent", title: "Parent decision", thumbnailUrl: "", approvalStatus: .approved)
        parent.profile = profile
        parent.approvedBy = "Eltern"
        context.insert(parent)
        try context.save()
        try WelcomeLibrary.requireReviewForLegacySeeds(context: context)
        #expect(legacy.approvalStatus == .reviewRequired)
        #expect(parent.approvalStatus == .approved)
        let deletedId = legacy.youtubeId
        context.delete(legacy)
        try context.save()
        try WelcomeLibrary.requireReviewForLegacySeeds(context: context)
        #expect(!WhitelistRepository(context: context).contains(youtubeId: deletedId, in: profile))
    }
}
