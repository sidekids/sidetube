// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Observation

/// Schlaf-Timer (FR-09): läuft im Hintergrund weiter, Ablauf löst das „Gute Nacht"-Overlay aus.
@Observable
final class SleepTimer {
    private(set) var endDate: Date?
    private(set) var expired = false
   /// Sekunden vor Ablauf, in denen die Lautstärke linear ausgeblendet wird.
    static let fadeSeconds = 60

    var isRunning: Bool { endDate != nil && !expired }

    func start(seconds: Int, now: Date = .now) {
        endDate = now.addingTimeInterval(TimeInterval(max(1, seconds)))
        expired = false
    }

    func stop() {
        endDate = nil
        expired = false
    }

    func remainingSeconds(at now: Date = .now) -> Int? {
        guard let endDate, !expired else { return nil }
        return max(0, Int(endDate.timeIntervalSince(now).rounded(.up)))
    }

   /// Sekündlich aufrufen. Liefert `true` genau in dem Tick, in dem der Timer abläuft.
    @discardableResult
    func tick(now: Date = .now) -> Bool {
        guard let endDate, !expired else { return false }
        if now >= endDate {
            expired = true
            return true
        }
        return false
    }

   /// Lautstärke 0…100 für die Ausblendung; `nil` = unverändert.
    func fadeVolume(at now: Date = .now) -> Int? {
        guard let remaining = remainingSeconds(at: now), remaining <= Self.fadeSeconds else { return nil }
        return Int((Double(remaining) / Double(Self.fadeSeconds) * 100).rounded())
    }

   /// „Xh Ym“ oder „Xm“; unter einer Minute „<1m".
    static func format(seconds: Int) -> String {
        let minutes = Int((Double(seconds) / 60).rounded(.up))
        if minutes < 1 { return "<1m" }
        if minutes < 60 { return "\(minutes)m" }
        return "\(minutes / 60)h \(minutes % 60)m"
    }
}

/// Tageslimit (FR-10) inklusive noch nicht gebuchter Sekunden des laufenden Videos.
enum DailyLimit {
   /// Verbleibende Sekunden heute; `nil` = kein Limit.
    static func remainingSeconds(profile: KidProfile, watchTime: WatchTimeRepository, liveSeconds: Int, now: Date = .now) -> Int? {
        guard let booked = watchTime.remainingSeconds(for: profile, now: now) else { return nil }
        return max(0, booked - max(0, liveSeconds))
    }
}
