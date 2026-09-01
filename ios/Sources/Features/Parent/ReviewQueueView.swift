import SwiftData
import SwiftUI

/// Redaktionsansicht: neue Kandidaten prüfen – Freigeben / Ablehnen / Später.
struct ReviewQueueView: View {
    @Environment(\.modelContext) private var modelContext
    let profile: KidProfile
    @State private var editing: WhitelistItem?
    @State private var showDiscardAll = false
    @State private var error: String?

    private var pending: [WhitelistItem] { CurationRepository(context: modelContext).pendingReview(in: profile) }

    var body: some View {
        List {
            if pending.isEmpty {
                ContentUnavailableView("Nichts zu prüfen", systemImage: "checkmark.seal",
                                       description: Text("Neue Kandidaten erscheinen hier, bevor sie im Kinderprofil sichtbar werden."))
            }
            ForEach(pending) { item in
                Button { editing = item } label: { ReviewCandidateRow(item: item) }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("review.row")
                    .swipeActions(edge: .trailing) {
                        Button("Ablehnen", systemImage: "xmark", role: .destructive) { try? CurationRepository(context: modelContext).reject(item, actor: "Eltern") }
                    }
            }
        }
        .navigationTitle("Prüfen (\(pending.count))")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if !pending.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Alle verwerfen", systemImage: "trash") { showDiscardAll = true }
                        .accessibilityIdentifier("review.discardAll")
                }
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
        .sheet(item: $editing) { item in ReviewDecisionView(item: item, profile: profile) }
    }
}

extension ReviewQueueView {
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
                            Text("\(event.decision.rawValue) · \(event.actor)").font(.caption.weight(.semibold))
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
            try repo.approve(item, with: .init(ageMin: effectiveMin, ageMax: ageMaxEnabled ? ageMax : nil, category: category,
                                               newsStatus: (item.isNews || category == .news) ? newsStatus : nil,
                                               parentNotes: notes.isEmpty ? nil : notes), actor: "Eltern")
        }
    }

    private func decide(_ action: (CurationRepository) throws -> Void) {
        do { try action(CurationRepository(context: modelContext)); dismiss() } catch { self.error = error.localizedDescription }
    }
}
