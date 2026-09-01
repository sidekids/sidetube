// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// Die eine Stelle, die entscheidet, ob ein Inhalt im Kinderprofil sichtbar ist.
/// Regel: Sichtbar ist nur, was freigegeben, altersgerecht, kategorie-erlaubt, nicht gesperrt, kein Short/Live ist.
enum ContentPolicy {
    struct Verdict: Equatable {
        var visible: Bool
        var reason: String?
        static let ok = Verdict(visible: true, reason: nil)
        static func hidden(_ reason: String) -> Verdict { Verdict(visible: false, reason: reason) }
    }

    static func evaluate(_ item: WhitelistItem, for profile: KidProfile, source: CuratedSource?) -> Verdict {
        if item.provider == .peertube, source == nil { return .hidden("PeerTube-Instanz nicht freigegeben") }
        if let source, source.trust == .blocked { return .hidden("Quelle gesperrt") }
        if let source, source.trust == .parentOnly { return .hidden("Quelle nur für Eltern") }
        guard item.approvalStatus == .approved else { return .hidden("nicht freigegeben (\(item.approvalStatus.title))") }
        let age = profile.ageBand.age
        if item.ageMin > age { return .hidden("Mindestalter \(item.ageMin)") }
        if let max = item.ageMax, age > max { return .hidden("Höchstalter \(max)") }
        if item.isLive { return .hidden("Livestream") }
        if item.isShort, !profile.allowShorts { return .hidden("Short") }
        if let category = item.category {
            if category.minimumAge > age { return .hidden("Kategorie ab \(category.minimumAge)") }
            if profile.disabledCategories.contains(category) { return .hidden("Kategorie deaktiviert") }
            if category == .mangaDrawing, !profile.allowManga { return .hidden("Manga deaktiviert") }
            if category == .animeManga, !(profile.allowManga && profile.allowMangaEntertainment) { return .hidden("Anime & Manga deaktiviert") }
            if category == .news, !profile.allowNews { return .hidden("Nachrichten deaktiviert") }
        }
        if item.isNews {
            if !profile.allowNews { return .hidden("Nachrichten deaktiviert") }
            if item.newsStatus == .sensitive || item.newsStatus == .parentReview { return .hidden("Nachricht: \(item.newsStatus?.title ?? "prüfen")") }
        }
        if item.containsSexualContent { return .hidden("sexualisierter Inhalt") }
        return .ok
    }

    static func isVisible(_ item: WhitelistItem, for profile: KidProfile, source: CuratedSource?) -> Bool {
        evaluate(item, for: profile, source: source).visible
    }

   /// Belastende Nachrichten werden auf Home nicht hervorgehoben, auch wenn sie freigegeben sind.
    static func isHomeHighlightable(_ item: WhitelistItem) -> Bool {
        !(item.isNews && item.newsStatus != .safe)
    }

   /// Kategorien-Sektionen für Home je Altersprofil. Leere Sektionen blendet die View aus.
    static func homeCategorySections(for profile: KidProfile) -> [ContentCategory] {
        var sections: [ContentCategory] = [.knowledge]
        let age = profile.ageBand.age
        if profile.allowManga, age >= ContentCategory.mangaDrawing.minimumAge { sections.append(.mangaDrawing) }
        if profile.allowManga, profile.allowMangaEntertainment, age >= ContentCategory.animeManga.minimumAge { sections.append(.animeManga) }
        return sections.filter { !profile.disabledCategories.contains($0) }
    }

   /// Prüffrist: 6 Monate bei Nachrichten/Einzelprüfung, sonst 12 Monate. Zeigt Fälligkeit an, ändert keinen Status.
    static func reviewIsDue(_ item: WhitelistItem, source: CuratedSource?, now: Date = .now) -> Bool {
        guard item.approvalStatus == .approved, let reviewed = item.lastReviewedAt ?? item.approvedAt else { return false }
        let months = (item.isNews || source?.trust == .perVideoReview) ? 6 : 12
        guard let due = Calendar.current.date(byAdding: .month, value: months, to: reviewed) else { return false }
        return now >= due
    }
}
