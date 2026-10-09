// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Observation

/// Wiedergabe-Logik (FR-06): Warteschlange aus der aktuellen Liste, Autoplay, Weiter/Zurück zyklisch,
/// nicht einbettbare Videos überspringen, Sehzeit buchen. Engine ist austauschbar (Tests).
@Observable
final class PlayerModel {
    struct Item: Equatable, Sendable {
        var videoId: String
        var title: String
        var thumbnailURL: String? = nil
        var channelTitle: String? = nil
        var sourceChannelId: String? = nil
        var sourcePlaylistId: String? = nil
    }

    enum Status: Equatable {
        case loading, playing, paused, ended, allUnavailable, engineFailed, storageFailed
    }

   /// Warteschlange; wächst nur durch `play(_:)` mit freigegebenen Vorschlägen vom Videoende.
    private(set) var queue: [Item]
   /// AUTOPLAY DEFAULT = FALSE – nach dem Ende entscheidet das Kind bewusst über das nächste Video.
    var autoAdvance = false
    private(set) var index: Int
    private(set) var status: Status = .loading
    private(set) var skippedTitles: [String] = []
   /// Zuletzt übersprungener Titel, bis das nächste Video läuft (für die Anzeige).
    private(set) var recentlySkipped: String?
   /// Letzte gemeldete Wiedergabeposition in Sekunden.
    private(set) var currentSeconds = 0
   /// Länge des laufenden Videos in Sekunden; 0, solange sie der Player noch nicht kennt.
    private(set) var durationSeconds = 0
   /// Läuft gerade Bild und Ton? (Für die eigene Steuerung; `status` beschreibt den gröberen Zustand.)
    private(set) var isPlaying = false
   /// Lautstärke 0…100 (Ring oben/unten bzw. Ecktasten).
    private(set) var volume = 100
   /// Video-IDs, die der IFrame von sich aus starten wollte und die gestoppt wurden (Diagnose, Tests).
    private(set) var blockedForeignVideoIds: [String] = []
   /// Hat das laufende Video Untertitel? Nur dann zeigt die Steuerung den Knopf.
    private(set) var captionsAvailable = false
   /// Sind sie gerade an? Die Wahl gilt auch für die nächsten Videos (`CaptionsPreference`).
    private(set) var captionsEnabled = CaptionsPreference.isOn

    private let engine: any PlayerEngine
    private let watchTime: (any WatchTimeRecording)?
    private(set) var hasWatchTimeFailure = false
    struct UncommittedWatchTime {
        let videoId: String
        let title: String
        let seconds: Int
        let profileID: UUID
        let date: Date
    }
    private(set) var failedWatchRecord: UncommittedWatchTime?
    private let profile: KidProfile?
    private let now: () -> Date
    private let elapsedNow: () -> TimeInterval
    private let calendar: Calendar
    private var playingSince: TimeInterval?
   /// Wall-clock time `playingSince` corresponds to - captured once per "playing" stretch so a
   /// stretch that runs past midnight can be split by calendar day when it is flushed.
    private var playingSinceCalendar: Date?
   /// Already-elapsed seconds of the current video, keyed by the calendar day they were watched
   /// on (`calendar.startOfDay`). Almost always a single entry; more than one only when a
   /// continuous stretch crossed midnight.
    private var accumulatedSecondsByDay: [Date: TimeInterval] = [:]
    private var failedIds: Set<String> = []

    var current: Item { queue[index] }
    var positionText: String { String(localized: "Video \(index + 1) von \(queue.count)") }

    init(queue: [Item], startIndex: Int, engine: any PlayerEngine,
         watchTime: (any WatchTimeRecording)? = nil, profile: KidProfile? = nil,
         now: (() -> Date)? = nil, elapsedNow: (() -> TimeInterval)? = nil, calendar: Calendar = .current) {
        precondition(!queue.isEmpty, "Player braucht mindestens ein Video")
        self.queue = queue
        self.index = min(max(0, startIndex), queue.count - 1)
        self.engine = engine
        self.watchTime = watchTime
        self.profile = profile
        self.now = now ?? { Date() }
        if let elapsedNow { self.elapsedNow = elapsedNow }
        else if let now { self.elapsedNow = { now().timeIntervalSinceReferenceDate } }
        else { self.elapsedNow = { ProcessInfo.processInfo.systemUptime } }
        self.calendar = calendar
    }

    func start() {
        guard !hasWatchTimeFailure else { return }
        engine.onEvent = { [weak self] event in self?.handle(event) }
        loadCurrent()
    }

    func next() { advance(by: 1) }
    func previous() { advance(by: -1) }

   /// Direkt zu einem Eintrag der Warteschlange (Tipp in der Liste "Als Naechstes").
    func jump(to newIndex: Int) {
        guard newIndex >= 0, newIndex < queue.count, newIndex != index else { return }
        guard flushWatchTime() else { return }
        index = newIndex
        loadCurrent()
    }

