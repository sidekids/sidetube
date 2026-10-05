// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Redaktionsansicht: neue Kandidaten prüfen – Freigeben / Ablehnen / Später.
struct ReviewQueueView: View {
    @Environment(\.modelContext) private var modelContext
    let profile: KidProfile
    @State private var editing: WhitelistItem?
    @State private var showDiscardAll = false
    @State private var error: String?
    @State private var decidingWish: KidWish?
    /// Sammelprüfung (ADR 0004): Auswahlmodus mit Häkchen je Eintrag.
    @State private var auswaehlen = false
    @State private var auswahl: Set<UUID> = []
    @State private var zeigeSammelFreigabe = false
    @State private var zeigeSammelAblehnen = false
    @State private var meldung: String?
   /// Alle Wünsche; gefiltert wird nach Profil (wenige Einträge, keine dynamische Abfrage nötig).
    @Query(sort: \KidWish.createdAt, order: .reverse) private var allWishes: [KidWish]

    private var pending: [WhitelistItem] { CurationRepository(context: modelContext).pendingReview(in: profile) }
   /// Wünsche zuerst (ADR 0001): offene vor „besprechen".
    private var wishes: [KidWish] {
        allWishes.filter { $0.profileID == profile.id && $0.status.isPending }
            .sorted { ($0.status == .offen ? 0 : 1, $1.createdAt) < ($1.status == .offen ? 0 : 1, $0.createdAt) }
    }

