// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Testing
@testable import sidetube

private final class RecordingWatchTime: WatchTimeRecording {
    enum Failure: Error { case unavailable }
    var records: [(String, Int, Date)] = []
    var shouldFail = false
    func record(videoId: String, title: String, seconds: Int, for profile: KidProfile, at date: Date) throws {
        if shouldFail { throw Failure.unavailable }
        records.append((videoId, seconds, date))
    }
}

private func berlinCalendar() -> Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "Europe/Berlin")!
    return calendar
}

struct PlayerAccountingTests {
    @Test(arguments: [-1, 3, 5])
    func loadingStatesDoNotConsumeViewingTime(state: Int) {
        let clock = FakeClock()
        let engine = FakePlayerEngine()
        let recorder = RecordingWatchTime()
        let model = PlayerModel(queue: [.init(videoId: "a", title: "A")], startIndex: 0,
            engine: engine, watchTime: recorder, profile: KidProfile(name: "Example"), now: { clock.now })
        model.start(); engine.emit(.state(1)); clock.advance(12)
        engine.emit(.state(state)); clock.advance(100)
        #expect(!model.isPlaying)
        #expect(model.unrecordedSeconds == 12)
        engine.emit(.state(1)); clock.advance(8); model.close()
        #expect(recorder.records.map { $0.1 } == [20])
    }

    @Test func wallClockChangesDoNotChangeElapsedViewing() {
        let wall = FakeClock()
        var monotonic: TimeInterval = 100
        let engine = FakePlayerEngine()
        let recorder = RecordingWatchTime()
        let model = PlayerModel(queue: [.init(videoId: "a", title: "A")], startIndex: 0,
            engine: engine, watchTime: recorder, profile: KidProfile(name: "Example"),
            now: { wall.now }, elapsedNow: { monotonic })
        model.start(); engine.emit(.state(1))
        monotonic += 10; wall.advance(86400)
        #expect(model.unrecordedSeconds == 10)
        monotonic += 10; wall.advance(-172800)
        model.close()
        #expect(recorder.records.map { $0.1 } == [20])
        #expect(recorder.records.first?.2 == wall.now, "History date and elapsed clock serve different purposes")
    }

