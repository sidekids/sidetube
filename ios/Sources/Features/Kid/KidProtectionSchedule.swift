// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// No idle polling: wake only for a rule boundary, budget exhaustion or the bounded audio fade.
enum KidProtectionSchedule {
    static func next(now: Date, bedtime: BedtimeSettings?, hasDailyLimit: Bool,
                     remainingSeconds: Int?, playing: Bool, hasPlayer: Bool,
                     sleepEnd: Date?, calendar: Calendar = .current) -> Date? {
        var dates: [Date] = []
        if let bedtime, let next = BedtimeEvaluator.nextBoundary(bedtime, now: now, calendar: calendar) {
            dates.append(next)
        }
        if hasDailyLimit, let midnight = calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: now)) {
            dates.append(midnight)
        }
        if playing, let remainingSeconds, remainingSeconds > 0 {
            dates.append(now.addingTimeInterval(TimeInterval(remainingSeconds)))
        }
        if let end = sleepEnd, end > now {
            dates.append(end)
            if hasPlayer {
                let fadeStart = end.addingTimeInterval(-TimeInterval(SleepTimer.fadeSeconds))
                dates.append(fadeStart > now ? fadeStart : min(end, now.addingTimeInterval(1)))
            }
        }
        return dates.filter { $0 > now }.min()
    }
}