    var body: some View {
        List {
            if pending.isEmpty && wishes.isEmpty {
                ContentUnavailableView("Nichts zu prüfen", systemImage: "checkmark.seal",
                                       description: Text("Neue Kandidaten erscheinen hier, bevor sie im Kinderprofil sichtbar werden."))
            }
            if let meldung {
                Label(meldung, systemImage: "checkmark.circle").font(.footnote)
                    .accessibilityIdentifier("review.meldung")
            }
            if !wishes.isEmpty && !auswaehlen {
                Section {
                    ForEach(wishes, id: \.id) { wish in
                        Button { decidingWish = wish } label: { WishRow(wish: wish) }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("review.wish")
                    }
                } header: {
                    Text("Wünsche von \(profile.name) (\(wishes.count))")
                } footer: {
                    Text("Wünsche bleiben auf diesem Gerät. Es gibt keine Benachrichtigung – \(profile.name) sieht den Stand unter „Meine Wünsche“.")
                }
            }
            ForEach(pending) { item in
                if auswaehlen {
                    Button { umschalten(item) } label: {
                        HStack(spacing: 10) {
                            Image(systemName: auswahl.contains(item.id) ? "checkmark.circle.fill" : "circle")
                                .font(.title3)
                                .foregroundStyle(auswahl.contains(item.id) ? Color.accentColor : .secondary)
                            ReviewCandidateRow(item: item)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("review.row")
                    .accessibilityAddTraits(auswahl.contains(item.id) ? .isSelected : [])
                } else {
                    Button { editing = item } label: { ReviewCandidateRow(item: item) }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("review.row")
                        .swipeActions(edge: .trailing) {
                            Button("Ablehnen", systemImage: "xmark", role: .destructive) { try? CurationRepository(context: modelContext).reject(item, actor: "Eltern") }
                        }
                }
            }
        }
        .navigationTitle("Prüfen (\(pending.count + wishes.count))")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(auswaehlen)
        .toolbar {
            if auswaehlen {
                ToolbarItem(placement: .topBarLeading) {
                    let alle = !pending.isEmpty && pending.allSatisfy { auswahl.contains($0.id) }
                    Button(alle ? "Keine auswählen" : "Alle auswählen") {
                        auswahl = alle ? [] : Set(pending.map(\.id))
                    }
                    .accessibilityIdentifier("review.alleAuswaehlen")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fertig") { auswahlBeenden() }.accessibilityIdentifier("review.auswaehlen")
                }
                ToolbarItemGroup(placement: .bottomBar) {
                    Button("Ablehnen (\(ausgewaehlt.count))", systemImage: "xmark.circle", role: .destructive) { zeigeSammelAblehnen = true }
                        .labelStyle(.titleOnly)
                        .disabled(ausgewaehlt.isEmpty)
                        .accessibilityIdentifier("review.sammelAblehnen")
                    Spacer()
                    Button("Freigeben (\(ausgewaehlt.count))", systemImage: "checkmark.circle.fill") { zeigeSammelFreigabe = true }
                        .labelStyle(.titleOnly)
                        .fontWeight(.semibold)
                        .disabled(ausgewaehlt.isEmpty)
                        .accessibilityIdentifier("review.sammelFreigeben")
                }
            } else if !pending.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Auswählen") { meldung = nil; auswaehlen = true }
                        .accessibilityIdentifier("review.auswaehlen")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Alle verwerfen", systemImage: "trash") { showDiscardAll = true }
                        .accessibilityIdentifier("review.discardAll")
                }
            }
        }
        .confirmationDialog(ablehnTitel, isPresented: $zeigeSammelAblehnen, titleVisibility: .visible) {
            let n = ablehnbar.count
            if n > 0 {
                Button(n == 1 ? "1 Eintrag ablehnen" : "\(n) Einträge ablehnen", role: .destructive, action: sammelAblehnen)
                    .accessibilityIdentifier("review.sammelAblehnenBestaetigen")
            }
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text(([ablehnbar.isEmpty ? nil : "Sie erscheinen nicht bei \(profile.name). Der Verlauf hält jede Ablehnung einzeln fest."]
                  + [Sammelpruefung.hinweis(ausgewaehlt.compactMap { Sammelpruefung.grund(CurationRepository(context: modelContext).pruefling($0)) })])
                .compactMap { $0 }.joined(separator: "\n\n"))
        }
        .sheet(isPresented: $zeigeSammelFreigabe) {
            SammelFreigabeSheet(items: ausgewaehlt) { ergebnis in
                let n = ergebnis.erledigt.count
                var text = n == 1 ? "1 Eintrag freigegeben" : "\(n) Einträge freigegeben"
                switch ergebnis.erfuellteWuensche {
                case 0: break
                case 1: text += ", 1 Wunsch erfüllt"
                case let w: text += ", \(w) Wünsche erfüllt"
                }
                if !ergebnis.uebersprungen.isEmpty { text += "; \(ergebnis.uebersprungen.count) bleiben zur Einzelprüfung" }
                auswahlBeenden()
                meldung = text + "."
            }
        }
        .confirmationDialog("Alle offenen Kandidaten verwerfen?", isPresented: $showDiscardAll, titleVisibility: .visible) {
            Button("\(pending.count) Einträge verwerfen", role: .destructive, action: discardAll)
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("Die Einträge verschwinden aus der Whitelist von \(profile.name). Freigegebene Inhalte bleiben unberührt; ein Startpaket lässt sich jederzeit erneut laden.")
        }
        .alert("Fehler", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(error ?? "") }
        .sheet(item: $editing) { item in
            // Kanal-Kandidaten (aus älteren Ständen) werden wie beim Hinzufügen eingestuft – ADR 0003;
            // Ablehnen bleibt per Wischgeste.
            if item.type == .channel {
                KanalEinstufenSheet(kandidat: KanalKandidat(draft: WhitelistItemDraft(
                    type: .channel, youtubeId: item.youtubeId, title: item.title, thumbnailUrl: item.thumbnailUrl,
                    channelTitle: item.channelTitle, provider: item.provider,
                    sourceChannelId: item.sourceChannelId ?? item.youtubeId, sourceUrl: item.sourceUrl)), profile: profile)
            } else {
                ReviewDecisionView(item: item, profile: profile)
            }
        }
        .sheet(item: $decidingWish) { wish in WishDecisionView(wish: wish, profile: profile) }
    }
}

extension ReviewQueueView {
    /// Ausgewählte Einträge in der Reihenfolge der Liste; was inzwischen nicht mehr offen ist, fällt heraus.
    fileprivate var ausgewaehlt: [WhitelistItem] { pending.filter { auswahl.contains($0.id) } }

    fileprivate func umschalten(_ item: WhitelistItem) {
        if auswahl.contains(item.id) { auswahl.remove(item.id) } else { auswahl.insert(item.id) }
    }

    fileprivate func auswahlBeenden() {
        auswaehlen = false
        auswahl = []
    }

    /// Ausgewählte, die sich gemeinsam ablehnen lassen – Nicht-Sammelbares bleibt auch hier unberührt.
    fileprivate var ablehnbar: [WhitelistItem] {
        let curation = CurationRepository(context: modelContext)
        return ausgewaehlt.filter { Sammelpruefung.istSammelbar(curation.pruefling($0)) }
    }

