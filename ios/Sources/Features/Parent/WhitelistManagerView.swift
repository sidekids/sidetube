// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Whitelist eines Profils: Filter nach Typ, Entfernen, Hinzufügen per URL. FR-04.6–04.8
struct WhitelistManagerView: View {
    @Environment(\.modelContext) private var context
    let profile: KidProfile
    @State private var filter: YouTubeContentType?
    @State private var showAdd = false
    @State private var showEdit = false
    @State private var showSleep = false
    @State private var showChannelSearch = false
    @State private var editing: WhitelistItem?
    @State private var seedMessage: String?
    @State private var errorMessage: String?

    private var items: [WhitelistItem] { WhitelistRepository(context: context).items(of: profile, type: filter) }

    var body: some View {
        VStack(spacing: 0) {
            Picker("Filter", selection: $filter) {
                Text("Alle").tag(YouTubeContentType?.none)
                Text("Kanäle").tag(YouTubeContentType?.some(.channel))
                Text("Videos").tag(YouTubeContentType?.some(.video))
                Text("Playlists").tag(YouTubeContentType?.some(.playlist))
            }
            .pickerStyle(.segmented).padding()

            if items.isEmpty {
                ContentUnavailableView {
                    Label("Whitelist ist leer", systemImage: "checklist")
                } description: {
                    Text("Füge YouTube-Kanäle, -Videos oder -Playlists per Link hinzu.")
                } actions: {
                    Button("Link hinzufügen") { showAdd = true }.buttonStyle(.borderedProminent)
                }
            } else {
                List {
                    ForEach(items) { item in
                        // Antippen öffnet dieselbe Maske wie beim Prüfen – Einstufung bleibt änderbar.
                        Button { editing = item } label: { WhitelistItemRow(item: item) }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("whitelist.row")
                            .swipeActions {
                                Button("Entfernen", systemImage: "trash", role: .destructive) { remove(item) }
                            }
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle(profile.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                NavigationLink { ReviewQueueView(profile: profile) } label: {
                    Label("Freigaben prüfen", systemImage: "checkmark.seal")
                }
                Menu {
                    Button("Abo finden", systemImage: "magnifyingglass") { showChannelSearch = true }
                    NavigationLink { WatchStatsView(profile: profile) } label: {
                        Label("Nutzung", systemImage: "chart.bar")
                    }
                    Divider()
                    Button("Schlafmodus", systemImage: "moon.zzz") { showSleep = true }
                    Button("Profil bearbeiten", systemImage: "slider.horizontal.3") { showEdit = true }
                    Menu {
                        ForEach(SeedLibraryImporter.catalogs()) { catalog in
                            Button(catalog.title) { importSeed(catalog) }
                        }
                    } label: {
                        Label("Startpaket laden (zur Prüfung)", systemImage: "square.and.arrow.down")
                    }
                } label: { Image(systemName: "ellipsis.circle") }
                .accessibilityLabel("Weitere Aktionen")
                .accessibilityIdentifier("profile.menu")
                Button("Hinzufügen", systemImage: "plus") { showAdd = true }
            }
        }
        .sheet(isPresented: $showAdd) { AddWhitelistItemView(profile: profile) }
        .sheet(isPresented: $showEdit) { ProfileEditorView(profile: profile) }
        .sheet(isPresented: $showSleep) { SleepModeSheet(profile: profile) }
        .sheet(isPresented: $showChannelSearch) { ChannelSearchView(profile: profile) }
        .sheet(item: $editing) { item in
            ReviewDecisionView(item: item, profile: profile,
                               mode: item.approvalStatus == .approved ? .edit : .review)
        }
        .alert("Startpaket", isPresented: Binding(get: { seedMessage != nil }, set: { if !$0 { seedMessage = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(seedMessage ?? "") }
        .alert("Fehler", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(errorMessage ?? "") }
    }

   /// Startpakete landen als „Prüfung nötig“ in genau diesem Profil – nie direkt sichtbar.
    private func importSeed(_ catalog: SeedLibraryImporter.Catalog) {
        do {
            let library = try SeedLibraryImporter.load(catalog)
            let result = try SeedLibraryImporter(context: context).importLibrary(library, into: profile, applyProfilePreset: true)
            let preset = library.profilePreset.map { " Profil auf „\($0.ageBand.title)“ gesetzt." } ?? ""
            seedMessage = "\(catalog.title): \(result.imported) Kandidaten zur Prüfung für \(profile.name) übernommen, \(result.skipped) bereits vorhanden, \(result.blocked) aus gesperrten Quellen übersprungen.\(preset) Freigabe unter „Freigaben prüfen“."
        } catch {
            seedMessage = "Import fehlgeschlagen: \(error.localizedDescription)"
        }
    }

    private func remove(_ item: WhitelistItem) {
        do { try WhitelistRepository(context: context).remove(item) } catch { errorMessage = error.localizedDescription }
    }
}

struct WhitelistItemRow: View {
    let item: WhitelistItem

    var body: some View {
        HStack(spacing: 12) {
            Thumbnail(url: item.thumbnailUrl, isChannel: item.type == .channel)
            VStack(alignment: .leading, spacing: 3) {
                Text(item.title).lineLimit(2)
                HStack(spacing: 6) {
                    Text(item.type.label).font(.caption2.weight(.semibold)).padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Capsule().fill(Color.accentColor.opacity(0.15)))
                    if item.approvalStatus != .approved {
                        Text(item.approvalStatus.title).font(.caption2.weight(.semibold)).padding(.horizontal, 6).padding(.vertical, 2)
                            .background(Capsule().fill(Color.orange.opacity(0.2))).foregroundStyle(.orange)
                    } else if item.type == .video {
                        Text("ab \(item.ageMin)\(item.category.map { " · \($0.title)" } ?? "")").font(.caption2).foregroundStyle(.secondary)
                    }
                    if let channel = item.channelTitle { Text(channel).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
                }
            }
        }
        .padding(.vertical, 4)
    }
}

struct Thumbnail: View {
    let url: String
    var isChannel = false

    var body: some View {
        AsyncImage(url: URL(string: url)) { image in
            image.resizable().aspectRatio(contentMode: .fill)
        } placeholder: {
            Color.gray.opacity(0.2).overlay(Image(systemName: isChannel ? "person.crop.circle" : "play.rectangle").foregroundStyle(.secondary))
        }
        .frame(width: isChannel ? 48 : 80, height: 48)
        .clipShape(RoundedRectangle(cornerRadius: isChannel ? 24 : 6))
    }
}

extension YouTubeContentType {
    var label: String {
        switch self {
        case .channel: "Kanal"
        case .video: "Video"
        case .playlist: "Playlist"
        }
    }
}
