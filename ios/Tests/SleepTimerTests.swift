import Foundation
import SwiftData
import Testing
@testable import sidetube

struct SleepTimerTests {
    @Test func countsDownExpiresOnceAndFades() {
        let timer = SleepTimer()
        let clock = FakeClock()
        #expect(timer.remainingSeconds(at: clock.now) == nil)
        timer.start(seconds: 90, now: clock.now)
        #expect(timer.isRunning)
        #expect(timer.remainingSeconds(at: clock.now) == 90)
        #expect(timer.fadeVolume(at: clock.now) == nil, "außerhalb der letzten Minute keine Ausblendung")
        clock.advance(45)
        #expect(timer.fadeVolume(at: clock.now) == 75)
        clock.advance(44)
        #expect(timer.tick(now: clock.now) == false)
        clock.advance(1)
        #expect(timer.tick(now: clock.now) == true, "genau ein Ablauf-Tick")
        #expect(timer.tick(now: clock.now) == false)
        #expect(timer.expired)
        #expect(!timer.isRunning)
        #expect(timer.remainingSeconds(at: clock.now) == nil)
        timer.stop()
        #expect(!timer.expired)
    }

    @Test(arguments: [(0, "<1m"), (59, "1m"), (60, "1m"), (61, "2m"), (1500, "25m"), (3600, "1h 0m"), (3900, "1h 5m"), (7260, "2h 1m")])
    func formatsLikeAndroid(seconds: Int, expected: String) {
        #expect(SleepTimer.format(seconds: seconds) == expected)
    }

    @Test func dailyLimitIncludesLiveSeconds() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia", dailyLimitMinutes: 10)
        let repo = WatchTimeRepository(context: context)
        let today = Date()
        try repo.record(videoId: "a", title: "A", seconds: 540, for: profile, at: today)
        #expect(DailyLimit.remainingSeconds(profile: profile, watchTime: repo, liveSeconds: 0, now: today) == 60)
        #expect(DailyLimit.remainingSeconds(profile: profile, watchTime: repo, liveSeconds: 45, now: today) == 15)
        #expect(DailyLimit.remainingSeconds(profile: profile, watchTime: repo, liveSeconds: 500, now: today) == 0)
        let tomorrow = Calendar.current.date(byAdding: .day, value: 1, to: Calendar.current.startOfDay(for: today))!
        #expect(DailyLimit.remainingSeconds(profile: profile, watchTime: repo, liveSeconds: 0, now: tomorrow) == 600, "Mitternachts-Reset")
        profile.dailyLimitMinutes = nil
        #expect(DailyLimit.remainingSeconds(profile: profile, watchTime: repo, liveSeconds: 999, now: today) == nil)
    }

    @Test func sessionStartsAndEndsSleepMode() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia")
        profile.sleepPlaylistId = "PLsleep"
        let session = SessionState()
        session.startSleepMode(profile: profile, seconds: 600)
        #expect(session.sleepTimer.isRunning)
        #expect(session.sleepProfileId == profile.id)
        #expect(session.kidModeRequests == 1)
        #expect(session.consumePendingSleepPlaylist() == "PLsleep")
        #expect(session.consumePendingSleepPlaylist() == nil)
        session.endSleepMode()
        #expect(!session.sleepTimer.isRunning)
        #expect(session.sleepProfileId == nil)
    }
}
