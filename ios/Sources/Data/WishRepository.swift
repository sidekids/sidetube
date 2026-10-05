// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

/// Wünsche von Kindern (ADR 0001): annehmen mit Tagesgrenze und ohne Dubletten, Eltern entscheiden,
/// jeder Schritt landet im Verlauf der Freigaben (`ReviewEvent`). Nur lokal, kein Netz.
struct WishRepository {
    let context: ModelContext
    var now: () -> Date = { Date() }
    var calendar: Calendar = .current

    /// Höchstens so viele neue Wünsche je Profil und Kalendertag (ADR: vorerst fest).
    static let dailyLimit = 3

    enum WishError: Error, Equatable {
        case dailyLimitReached
        case emptyTopic
        case missingVideo
        case blockedSource
        case invalidTransition
        case wrongKind
        /// Der Risikofilter hat das Video hart abgelehnt; Freigeben geht nur über die normale Prüfung.
        case notApprovable
    }

    enum SubmitResult: Equatable {
        case created(KidWish)
        /// Derselbe Wunsch wartet schon – es entsteht kein neuer, die Tagesgrenze bleibt unberührt.
        case duplicate(KidWish)

        var wish: KidWish {
            switch self {
            case .created(let wish), .duplicate(let wish): wish
            }
        }
    }

   // MARK: Lesen

    func wishes(of profileID: UUID) -> [KidWish] {
        let descriptor = FetchDescriptor<KidWish>(predicate: #Predicate { $0.profileID == profileID },
                                                  sortBy: [SortDescriptor(\.createdAt, order: .reverse)])
        return (try? context.fetch(descriptor)) ?? []
    }

    /// Offene und „besprechen" – das, was in der Prüfliste der Eltern steht. Offene zuerst, dann neueste.
    func pending(of profileID: UUID) -> [KidWish] {
        wishes(of: profileID).filter { $0.status.isPending }
            .sorted { ($0.status == .offen ? 0 : 1, $1.createdAt) < ($1.status == .offen ? 0 : 1, $0.createdAt) }
    }

    func createdToday(by profileID: UUID) -> Int {
        let start = calendar.startOfDay(for: now())
        guard let end = calendar.date(byAdding: .day, value: 1, to: start) else { return 0 }
        return wishes(of: profileID).filter { $0.createdAt >= start && $0.createdAt < end }.count
    }

