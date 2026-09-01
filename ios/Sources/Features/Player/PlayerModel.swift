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
    }

    enum Status: Equatable {
        case loading, playing, paused, ended, allUnavailable, engineFailed
    }

    let queue: [Item]
   /// AUTOPLAY DEFAULT = FALSE – nach dem Ende entscheidet das Kind bewusst über das nächste Video.
    var autoAdvance = false
    private(set) var index: Int
    private(set) var status: Status = .loading
    private(set) var skippedTitles: [String] = []
   /// Zuletzt übersprungener Titel, bis das nächste Video läuft (für die Anzeige).
    private(set) var recentlySkipped: String?
   /// Letzte gemeldete Wiedergabeposition in Sekunden.
    private(set) var currentSeconds = 0
   /// Lautstärke 0…100 (Ring oben/unten bzw. Ecktasten).
    private(set) var volume = 100

    private let engine: any PlayerEngine
    private let watchTime: WatchTimeRepository?
    private let profile: KidProfile?
    private let now: () -> Date
    private var playingSince: Date?
    private var accumulatedSeconds: TimeInterval = 0
    private var failedIds: Set<String> = []

    var current: Item { queue[index] }
    var positionText: String { "\(index + 1) von \(queue.count)" }

    init(queue: [Item], startIndex: Int, engine: any PlayerEngine,
         watchTime: WatchTimeRepository? = nil, profile: KidProfile? = nil, now: @escaping () -> Date = { Date() }) {
        precondition(!queue.isEmpty, "Player braucht mindestens ein Video")
        self.queue = queue
        self.index = min(max(0, startIndex), queue.count - 1)
        self.engine = engine
        self.watchTime = watchTime
        self.profile = profile
        self.now = now
    }

    func start() {
        engine.onEvent = { [weak self] event in self?.handle(event) }
        loadCurrent()
    }

    func next() { advance(by: 1) }
    func previous() { advance(by: -1) }

   /// Direkt zu einem Eintrag der Warteschlange (Tipp in der Liste "Als Naechstes").
    func jump(to newIndex: Int) {
        guard newIndex >= 0, newIndex < queue.count, newIndex != index else { return }
        flushWatchTime()
        index = newIndex
        loadCurrent()
    }
    func togglePlayback() { engine.togglePlayback() }
    func pause() { engine.pause() }
    func seek(by seconds: Double) { engine.seek(by: seconds) }
    func setVolume(_ percent: Int) {
        volume = min(100, max(0, percent))
        engine.setVolume(volume)
    }

    func adjustVolume(by delta: Int) { setVolume(volume + delta) }

   /// Bereits geschaute, aber noch nicht gebuchte Sekunden des laufenden Videos (für das Live-Tageslimit).
    var unrecordedSeconds: Int {
        var total = accumulatedSeconds
        if let since = playingSince { total += now().timeIntervalSince(since) }
        return Int(total.rounded())
    }

   /// Beim Verlassen: angefangene Sehzeit trotzdem buchen (Tageslimit soll nicht durch Abbrechen umgangen werden).
    func close() {
        flushWatchTime()
    }

    private func advance(by delta: Int) {
        flushWatchTime()
        index = (index + delta + queue.count) % queue.count   // FR-06.4 zyklisch
        loadCurrent()
    }

    private func loadCurrent() {
        status = .loading
        currentSeconds = 0
        accumulatedSeconds = 0
        playingSince = nil
        engine.load(videoId: current.videoId)
    }

    private func handle(_ event: PlayerEngineEvent) {
        switch event {
        case .ready, .state(-1), .state(5):
            break
        case .state(1), .state(3):
            if playingSince == nil { playingSince = now() }
            status = .playing
            recentlySkipped = nil
        case .state(2):
            pauseAccumulation()
            status = .paused
        case .state(0):
            flushWatchTime()
            status = .ended
            if autoAdvance, queue.count > 1 {
                advance(by: 1)
            } else {
                engine.stop()   // zurück zum Vorschaubild statt YouTube-Empfehlungsraster
            }
        case .state:
            break
        case .error(let code):
            failedIds.insert(current.videoId)
            skippedTitles.append(current.title)
            recentlySkipped = current.title
            flushWatchTime()
            if failedIds.count >= queue.count {
                status = .allUnavailable
            } else {
                advance(by: 1)   // FR-06.5: nicht einbettbar (101/150) & Co. überspringen; Status = .loading
            }
            _ = code
        case .apiFailed:
            status = .engineFailed
        case .time(let seconds):
            currentSeconds = seconds
        }
    }

    private func pauseAccumulation() {
        if let since = playingSince {
            accumulatedSeconds += now().timeIntervalSince(since)
            playingSince = nil
        }
    }

    private func flushWatchTime() {
        pauseAccumulation()
        let seconds = Int(accumulatedSeconds.rounded())
        accumulatedSeconds = 0
        guard seconds > 0, let watchTime, let profile else { return }
        try? watchTime.record(videoId: current.videoId, title: current.title, seconds: seconds, for: profile, at: now())
    }
}
