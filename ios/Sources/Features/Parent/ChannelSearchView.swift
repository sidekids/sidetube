// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// „Abo finden": Kanäle bei YouTube suchen und in die Whitelist eines Profils übernehmen.
/// Die Treffer landen wie jeder andere Zugang zur Prüfung, nicht direkt beim Kind.
struct ChannelSearchView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppServices.self) private var services
    @Environment(\.dismiss) private var dismiss
    let profile: KidProfile

    @State private var query = ""
    @State private var results: [ChannelMetadata] = []
    @State private var isSearching = false
    @State private var message: String?
    @State private var added: Set<String> = []

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        TextField("Kanalname, z. B. Die Maus", text: $query)
                            .textInputAutocapitalization(.words)
                            .autocorrectionDisabled()
                            .submitLabel(.search)
                            .onSubmit(search)
                            .accessibilityIdentifier("channelsearch.field")
                        Button("Suchen", action: search)
                            .disabled(query.trimmingCharacters(in: .whitespaces).count < 2 || isSearching)
                    }
                } footer: {
                    Text(AppConfig.hasYouTubeAPIKey
                         ? "Die Suche nutzt das YouTube-Kontingent. Für einzelne Kanäle ist „Link hinzufügen“ sparsamer."
                         : "Ohne API-Schlüssel ist die Suche nicht möglich. Ein Kanal lässt sich weiterhin über „Link hinzufügen“ per Adresse aufnehmen.")
                }

                if isSearching {
                    HStack { ProgressView(); Text("Suche läuft …").foregroundStyle(.secondary) }
                }

                if !results.isEmpty {
                    Section("Treffer") {
                        ForEach(results, id: \.id) { channel in
                            ChannelResultRow(channel: channel,
                                             state: state(for: channel),
                                             add: { add(channel) })
                        }
                    }
                }
            }
            .navigationTitle("Abo finden")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Schließen") { dismiss() } } }
            .alert("Hinweis", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
                Button("OK", role: .cancel) {}
            } message: { Text(message ?? "") }
        }
    }

    private enum RowState { case addable, alreadyInList, justAdded, blocked }

    private func state(for channel: ChannelMetadata) -> RowState {
        if added.contains(channel.id) { return .justAdded }
        if WhitelistRepository(context: context).contains(youtubeId: channel.id, in: profile) { return .alreadyInList }
        if CurationRepository(context: context).source(channelId: channel.id)?.trust == .blocked { return .blocked }
        return .addable
    }

    private func search() {
        let term = query.trimmingCharacters(in: .whitespaces)
        guard term.count >= 2 else { return }
        isSearching = true
        Task {
            do {
                results = try await services.youtube.searchChannels(query: term)
                if results.isEmpty { message = "Kein Kanal gefunden. Andere Schreibweise probieren oder den Link direkt einfügen." }
            } catch YouTubeError.missingAPIKey {
                message = "Für die Suche fehlt der YouTube-API-Schlüssel. Kanäle lassen sich weiterhin über ihre Adresse aufnehmen."
            } catch {
                message = "Suche fehlgeschlagen: \(error.localizedDescription)"
            }
            isSearching = false
        }
    }

    private func add(_ channel: ChannelMetadata) {
        let draft = WhitelistItemDraft(type: .channel, youtubeId: channel.id, title: channel.title,
                                       thumbnailUrl: channel.thumbnailUrl, channelTitle: channel.title,
                                       sourceChannelId: channel.id, description: channel.description)
        do {
            _ = try CurationRepository(context: context).discover(draft, for: profile, sourceChannelId: channel.id, actor: "Eltern")
            added.insert(channel.id)
        } catch CurationRepository.DiscoverError.duplicate {
            added.insert(channel.id)
        } catch CurationRepository.DiscoverError.blockedSource {
            message = "„\(channel.title)“ ist als Quelle gesperrt und wird nicht aufgenommen."
        } catch {
            message = error.localizedDescription
        }
    }

    private struct ChannelResultRow: View {
        let channel: ChannelMetadata
        let state: RowState
        let add: () -> Void

        typealias RowState = ChannelSearchView.RowState

        var body: some View {
            HStack(spacing: 12) {
                Thumbnail(url: channel.thumbnailUrl, isChannel: true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(channel.title).lineLimit(1)
                    if !channel.description.isEmpty {
                        Text(channel.description).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                    }
                }
                Spacer(minLength: 8)
                switch state {
                case .addable:
                    Button("Prüfen", systemImage: "plus.circle", action: add)
                        .labelStyle(.iconOnly).font(.title3).buttonStyle(.plain)
                        .accessibilityLabel("Zur Prüfung aufnehmen")
                case .justAdded:
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                        .accessibilityLabel("Zur Prüfung aufgenommen")
                case .alreadyInList:
                    Text("dabei").font(.caption).foregroundStyle(.secondary)
                case .blocked:
                    Text("gesperrt").font(.caption).foregroundStyle(.red)
                }
            }
        }
    }
}
