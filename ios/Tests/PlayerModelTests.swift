// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

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
    func seek(to seconds: Double) { commands.append("seekTo \(Int(seconds))") }
    func setVolume(_ percent: Int) { commands.append("volume \(percent)") }
    func stop() { commands.append("stop") }
    func setCaptions(_ on: Bool) { commands.append("captions \(on)") }
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
        #expect(model.positionText == "Video 3 von 3")
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

    @Test func endedWithoutAutoplayStopsEngineAndStaysOnVideo() {
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: queue, startIndex: 0, engine: engine)
        model.start()
        engine.emit(.state(1))
        engine.emit(.state(0))
        #expect(model.status == .ended)
        #expect(model.current.videoId == "a", "kein automatisches Weiterspringen")
        #expect(engine.commands == ["stop"], "YouTube-Endscreen wird sofort durch stopVideo() ersetzt")
    }

    @Test func foreignVideoFromIframeIsStoppedAndNeverBecomesCurrent() {
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: queue, startIndex: 0, engine: engine)
        model.start()
        engine.emit(.state(1))
        engine.emit(.foreignVideo("dQw4w9WgXcQ"))   // Endscreen-Karte oder Pausen-Vorschlag im IFrame
        #expect(engine.commands == ["stop"])
        #expect(model.status == .ended, "klarer Zustand statt fremdem Video")
        #expect(model.current.videoId == "a")
        #expect(model.queue.map(\.videoId) == ["a", "b", "c"], "fremde ID landet nie in der Warteschlange")
        #expect(model.blockedForeignVideoIds == ["dQw4w9WgXcQ"])
    }

    @Test func playAppendsUnknownSuggestionAndJumpsToKnownOne() {
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: queue, startIndex: 0, engine: engine)
        model.start()
        model.play(PlayerModel.Item(videoId: "d", title: "D"))
        #expect(model.queue.map(\.videoId) == ["a", "b", "c", "d"])
        #expect(model.current.videoId == "d")
        model.play(PlayerModel.Item(videoId: "b", title: "B"))
        #expect(model.queue.count == 4, "bekannte Videos werden nicht doppelt angehängt")
        #expect(model.current.videoId == "b")
        model.play(PlayerModel.Item(videoId: "b", title: "B"))
        #expect(engine.loaded == ["a", "d", "b", "b"], "dasselbe Video noch einmal = Nochmal")
        #expect(model.status == .loading)
    }

    @Test func replayReloadsCurrentAndCloseStopsEngine() {
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: queue, startIndex: 1, engine: engine)
        model.start()
        engine.emit(.state(0))
        model.replay()
        #expect(engine.loaded == ["b", "b"])
        #expect(model.status == .loading)
        model.close()
        #expect(engine.commands.last == "stop", "Ton läuft nach Fertig nicht weiter")
    }

    @Test func positionUpdatesFeedTheProgressBarAndSeekingStaysInsideTheVideo() {
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: queue, startIndex: 0, engine: engine)
        model.start()
        engine.emit(.position(seconds: 30, duration: 120, isPlaying: true))
        #expect(model.currentSeconds == 30)
        #expect(model.durationSeconds == 120)
        #expect(model.isPlaying)

        model.seek(by: 10)
        #expect(model.currentSeconds == 40, "die Anzeige folgt sofort, nicht erst beim nächsten Takt")
        model.seek(by: 600)
        #expect(model.currentSeconds == 120, "nie über das Ende hinaus")
        model.seek(to: -5)
        #expect(model.currentSeconds == 0)
        #expect(engine.commands == ["seek 10", "seek 600", "seekTo 0"])

        model.next()
        #expect(model.durationSeconds == 0, "das nächste Video beginnt mit leerem Balken")
        #expect(!model.isPlaying)
    }

    @Test func captionsAppearOnlyWhenTheVideoHasThemAndTheChoiceSurvivesTheNextVideo() {
        CaptionsPreference.isOn = false
        defer { CaptionsPreference.isOn = false }
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: queue, startIndex: 0, engine: engine)
        model.start()
        #expect(!model.captionsAvailable, "ohne Meldung der Seite kein Knopf")

        engine.emit(.captions(available: true, enabled: false))
        #expect(model.captionsAvailable)
        #expect(!model.captionsEnabled)
        #expect(!engine.commands.contains("captions false"), "passt schon, also kein Befehl")

        // Das Laden des Moduls schaltet die Untertitel von sich aus ein – die Wahl des Kindes gilt.
        engine.emit(.captions(available: true, enabled: true))
        #expect(engine.commands.last == "captions false")
        #expect(!model.captionsEnabled)

        model.toggleCaptions()
        #expect(model.captionsEnabled)
        #expect(engine.commands.last == "captions true")
        #expect(CaptionsPreference.isOn, "die Wahl bleibt gemerkt")

        model.next()
        #expect(!model.captionsAvailable, "das nächste Video meldet erst wieder selbst")
        engine.emit(.state(1))
        #expect(engine.commands.last == "captions true", "die gemerkte Wahl gilt auch dort")
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
