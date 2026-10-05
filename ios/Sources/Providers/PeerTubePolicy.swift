// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

/// Regeln für PeerTube: Videos und Kanäle nur von Instanzen, die im Quellenregister stehen (Allowlist).
/// Föderation bedeutet sehr unterschiedliche Moderation – eine unbekannte Instanz ist keine Kinderquelle.
enum PeerTubePolicy {
   /// Ist die Instanz eines PeerTube-Bezeichners erlaubt (Quelle vorhanden und nicht gesperrt)?
    static func instanceAllowed(for id: String, curation: CurationRepository) -> Bool {
        guard let instanceId = PeerTubeIDs.instanceId(of: id) else { return true }   // kein PeerTube → nicht zuständig
        guard let source = curation.source(channelId: instanceId) else { return false }
        return source.trust != .blocked
    }

    static func hostAllowed(_ host: String, curation: CurationRepository) -> Bool {
        instanceAllowed(for: PeerTubeIDs.instanceId(host: host), curation: curation)
    }
}
