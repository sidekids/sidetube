import Foundation
import SwiftData
import Testing
@testable import sidetube

final class FakePlayerEngine: PlayerEngine {
    var onEvent: ((PlayerEngineEvent) -> Void)?
    var loaded: [String] = []
    var commands: [String] = []
    func load(videoId: String) { loaded.append(videoId) }
    func play() { commands.append("play") }
    func pause() { commands.append("pause") }
    func togglePlayback() { commands.append("toggle") }
    func seek(by seconds: Double) { commands.append("seek \(Int(seconds))") }
    func setVolume(_ percent: Int) { commands.append("volume \(percent)") }
    func stop() { commands.append("stop") }
    func emit(_ event: PlayerEngineEvent) { onEvent?(event) }
}

struct PlayerModelTests {
    let queue = [PlayerModel.Item(videoId: "a", title: "A"), PlayerModel.Item(videoId: "b", title: "B"), PlayerModel.Item(videoId: "c", title: "C")]

    @Test func startsAtIndexAndCyclesBothWays() {
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: queue, startIndex: 1, engine: engine)
        model.start()
        #expect(engine.loaded == ["b"])
        model.next(); model.next()
        #expect(model.current.videoId == "a", "nach dem letzten kommt wieder das erste")
        model.previous()
        #expect(model.current.videoId == "c")
        #expect(model.positionText == "3 von 3")
    }

    @Test func endedAdvancesAutomatically() {
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: queue, startIndex: 0, engine: engine)
        model.autoAdvance = true   // Eltern-Einstellung; Standard ist aus (siehe AutoplayPolicyTests)
        model.start()
        engine.emit(.state(1))
        #expect(model.status == .playing)
        engine.emit(.state(0))
        #expect(model.current.videoId == "b")
        #expect(engine.loaded == ["a", "b"])
    }

    @Test func embedErrorSkipsUntilAllFailed() {
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: queue, startIndex: 0, engine: engine)
        model.start()
        engine.emit(.error(150))
        #expect(model.current.videoId == "b")
        #expect(model.status == .loading)
        #expect(model.recentlySkipped == "A")
        engine.emit(.state(1))
        #expect(model.recentlySkipped == nil)
        engine.emit(.error(101))
        engine.emit(.error(100))
        #expect(model.status == .allUnavailable)
        #expect(engine.loaded == ["a", "b", "c"], "kein Endlos-Kreisen, wenn alles fehlschlägt")
    }

    @Test func wheelCommandsReachEngine() {
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: queue, startIndex: 0, engine: engine)
        model.start()
        model.togglePlayback(); model.seek(by: 20); model.pause()
        #expect(engine.commands == ["toggle", "seek 20", "pause"])
        engine.emit(.apiFailed)
        #expect(model.status == .engineFailed)
    }

    @Test func watchTimeIsRecordedOnEndAndOnClose() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia")
        let repo = WatchTimeRepository(context: context)
        let clock = FakeClock()
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: queue, startIndex: 0, engine: engine, watchTime: repo, profile: profile, now: { clock.now })
        model.start()
        model.autoAdvance = true
        engine.emit(.state(1)); clock.advance(40)
        engine.emit(.state(2)); clock.advance(100)          // Pause zählt nicht
        engine.emit(.state(1)); clock.advance(20)
        engine.emit(.state(0))                              // Ende → 60 s für "a", weiter zu "b"
        #expect(repo.totalSeconds(of: profile) == 60)
        engine.emit(.state(1)); clock.advance(15)
        #expect(model.unrecordedSeconds == 15, "laufende Sekunden zählen live fürs Tageslimit")
        model.close()                                       // Abbruch → 15 s für "b"
        #expect(repo.totalSeconds(of: profile) == 75)
        #expect(profile.watchHistory.map(\.videoId).sorted() == ["a", "b"])
    }
}
