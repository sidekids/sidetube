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

    /// Evaluates an explicitly approved item. `allowTrustedBrowsing` is only for
    /// candidates coming from the existing trusted-child-source browsing path; it
    /// never approves or persists the item.
    static func evaluate(_ item: WhitelistItem, for profile: KidProfile, source: CuratedSource?, allowTrustedBrowsing: Bool = false) -> Verdict {
        if item.provider == .peertube, source == nil { return .hidden("PeerTube-Instanz nicht freigegeben") }
        if let source, source.trust == .blocked { return .hidden("Quelle gesperrt") }
        if let source, source.trust == .parentOnly { return .hidden("Quelle nur für Eltern") }
        guard item.approvalStatus == .approved || (allowTrustedBrowsing && source?.trust.allowsChannelBrowsing == true) else {
            return .hidden("nicht freigegeben (\(item.approvalStatus.title))")
        }
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

   /// Darf ein Video aus einer freigegebenen Playlist gezeigt werden? (wie Android `canPlayFromPlaylist`)
   ///
   /// Die Freigabe der Playlist trägt ihre Videos – aber nur, wenn die Playlist selbst sichtbar ist.
   /// Eine eigene Elternentscheidung zum Video geht immer vor (abgelehnt, zurückgestellt, zu alt → weg).
   /// Ohne eigene Entscheidung: kein Risikotreffer, kein Short/Live (auch wenn nur der Feed es als Short
   /// bzw. angekündigt kennt), Alter und Kategorie der Playlist, und weder die Quelle der Playlist noch die
   /// des Videos ist gesperrt oder nur für Eltern.
   ///
   /// **Eigene Freigabe nötig** (ADR 0002): wenn der Kanal des Videos die Stufe „Nur einzeln geprüfte
   /// Videos" hat, und wenn der Kanal unbekannt ist (`video.channelId == nil`, etwa ein alter
   /// Zwischenspeicher) – dann lassen sich weder Sperre noch Stufe prüfen. Ein Kanal ohne Quelle (nie
   /// eingestuft) wird von der Playlist-Freigabe getragen.
    static func canPlayFromPlaylist(_ video: PlaylistVideo, for profile: KidProfile, playlist: WhitelistItem,
                                    playlistSource: CuratedSource?, videoSource: CuratedSource?,
                                    priorDecision: WhitelistItem?, risk: RiskAssessment) -> Bool {
        guard playlist.type == .playlist, isVisible(playlist, for: profile, source: playlistSource) else { return false }
        if let priorDecision {
            return priorDecision.type == .video && priorDecision.provider.isPlayable
                && isVisible(priorDecision, for: profile, source: videoSource ?? playlistSource)
        }
        guard let channelId = video.channelId, !channelId.isEmpty else { return false }
        if videoSource?.trust == .perVideoReview { return false }
        if risk.isHardBlocked || risk.requiresReview { return false }
        // Nur ein Prüfling für die Regel – wird nie in den Store eingefügt.
        let inherited = WhitelistItem(type: .video, youtubeId: video.videoId, title: video.title,
                                      thumbnailUrl: video.thumbnailUrl, channelTitle: video.channelTitle,
                                      approvalStatus: .approved)
        inherited.provider = playlist.provider
        inherited.sourceChannelId = channelId
        inherited.ageMin = playlist.ageMin
        inherited.ageMax = playlist.ageMax
        inherited.category = playlist.category
        inherited.isNews = playlist.isNews
        inherited.newsStatus = playlist.newsStatus
        inherited.isLive = risk.isLive || video.isUpcoming
        inherited.isShort = risk.isShort || video.isShort
        guard isVisible(inherited, for: profile, source: playlistSource) else { return false }
        return videoSource == nil || isVisible(inherited, for: profile, source: videoSource)
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
