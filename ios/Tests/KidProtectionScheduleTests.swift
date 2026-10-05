// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Testing
@testable import sidetube

struct KidProtectionScheduleTests {
    private var calendar: Calendar {
        var value = Calendar(identifier: .gregorian)
        value.timeZone = TimeZone(identifier: "Europe/Berlin")!
        return value
    }
    private func date(_ value: String) -> Date { ISO8601DateFormatter().date(from: value)! }
    private var now: Date { date("2026-08-31T12:00:00+02:00") }

    private func next(bedtime: BedtimeSettings? = nil, limited: Bool = false,
                      remaining: Int? = nil, playing: Bool = false, player: Bool = false,
                      end: Date? = nil) -> Date? {
        KidProtectionSchedule.next(now: now, bedtime: bedtime, hasDailyLimit: limited,
            remainingSeconds: remaining, playing: playing, hasPlayer: player, sleepEnd: end, calendar: calendar)
    }

    @Test func unrestrictedIdleHasNoWakeup() {
        #expect(next() == nil)
        #expect(next(bedtime: BedtimeSettings(enabled: false)) == nil)
    }

    @Test func idleWaitsForWarningBoundaryRatherThanPolling() {
        #expect(next(bedtime: BedtimeSettings()) == date("2026-08-31T19:45:00+02:00"))
    }

    @Test func budgetOnlySchedulesConsumptionWhilePlaying() {
        #expect(next(limited: true, remaining: 12, playing: true, player: true) == now.addingTimeInterval(12))
        #expect(next(limited: true, remaining: 12, player: true) == date("2026-09-01T00:00:00+02:00"))
        #expect(next(limited: true, remaining: 0) == date("2026-09-01T00:00:00+02:00"))
    }

    @Test func sleepOnlyTicksDuringBoundedFadeAndOnlyWithPlayer() {
        let end = now.addingTimeInterval(600)
        #expect(next(player: true, end: end) == now.addingTimeInterval(540))
        #expect(next(end: end) == end)
        #expect(next(player: true, end: now.addingTimeInterval(30)) == now.addingTimeInterval(1))
        #expect(next(player: true, end: now.addingTimeInterval(0.5)) == now.addingTimeInterval(0.5))
        #expect(next(player: true, end: now) == nil)
    }

    @Test func earliestProtectionWins() {
        #expect(next(bedtime: BedtimeSettings(), limited: true, remaining: 4,
            playing: true, player: true, end: now.addingTimeInterval(30)) == now.addingTimeInterval(1))
    }

    @Test func warningsAndExceptionExpiryHaveExactBoundaries() {
        let settings = BedtimeSettings()
        #expect(BedtimeEvaluator.nextBoundary(settings, now: date("2026-08-31T19:44:59+02:00"), calendar: calendar)
            == date("2026-08-31T19:45:00+02:00"))
        #expect(BedtimeEvaluator.nextBoundary(settings, now: date("2026-08-31T19:45:00+02:00"), calendar: calendar)
            == date("2026-08-31T19:55:00+02:00"))
        let exception = BedtimeSettings(skipUntil: date("2026-08-31T22:30:00+02:00"))
        #expect(BedtimeEvaluator.nextBoundary(exception, now: date("2026-08-31T22:00:00+02:00"), calendar: calendar)
            == exception.skipUntil)
    }

    @Test func fridayWarningUsesWeekendOffset() {
        #expect(BedtimeEvaluator.nextBoundary(BedtimeSettings(), now: date("2026-08-28T20:30:00+02:00"), calendar: calendar)
            == date("2026-08-28T20:45:00+02:00"))
    }

    @Test func DSTGapAndRepeatedHourTriggerReevaluation() {
        let settings = BedtimeSettings(startMinutes: 150, endMinutes: 240, weekendOffsetMinutes: 0)
        let spring = date("2026-03-29T01:59:59+01:00")
        #expect(BedtimeEvaluator.nextBoundary(settings, now: spring, calendar: calendar) == spring.addingTimeInterval(1))
        let autumn = date("2026-10-25T02:59:59+02:00")
        #expect(BedtimeEvaluator.nextBoundary(settings, now: autumn, calendar: calendar) == autumn.addingTimeInterval(1))
        #expect(BedtimeEvaluator.nextBoundary(settings, now: autumn.addingTimeInterval(1), calendar: calendar)
            == date("2026-10-25T02:15:00+01:00"))
    }

    @Test func invalidSettingsFailClosedWithoutASpinLoop() {
        let invalid = BedtimeSettings(startMinutes: -1)
        #expect(BedtimeEvaluator.evaluate(invalid, now: now, calendar: calendar).isActive)
        #expect(BedtimeEvaluator.nextBoundary(invalid, now: now, calendar: calendar) == nil)
    }
}