    fileprivate var ablehnTitel: String {
        switch ablehnbar.count {
        case 0: "Nichts gemeinsam abzulehnen"
        case 1: "1 Eintrag ablehnen?"
        case let n: "\(n) Einträge ablehnen?"
        }
    }

    fileprivate func sammelAblehnen() {
        do {
            let ergebnis = try CurationRepository(context: modelContext).sammelAblehnen(ausgewaehlt, actor: "Eltern")
            auswahlBeenden()
            var text = ergebnis.erledigt.count == 1 ? "1 Eintrag abgelehnt" : "\(ergebnis.erledigt.count) Einträge abgelehnt"
            if !ergebnis.uebersprungen.isEmpty { text += "; \(ergebnis.uebersprungen.count) bleiben zur Einzelprüfung" }
            meldung = text + "."
        } catch { self.error = error.localizedDescription }
    }

   /// Verwirft alle offenen Kandidaten auf einmal – freigegebene und abgelehnte Einträge bleiben stehen.
    fileprivate func discardAll() {
        let repo = WhitelistRepository(context: modelContext)
        do {
            for item in pending { try repo.remove(item) }
        } catch { self.error = error.localizedDescription }
    }
}

struct ReviewCandidateRow: View {
    @Environment(\.modelContext) private var modelContext
    let item: WhitelistItem

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Thumbnail(url: item.thumbnailUrl)
            VStack(alignment: .leading, spacing: 4) {
                Text(item.title).font(.subheadline.weight(.semibold)).lineLimit(2)
                Text([item.channelTitle, item.durationSeconds.map { "\($0 / 60) min" }, "ab \(item.ageMin)", item.category?.title]
                    .compactMap { $0 }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(.secondary)
                HStack(spacing: 6) {
                    badge(item.approvalStatus.title, color: item.approvalStatus == .expiredReview ? .orange : .secondary)
                    if let source = CurationRepository(context: modelContext).source(channelId: item.sourceChannelId) {
                        badge(source.trust.title, color: source.trust == .blocked ? .red : .secondary)
                    }
                    if item.isNews, let news = item.newsStatus { badge(news.title, color: news == .safe ? .green : .orange) }
                    if item.isShort { badge("Short", color: .orange) }
                    if item.isLive { badge("Live", color: .red) }
                    if item.madeForKids != .unknown { badge(item.madeForKids == .madeForKids ? "Made for Kids" : "nicht MfK", color: .secondary) }
                }
                if !item.sensitiveTopics.isEmpty {
                    Text("Risiken: " + item.sensitiveTopics.map(\.title).sorted().joined(separator: ", ")).font(.caption).foregroundStyle(.orange)
                }
                if let notes = item.editorialNotes { Text(notes).font(.caption2).foregroundStyle(.secondary).lineLimit(2) }
            }
        }
        .padding(.vertical, 4)
    }

    private func badge(_ text: String, color: Color) -> some View {
        Text(text).font(.caption2.weight(.semibold)).padding(.horizontal, 6).padding(.vertical, 2)
            .background(Capsule().fill(color.opacity(0.15))).foregroundStyle(color == .secondary ? Color.primary : color)
    }
}

/// Entscheidung mit bearbeitbaren Feldern (Alter, Kategorie, Nachrichtenstatus, Anmerkung) und Audit-Trail.
/// Dieselbe Maske dient zum Prüfen neuer Kandidaten und zum Nachbessern bereits freigegebener Einträge.
struct ReviewDecisionView: View {
    enum Mode { case review, edit }

    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    let item: WhitelistItem
    let profile: KidProfile
    var mode: Mode = .review
    @State private var ageMin: Int
    @State private var ageMaxEnabled: Bool
    @State private var ageMax: Int
    @State private var category: ContentCategory?
    @State private var newsStatus: NewsStatus
    @State private var notes: String
    @State private var error: String?

