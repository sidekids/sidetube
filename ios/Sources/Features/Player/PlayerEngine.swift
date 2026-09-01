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
}

/// Abstraktion über die WebView-Brücke, damit `PlayerModel` ohne WebKit testbar ist.
protocol PlayerEngine: AnyObject {
    var onEvent: ((PlayerEngineEvent) -> Void)? { get set }
    func load(videoId: String)
    func play()
    func pause()
    func togglePlayback()
    func seek(by seconds: Double)
    func setVolume(_ percent: Int)
   /// Beendet die Wiedergabe und zeigt das Vorschaubild (kein Empfehlungsraster am Ende).
    func stop()
}
