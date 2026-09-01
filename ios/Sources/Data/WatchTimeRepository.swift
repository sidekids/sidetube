// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

struct WatchTimeRepository {
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
        guard let end = calendar.date(byAdding: .day, value: 1, to: start) else { return 0 }
        return profile.watchHistory
            .filter { $0.watchedAt >= start && $0.watchedAt < end }
            .reduce(0) { $0 + $1.watchedSeconds }
    }

    func totalSeconds(of profile: KidProfile) -> Int {
        profile.watchHistory.reduce(0) { $0 + $1.watchedSeconds }
    }

   /// Verbleibende Sekunden heute; `nil` = unbegrenzt.
    func remainingSeconds(for profile: KidProfile, now: Date = .now) -> Int? {
        guard let limit = profile.dailyLimitMinutes else { return nil }
        return max(0, limit * 60 - secondsWatched(by: profile, on: now))
    }
}
