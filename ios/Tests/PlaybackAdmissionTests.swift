// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

struct PlaybackAdmissionRepositoryTests {
    @Test func preAdmissionSchemaMigratesProfilesApprovalsAndHistory() throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: "pre-admission-upgrade-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let storeURL = directory.appending(path: "sidetube.store")
        let oldSchema = Schema([
            PreAdmissionModels.KidProfile.self, PreAdmissionModels.WhitelistItem.self,
            PreAdmissionModels.WatchHistoryEntry.self, PreAdmissionModels.CachedChannelVideo.self,
            PreAdmissionModels.CuratedSource.self, PreAdmissionModels.ReviewEvent.self
        ])
        #expect(oldSchema.entities.count == 6)
        #expect(!oldSchema.entities.contains { $0.name == "PlaybackAdmission" })
        #expect(ModelContainerFactory.schema.entities.count == 8)
        let profileID = UUID()
        do {
            let container = try ModelContainer(for: oldSchema, configurations: [ModelConfiguration(schema: oldSchema, url: storeURL)])
            let context = ModelContext(container)
            let profile = PreAdmissionModels.KidProfile(id: profileID, name: "Synthetic migration profile", dailyLimitMinutes: 10)
            profile.allowNews = false
            profile.bedtimeStartMinutes = 1200
            context.insert(profile)
            for status in [ApprovalStatus.approved, .reviewRequired, .rejected] {
                let item = PreAdmissionModels.WhitelistItem(type: .video, youtubeId: status.rawValue,
                    title: "Synthetic \(status.rawValue)", thumbnailUrl: "", approvalStatus: status)
                item.profile = profile
                context.insert(item)
            }
            let history = PreAdmissionModels.WatchHistoryEntry(videoId: "approved", videoTitle: "Synthetic", watchedSeconds: 30)
            history.profile = profile
            context.insert(history)
            context.insert(PreAdmissionModels.CachedChannelVideo(channelId: "synthetic", videoId: "approved",
                title: "Synthetic", thumbnailUrl: "", channelTitle: "Synthetic", position: 0))
            context.insert(PreAdmissionModels.CuratedSource(channelId: "synthetic", title: "Synthetic", trust: .perVideoReview))
            context.insert(PreAdmissionModels.ReviewEvent(itemYoutubeId: "approved", profileId: profileID,
                decision: .approved, actor: "synthetic-parent", itemVersion: "fixture"))
            try context.save()
        }
        do {
            let container = try ModelContainer(for: ModelContainerFactory.schema, configurations: [
                ModelConfiguration(schema: ModelContainerFactory.schema, url: storeURL)
            ])
            let context = ModelContext(container)
            let profile = try #require(try ProfileRepository(context: context).all().first)
            #expect(profile.id == profileID)
            #expect(profile.name == "Synthetic migration profile")
            #expect(profile.dailyLimitMinutes == 10)
            #expect(!profile.allowNews)
            #expect(profile.bedtimeStartMinutes == 1200)
            #expect(Set(profile.whitelistItems.map(\.approvalStatusRaw)) == Set(["approved", "reviewRequired", "rejected"]))
            #expect(WatchTimeRepository(context: context).totalSeconds(of: profile) == 30)
            let cached = try #require(try context.fetch(FetchDescriptor<CachedChannelVideo>()).first)
            #expect(cached.videoChannelId == nil, "alter Eintrag: Kanal unbekannt → Playlist-Video nur mit eigener Freigabe")
            #expect(!cached.isShort && !cached.isUpcoming)
            #expect(try context.fetchCount(FetchDescriptor<CuratedSource>()) == 1)
            #expect(try context.fetch(FetchDescriptor<ReviewEvent>()).first?.profileId == profileID)
            let admissions = PlaybackAdmissionRepository(context: context)
            #expect(try !admissions.isPending(for: profileID))
            let token = UUID()
            try admissions.begin(for: profile, token: token, at: Date())
            #expect(try admissions.isPending(for: profileID))
            try admissions.finish(profileID: profileID, token: token)
            #expect(try !admissions.isPending(for: profileID))
            // Wünsche (ADR 0001) sind eine neue Entität: alter Store öffnet, Wünsche lassen sich anlegen.
            #expect(try context.fetchCount(FetchDescriptor<KidWish>()) == 0)
            let wish = try WishRepository(context: context).submit(.thema("Dinos"), for: profile).wish
            #expect(wish.profileID == profileID)
            #expect(try context.fetchCount(FetchDescriptor<KidWish>()) == 1)
        }
    }

    private func makeProfile(in context: ModelContext, name: String = "Example") throws -> KidProfile {
        try ProfileRepository(context: context).create(name: name)
    }

    @Test func markerSurvivesReopeningAndParentAcknowledgementPreservesHistory() throws {
        let container = try ModelContainerFactory.make(inMemory: true)
        var context = ModelContext(container)
        let profile = try makeProfile(in: context)
        let repo = PlaybackAdmissionRepository(context: context)
        try repo.begin(for: profile, token: UUID(), at: Date())
        try WatchTimeRepository(context: context).record(videoId: "v", title: "V", seconds: 30, for: profile)

        // A fresh context against the same store stands in for the app relaunching after a kill.
        context = ModelContext(container)
        let reopened = PlaybackAdmissionRepository(context: context)
        #expect(try reopened.isPending(for: profile.id))
        #expect(throws: PlaybackAdmissionError.alreadyPending) {
            try reopened.begin(for: profile, token: UUID(), at: Date())
        }
        try reopened.acknowledgeByParent(profileID: profile.id)
        #expect(try !reopened.isPending(for: profile.id))
        #expect(try WatchTimeRepository(context: context).totalSeconds(of: profile) == 30, "acknowledging must not touch recorded watch time")
    }

    @Test func finishOnlyClearsItsOwnTokenNeverAStaleOne() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try makeProfile(in: context)
        let repo = PlaybackAdmissionRepository(context: context)
        try repo.begin(for: profile, token: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!, at: Date())
        // A stale completion for an already-superseded token must not clear the current marker.
        try repo.finish(profileID: profile.id, token: UUID(uuidString: "00000000-0000-0000-0000-000000000002")!)
        #expect(try repo.isPending(for: profile.id))
        try repo.finish(profileID: profile.id, token: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!)
        #expect(try !repo.isPending(for: profile.id))
    }

    @Test func markersAreProfileScopedAndRemovedOnlyWithTheirOwnProfile() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let p = try makeProfile(in: context, name: "P")
        let q = try makeProfile(in: context, name: "Q")
        let repo = PlaybackAdmissionRepository(context: context)
        try repo.begin(for: p, token: UUID(), at: Date())
        try repo.begin(for: q, token: UUID(), at: Date())
        #expect(try repo.interruptedProfileIDs() == Set([p.id, q.id]))
        context.delete(p)
        try context.save()
        #expect(try !repo.isPending(for: p.id), "cascade delete must remove p's own marker")
        #expect(try repo.isPending(for: q.id), "an unresolved session for p must not lock q's profile")
    }

    /// The current KidProfile relationship pulls PlaybackAdmission into this schema even
    /// when omitted from the explicit list. This is a reopen test, NOT an old-schema migration.
    @Test func currentRelationshipSchemaReopensWithoutInventingAnInterruptedSession() throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: "playback-admission-migration-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let storeURL = directory.appending(path: "sidetube.store")

        let relationshipSchema = Schema([KidProfile.self, WhitelistItem.self, WatchHistoryEntry.self,
                                 CachedChannelVideo.self, CuratedSource.self, ReviewEvent.self])
        #expect(relationshipSchema.entities.contains { $0.name == "PlaybackAdmission" })
        let profileID: UUID
        // Scoped so the first container/context are released before the same file is reopened
        // below - two live containers on one SQLite file at once is not what a real relaunch does.
        do {
            let container = try ModelContainer(for: relationshipSchema, configurations: [ModelConfiguration(schema: relationshipSchema, url: storeURL)])
            let context = ModelContext(container)
            let profile = try ProfileRepository(context: context).create(name: "Example", dailyLimitMinutes: 10)
            profileID = profile.id
            try WatchTimeRepository(context: context).record(videoId: "v", title: "Example", seconds: 30, for: profile)
        }

        let upgraded = try ModelContainer(for: ModelContainerFactory.schema,
                                          configurations: [ModelConfiguration(schema: ModelContainerFactory.schema, url: storeURL)])
        let newContext = ModelContext(upgraded)
        let profiles = try newContext.fetch(FetchDescriptor<KidProfile>())
        #expect(profiles.first?.id == profileID)
        #expect(profiles.first?.dailyLimitMinutes == 10)
        #expect(try WatchTimeRepository(context: newContext).totalSeconds(of: profiles[0]) == 30)
        let repo = PlaybackAdmissionRepository(context: newContext)
        #expect(try !repo.isPending(for: profileID), "reopening the store must not fabricate an interrupted session")
        try repo.begin(for: profiles[0], token: UUID(), at: Date())
        #expect(try repo.isPending(for: profileID))
    }

    @Test func diskMarkerAndParentRecoverySurviveSeparateContainerLifetimes() throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: "playback-admission-reopen-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let storeURL = directory.appending(path: "sidetube.store")
        func open() throws -> ModelContainer {
            try ModelContainer(for: ModelContainerFactory.schema, configurations: [
                ModelConfiguration(schema: ModelContainerFactory.schema, url: storeURL)
            ])
        }
        let profileID: UUID
        let token = UUID()
        do {
            let container = try open()
            let context = ModelContext(container)
            let profile = try makeProfile(in: context)
            profileID = profile.id
            try WatchTimeRepository(context: context).record(videoId: "v", title: "V", seconds: 30, for: profile)
            try PlaybackAdmissionRepository(context: context).begin(for: profile, token: token, at: Date())
        }
        do {
            let container = try open()
            let context = ModelContext(container)
            let profile = try #require(try ProfileRepository(context: context).all().first)
            #expect(profile.id == profileID)
            let repo = PlaybackAdmissionRepository(context: context)
            #expect(try repo.isPending(for: profileID))
            #expect(throws: PlaybackAdmissionError.alreadyPending) {
                try repo.begin(for: profile, token: UUID(), at: Date())
            }
            try repo.finish(profileID: profileID, token: UUID())
            #expect(try repo.isPending(for: profileID))
            try repo.acknowledgeByParent(profileID: profileID)
        }
        do {
            let container = try open()
            let context = ModelContext(container)
            let profile = try #require(try ProfileRepository(context: context).all().first)
            #expect(profile.id == profileID)
            #expect(try !PlaybackAdmissionRepository(context: context).isPending(for: profileID))
            #expect(WatchTimeRepository(context: context).totalSeconds(of: profile) == 30)
        }
    }

    @Test(arguments: [false, true])
    func markerRemovalDoesNotSaveOrDiscardUnrelatedParentEdits(parentRecovery: Bool) throws {
        let container = try ModelContainerFactory.make(inMemory: true)
        let context = ModelContext(container)
        context.autosaveEnabled = false
        let profile = try makeProfile(in: context, name: "Original")
        let repo = PlaybackAdmissionRepository(context: context)
        let token = UUID()
        try repo.begin(for: profile, token: token, at: Date())
        profile.name = "Unsaved parent edit"
        if parentRecovery {
            try repo.acknowledgeByParent(profileID: profile.id)
        } else {
            try repo.finish(profileID: profile.id, token: token)
        }
        #expect(profile.name == "Unsaved parent edit")
        #expect(context.hasChanges)
        let reader = ModelContext(container)
        #expect(try ProfileRepository(context: reader).all().first?.name == "Original")
        #expect(try !PlaybackAdmissionRepository(context: reader).isPending(for: profile.id))
        try context.save()
        let afterSave = ModelContext(container)
        #expect(try ProfileRepository(context: afterSave).all().first?.name == "Unsaved parent edit")
        #expect(try !PlaybackAdmissionRepository(context: afterSave).isPending(for: profile.id))
    }
}

