// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Observation

/// App-weiter Sitzungszustand, den Eltern- und Kindermodus teilen: Schlaf-Timer und der Wunsch,
/// aus dem Elternbereich direkt in den Kindermodus zu wechseln (z. B. nach „Schlafmodus starten").
@Observable
final class SessionState {
    let sleepTimer = SleepTimer()
   /// Profil, für das der Schlafmodus gestartet wurde; Playlist wird beim Wechsel in den Kindermodus geöffnet.
    private(set) var sleepProfileId: UUID?
    private(set) var pendingSleepPlaylistId: String?
    private(set) var kidModeRequests = 0

    func startSleepMode(profile: KidProfile, seconds: Int, now: Date = .now) {
        sleepTimer.start(seconds: seconds, now: now)
        sleepProfileId = profile.id
        pendingSleepPlaylistId = profile.sleepPlaylistId
        kidModeRequests += 1
    }

    func endSleepMode() {
        sleepTimer.stop()
        sleepProfileId = nil
        pendingSleepPlaylistId = nil
    }

    func consumePendingSleepPlaylist() -> String? {
        defer { pendingSleepPlaylistId = nil }
        return pendingSleepPlaylistId
    }
}
