// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

protocol WatchTimeRecording {
    func record(videoId: String, title: String, seconds: Int, for profile: KidProfile, at date: Date) throws
}

struct WatchTimeRepository: WatchTimeRecording {
    let context: ModelContext
    var calendar: Calendar = .current

    func record(videoId: String, title: String, seconds: Int, for profile: KidProfile, at date: Date = .now) throws {
        guard seconds > 0 else { return }
        let entry = WatchHistoryEntry(videoId: videoId, videoTitle: title, watchedSeconds: seconds, watchedAt: date)
        entry.profile = profile
        context.insert(entry)
        try context.save()
    }

   /// Sehzeit des Kalendertags (Systemzeitzone) in Sekunden – Grundlage für das Tageslimit (FR-10).
    func secondsWatched(by profile: KidProfile, on day: Date) -> Int {
        let start = calendar.startOfDay(for: day)
        guard let end = calendar.date(byAdding: .day, value: 1, to: start) else { return Int.max }
        return Self.safeTotal(profile.watchHistory.filter { $0.watchedAt >= start && $0.watchedAt < end })
    }

    func totalSeconds(of profile: KidProfile) -> Int {
        Self.safeTotal(profile.watchHistory)
    }

    private static func safeTotal(_ entries: [WatchHistoryEntry]) -> Int {
        var total = 0
        for entry in entries {
            guard entry.watchedSeconds >= 0 else { return Int.max }
            let (sum, overflow) = total.addingReportingOverflow(entry.watchedSeconds)
            guard !overflow else { return Int.max }
            total = sum
        }
        return total
    }

   /// Verbleibende Sekunden heute; `nil` = unbegrenzt.
    func remainingSeconds(for profile: KidProfile, now: Date = .now) -> Int? {
        guard let limit = profile.dailyLimitMinutes else { return nil }
        guard limit > 0, limit <= Int.max / 60 else { return 0 }
        return max(0, limit * 60 - secondsWatched(by: profile, on: now))
    }
}