private func approve(videoId: String, for profile: KidProfile, in context: ModelContext) throws {
    let item = WhitelistItem(type: .video, youtubeId: videoId, title: videoId, thumbnailUrl: "", approvalStatus: .approved)
    item.profile = profile
    context.insert(item)
    try context.save()
}

struct PlaybackAdmissionCoordinatorTests {
    private final class FailingAdmissionRecorder: PlaybackAdmissionRecording {
        enum Failure: Error { case unavailable }
        var failRead = false
        var failAcknowledgement = false
        var pending = true
        var beginCalls = 0
        func isPending(for profileID: UUID) throws -> Bool {
            if failRead { throw Failure.unavailable }
            return pending
        }
        func begin(for profile: KidProfile, token: UUID, at date: Date) throws {
            beginCalls += 1
            pending = true
        }
        func finish(profileID: UUID, token: UUID) throws { pending = false }
        func acknowledgeByParent(profileID: UUID) throws {
            if failAcknowledgement { throw Failure.unavailable }
            pending = false
        }
        func interruptedProfileIDs() throws -> Set<UUID> { [] }
    }

    @Test func admissionReadFailureNeverAttemptsToBeginOrLoadPlayback() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Example")
        try approve(videoId: "a", for: profile, in: context)
        let recorder = FailingAdmissionRecorder()
        recorder.failRead = true
        let coordinator = PlayerCoordinator()
        let engine = FakePlayerEngine()
        coordinator.makeEngine = { engine }
        coordinator.makeAdmissionRecorder = { _ in recorder }
        coordinator.play(queue: [.init(videoId: "a", title: "A")], startIndex: 0, profile: profile, context: context)
        #expect(recorder.beginCalls == 0)
        #expect(coordinator.player == nil)
        #expect(engine.loaded.isEmpty)
        #expect(coordinator.interruptedSessionProfileID == profile.id)
    }

    @Test func failedParentAcknowledgementKeepsBlockUntilSuccessfulRetry() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Example")
        try approve(videoId: "a", for: profile, in: context)
        let recorder = FailingAdmissionRecorder()
        recorder.failAcknowledgement = true
        let coordinator = PlayerCoordinator()
        let engine = FakePlayerEngine()
        coordinator.makeEngine = { engine }
        coordinator.makeAdmissionRecorder = { _ in recorder }
        coordinator.play(queue: [.init(videoId: "a", title: "A")], startIndex: 0, profile: profile, context: context)
        #expect(!coordinator.acknowledgeInterruptedSession(context: context))
        #expect(coordinator.interruptedSessionProfileID == profile.id)
        #expect(recorder.pending)
        #expect(engine.loaded.isEmpty)
        recorder.failAcknowledgement = false
        #expect(coordinator.acknowledgeInterruptedSession(context: context))
        #expect(!coordinator.isBlockedByInterruptedSession)
        #expect(!recorder.pending)
        #expect(!coordinator.acknowledgeInterruptedSession(context: context), "no pending recovery is not a new acknowledgement")
    }

    @Test func cleanCloseClearsTheMarkerButAnUnclosedOneBlocksAFreshPlayerUntilParentClears() throws {
        let container = try ModelContainerFactory.make(inMemory: true)
        var context = ModelContext(container)
        let profile = try ProfileRepository(context: context).create(name: "Example")
        let profileID = profile.id
        try approve(videoId: "a", for: profile, in: context)
        let queue = [PlayerModel.Item(videoId: "a", title: "A")]

        let clock = FakeClock()
        let coordinator = PlayerCoordinator(now: { clock.now })
        coordinator.makeEngine = { FakePlayerEngine() }
        coordinator.play(queue: queue, startIndex: 0, profile: profile, context: context)
        #expect(coordinator.player != nil)
        #expect(try PlaybackAdmissionRepository(context: context).isPending(for: profileID))
        coordinator.close()
        #expect(try !PlaybackAdmissionRepository(context: context).isPending(for: profileID), "a clean close must remove its own marker")
        #expect(!coordinator.isBlockedByInterruptedSession)

        // Simulate the app being killed mid-playback: a marker survives with nothing to clear it.
        try PlaybackAdmissionRepository(context: context).begin(for: profile, token: UUID(), at: clock.now)

        // Stand in for relaunching the app: a fresh coordinator, and a profile freshly fetched
        // from the reopened store rather than the live object from the context above.
        context = ModelContext(container)
        let relaunchedProfile = try #require(
            try context.fetch(FetchDescriptor<KidProfile>(predicate: #Predicate { $0.id == profileID })).first)
        let relaunched = PlayerCoordinator(now: { clock.now })
        let engine = FakePlayerEngine()
        relaunched.makeEngine = { engine }
        relaunched.play(queue: queue, startIndex: 0, profile: relaunchedProfile, context: context)
        #expect(relaunched.player == nil, "an unclosed session must block a fresh one for this profile")
        #expect(engine.loaded.isEmpty)
        #expect(relaunched.isBlockedByInterruptedSession)

        relaunched.acknowledgeInterruptedSession(context: context)
        #expect(!relaunched.isBlockedByInterruptedSession)
        relaunched.play(queue: queue, startIndex: 0, profile: relaunchedProfile, context: context)
        #expect(relaunched.player != nil, "after parent acknowledgement, playback resumes normally")
    }

    @Test func interruptedSessionForOneProfileDoesNotBlockAnother() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let repository = ProfileRepository(context: context)
        let stuck = try repository.create(name: "Stuck")
        let other = try repository.create(name: "Other")
        try approve(videoId: "a", for: other, in: context)
        try PlaybackAdmissionRepository(context: context).begin(for: stuck, token: UUID(), at: Date())

        let coordinator = PlayerCoordinator()
        coordinator.makeEngine = { FakePlayerEngine() }
        let queue = [PlayerModel.Item(videoId: "a", title: "A")]
        coordinator.play(queue: queue, startIndex: 0, profile: other, context: context)
        #expect(coordinator.player != nil, "another profile's unresolved session must not block this one")
        #expect(!coordinator.isBlockedByInterruptedSession)
    }
}
