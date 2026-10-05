// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

/// Holt fehlende Kanalbilder nach.
///
/// Videos haben eine ableitbare Bild-URL (`YouTubeIDs.defaultThumbnail`), Kanäle nicht: deren Bild
/// steht nur in den Kanaldaten. Startpakete (`welcome.json`, `libraries/*.json`) liefern es nicht mit,
/// weil es sich beim Anbieter ändern kann – ein Kanal aus dem Startpaket bliebe sonst dauerhaft bei
/// der Platzhalter-Silhouette. Deshalb wird es beim ersten Anzeigen einmalig geholt und gespeichert.
enum ChannelAvatarBackfill {
    /// Stabile, kuratierte Fallbacks für die beiden Demo-/Startquellen. Kanal-Seiten liefern
    /// nicht immer ein Bild (Consent, Rate-Limit oder Offlinebetrieb); ein kindgerechter
    /// Kanal darf deshalb trotzdem nicht als graue Silhouette erscheinen.
    private static let curatedFallbacks: [String: String] = [
        "UCLA_DiR1FfKNvjuUpBHmylQ": "https://i.ytimg.com/vi/6o3m9Bw67Os/hqdefault.jpg",
        "UCIBaDdAbGlFDeS33shmlD0A": "https://i.ytimg.com/vi/PqJpgizriFM/hqdefault.jpg",
    ]

   /// - Returns: Anzahl der ergänzten Bilder (0 = nichts zu tun oder kein Netz).
    @discardableResult
    static func run(for profile: KidProfile, context: ModelContext, youtube: YouTubeRepository) async -> Int {
        let pending = WhitelistRepository(context: context)
            .items(of: profile, type: .channel)
            .filter { $0.thumbnailUrl.isEmpty && $0.provider == .youtube }
        guard !pending.isEmpty else { return 0 }

        var filled = 0
        for item in pending {
            if let fallback = curatedFallbacks[item.youtubeId] {
                item.thumbnailUrl = fallback
                filled += 1
                continue
            }
            guard let metadata = try? await youtube.channel(id: item.youtubeId), !metadata.thumbnailUrl.isEmpty else { continue }
            item.thumbnailUrl = metadata.thumbnailUrl
            filled += 1
        }
        if filled > 0 { try? context.save() }
        return filled
    }
}
