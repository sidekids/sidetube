import SwiftData
import SwiftUI

/// Suche = gezieltes Finden: native `searchable`, letzte Suchen, lokale Treffer (0 Quota).
struct SearchScreen: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(AppServices.self) private var services
    @Environment(KidSession.self) private var kidSession
    @Environment(RemoteController.self) private var remote
    @Environment(PlayerCoordinator.self) private var playerCoordinator
    @State private var path = NavigationPath()
    @State private var model: SearchModel?
    @State private var text = ""
    @State private var recentSearches = RecentSearches()
    @FocusState private var searchFocused: Bool
    let onLock: () -> Void

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                searchField
                if let model {
                    results(model: model)
                } else {
                    KidEmptyState(systemImage: "tv", title: "Noch keine Inhalte", message: "Eltern legen im Elternbereich ein Profil an.")
                    Spacer()
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Suche")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { ParentControlButton(action: onLock) }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    // Ohne diesen Knopf verdeckt die Tastatur die Tab-Leiste und die Suche wäre eine Sackgasse.
                    Button("Fertig") { searchFocused = false }
                        .font(.body.weight(.semibold))
                        .accessibilityIdentifier("search.done")
                }
            }
            .navigationDestination(for: KidScreenFactory.self) { DetailScreen(factory: $0, onLock: onLock) }
            .kidRemoteHandle(hidden: searchFocused)
        }
        .onAppear {
            rebuild()
            focusSearchFieldIfIdle()
        }
        .onChange(of: kidSession.activeProfile?.id) { _, _ in rebuild() }
        .task(id: text) {
            try? await Task.sleep(for: .milliseconds(300))   // FR-07.1 Debounce
            guard !Task.isCancelled else { return }
            model?.searchText = text
        }
    }

    /// Immer sichtbares Suchfeld; ein Tipp auf den Tab öffnet direkt die Tastatur.
    private var searchField: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField("Kanäle und Videos", text: $text)
                .focused($searchFocused)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .onSubmit { recentSearches.add(text); searchFocused = false }
                .accessibilityIdentifier("search.field")
            if !text.isEmpty {
                Button {
                    text = ""
                    searchFocused = true
                } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                }
                .accessibilityLabel("Suche löschen")
            }
        }
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .padding(.horizontal, KidTheme.outerPadding)
        .padding(.bottom, 8)
        .frame(minHeight: KidTheme.minimumTouchTarget)
        .contentShape(Rectangle())
        .onTapGesture { searchFocused = true }
    }

    /// Beim Öffnen des Tabs direkt tippen können – aber nicht, wenn schon gesucht oder ein Detail offen ist.
    private func focusSearchFieldIfIdle() {
        guard text.isEmpty, path.isEmpty else { return }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(350))   // Suchfeld muss zuerst im Layout sein
            guard text.isEmpty, path.isEmpty else { return }
            searchFocused = true
        }
    }

    private func results(model: SearchModel) -> some View {
        let rows = model.rows
        return ScrollViewReader { proxy in
        List {
            if text.isEmpty {
                if recentSearches.items.isEmpty {
                    KidEmptyState(systemImage: "magnifyingglass", title: "Wonach suchst du?",
                                  message: "Suche in deinen freigegebenen Kanälen, Videos und Playlists.")
                        .listRowBackground(Color.clear)
                } else {
                    Section("Zuletzt gesucht") {
                        ForEach(recentSearches.items, id: \.self) { term in
                            Button { text = term; searchFocused = false } label: {
                                Label(term, systemImage: "clock.arrow.circlepath").foregroundStyle(.primary)
                            }
                        }
                    }
                }
            } else if rows.isEmpty {
                KidEmptyState(systemImage: "questionmark.circle", title: "Nichts gefunden",
                              message: "Für „\(text)“ gibt es keinen Treffer. Probiere ein anderes Wort.")
                    .listRowBackground(Color.clear)
            }
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                RecentVideoRow(row: row, isSelected: remote.isSelected(model, index: index)) { activate(row, model: model) }
                    .id(row.id)
                    .listRowInsets(EdgeInsets(top: 2, leading: 8, bottom: 2, trailing: 8))
                    .listRowSeparator(.hidden)
            }
        }
        .listStyle(.plain)
        .scrollDismissesKeyboard(.immediately)
        .remoteAutoScroll(model: model, proxy: proxy, ids: rows.map(\.id))
        }
    }

    private func rebuild() {
        guard let profile = kidSession.activeProfile else { model = nil; return }
        let created = SearchModel(profile: profile, context: KidContext(modelContext: modelContext, youtube: services.youtube, resolver: services.resolver))
        created.searchText = text
        model = created
        remote.target = RemoteTargetBinding(model: created) { row in activate(row, model: created) }
    }

    private func activate(_ row: KidRow, model: SearchModel) {
        searchFocused = false
        recentSearches.add(text)
        switch row.action {
        case .push(let factory): path.append(factory)
        case .play: playerCoordinator.play(row, in: model.allItems, profile: kidSession.activeProfile, context: modelContext)
        case .none: break
        }
    }
}

/// Letzte Suchbegriffe (max. 6), lokal gespeichert.
@Observable
final class RecentSearches {
    private static let key = "kid.recentSearches"
    private(set) var items: [String]

    init() {
        items = UserDefaults.standard.stringArray(forKey: Self.key) ?? []
    }

    func add(_ term: String) {
        let trimmed = term.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= 2 else { return }
        items.removeAll { $0.caseInsensitiveCompare(trimmed) == .orderedSame }
        items.insert(trimmed, at: 0)
        items = Array(items.prefix(6))
        UserDefaults.standard.set(items, forKey: Self.key)
    }
}
