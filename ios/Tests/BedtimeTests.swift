// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

/// Ruhezeiten: gleiche Semantik wie in der Android-App (Minuten seit Mitternacht, Fr/Sa später,
/// Vorwarnung 15 min, Ausnahme durch die Eltern).
struct BedtimeTests {
    private var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Europe/Berlin")!
        return c
    }

    /// 2026-08-31 ist ein Montag.
    private func date(_ day: Int, _ hour: Int, _ minute: Int) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 8, day: day, hour: hour, minute: minute))!
    }

    private var standard: BedtimeSettings { BedtimeSettings() }

    @Test func defaultsMatchTheAndroidSchema() {
        #expect(BedtimeSettings.defaultStartMinutes == 1200)      // 20:00
        #expect(BedtimeSettings.defaultEndMinutes == 390)         // 6:30
        #expect(BedtimeSettings.defaultWeekendOffsetMinutes == 60)
        #expect(standard.enabled)
        #expect(BedtimeEvaluator.format(minutes: 1200) == "20:00")
        #expect(BedtimeEvaluator.format(minutes: 390) == "6:30")
    }

    @Test(arguments: [(31, 19, 0, false), (31, 19, 46, false), (31, 19, 50, false),
                      (31, 20, 0, true), (31, 23, 30, true), (1, 2, 0, true), (1, 6, 29, true), (1, 6, 30, false), (1, 12, 0, false)])
    func windowAcrossMidnight(day: Int, hour: Int, minute: Int, active: Bool) {
        let now = day == 31 ? date(31, hour, minute) : calendar.date(from: DateComponents(year: 2026, month: 9, day: 1, hour: hour, minute: minute))!
        #expect(BedtimeEvaluator.evaluate(standard, now: now, calendar: calendar).isActive == active,
                "\(day).: \(hour):\(minute)")
    }

    @Test func warningWindow() {
        #expect(BedtimeEvaluator.evaluate(standard, now: date(31, 19, 44), calendar: calendar) == .off)
        #expect(BedtimeEvaluator.evaluate(standard, now: date(31, 19, 45), calendar: calendar) == .warning(minutesLeft: 15))
        #expect(BedtimeEvaluator.evaluate(standard, now: date(31, 19, 55), calendar: calendar) == .warning(minutesLeft: 5))
        #expect(BedtimeEvaluator.evaluate(standard, now: date(31, 19, 59), calendar: calendar) == .warning(minutesLeft: 1))
        #expect(BedtimeEvaluator.evaluate(standard, now: date(31, 20, 0), calendar: calendar) == .active)
    }

    @Test func weekendShiftsFridayAndSaturdayOnly() {
        // 2026-08-28 Freitag, 29 Samstag, 30 Sonntag
        #expect(BedtimeEvaluator.evaluate(standard, now: date(28, 20, 30), calendar: calendar) == .off, "Freitag: erst ab 21:00")
        #expect(BedtimeEvaluator.evaluate(standard, now: date(28, 21, 0), calendar: calendar) == .active)
        #expect(BedtimeEvaluator.evaluate(standard, now: date(29, 20, 30), calendar: calendar) == .off, "Samstag: erst ab 21:00")
        #expect(BedtimeEvaluator.evaluate(standard, now: date(30, 20, 30), calendar: calendar) == .active, "Sonntag: wieder ab 20:00")
        // Samstagmorgen gehört noch zur Freitagnacht
        #expect(BedtimeEvaluator.evaluate(standard, now: date(29, 6, 0), calendar: calendar) == .active)
    }

    @Test func disabledAndParentException() {
        var settings = standard
        settings.enabled = false
        #expect(BedtimeEvaluator.evaluate(settings, now: date(31, 22, 0), calendar: calendar) == .off)

        settings = standard
        let night = date(31, 22, 0)
        #expect(BedtimeEvaluator.evaluate(settings, now: night, calendar: calendar).isActive)
        let end = BedtimeEvaluator.endOfCurrentWindow(settings, now: night, calendar: calendar)
        #expect(end == calendar.date(from: DateComponents(year: 2026, month: 9, day: 1, hour: 6, minute: 30)))
        settings.skipUntil = end
        #expect(BedtimeEvaluator.evaluate(settings, now: night, calendar: calendar) == .off, "Ausnahme gilt")
        #expect(BedtimeEvaluator.evaluate(settings, now: date(31, 23, 59), calendar: calendar) == .off)
        // am nächsten Abend greift die Ruhezeit wieder
        let nextNight = calendar.date(from: DateComponents(year: 2026, month: 9, day: 1, hour: 21, minute: 0))!
        #expect(BedtimeEvaluator.evaluate(settings, now: nextNight, calendar: calendar).isActive)
    }

    @Test func nextChangeStaysWithinAnHour() {
        for hour in 0...23 {
            let minutes = BedtimeEvaluator.minutesUntilNextChange(standard, now: date(31, hour, 7), calendar: calendar)
            #expect(minutes >= 1 && minutes <= 60, "um \(hour):07")
        }
        #expect(BedtimeEvaluator.minutesUntilNextChange(standard, now: date(31, 19, 40), calendar: calendar) == 5, "bis zur ersten Warnung")
        #expect(BedtimeEvaluator.minutesUntilNextChange(standard, now: date(31, 19, 50), calendar: calendar) == 5, "bis zur zweiten Warnung")
    }

    @Test func profileStoresSettingsLikeAndroid() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mira")
        #expect(profile.bedtimeEnabled)
        #expect(profile.bedtimeStartMinutes == 1200 && profile.bedtimeEndMinutes == 390)
        #expect(profile.bedtimeWeekendOffsetMinutes == 60)
        #expect(profile.bedtimeSkipUntil == nil)
        profile.bedtime = BedtimeSettings(enabled: true, startMinutes: 19 * 60, endMinutes: 7 * 60,
                                          weekendOffsetMinutes: 30, skipUntil: date(31, 23, 0))
        #expect(profile.bedtimeStartMinutes == 1140)
        #expect(profile.bedtimeEndMinutes == 420)
        #expect(profile.bedtimeWeekendOffsetMinutes == 30)
        #expect(profile.bedtime.skipUntil == date(31, 23, 0))
    }
}
