// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import Foundation

/// Auswertung der Sehzeit für den Elternbereich (FR-11). Reine Rechnung auf den Verlaufseinträgen,
/// damit sie ohne Datenbank und ohne Systemuhr prüfbar bleibt.
enum StatsPeriod: Int, CaseIterable, Identifiable {
    case day = 1
    case week = 7
    case month = 30

    var id: Int { rawValue }

    var title: String {
        switch self {
        case .day: "Heute"
        case .week: "7 Tage"
        case .month: "30 Tage"
        }
    }
}

struct WatchStats: Equatable {
    struct Day: Equatable, Identifiable {
        var date: Date
        var seconds: Int
        var id: Date { date }
        var minutes: Int { seconds / 60 }
    }

    struct Video: Equatable, Identifiable {
        var videoId: String
        var title: String
        var seconds: Int
        var plays: Int
        var id: String { videoId }
    }

    var totalSeconds = 0
    var videoCount = 0
    var days: [Day] = []
    var topVideos: [Video] = []

    var isEmpty: Bool { totalSeconds == 0 }

    /// „2 h 15 min" bzw. „15 min" – dieselbe Darstellung wie in der Android-App.
    var totalFormatted: String { Self.format(seconds: totalSeconds) }

    static func format(seconds: Int) -> String {
        let hours = seconds / 3600
        let minutes = (seconds % 3600) / 60
        return hours > 0 ? "\(hours) h \(minutes) min" : "\(minutes) min"
    }
}

enum WatchStatsCalculator {
    /// Wertet den Zeitraum bis einschließlich `now` aus; jeder Tag erscheint, auch ohne Sehzeit.
    static func stats(for entries: [WatchHistoryEntry], period: StatsPeriod,
                      now: Date = .now, calendar: Calendar = .current,
                      topVideoLimit: Int = 5) -> WatchStats {
        let today = calendar.startOfDay(for: now)
        guard let start = calendar.date(byAdding: .day, value: -(period.rawValue - 1), to: today) else {
            return WatchStats()
        }
        let inRange = entries.filter { $0.watchedAt >= start && $0.watchedAt < (calendar.date(byAdding: .day, value: 1, to: today) ?? now) }

        var perDay: [Date: Int] = [:]
        var perVideo: [String: WatchStats.Video] = [:]
        for entry in inRange {
            let day = calendar.startOfDay(for: entry.watchedAt)
            perDay[day, default: 0] += entry.watchedSeconds
            var video = perVideo[entry.videoId] ?? WatchStats.Video(videoId: entry.videoId, title: entry.videoTitle, seconds: 0, plays: 0)
            video.seconds += entry.watchedSeconds
            video.plays += 1
            perVideo[entry.videoId] = video
        }

        let days = (0..<period.rawValue).compactMap { offset -> WatchStats.Day? in
            guard let date = calendar.date(byAdding: .day, value: offset, to: start) else { return nil }
            return WatchStats.Day(date: date, seconds: perDay[date] ?? 0)
        }

        let top = perVideo.values
            .sorted { ($0.seconds, $0.title) > ($1.seconds, $1.title) }
            .prefix(topVideoLimit)

        return WatchStats(totalSeconds: inRange.reduce(0) { $0 + $1.watchedSeconds },
                          videoCount: perVideo.count,
                          days: days,
                          topVideos: Array(top))
    }
}
