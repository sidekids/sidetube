// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// Ereignisse des eingebetteten YouTube-Players (Zustände wie in der IFrame-API: -1 unstarted, 0 ended,
/// 1 playing, 2 paused, 3 buffering, 5 cued; Fehler 2/5/100/101/150 = nicht abspielbar/einbettbar).
enum PlayerEngineEvent: Equatable {
    case ready
    case state(Int)
    case error(Int)
    case apiFailed
   /// Wiedergabeposition in Sekunden (alle 5 s)
    case time(Int)
   /// Der IFrame hat ein Video begonnen, das die App nicht angefordert hat (Endscreen-Karte, Pausen-Vorschlag).
   /// Die Seite hat es bereits gestoppt; der Wert ist die fremde Video-ID.
    case foreignVideo(String)
   /// Stelle, Dauer und ob gerade gespielt wird – zweimal pro Sekunde, nur für den Fortschrittsbalken.
    case position(seconds: Int, duration: Int, isPlaying: Bool)
   /// Hat das laufende Video Untertitel, und sind sie gerade an?
    case captions(available: Bool, enabled: Bool)
}

/// Abstraktion über die WebView-Brücke, damit `PlayerModel` ohne WebKit testbar ist.
protocol PlayerEngine: AnyObject {
    var onEvent: ((PlayerEngineEvent) -> Void)? { get set }
    func load(videoId: String)
    func play()
    func pause()
    func togglePlayback()
    func seek(by seconds: Double)
   /// Springt an eine Stelle des laufenden Videos.
    func seek(to seconds: Double)
    func setVolume(_ percent: Int)
   /// Beendet die Wiedergabe und zeigt das Vorschaubild (kein Empfehlungsraster am Ende).
    func stop()
   /// Untertitel ein- oder ausschalten, sofern das Video welche hat.
    func setCaptions(_ on: Bool)
}

extension PlayerEngine {
   /// Anbieter ohne eigene Untertitel-Steuerung (PeerTube) ignorieren den Wunsch stillschweigend.
    func setCaptions(_ on: Bool) {}
}
