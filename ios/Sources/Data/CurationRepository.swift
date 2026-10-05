// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

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
        for definition in definitions {
            if let existing = source(channelId: definition.channelId) {
                // Migrate the previous bundled PUR+ classification once. A later
                // explicit parent choice is never overwritten.
                if definition.channelId == SourceRegistry.purPlusChannelId,
                   definition.trust == .trustedChildSource,
                   existing.trust == .trustedSeries {
                    existing.trust = .trustedChildSource
                    existing.lastReviewedAt = definition.verifiedAt
                }
                continue
            }
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

   // MARK: Kanal beim Hinzufügen einstufen (ADR 0003)

    enum KanalAufnahme: Equatable {
        /// Quelle ist gesperrt gespeichert, nichts kam in die Whitelist.
        case gesperrt
        /// Kanal-Eintrag steht freigegeben in der Whitelist (neu oder ein bisheriger Kandidat).
        case aufgenommen(youtubeId: String)
    }

    /// Speichert Stufe, Mindestalter und Kategorie in der Quelle des Kanals – angelegt oder aktualisiert.
    /// Geschrieben wird immer die Quelle genau dieses Kanals, nie die einer PeerTube-Instanz, auf die
    /// `effectiveSource` zurückfällt: Ein gesperrter Kanal darf nicht die ganze Instanz sperren.
    @discardableResult
    func einstufen(kanal draft: WhitelistItemDraft, als einstufung: Kanaleinstufung, actor: String) throws -> CuratedSource {
        let channelId = draft.youtubeId
        let source: CuratedSource
        if let existing = self.source(channelId: channelId) {
            source = existing
        } else {
            source = CuratedSource(channelId: channelId, handle: nil, title: draft.title, provider: draft.provider,
                                   trust: einstufung.trust, notes: "Von Eltern beim Hinzufügen eingestuft.")
            context.insert(source)
        }
        source.trust = einstufung.trust
        source.defaultAgeMin = einstufung.effektivesMindestalter
        source.defaultCategoryRaw = einstufung.category?.rawValue
        source.lastReviewedAt = now()
        let kategorie = einstufung.category.map { ", \($0.title)" } ?? ""
        context.insert(ReviewEvent(itemYoutubeId: "source:\(channelId)", profileId: nil,
                                   decision: einstufung.trust == .blocked ? .blockedSource : .approved, actor: actor, at: now(),
                                   itemVersion: einstufung.trust.rawValue,
                                   note: "Quelle \(source.title) → \(einstufung.trust.title), ab \(einstufung.effektivesMindestalter)\(kategorie)"))
        try context.save()
        return source
    }

    /// Einstufen und – außer bei „Gesperrt" – den Kanal gleich freigegeben in die Whitelist des Profils
    /// nehmen. Ein schon vorhandener Kandidat (Kanalsuche, Wunsch) wird dabei freigegeben statt doppelt angelegt.
    func kanalAufnehmen(_ draft: WhitelistItemDraft, als einstufung: Kanaleinstufung, for profile: KidProfile,
                        actor: String) throws -> KanalAufnahme {
        try einstufen(kanal: draft, als: einstufung, actor: actor)
        guard einstufung.ergebnis == .hinzufuegen else { return .gesperrt }
        let approval = Approval(ageMin: einstufung.effektivesMindestalter, ageMax: nil, category: einstufung.category,
                                newsStatus: nil, parentNotes: nil)
        if let existing = profile.whitelistItems.first(where: { $0.youtubeId == draft.youtubeId }) {
            try approve(existing, with: approval, actor: actor)
            return .aufgenommen(youtubeId: existing.youtubeId)
        }
        let item = WhitelistItem(type: .channel, youtubeId: draft.youtubeId, title: draft.title, thumbnailUrl: draft.thumbnailUrl,
                                 channelTitle: draft.channelTitle, addedAt: now(), approvalStatus: .reviewRequired)
        item.profile = profile
        item.provider = draft.provider
        item.sourceChannelId = draft.sourceChannelId ?? draft.youtubeId
        item.sourceUrl = draft.sourceUrl
        context.insert(item)
        // Status erst über `approve`, damit Freigabe und Verlauf denselben Weg nehmen wie bei Videos.
        try approve(item, with: approval, actor: actor)
        return .aufgenommen(youtubeId: item.youtubeId)
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

    /// `vermerk` steht zusätzlich im Verlauf (z. B. „Sammelprüfung"), nicht in der Anmerkung für die Familie.
    func approve(_ item: WhitelistItem, with approval: Approval, actor: String, vermerk: String? = nil) throws {
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
                                   actor: actor, at: now(), itemVersion: item.reviewVersion,
                                   note: Self.notiz(vermerk, approval.parentNotes)))
        try context.save()
    }

    func reject(_ item: WhitelistItem, actor: String, note: String? = nil) throws {
        item.approvalStatus = .rejected
        item.lastReviewedAt = now()
        context.insert(ReviewEvent(itemYoutubeId: item.youtubeId, profileId: item.profile?.id, decision: .rejected,
                                   actor: actor, at: now(), itemVersion: item.reviewVersion, note: note))
        try context.save()
    }

    private static func notiz(_ teile: String?...) -> String? {
        let text = teile.compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
        return text.isEmpty ? nil : text
    }

   // MARK: Sammelprüfung (ADR 0004)

    struct SammelErgebnis: Equatable {
        /// youtubeIds der freigegebenen bzw. abgelehnten Einträge.
        var erledigt: [String] = []
        /// Einträge, die unberührt blieben, mit Grund.
        var uebersprungen: [(youtubeId: String, grund: Sammelpruefung.Grund)] = []
        /// Wünsche, die durch die Freigaben erfüllt sind.
        var erfuellteWuensche = 0

        static func == (a: Self, b: Self) -> Bool {
            a.erledigt == b.erledigt && a.erfuellteWuensche == b.erfuellteWuensche
                && a.uebersprungen.map(\.youtubeId) == b.uebersprungen.map(\.youtubeId)
                && a.uebersprungen.map(\.grund) == b.uebersprungen.map(\.grund)
        }
    }

    func pruefling(_ item: WhitelistItem) -> Sammelpruefung.Pruefling {
        Sammelpruefung.Pruefling(item: item, source: effectiveSource(channelId: item.sourceChannelId ?? (item.type == .channel ? item.youtubeId : nil)))
    }

    /// Einzelfreigabe aus der Prüfliste: freigeben und passende offene Wünsche erfüllen – wie die
    /// Sammelfreigabe und wie Android (`ParentViewModel.approve`). Vorher blieb auf iOS ein Wunsch
    /// „offen", obwohl sein Video einzeln freigegeben war (ADR 0004, Nachtrag).
    /// - Returns: wie viele Wünsche damit erfüllt sind.
    @discardableResult
    func approveUndErfuelleWuensche(_ item: WhitelistItem, with approval: Approval, actor: String) throws -> Int {
        try approve(item, with: approval, actor: actor)
        guard let profile = item.profile else { return 0 }
        return try WishRepository(context: context, now: now).erfuelleDurchFreigabe(youtubeId: item.youtubeId, in: profile, actor: actor)
    }

    /// Gibt alle sammelbaren Einträge mit den Sammelwerten frei – jeder mit eigenem Verlaufseintrag. Jeder
    /// Eintrag wird dabei neu geprüft (Stand der Quelle, Status), nicht der Stand beim Öffnen der Maske.
    /// Nicht sammelbare bleiben unberührt in der Prüfliste. Kanäle bekommen die gewählte Stufe in ihrer
    /// Quelle (ADR 0003). Passende offene Wünsche gelten mit der Freigabe als erfüllt (ADR 0001).
    @discardableResult
    func sammelFreigeben(_ items: [WhitelistItem], mit werte: Sammelpruefung.Werte, actor: String) throws -> SammelErgebnis {
        var ergebnis = SammelErgebnis()
        let wuensche = WishRepository(context: context, now: now)
        for item in items {
            let p = pruefling(item)
            if let grund = Sammelpruefung.grund(p) {
                ergebnis.uebersprungen.append((item.youtubeId, grund))
                continue
            }
            let f = Sammelpruefung.freigabe(p, mit: werte)
            if let trust = f.trust {
                let draft = WhitelistItemDraft(type: .channel, youtubeId: item.youtubeId, title: item.title,
                                               thumbnailUrl: item.thumbnailUrl, channelTitle: item.channelTitle,
                                               provider: item.provider, sourceChannelId: item.sourceChannelId ?? item.youtubeId,
                                               sourceUrl: item.sourceUrl)
                try einstufen(kanal: draft, als: Kanaleinstufung(trust: trust, ageMin: f.ageMin, category: f.category), actor: actor)
            }
            // Nachrichtenstatus und Anmerkung des Eintrags bleiben, wie sie sind.
            try approve(item, with: Approval(ageMin: f.ageMin, ageMax: f.ageMax, category: f.category,
                                             newsStatus: item.newsStatus, parentNotes: item.parentNotes),
                        actor: actor, vermerk: Sammelpruefung.vermerk)
            ergebnis.erledigt.append(item.youtubeId)
            if let profile = item.profile {
                ergebnis.erfuellteWuensche += try wuensche.erfuelleDurchFreigabe(youtubeId: item.youtubeId, in: profile, actor: actor)
            }
        }
        return ergebnis
    }

    /// Lehnt jeden sammelbaren Eintrag ab, jeder mit eigenem Verlaufseintrag. Nicht Sammelbares bleibt wie
    /// bei der Freigabe unberührt in der Prüfliste (gleich Android).
    @discardableResult
    func sammelAblehnen(_ items: [WhitelistItem], actor: String) throws -> SammelErgebnis {
        var ergebnis = SammelErgebnis()
        for item in items {
            if let grund = Sammelpruefung.grund(pruefling(item)) {
                ergebnis.uebersprungen.append((item.youtubeId, grund))
                continue
            }
            try reject(item, actor: actor, note: Sammelpruefung.vermerk)
            ergebnis.erledigt.append(item.youtubeId)
        }
        return ergebnis
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