   /// Spielt ein Video aus der Warteschlange oder hängt es an (Vorschlag „Als Nächstes“ am Videoende).
   /// Aufrufer bürgen dafür, dass das Video die Freigabelogik passiert hat (`NextUpPolicy`).
    func play(_ item: Item) {
        if let existing = queue.firstIndex(where: { $0.videoId == item.videoId }) {
            if existing == index { replay() } else { jump(to: existing) }
            return
        }
        guard flushWatchTime() else { return }
        queue.append(item)
        index = queue.count - 1
        loadCurrent()
    }

   /// Untertitel an oder aus. Die Wahl bleibt für die nächsten Videos bestehen – ein Kind soll sie
   /// einmal treffen und nicht bei jedem Video neu.
    func toggleCaptions() {
        captionsEnabled.toggle()
        CaptionsPreference.isOn = captionsEnabled
        engine.setCaptions(captionsEnabled)
    }

   /// Dasselbe Video noch einmal von vorn („Nochmal“).
    func replay() {
        guard flushWatchTime() else { return }
        loadCurrent()
    }

    func togglePlayback() { if !hasWatchTimeFailure { engine.togglePlayback() } }
    func pause() { engine.pause() }
    func seek(by seconds: Double) {
        guard !hasWatchTimeFailure else { return }
        if durationSeconds > 0 {
            currentSeconds = min(max(0, currentSeconds + Int(seconds)), durationSeconds)
        } else {
            currentSeconds = max(0, currentSeconds + Int(seconds))
        }
        engine.seek(by: seconds)
    }

   /// Springt an eine Stelle (Balken losgelassen); bleibt im Video.
    func seek(to seconds: Double) {
        guard !hasWatchTimeFailure else { return }
        let target = durationSeconds > 0 ? min(max(0, Int(seconds)), durationSeconds) : max(0, Int(seconds))
        currentSeconds = target
        engine.seek(to: Double(target))
    }
    func setVolume(_ percent: Int) {
        volume = min(100, max(0, percent))
        engine.setVolume(volume)
    }

   /// Bereits geschaute, aber noch nicht gebuchte Sekunden des laufenden Videos (für das Live-Tageslimit).
    var unrecordedSeconds: Int {
        var total = accumulatedSecondsByDay.values.reduce(0, +)
        if let since = playingSince { total += max(0, elapsedNow() - since) }
        return Int(total.rounded(.up))
    }

   /// Beim Verlassen: angefangene Sehzeit trotzdem buchen (Tageslimit soll nicht durch Abbrechen umgangen werden)
   /// und die Wiedergabe beenden. Der Coordinator gibt anschließend die WebView frei.
    func close() {
        flushWatchTime()
        engine.stop()
        engine.onEvent = nil
    }

    private func advance(by delta: Int) {
        guard flushWatchTime() else { return }
        index = (index + delta + queue.count) % queue.count   // FR-06.4 zyklisch
        loadCurrent()
    }

    private func loadCurrent() {
        guard !hasWatchTimeFailure else { return }
        status = .loading
        currentSeconds = 0
        durationSeconds = 0
        isPlaying = false
        captionsAvailable = false
        accumulatedSecondsByDay = [:]
        playingSince = nil
        playingSinceCalendar = nil
        engine.load(videoId: current.videoId)
    }

