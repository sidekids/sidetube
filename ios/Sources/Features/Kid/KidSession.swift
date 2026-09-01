// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Observation

/// Aktives Kinderprofil im Kindermodus (merkt sich die letzte Wahl).
@Observable
final class KidSession {
    private static let lastProfileKey = "kid.lastProfileId"
    private(set) var activeProfile: KidProfile?

   /// Wählt das zuletzt genutzte Profil, sonst das erste; hält die Wahl gültig, wenn Profile gelöscht werden.
    func resolve(from profiles: [KidProfile]) {
        if let active = activeProfile, profiles.contains(where: { $0.id == active.id }) { return }
        let lastId = UserDefaults.standard.string(forKey: Self.lastProfileKey).flatMap(UUID.init)
        activeProfile = profiles.first { $0.id == lastId } ?? profiles.first
    }

    func select(_ profile: KidProfile) {
        activeProfile = profile
        UserDefaults.standard.set(profile.id.uuidString, forKey: Self.lastProfileKey)
    }
}