    init(item: WhitelistItem, profile: KidProfile, mode: Mode = .review) {
        self.item = item
        self.profile = profile
        self.mode = mode
        _ageMin = State(initialValue: max(item.ageMin, item.category?.minimumAge ?? 0))
        _ageMaxEnabled = State(initialValue: item.ageMax != nil)
        _ageMax = State(initialValue: item.ageMax ?? 12)
        _category = State(initialValue: item.category)
        _newsStatus = State(initialValue: item.newsStatus ?? .parentReview)
        _notes = State(initialValue: item.parentNotes ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ReviewCandidateRow(item: item)
                    if let url = URL(string: item.sourceUrl ?? "https://www.youtube.com/watch?v=\(item.youtubeId)") {
                        Link("Video im Original ansehen (Eltern)", destination: url).font(.footnote)
                    }
                } footer: {
                    Text(mode == .review
                         ? "Bitte das Video ausreichend ansehen. Der automatische Filter ist nur ein Hinweis."
                         : "Änderungen werden im Verlauf festgehalten. „Zurück zur Prüfung“ nimmt den Eintrag so lange aus dem Kinderprofil.")
                }
                Section("Einordnung") {
                    Stepper("Ab \(ageMin) Jahren", value: $ageMin, in: 3...16)
                    Toggle("Höchstalter", isOn: $ageMaxEnabled)
                    if ageMaxEnabled { Stepper("Bis \(ageMax) Jahre", value: $ageMax, in: ageMin...17) }
                    Picker("Kategorie", selection: $category) {
                        Text("Keine").tag(ContentCategory?.none)
                        ForEach(ContentCategory.allCases) { Label($0.title, systemImage: $0.systemImage).tag(Optional($0)) }
                    }
                    if item.isNews || category == .news {
                        Picker("Nachrichtenstatus", selection: $newsStatus) {
                            ForEach(NewsStatus.allCases, id: \.self) { Text($0.title).tag($0) }
                        }
                    }
                    TextField("Anmerkung für die Familie (optional)", text: $notes, axis: .vertical)
                }
                if let category, category.minimumAge > 0, ageMin < category.minimumAge {
                    Text("„\(category.title)“ wird erst ab \(category.minimumAge) gezeigt – das Mindestalter wird entsprechend angehoben.")
                        .font(.footnote).foregroundStyle(.orange)
                }
                Section {
                    switch mode {
                    case .review:
                        Button("Freigeben", systemImage: "checkmark.circle.fill", action: approve).buttonStyle(.borderedProminent)
                        Button("Ablehnen", systemImage: "xmark.circle", role: .destructive) { decide { try $0.reject(item, actor: "Eltern", note: notes.isEmpty ? nil : notes) } }
                        Button("Später", systemImage: "clock") { decide { try $0.defer_(item, actor: "Eltern") } }
                    case .edit:
                        Button("Änderungen sichern", systemImage: "checkmark.circle.fill", action: approve).buttonStyle(.borderedProminent)
                        Button("Zurück zur Prüfung", systemImage: "arrow.uturn.backward") { decide { try $0.defer_(item, actor: "Eltern") } }
                        Button("Ablehnen", systemImage: "xmark.circle", role: .destructive) { decide { try $0.reject(item, actor: "Eltern", note: notes.isEmpty ? nil : notes) } }
                    }
                }
                Section("Verlauf") {
                    let events = CurationRepository(context: modelContext).events(for: item.youtubeId)
                    if events.isEmpty { Text("Noch keine Einträge").foregroundStyle(.secondary) }
                    ForEach(events, id: \.at) { event in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(event.headline).font(.caption.weight(.semibold))
                            Text(event.at.formatted(date: .abbreviated, time: .shortened)).font(.caption2).foregroundStyle(.secondary)
                            if let note = event.note { Text(note).font(.caption2).foregroundStyle(.secondary) }
                        }
                    }
                }
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle(mode == .review ? "Prüfen" : "Bearbeiten")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Schließen") { dismiss() } } }
        }
    }

    private func approve() {
        decide { repo in
            let effectiveMin = max(ageMin, category?.minimumAge ?? 0)
            try repo.approveUndErfuelleWuensche(item, with: .init(ageMin: effectiveMin, ageMax: ageMaxEnabled ? ageMax : nil, category: category,
                                               newsStatus: (item.isNews || category == .news) ? newsStatus : nil,
                                               parentNotes: notes.isEmpty ? nil : notes), actor: "Eltern")
        }
    }

    private func decide(_ action: (CurationRepository) throws -> Void) {
        do { try action(CurationRepository(context: modelContext)); dismiss() } catch { self.error = error.localizedDescription }
    }
}
