// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

/// Startinhalt für ein neu angelegtes Kinderprofil.
///
/// Die Vorschläge aus `content/welcome.json` benötigen dieselbe Elternfreigabe wie andere Inhalte.
/// Eine freie Lizenz oder amtliche Herkunft ersetzt keine individuelle Prüfung.
///
/// Der Kanal bekommt dadurch **keine** höhere Vertrauensstufe: was ein Kind von ihm sieht,
/// entscheidet weiterhin `content/sources.json` (NASA dort als „vertrauenswürdige Reihe“, also nur
/// einzeln freigegebene Videos).
enum WelcomeLibrary {
    static let fileName = "welcome"

   /// Legt Startinhalt zur Prüfung an. Nur bei Profilerstellung aufrufen, nie beim App-Start.
   /// - Returns: Anzahl der angelegten Einträge.
    @discardableResult
    static func seed(into profile: KidProfile, context: ModelContext, bundle: Bundle = .main) -> Int {
        guard let library = try? ContentBundle.load(SeedLibraryImporter.SeedLibrary.self, fileName, bundle: bundle) else {
            return 0
        }
        let whitelist = WhitelistRepository(context: context)
        var added = 0
        for channel in library.channels ?? [] where !whitelist.contains(youtubeId: channel.id, in: profile) {
            let item = WhitelistItem(type: .channel, youtubeId: channel.id, title: channel.title,
                                     thumbnailUrl: channel.thumbnailUrl ?? "", approvalStatus: .reviewRequired)
            item.profile = profile
            item.provider = channel.provider ?? .youtube
            item.sourceChannelId = channel.id
            item.category = channel.category
            item.ageMin = channel.ageMin ?? 0
            item.editorialNotes = channel.note
            context.insert(item)
            added += 1
        }
        for video in library.videos where !whitelist.contains(youtubeId: video.id, in: profile) {
            let item = WhitelistItem(type: .video, youtubeId: video.id, title: video.title,
                                     thumbnailUrl: YouTubeIDs.defaultThumbnail(videoId: video.id),
                                     channelTitle: video.channelTitle, approvalStatus: .reviewRequired)
            item.profile = profile
            item.provider = video.provider ?? .youtube
            item.sourceChannelId = video.channelId
            item.category = video.category
            item.ageMin = video.ageMin
            item.ageMax = video.ageMax
            item.editorialNotes = video.note
            context.insert(item)
            added += 1
        }
        if added > 0 { try? context.save() }
        return added
    }

    /// Alte automatische Freigaben zurück in die Prüfung legen; Elternentscheidungen bleiben erhalten.
    /// Idempotent, ohne gelöschte Einträge neu anzulegen.
    static func requireReviewForLegacySeeds(context: ModelContext) throws {
        let items = try context.fetch(FetchDescriptor<WhitelistItem>())
        for item in items where item.approvedBy == "SideTube" && item.approvalStatus == .approved {
            item.approvalStatus = .reviewRequired
            item.approvedBy = nil
            item.approvedAt = nil
            item.lastReviewedAt = nil
        }
        try context.save()
    }
}