    /// Offene Wünsche aller Profile – die Zahl in der Meldung an die Eltern.
    func openWishCount() -> Int {
        let open = WishStatus.offen.rawValue
        let descriptor = FetchDescriptor<KidWish>(predicate: #Predicate { $0.statusRaw == open })
        return (try? context.fetchCount(descriptor)) ?? 0
    }

    /// Meldet den Eltern einen neu angelegten Wunsch (ADR 0005) – nicht bei Dubletten. Läuft im
    /// Hintergrund; das Ergebnis ändert nichts am Wunsch.
    static func notifyParents(after result: SubmitResult, openCount: Int, notifier: ParentNotifier) -> Task<ParentNotifyResult, Never>? {
        guard case .created = result else { return nil }
        return Task.detached { await notifier.notifyNewWish(openCount: openCount) }
    }

    func remainingToday(for profileID: UUID) -> Int {
        max(0, Self.dailyLimit - createdToday(by: profileID))
    }

    /// Ein noch wartender Wunsch mit demselben Inhalt (gleiches Thema bzw. gleiches Video, gleiche Art).
    func pendingDuplicate(of draft: WishDraft, profileID: UUID) -> KidWish? {
        guard let key = draft.duplicateKey else { return nil }
        return wishes(of: profileID).first { wish in
            guard wish.kind == draft.kind, wish.status.isPending else { return false }
            return Self.duplicateKey(of: wish) == key
        }
    }

    /// Videos, die unter „Neu bei deinen Kanälen" nicht erscheinen: alles mit Whitelist-Eintrag im Profil
    /// (freigegeben, zurückgestellt, abgelehnt) und abgelehnte Wünsche.
    func excludedEpisodeIds(for profile: KidProfile) -> Set<String> {
        var ids = Set(profile.whitelistItems.map(\.youtubeId))
        for wish in wishes(of: profile.id) where wish.status == .abgelehnt {
            if let videoId = wish.videoId { ids.insert(videoId) }
        }
        return ids
    }

    /// Wartende Wünsche nach Video – die Startseite zeigt „Gewünscht" statt „Wünschen".
    func pendingVideoWishes(of profileID: UUID) -> [String: KidWish] {
        var result: [String: KidWish] = [:]
        for wish in wishes(of: profileID) where wish.status.isPending {
            if let videoId = wish.videoId, result[videoId] == nil { result[videoId] = wish }
        }
        return result
    }

    func history(of wish: KidWish) -> [ReviewEvent] {
        let key = wish.historyKey
        let descriptor = FetchDescriptor<ReviewEvent>(predicate: #Predicate { $0.itemVersion == key },
                                                      sortBy: [SortDescriptor(\.at, order: .reverse)])
        return (try? context.fetch(descriptor)) ?? []
    }

   // MARK: Kind wünscht sich etwas

    @discardableResult
    func submit(_ draft: WishDraft, for profile: KidProfile, actor: String = "Kind") throws -> SubmitResult {
        var draft = draft
        switch draft.kind {
        case .thema:
            let topic = (draft.topic ?? "").split(whereSeparator: \.isWhitespace).joined(separator: " ")
            guard !topic.isEmpty else { throw WishError.emptyTopic }
            draft.topic = String(topic.prefix(WishDraft.maxTopicLength))
        case .mehrDavon, .neueFolge:
            guard let videoId = draft.videoId, !videoId.isEmpty else { throw WishError.missingVideo }
        }
        let curation = CurationRepository(context: context)
        // Gesperrt oder „nur für Eltern": Das Kind soll daraus nichts bekommen, also auch nicht wünschen (wie Android).
        if let channelId = draft.channelId, let source = curation.effectiveSource(channelId: channelId),
           source.trust == .blocked || source.trust == .parentOnly {
            throw WishError.blockedSource
        }
        if let existing = pendingDuplicate(of: draft, profileID: profile.id) { return .duplicate(existing) }
        guard remainingToday(for: profile.id) > 0 else { throw WishError.dailyLimitReached }

        let wish = KidWish(profileID: profile.id, kind: draft.kind, topic: draft.topic, videoId: draft.videoId,
                           videoTitle: draft.videoTitle, thumbnailUrl: draft.thumbnailUrl, channelId: draft.channelId,
                           channelTitle: draft.channelTitle, createdAt: now())
        context.insert(wish)
        log(wish, decision: .wished, actor: actor, note: "\(draft.kind.origin): \(wish.headline)")
        try context.save()
        return .created(wish)
    }

   // MARK: Eltern entscheiden

    /// Setzt den Stand. Eine leere Antwort lässt eine frühere Antwort stehen.
    func decide(_ wish: KidWish, status: WishStatus, reply: String? = nil, fulfilledYoutubeId: String? = nil,
                actor: String = "Eltern") throws {
        guard wish.status.canMove(to: status) else { throw WishError.invalidTransition }
        wish.status = status
        let trimmed = reply?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let trimmed, !trimmed.isEmpty { wish.parentReply = String(trimmed.prefix(200)) }
        if let fulfilledYoutubeId { wish.fulfilledYoutubeId = fulfilledYoutubeId }
        wish.decidedAt = now()
        let decision: ReviewDecision = switch status {
        case .erfuellt: .wishFulfilled
        case .abgelehnt: .wishRejected
        case .besprechen, .offen: .wishDiscuss
        }
        log(wish, decision: decision, actor: actor,
            note: [wish.headline, wish.parentReply.map { "Antwort: \($0)" }].compactMap { $0 }.joined(separator: " · "))
        try context.save()
    }

    /// neueFolge → „Freigeben": legt den freigegebenen Whitelist-Eintrag an (Einordnung aus der Quelle)
    /// und erfüllt den Wunsch. Hart gefilterte Titel lassen sich hier nicht freigeben.
    @discardableResult
    func approveEpisode(_ wish: KidWish, in profile: KidProfile, reply: String? = nil, actor: String = "Eltern") throws -> WhitelistItem {
        guard wish.kind == .neueFolge else { throw WishError.wrongKind }
        guard let videoId = wish.videoId else { throw WishError.missingVideo }
        guard wish.status.canMove(to: .erfuellt) else { throw WishError.invalidTransition }
        let curation = CurationRepository(context: context)
        let item: WhitelistItem
        if let existing = profile.whitelistItems.first(where: { $0.youtubeId == videoId && $0.type == .video }) {
            item = existing
        } else {
            do {
                item = try curation.discover(
                    WhitelistItemDraft(type: .video, youtubeId: videoId, title: wish.videoTitle ?? videoId,
                                       thumbnailUrl: wish.thumbnailUrl ?? YouTubeIDs.defaultThumbnail(videoId: videoId),
                                       channelTitle: wish.channelTitle, sourceChannelId: wish.channelId),
                    for: profile, sourceChannelId: wish.channelId, actor: "Wunsch")
            } catch CurationRepository.DiscoverError.blockedSource {
                throw WishError.blockedSource
            }
        }
        guard item.approvalStatus != .rejected || !RiskScreen.assess(title: item.title).isHardBlocked else {
            throw WishError.notApprovable
        }
        let source = curation.effectiveSource(channelId: item.sourceChannelId ?? wish.channelId)
        let category = item.category ?? source?.defaultCategory
        try curation.approve(item, with: .init(ageMin: max(item.ageMin, source?.defaultAgeMin ?? 0, category?.minimumAge ?? 0),
                                               ageMax: item.ageMax, category: category,
                                               newsStatus: item.isNews ? .parentReview : nil,
                                               parentNotes: "Wunsch erfüllt"), actor: actor)
        try decide(wish, status: .erfuellt, reply: reply, fulfilledYoutubeId: item.youtubeId, actor: actor)
        return item
    }

    /// Eine Freigabe erfüllt die offenen Wünsche, die genau dieses Video meinen (ADR 0001, wie Android
    /// `erfuelleDurchFreigabe`). Auf iOS zeigt nur „Neue Folge" vorab auf ein bestimmtes Video;
    /// „Mehr davon" nennt das schon freigegebene Ausgangsvideo und bleibt deshalb unberührt.
    @discardableResult
    func erfuelleDurchFreigabe(youtubeId: String, in profile: KidProfile, actor: String = "Eltern") throws -> Int {
        let passende = wishes(of: profile.id).filter { $0.kind == .neueFolge && $0.videoId == youtubeId && $0.status.isPending }
        for wish in passende { try decide(wish, status: .erfuellt, fulfilledYoutubeId: youtubeId, actor: actor) }
        return passende.count
    }

    /// mehrDavon → „Kanal prüfen": der Kanal des Wunsches als Entwurf zum Einstufen. Aufgenommen wird er
    /// erst mit der Entscheidung der Eltern über Stufe, Alter und Kategorie (ADR 0003,
    /// `CurationRepository.kanalAufnehmen`) – nicht mehr als ungeprüfter Kandidat in der Prüfliste.
    func kanalEntwurf(of wish: KidWish) -> WhitelistItemDraft? {
        guard let channelId = wish.channelId, !channelId.isEmpty else { return nil }
        let title = wish.channelTitle ?? channelId
        return WhitelistItemDraft(type: .channel, youtubeId: channelId, title: title, thumbnailUrl: "",
                                  channelTitle: wish.channelTitle, sourceChannelId: channelId,
                                  sourceUrl: "https://www.youtube.com/channel/\(channelId)")
    }

   // MARK: Intern

    private func log(_ wish: KidWish, decision: ReviewDecision, actor: String, note: String?) {
        let item = wish.videoId ?? "thema:\(WishDraft.normalizedTopic(wish.topic ?? ""))"
        context.insert(ReviewEvent(itemYoutubeId: item, profileId: wish.profileID, decision: decision, actor: actor,
                                   at: now(), itemVersion: wish.historyKey, note: note))
    }

    private static func duplicateKey(of wish: KidWish) -> String? {
        WishDraft(kind: wish.kind, topic: wish.topic, videoId: wish.videoId).duplicateKey
    }
}
