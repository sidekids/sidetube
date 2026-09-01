// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

/// Nutzungsstatistik: Zeitraum, Tagesaufteilung und meistgesehene Videos – mit fester Uhrzeit,
/// damit die Prüfung nicht vom Testzeitpunkt abhängt.
struct WatchStatsTests {
    private let calendar = Calendar(identifier: .gregorian)
    private let now = Date(timeIntervalSince1970: 1_756_000_000)   // 24.08.2025, fester Bezugspunkt

    private func entry(_ videoId: String, _ title: String, seconds: Int, daysAgo: Int) -> WatchHistoryEntry {
        let day = calendar.date(byAdding: .day, value: -daysAgo, to: now)!
        return WatchHistoryEntry(videoId: videoId, videoTitle: title, watchedSeconds: seconds, watchedAt: day)
    }

    @Test func dayPeriodCountsOnlyToday() {
        let entries = [entry("a", "Heute", seconds: 600, daysAgo: 0),
                       entry("b", "Gestern", seconds: 900, daysAgo: 1)]

        let stats = WatchStatsCalculator.stats(for: entries, period: .day, now: now, calendar: calendar)

        #expect(stats.totalSeconds == 600)
        #expect(stats.videoCount == 1)
        #expect(stats.days.count == 1)
    }

    @Test func weekCoversSevenDaysIncludingEmptyOnes() {
        let entries = [entry("a", "Video A", seconds: 600, daysAgo: 0),
                       entry("b", "Video B", seconds: 300, daysAgo: 6)]

        let stats = WatchStatsCalculator.stats(for: entries, period: .week, now: now, calendar: calendar)

        #expect(stats.days.count == 7, "auch Tage ohne Sehzeit erscheinen")
        #expect(stats.totalSeconds == 900)
        #expect(stats.days.first?.seconds == 300, "ältester Tag zuerst")
        #expect(stats.days.last?.seconds == 600)
        #expect(stats.days.dropFirst().dropLast().allSatisfy { $0.seconds == 0 })
    }

    @Test func entriesOutsideThePeriodAreIgnored() {
        let entries = [entry("alt", "Zu alt", seconds: 3600, daysAgo: 40),
                       entry("neu", "Im Zeitraum", seconds: 120, daysAgo: 3)]

        let stats = WatchStatsCalculator.stats(for: entries, period: .month, now: now, calendar: calendar)

        #expect(stats.totalSeconds == 120)
        #expect(stats.videoCount == 1)
    }

    @Test func repeatedVideoIsCountedOnceButSummed() {
        let entries = [entry("a", "Zweimal", seconds: 300, daysAgo: 0),
                       entry("a", "Zweimal", seconds: 200, daysAgo: 1),
                       entry("b", "Einmal", seconds: 100, daysAgo: 0)]

        let stats = WatchStatsCalculator.stats(for: entries, period: .week, now: now, calendar: calendar)

        #expect(stats.videoCount == 2, "zwei verschiedene Videos")
        #expect(stats.totalSeconds == 600)
        let top = try! #require(stats.topVideos.first)
        #expect(top.videoId == "a" && top.seconds == 500 && top.plays == 2)
    }

    @Test func topVideosAreLimitedAndSortedByTime() {
        let entries = (1...8).map { entry("v\($0)", "Video \($0)", seconds: $0 * 60, daysAgo: 0) }

        let stats = WatchStatsCalculator.stats(for: entries, period: .week, now: now, calendar: calendar, topVideoLimit: 3)

        #expect(stats.topVideos.map(\.videoId) == ["v8", "v7", "v6"])
    }

    @Test func emptyHistoryStaysEmpty() {
        let stats = WatchStatsCalculator.stats(for: [], period: .week, now: now, calendar: calendar)

        #expect(stats.isEmpty)
        #expect(stats.totalFormatted == "0 min")
        #expect(stats.days.count == 7, "der Zeitraum wird trotzdem gezeigt")
    }

    @Test func formattingMatchesTheAndroidWording() {
        #expect(WatchStats.format(seconds: 0) == "0 min")
        #expect(WatchStats.format(seconds: 59) == "0 min")
        #expect(WatchStats.format(seconds: 900) == "15 min")
        #expect(WatchStats.format(seconds: 3600) == "1 h 0 min")
        #expect(WatchStats.format(seconds: 8100) == "2 h 15 min")
    }
}
