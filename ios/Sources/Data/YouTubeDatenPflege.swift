// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

/// YouTube-Daten höchstens 30 Tage (ADR 0010; YouTube API Services Developer Policies III.E.4), wie
/// `YouTubeDatenPflege.kt`: Freigaben auffrischen, Zwischenspeicher, Sehverlauf und entschiedene Wünsche
/// löschen, offene Wünsche um YouTube-Titel kürzen. Uhr und Abruf sind einsetzbar, damit prüfbar.
@MainActor
struct YouTubeDatenPflege {
    struct Bericht: Equatable {
        var aufgefrischt = 0, verschwunden = 0, spaeter = 0
        var zwischenspeicher = 0, sehverlauf = 0, wuenscheGeloescht = 0, wuenscheGekuerzt = 0
    }

    static let tage = 30
    static let maxAuffrischen = 25

    let context: ModelContext
    /// Holt Titel, Kanal und Bild neu – derselbe schlüsselfreie Weg wie beim Hinzufügen.
    let holen: (YouTubeContentType, String) async throws -> WhitelistItemDraft
    var now: () -> Date = Date.init

    static func grenze(_ now: Date) -> Date { now.addingTimeInterval(-Double(tage) * 24 * 60 * 60) }

    /// 404/401 (oEmbed: gelöscht oder privat) und 403 (nicht mehr einbettbar) – alles andere: später nochmal.
    static func endgueltigWeg(_ error: Error) -> Bool {
        switch error as? YouTubeError {
        case .notFound: true
        case .http(let status): status == 401 || status == 403
        default: false
        }
    }

    func lauf() async throws -> Bericht {
        let grenze = Self.grenze(now())
        var bericht = Bericht()

        let alterSpeicher = try context.fetch(FetchDescriptor<CachedChannelVideo>(predicate: #Predicate { $0.cachedAt < grenze }))
        alterSpeicher.forEach(context.delete); bericht.zwischenspeicher = alterSpeicher.count

        let alterVerlauf = try context.fetch(FetchDescriptor<WatchHistoryEntry>(predicate: #Predicate { $0.watchedAt < grenze }))
        alterVerlauf.forEach(context.delete); bericht.sehverlauf = alterVerlauf.count

        let offen = [WishStatus.offen.rawValue, WishStatus.besprechen.rawValue]
        for wunsch in try context.fetch(FetchDescriptor<KidWish>(predicate: #Predicate { $0.createdAt < grenze })) {
            if !offen.contains(wunsch.statusRaw) {
                context.delete(wunsch); bericht.wuenscheGeloescht += 1
            } else if wunsch.videoTitle != nil || wunsch.channelTitle != nil || wunsch.thumbnailUrl != nil {
                wunsch.videoTitle = nil; wunsch.channelTitle = nil; wunsch.thumbnailUrl = nil
                bericht.wuenscheGekuerzt += 1
            }
        }
        try context.save()

        let youtube = ContentProvider.youtube.rawValue
        let faellig = try context.fetch(FetchDescriptor<WhitelistItem>(predicate: #Predicate { $0.providerRaw == youtube }))
            .filter { ($0.metadataAt ?? $0.addedAt) < grenze }
            .sorted { ($0.metadataAt ?? $0.addedAt) < ($1.metadataAt ?? $1.addedAt) }
            .prefix(Self.maxAuffrischen)
        for item in faellig {
            do {
                let neu = try await holen(item.type, item.youtubeId)
                item.title = neu.title
                if let kanal = neu.channelTitle { item.channelTitle = kanal }
                if !neu.thumbnailUrl.isEmpty { item.thumbnailUrl = neu.thumbnailUrl }
                item.metadataAt = now()
                bericht.aufgefrischt += 1
            } catch where Self.endgueltigWeg(error) {
                // Nicht mehr bei YouTube: Daten weg, Eintrag zurück zur Prüfung – das Kind sieht ihn nicht mehr.
                item.title = ""; item.channelTitle = nil; item.thumbnailUrl = ""
                item.metadataAt = now()
                if item.approvalStatus == .approved { item.approvalStatus = .expiredReview }
                bericht.verschwunden += 1
            } catch {
                bericht.spaeter += 1
            }
        }
        try context.save()
        return bericht
    }
}
