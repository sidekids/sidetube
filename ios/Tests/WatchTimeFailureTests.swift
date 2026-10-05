// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

private final class FailingWatchRecorder: WatchTimeRecording {
    enum Failure: Error { case unavailable }
    var calls = 0
    func record(videoId: String, title: String, seconds: Int, for profile: KidProfile, at date: Date) throws {
        calls += 1
        throw Failure.unavailable
    }
}

struct WatchTimeFailureTests {
    @Test(arguments: ["next", "previous", "jump", "replay", "recommendation", "end", "error", "close"])
    func failedWritesStopEveryTransitionAndRetainTime(transition: String) {
        let clock = FakeClock()
        let recorder = FailingWatchRecorder()
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: [.init(videoId: "a", title: "A"), .init(videoId: "b", title: "B")],
            startIndex: 0, engine: engine, watchTime: recorder,
            profile: KidProfile(name: "Example"), now: { clock.now })
        model.autoAdvance = true
        model.start(); engine.emit(.state(1)); clock.advance(12)
        switch transition {
        case "next": model.next()
        case "previous": model.previous()
        case "jump": model.jump(to: 1)
        case "replay": model.replay()
        case "recommendation": model.play(.init(videoId: "c", title: "C"))
        case "end": engine.emit(.state(0))
        case "error": engine.emit(.error(150))
        default: model.close()
        }
        #expect(model.hasWatchTimeFailure)
        #expect(model.status == .storageFailed)
        #expect(!model.isPlaying)
        #expect(model.unrecordedSeconds == 12)
        #expect(model.failedWatchRecord?.seconds == 12)
        #expect(model.failedWatchRecord?.videoId == "a")
        #expect(engine.commands.contains("stop"))
        #expect(engine.onEvent == nil)
        model.next(); model.previous(); model.replay(); model.togglePlayback(); model.start(); model.close()
        engine.emit(.state(1)); clock.advance(30)
        #expect(engine.loaded == ["a"])
        #expect(!engine.commands.contains("toggle"))
        #expect(recorder.calls == 1, "An ambiguous failed save must not be blindly retried")
        #expect(model.unrecordedSeconds == 12)
    }

    @Test func coordinatorKeepsFailureLatchedAfterClosingAndBlocksFreshPlayers() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Example")
        let approved = WhitelistItem(type: .video, youtubeId: "a", title: "A", thumbnailUrl: "", approvalStatus: .approved)
        approved.profile = profile
        context.insert(approved)
        try context.save()
        let engine = FakePlayerEngine()
        let recorder = FailingWatchRecorder()
        let clock = FakeClock()
        let coordinator = PlayerCoordinator(now: { clock.now })
        coordinator.makeEngine = { engine }
        coordinator.makeWatchTimeRecorder = { _ in recorder }
        let queue = [PlayerModel.Item(videoId: "a", title: "A")]
        coordinator.play(queue: queue, startIndex: 0, profile: profile, context: context)
        #expect(coordinator.player != nil)
        engine.emit(.state(1)); clock.advance(10)
        coordinator.close()
        #expect(coordinator.hasWatchTimeFailure)
        #expect(coordinator.failedWatchRecord?.seconds == 10)
        #expect(coordinator.failedWatchRecord?.profileID == profile.id)
        coordinator.play(queue: queue, startIndex: 0, profile: profile, context: context)
        #expect(coordinator.player == nil)
        #expect(engine.loaded == ["a"])
        #expect(recorder.calls == 1)
        #expect(coordinator.failedWatchRecord?.seconds == 10)
    }
}
