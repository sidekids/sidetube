// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// „Neu bei deinen Kanälen" (ADR 0001, Weg 3): welche neuen Folgen eines bekannten Kanals das Kind
/// **gesperrt** – mit Bild und Titel, nicht abspielbar – sehen und sich wünschen darf.
///
/// Nur Kanäle mit der Stufe „Vertrauenswürdige Reihe" (bei „Vertrauenswürdige Kinderquelle" darf das
/// Kind ohnehin stöbern, alle anderen Stufen zeigen nichts Ungeprüftes). Grenzen: höchstens 6 je Kanal,
/// nicht älter als 60 Tage, keine Shorts/Livestreams/Premieren, Risikofilter auf dem Titel, schon
/// entschiedene oder bereits bekannte Videos nie. Gesperrte Quellen nie.
enum NewEpisodesPolicy {
    static let maxPerChannel = 6
    static let maxAgeDays = 60

    /// Darf der Kanal für dieses Profil überhaupt neue Folgen zeigen? Alter **und Kategorie** der Quelle
    /// gelten wie bei einer Freigabe – auch die Schalter der Eltern (Kategorie aus, Anime & Manga, Manga),
    /// wie Android `ContentPolicy.canShowLockedNewEpisode`.
    static func channelQualifies(_ source: CuratedSource?, profile: KidProfile) -> Bool {
        guard let source, source.trust == .trustedSeries, source.provider == .youtube else { return false }
        // Nachrichten werden einzeln eingestuft; ein Titel allein reicht dafür nicht.
        if source.isNewsSource { return false }
        // Nur ein Prüfling für die Regel – wird nie in den Store eingefügt.
        let probe = WhitelistItem(type: .video, youtubeId: "neue-folge:\(source.channelId)", title: source.title,
                                  thumbnailUrl: "", approvalStatus: .approved)
        probe.sourceChannelId = source.channelId
        probe.ageMin = source.defaultAgeMin
        probe.category = source.defaultCategory
        return ContentPolicy.isVisible(probe, for: profile, source: source)
    }

    /// Filtert die Feed-Einträge eines Kanals.
    /// - Parameters:
    ///   - excludedVideoIds: Videos, die das Kind hier nicht sehen soll – alles, was im Profil schon einen
    ///     Whitelist-Eintrag hat (freigegeben, zurückgestellt, abgelehnt), und abgelehnte Wünsche.
    ///   - videoSource: Quelle des einzelnen Videos, falls sie vom Kanal abweicht (gesperrt ⇒ nie).
    static func filter(_ entries: [FeedEntry], channel source: CuratedSource?, profile: KidProfile,
                       excludedVideoIds: Set<String>, now: Date = .now,
                       videoSource: (String) -> CuratedSource? = { _ in nil }) -> [FeedEntry] {
        guard channelQualifies(source, profile: profile), let source else { return [] }
        let oldest = Calendar.current.date(byAdding: .day, value: -maxAgeDays, to: now) ?? now
        let filtered = entries.filter { entry in
            guard let published = entry.publishedAt, published >= oldest, published <= now.addingTimeInterval(86_400) else { return false }
            guard !entry.isShort, !entry.isUpcoming else { return false }
            guard !excludedVideoIds.contains(entry.video.videoId) else { return false }
            if let channelId = entry.video.channelId, channelId != source.channelId,
               let other = videoSource(channelId), other.trust == .blocked || other.trust == .parentOnly {
                return false
            }
            let risk = RiskScreen.assess(title: entry.video.title)
            // Strenger als beim Stöbern: das Kind sieht hier ungeprüfte Titel, also auch keine Themenhinweise.
            return !risk.isHardBlocked && !risk.requiresReview
        }
        let newestFirst = filtered.sorted { ($0.publishedAt ?? .distantPast) > ($1.publishedAt ?? .distantPast) }
        var seen: Set<String> = []
        return Array(newestFirst.filter { seen.insert($0.video.videoId).inserted }.prefix(maxPerChannel))
    }
}