    private func handle(_ event: PlayerEngineEvent) {
        guard !hasWatchTimeFailure else { return }
        if status == .ended || status == .allUnavailable || status == .engineFailed {
            switch event {
            case .state(1), .position(_, _, true): engine.stop(); return
            case .state, .position: return
            default: break
            }
        }
        switch event {
        case .ready:
            break
        case .state(-1), .state(3), .state(5):
            pauseAccumulation()
            status = .loading
        case .state(1):
            if playingSince == nil { playingSince = elapsedNow(); playingSinceCalendar = now() }
            isPlaying = true
            status = .playing
            recentlySkipped = nil
            // Die Wahl des Kindes gilt auch für dieses Video – die Seite kennt sie noch nicht.
            if captionsEnabled { engine.setCaptions(true) }
        case .state(2):
            pauseAccumulation()
            status = .paused
        case .state(0):
            guard flushWatchTime() else { return }
            status = .ended
            if autoAdvance, queue.count > 1 {
                advance(by: 1)
            } else {
                engine.stop()   // zurück zum Vorschaubild statt YouTube-Empfehlungsraster; die App zeigt „Fertig“
            }
        case .state:
            break
        case .error(let code):
            failedIds.insert(current.videoId)
            skippedTitles.append(current.title)
            recentlySkipped = current.title
            guard flushWatchTime() else { return }
            if failedIds.count >= queue.count {
                status = .allUnavailable
            } else {
                advance(by: 1)   // FR-06.5: nicht einbettbar (101/150) & Co. überspringen; Status = .loading
            }
            _ = code
        case .apiFailed:
            guard flushWatchTime() else { return }
            engine.stop()
            status = .engineFailed
        case .time(let seconds):
            currentSeconds = seconds
        case .captions(let available, let enabled):
            captionsAvailable = available
            // Das Laden des Moduls schaltet die Untertitel von sich aus ein. Maßgeblich ist die
            // Wahl des Kindes, nicht der Zustand der Seite – deshalb wird sie hier durchgesetzt.
            if available, enabled != captionsEnabled { engine.setCaptions(captionsEnabled) }
        case .position(let seconds, let duration, let playing):
            // Reconcile missing state callbacks without resetting an already running interval.
            currentSeconds = seconds
            if duration > 0 { durationSeconds = duration }
            if playing {
                if playingSince == nil { playingSince = elapsedNow(); playingSinceCalendar = now() }
                isPlaying = true
                status = .playing
            } else {
                pauseAccumulation()
                if status == .playing { status = .paused }
            }
        case .foreignVideo(let id):
            // Der IFrame wollte ein nicht angefordertes Video spielen (Endscreen, Pausen-Vorschlag).
            // Save only the already requested video's accumulated time, never a record for the foreign ID.
            blockedForeignVideoIds.append(id)
            guard flushWatchTime() else { return }
            engine.stop()
            status = .ended
        }
    }

    private func pauseAccumulation() {
        isPlaying = false
        if let since = playingSince, let sinceCalendar = playingSinceCalendar {
            let elapsed = max(0, elapsedNow() - since)
            addAccumulated(elapsed, startingAt: sinceCalendar)
        }
        playingSince = nil
        playingSinceCalendar = nil
    }

   /// Splits one continuous playing stretch into per-calendar-day seconds, so a stretch that runs
   /// past midnight is booked to both days instead of entirely to whichever day it happens to be
   /// flushed on. Monotonic elapsed time is treated as equal to wall-clock elapsed time within this
   /// one stretch - a device clock change mid-stretch is the separate, already-documented
   /// threat-model caveat, not something this split needs to guard against on its own.
    private func addAccumulated(_ elapsed: TimeInterval, startingAt start: Date) {
        var remaining = elapsed
        var cursor = start
        while remaining > 0 {
            let day = calendar.startOfDay(for: cursor)
            let nextDay = calendar.date(byAdding: .day, value: 1, to: day) ?? day.addingTimeInterval(86400)
            let slice = min(remaining, nextDay.timeIntervalSince(cursor))
            accumulatedSecondsByDay[day, default: 0] += slice
            remaining -= slice
            cursor = nextDay
        }
    }

    @discardableResult
    private func flushWatchTime() -> Bool {
        guard !hasWatchTimeFailure else { return false }
        pauseAccumulation()
        guard let watchTime, let profile else {
            accumulatedSecondsByDay = [:]
            return true
        }
        let sortedDays = accumulatedSecondsByDay.keys.sorted()
        for day in sortedDays {
            let seconds = accumulatedSecondsByDay[day] ?? 0
            let wholeSeconds = Int(seconds.rounded(.up))
            guard wholeSeconds > 0 else {
                accumulatedSecondsByDay.removeValue(forKey: day)
                continue
            }
            // The most recent (or only) day books at the actual flush time, matching prior
            // single-record behavior exactly when nothing crossed midnight. An earlier, already
            // completed day books at its own start-of-day - any timestamp within that day
            // attributes correctly, since `secondsWatched` uses a [start, nextDay) range.
            let date = day == sortedDays.last ? now() : day
            do {
                try watchTime.record(videoId: current.videoId, title: current.title, seconds: wholeSeconds, for: profile, at: date)
                accumulatedSecondsByDay.removeValue(forKey: day)
            } catch {
                // Do not discard uncommitted time (this day's or any later day's still pending)
                // or retry an ambiguous save and double-book it.
                hasWatchTimeFailure = true
                failedWatchRecord = UncommittedWatchTime(videoId: current.videoId, title: current.title,
                    seconds: wholeSeconds, profileID: profile.id, date: date)
                status = .storageFailed
                isPlaying = false
                engine.stop()
                engine.onEvent = nil
                return false
            }
        }
        return true
    }
}

/// Merkt sich, ob das Kind Untertitel sehen will – über Videos und Sitzungen hinweg.
/// Bewusst geräteweit und nicht je Profil: es ist eine Lesehilfe, keine Inhaltsentscheidung.
enum CaptionsPreference {
    private static let key = "kid.captionsEnabled"

    static var isOn: Bool {
        get { UserDefaults.standard.bool(forKey: key) }
        set { UserDefaults.standard.set(newValue, forKey: key) }
    }
}
