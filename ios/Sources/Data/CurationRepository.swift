import Foundation
import SwiftData

/// Freigabe-Workflow mit Audit-Trail und Quellenregister.
/// Entdeckung ≠ Veröffentlichung: `discover` legt nie `approved` an.
struct CurationRepository {
    let context: ModelContext
    var now: () -> Date = { Date() }

   // MARK: Quellen

    func source(channelId: String?) -> CuratedSource? {
        guard let channelId else { return nil }
        let descriptor = FetchDescriptor<CuratedSource>(predicate: #Predicate { $0.channelId == channelId })
        return try? context.fetch(descriptor).first
    }

    func source(handle: String) -> CuratedSource? {
        let needle = handle.lowercased()
        return allSources().first { $0.handle?.lowercased() == needle }
    }

   /// Quelle für einen Kanal; bei PeerTube fällt sie auf die Instanz (`pt:<host>`) zurück.
    func effectiveSource(channelId: String?) -> CuratedSource? {
        guard let channelId else { return nil }
        if let exact = source(channelId: channelId) { return exact }
        if let instance = PeerTubeIDs.instanceId(of: channelId), instance != channelId { return source(channelId: instance) }
        return nil
    }

    func allSources() -> [CuratedSource] {
        (try? context.fetch(FetchDescriptor<CuratedSource>(sortBy: [SortDescriptor(\.title)]))) ?? []
    }

   /// Legt bekannte Quellen an (idempotent) – Trust nur setzen, wenn die Quelle neu ist (Eltern-Änderungen bleiben).
    func ensureSources(_ definitions: [SourceDefinition]) throws {
        for definition in definitions where source(channelId: definition.channelId) == nil {
            context.insert(CuratedSource(channelId: definition.channelId, handle: definition.handle, title: definition.title,
                                         provider: definition.provider, trust: definition.trust, isNewsSource: definition.isNews,
                                         defaultAgeMin: definition.defaultAgeMin, defaultCategory: definition.defaultCategory,
                                         notes: definition.notes, lastReviewedAt: definition.verifiedAt))
        }
        try context.save()
    }

    func setTrust(_ trust: SourceTrust, for source: CuratedSource, actor: String) throws {
        source.trust = trust
        source.lastReviewedAt = now()
        context.insert(ReviewEvent(itemYoutubeId: "source:\(source.channelId)", profileId: nil,
                                   decision: trust == .blocked ? .blockedSource : .approved, actor: actor, at: now(),
                                   itemVersion: trust.rawValue, note: "Quelle \(source.title) → \(trust.title)"))
        try context.save()
    }

   // MARK: Entdeckung / Import

    enum DiscoverError: Error, Equatable { case blockedSource, duplicate }

   /// Nimmt einen aufgelösten Inhalt in die Prüfschleife auf. Ergebnis: `reviewRequired`, bei harten Treffern `rejected`.
    @discardableResult
    func discover(_ draft: WhitelistItemDraft, for profile: KidProfile, sourceChannelId: String? = nil,
                  sourceHandle: String? = nil, description: String? = nil, durationSeconds: Int? = nil,
                  actor: String = "System") throws -> WhitelistItem {
        let source = effectiveSource(channelId: sourceChannelId ?? draft.sourceChannelId) ?? sourceHandle.flatMap(source(handle:))
        if source?.trust == .blocked { throw DiscoverError.blockedSource }
   // PeerTube: nur Instanzen aus der Allowlist (Föderation ⇒ unbekannte Instanz ist keine Kinderquelle)
        if draft.provider == .peertube, !PeerTubePolicy.instanceAllowed(for: draft.sourceChannelId ?? draft.youtubeId, curation: self) {
            throw DiscoverError.blockedSource
        }
        guard !WhitelistRepository(context: context).contains(youtubeId: draft.youtubeId, in: profile) else { throw DiscoverError.duplicate }

        var assessment = RiskScreen.assess(title: draft.title, description: description ?? draft.description, durationSeconds: durationSeconds ?? draft.durationSeconds)
        if draft.isNSFW { assessment.hardBlockTerms.append("nsfw-flag") }   // PeerTube kennzeichnet NSFW explizit
        if draft.isLive { assessment.isLive = true }
        let item = WhitelistItem(type: draft.type, youtubeId: draft.youtubeId, title: draft.title, thumbnailUrl: draft.thumbnailUrl,
                                 channelTitle: draft.channelTitle, addedAt: now(),
                                 approvalStatus: assessment.isHardBlocked ? .rejected : .reviewRequired)
        item.profile = profile
        item.provider = draft.provider
        item.sourceChannelId = sourceChannelId ?? draft.sourceChannelId ?? source?.channelId
        item.sourceChannelHandle = sourceHandle ?? source?.handle
        item.videoDescription = description
        item.durationSeconds = durationSeconds
        item.sensitiveTopics = assessment.topics
        item.isShort = assessment.isShort
        item.isLive = assessment.isLive
        item.containsSexualContent = assessment.topics.contains(.sexual)
        item.containsViolence = assessment.topics.contains(.violence)
        item.containsFear = assessment.topics.contains(.fear) || assessment.topics.contains(.horror)
        item.containsCoarseLanguage = assessment.topics.contains(.coarseLanguage)
        item.containsAdvertising = assessment.topics.contains(.advertising)
        item.ageMin = source?.defaultAgeMin ?? 0
        item.category = source?.defaultCategory
        if source?.isNewsSource == true || item.category == .news {
            item.isNews = true
            item.category = item.category ?? .news
            item.newsStatus = RiskScreen.newsStatus(for: assessment)
        }
        if source?.trust == .perVideoReview || source == nil, item.category == nil {
            item.editorialNotes = "Quelle ohne Vertrauensstufe – Einzelprüfung."
        }
        if !assessment.matchedTerms.isEmpty {
            item.editorialNotes = [item.editorialNotes, "Risikobegriffe: " + assessment.matchedTerms.joined(separator: ", ")].compactMap { $0 }.joined(separator: " ")
        }
        item.sourceUrl = draft.sourceUrl ?? "https://www.youtube.com/watch?v=\(draft.youtubeId)"
        context.insert(item)
        context.insert(ReviewEvent(itemYoutubeId: item.youtubeId, profileId: profile.id,
                                   decision: assessment.isHardBlocked ? .autoRejected : .discovered, actor: actor, at: now(),
                                   itemVersion: item.reviewVersion,
                                   note: assessment.isHardBlocked ? "Automatisch abgelehnt: \(assessment.hardBlockTerms.joined(separator: ", "))" : nil))
        try context.save()
        return item
    }

   // MARK: Menschliche Entscheidung

    struct Approval {
        var ageMin: Int
        var ageMax: Int?
        var category: ContentCategory?
        var newsStatus: NewsStatus?
        var parentNotes: String?
    }

    func approve(_ item: WhitelistItem, with approval: Approval, actor: String) throws {
        item.ageMin = approval.ageMin
        item.ageMax = approval.ageMax
        item.category = approval.category
        if item.isNews { item.newsStatus = approval.newsStatus ?? item.newsStatus ?? .parentReview }
        item.parentNotes = approval.parentNotes
        item.approvalStatus = .approved
        item.approvedBy = actor
        item.approvedAt = now()
        item.lastReviewedAt = now()
        context.insert(ReviewEvent(itemYoutubeId: item.youtubeId, profileId: item.profile?.id, decision: .approved,
                                   actor: actor, at: now(), itemVersion: item.reviewVersion, note: approval.parentNotes))
        try context.save()
    }

    func reject(_ item: WhitelistItem, actor: String, note: String? = nil) throws {
        item.approvalStatus = .rejected
        item.lastReviewedAt = now()
        context.insert(ReviewEvent(itemYoutubeId: item.youtubeId, profileId: item.profile?.id, decision: .rejected,
                                   actor: actor, at: now(), itemVersion: item.reviewVersion, note: note))
        try context.save()
    }

    func defer_(_ item: WhitelistItem, actor: String) throws {
        item.approvalStatus = .reviewRequired
        context.insert(ReviewEvent(itemYoutubeId: item.youtubeId, profileId: item.profile?.id, decision: .deferred,
                                   actor: actor, at: now(), itemVersion: item.reviewVersion))
        try context.save()
    }

   /// Markiert fällige Freigaben – kein automatisches Ablehnen.
    func markExpiredReviews(in profile: KidProfile) throws -> Int {
        var count = 0
        for item in profile.whitelistItems where ContentPolicy.reviewIsDue(item, source: source(channelId: item.sourceChannelId), now: now()) {
            item.approvalStatus = .expiredReview
            context.insert(ReviewEvent(itemYoutubeId: item.youtubeId, profileId: profile.id, decision: .statusExpired,
                                       actor: "System", at: now(), itemVersion: item.reviewVersion))
            count += 1
        }
        if count > 0 { try context.save() }
        return count
    }

    func pendingReview(in profile: KidProfile) -> [WhitelistItem] {
        profile.whitelistItems
            .filter { $0.approvalStatus == .reviewRequired || $0.approvalStatus == .expiredReview || $0.approvalStatus == .discovered }
            .sorted { $0.addedAt > $1.addedAt }
    }

    func events(for youtubeId: String) -> [ReviewEvent] {
        let descriptor = FetchDescriptor<ReviewEvent>(predicate: #Predicate { $0.itemYoutubeId == youtubeId }, sortBy: [SortDescriptor(\.at, order: .reverse)])
        return (try? context.fetch(descriptor)) ?? []
    }
}

/// Deklarative Quellenbeschreibung (Register + Seed-Datei).
struct SourceDefinition: Codable, Equatable, Sendable {
    var channelId: String
    var handle: String?
    var title: String
    var provider: ContentProvider = .youtube
    var trust: SourceTrust
    var isNews: Bool = false
    var defaultAgeMin: Int = 0
    var defaultCategory: ContentCategory?
    var notes: String?
    var verifiedAt: Date?
}
