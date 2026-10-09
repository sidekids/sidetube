// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// Ruhezeiten eines Kinderprofils. Zeiten sind Minuten seit Mitternacht – Zeitzone und Sommerzeit
/// verschieben sie damit nie. Gleiche Semantik wie in der Android-App.
struct BedtimeSettings: Equatable, Sendable {
    static let defaultStartMinutes = 20 * 60
    static let defaultEndMinutes = 6 * 60 + 30
    static let defaultWeekendOffsetMinutes = 60

    /// Vorschläge für Eltern, nach Alter.
    static let ageSuggestions: [(label: String, startMinutes: Int)] = [
        (String(localized: "6–9 Jahre"), 19 * 60), (String(localized: "10–12 Jahre"), 20 * 60), (String(localized: "ab 13"), 21 * 60),
    ]

    var enabled = true
    var startMinutes = defaultStartMinutes
    var endMinutes = defaultEndMinutes
    var weekendOffsetMinutes = defaultWeekendOffsetMinutes
    /// Von den Eltern gesetzt, um die Ruhezeit bis zu diesem Zeitpunkt auszusetzen.
    var skipUntil: Date?
}

enum BedtimeState: Equatable, Sendable {
    /// Ruhezeit ist aus oder noch weit weg.
    case off
    /// Ruhezeit beginnt in `minutesLeft` Minuten.
    case warning(minutesLeft: Int)
    /// Ruhezeit läuft.
    case active

    var isActive: Bool { self == .active }
}

enum BedtimeEvaluator {
    /// Vorwarnung vor dem Beginn.
    static let warningLeadMinutes = 15
    /// Zweite, dringlichere Warnung.
    static let warningLastMinutes = 5
    private static let minutesPerDay = 24 * 60
    private static let maxSleepMinutes = 60

    static func evaluate(_ settings: BedtimeSettings, now: Date, calendar: Calendar = .current) -> BedtimeState {
        guard settings.enabled else { return .off }
        guard valid(settings) else { return .active }
        if let skipUntil = settings.skipUntil, now < skipUntil { return .off }

        let parts = calendar.dateComponents([.hour, .minute, .weekday], from: now)
        let nowMinutes = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        let weekday = parts.weekday ?? 1
        let startToday = start(settings, weekday: weekday)
        let startYesterday = start(settings, weekday: weekday == 1 ? 7 : weekday - 1)
        let end = settings.endMinutes

        let wrapsMidnight = end <= startToday
        let inEvening = nowMinutes >= startToday && (wrapsMidnight || nowMinutes < end)
        let inMorning = end <= startYesterday && nowMinutes < end
        if inEvening || inMorning { return .active }

        let minutesUntilStart = startToday - nowMinutes
        if minutesUntilStart >= 1 && minutesUntilStart <= warningLeadMinutes {
            return .warning(minutesLeft: minutesUntilStart)
        }
        return .off
    }

    private static func valid(_ settings: BedtimeSettings) -> Bool {
        (0..<1440).contains(settings.startMinutes) && (0..<1440).contains(settings.endMinutes)
            && (0...120).contains(settings.weekendOffsetMinutes)
    }

    /// Exact calendar boundaries, including warning thresholds and repeated DST hours.
    static func nextBoundary(_ settings: BedtimeSettings, now: Date, calendar: Calendar = .current) -> Date? {
        guard settings.enabled, valid(settings) else { return nil }
        var candidates: [Date] = []
        let day = calendar.startOfDay(for: now)
        for offset in 0...1 {
            guard let date = calendar.date(byAdding: .day, value: offset, to: day) else { continue }
            let beginning = start(settings, weekday: calendar.component(.weekday, from: date))
            for minutes in [0, beginning - warningLeadMinutes, beginning - warningLastMinutes, beginning, settings.endMinutes] where minutes >= 0 {
                for repeated in [Calendar.RepeatedTimePolicy.first, .last] {
                    if let boundary = calendar.nextDate(after: date.addingTimeInterval(-1),
                        matching: DateComponents(hour: minutes / 60, minute: minutes % 60, second: 0),
                        matchingPolicy: .nextTime, repeatedTimePolicy: repeated), boundary > now {
                        candidates.append(boundary)
                    }
                }
            }
        }
        if let skip = settings.skipUntil, skip > now { candidates.append(skip) }
        if let transition = calendar.timeZone.nextDaylightSavingTimeTransition(after: now) {
            candidates.append(transition)
        }
        return candidates.min()
    }

    /// Minuten bis zur nächsten möglichen Zustandsänderung – nie länger als eine Stunde,
    /// damit geänderte Einstellungen zeitnah greifen.
    static func minutesUntilNextChange(_ settings: BedtimeSettings, now: Date, calendar: Calendar = .current) -> Int {
        guard settings.enabled else { return maxSleepMinutes }
        let parts = calendar.dateComponents([.hour, .minute, .weekday], from: now)
        let nowMinutes = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        let startToday = start(settings, weekday: parts.weekday ?? 1)
        let boundaries = [startToday - warningLeadMinutes, startToday - warningLastMinutes, startToday, settings.endMinutes]
        let next = boundaries
            .map { (($0 - nowMinutes) % minutesPerDay + minutesPerDay) % minutesPerDay }
            .filter { $0 > 0 }
            .min() ?? maxSleepMinutes
        return min(max(next, 1), maxSleepMinutes)
    }

    /// Ende der laufenden Ruhezeit als Zeitpunkt – Grundlage für eine Ausnahme durch die Eltern.
    static func endOfCurrentWindow(_ settings: BedtimeSettings, now: Date, calendar: Calendar = .current) -> Date? {
        guard evaluate(settings, now: now, calendar: calendar).isActive else { return nil }
        let startOfDay = calendar.startOfDay(for: now)
        let todayEnd = calendar.date(byAdding: .minute, value: settings.endMinutes, to: startOfDay) ?? now
        return todayEnd > now ? todayEnd : calendar.date(byAdding: .day, value: 1, to: todayEnd)
    }

    /// Freitag und Samstag verschieben den Beginn um den Wochenend-Zuschlag.
    private static func start(_ settings: BedtimeSettings, weekday: Int) -> Int {
        let offset = (weekday == 6 || weekday == 7) ? settings.weekendOffsetMinutes : 0
        return min(settings.startMinutes + offset, minutesPerDay - 1)
    }

    /// „20:00“ aus Minuten seit Mitternacht.
    static func format(minutes: Int) -> String {
        String(format: "%d:%02d", minutes / 60, minutes % 60)
    }
}