    @Test func positionSignalsReconcileMissingCallbacksWithoutDoubleCounting() {
        let clock = FakeClock()
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: [.init(videoId: "a", title: "A")], startIndex: 0,
            engine: engine, now: { clock.now })
        model.start()
        engine.emit(.position(seconds: 0, duration: 200, isPlaying: true)); clock.advance(4)
        engine.emit(.state(1)); clock.advance(6)
        engine.emit(.position(seconds: 10, duration: 200, isPlaying: true))
        #expect(model.unrecordedSeconds == 10)
        engine.emit(.position(seconds: 10, duration: 200, isPlaying: false)); clock.advance(100)
        #expect(model.unrecordedSeconds == 10)
        #expect(model.status == .paused)
    }

    @Test(arguments: [true, false])
    func terminalProviderEventsPreserveTheRequestedVideosTime(foreign: Bool) {
        let clock = FakeClock()
        let engine = FakePlayerEngine()
        let recorder = RecordingWatchTime()
        let model = PlayerModel(queue: [.init(videoId: "a", title: "A")], startIndex: 0,
            engine: engine, watchTime: recorder, profile: KidProfile(name: "Example"), now: { clock.now })
        model.start(); engine.emit(.state(1)); clock.advance(12)
        engine.emit(foreign ? .foreignVideo("unrequested") : .apiFailed)
        #expect(!model.isPlaying)
        #expect(recorder.records.map { $0.0 } == ["a"])
        #expect(recorder.records.map { $0.1 } == [12])
        clock.advance(60); model.close()
        #expect(recorder.records.count == 1)
        #expect(engine.commands.contains("stop"))
    }

    @Test func shortSessionsAreNotRoundedAwayAndRepeatedCloseDoesNotDuplicateThem() {
        var monotonic: TimeInterval = 0
        let engine = FakePlayerEngine()
        let recorder = RecordingWatchTime()
        let model = PlayerModel(queue: [.init(videoId: "a", title: "A")], startIndex: 0,
            engine: engine, watchTime: recorder, profile: KidProfile(name: "Example"), elapsedNow: { monotonic })
        model.start(); engine.emit(.state(1)); monotonic = 0.1
        model.close(); model.close()
        #expect(recorder.records.map { $0.1 } == [1])
    }

    @Test func continuousStretchCrossingMidnightSplitsByCalendarDay() {
        let calendar = berlinCalendar()
        let clock = FakeClock()
        clock.now = calendar.date(from: DateComponents(year: 2026, month: 1, day: 1, hour: 23, minute: 59, second: 50))!
        let engine = FakePlayerEngine()
        let recorder = RecordingWatchTime()
        let model = PlayerModel(queue: [.init(videoId: "a", title: "A")], startIndex: 0,
            engine: engine, watchTime: recorder, profile: KidProfile(name: "Example"),
            now: { clock.now }, calendar: calendar)
        model.start(); engine.emit(.state(1))
        clock.advance(20)   // 23:59:50 -> 00:00:10 the next day
        model.close()

        #expect(recorder.records.count == 2)
        let jan1 = try! #require(recorder.records.first { $0.1 == 10 && calendar.isDate($0.2, inSameDayAs: calendar.date(from: DateComponents(year: 2026, month: 1, day: 1))!) })
        let jan2 = try! #require(recorder.records.first { calendar.isDate($0.2, inSameDayAs: calendar.date(from: DateComponents(year: 2026, month: 1, day: 2))!) })
        #expect(jan1.1 == 10, "10 seconds before midnight belong to Jan 1")
        #expect(jan2.1 == 10, "10 seconds after midnight belong to Jan 2")
        #expect(jan2.2 == clock.now, "the most recent day books at the actual flush time")
    }

    @Test func unrecordedSecondsStayCorrectWhileStillPlayingAcrossMidnight() {
        let calendar = berlinCalendar()
        let clock = FakeClock()
        clock.now = calendar.date(from: DateComponents(year: 2026, month: 1, day: 1, hour: 23, minute: 59, second: 55))!
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: [.init(videoId: "a", title: "A")], startIndex: 0,
            engine: engine, now: { clock.now }, calendar: calendar)
        model.start(); engine.emit(.state(1))
        clock.advance(10)   // crosses midnight while still playing, no pause/close yet
        #expect(model.unrecordedSeconds == 10)
    }

    @Test func aFailureOnTheEarlierDayLeavesBothDaysUncommitted() {
        let calendar = berlinCalendar()
        let clock = FakeClock()
        clock.now = calendar.date(from: DateComponents(year: 2026, month: 1, day: 1, hour: 23, minute: 59, second: 50))!
        let engine = FakePlayerEngine()
        let recorder = RecordingWatchTime()
        recorder.shouldFail = true
        let model = PlayerModel(queue: [.init(videoId: "a", title: "A")], startIndex: 0,
            engine: engine, watchTime: recorder, profile: KidProfile(name: "Example"),
            now: { clock.now }, calendar: calendar)
        model.start(); engine.emit(.state(1))
        clock.advance(20)
        model.close()

        #expect(model.hasWatchTimeFailure)
        #expect(model.status == .storageFailed)
        #expect(recorder.records.isEmpty)
        #expect(model.failedWatchRecord?.seconds == 10, "fails on the earlier day first, in order")
        #expect(model.unrecordedSeconds == 20, "neither day's seconds were discarded")
    }

    @Test(arguments: [true, false])
    func latePlayingSignalsCannotReviveATerminalPlayer(failed: Bool) {
        let clock = FakeClock()
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: [.init(videoId: "a", title: "A")], startIndex: 0,
            engine: engine, now: { clock.now })
        model.start(); engine.emit(.state(1)); clock.advance(10)
        engine.emit(failed ? .apiFailed : .state(0))
        let terminal = model.status
        engine.emit(.position(seconds: 10, duration: 100, isPlaying: true))
        engine.emit(.state(1)); clock.advance(100)
        #expect(model.status == terminal)
        #expect(!model.isPlaying)
        #expect(model.unrecordedSeconds == 0)
        model.replay()
        engine.emit(.state(1)); clock.advance(2)
        #expect(model.isPlaying)
        #expect(model.unrecordedSeconds == 2)
    }
}
