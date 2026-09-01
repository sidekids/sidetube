// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Mediathek = Browsen: Kanäle | Videos | Playlists als Segment, Inhalte im Raster.
struct LibraryScreen: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(AppServices.self) private var services
    @Environment(KidSession.self) private var kidSession
    @Environment(RemoteController.self) private var remote
    @Environment(PlayerCoordinator.self) private var playerCoordinator
    @Binding var segment: YouTubeContentType
    @State private var path = NavigationPath()
    @State private var model: LibraryModel?
    let onLock: () -> Void

    private let columns = [GridItem(.adaptive(minimum: 150), spacing: KidTheme.cardSpacing)]
    private let channelColumns = [GridItem(.adaptive(minimum: 96), spacing: KidTheme.cardSpacing)]

    var body: some View {
        NavigationStack(path: $path) {
            VStack(spacing: 0) {
                Picker("Bereich", selection: $segment) {
                    Text("Kanäle").tag(YouTubeContentType.channel)
                    Text("Videos").tag(YouTubeContentType.video)
                    Text("Playlists").tag(YouTubeContentType.playlist)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, KidTheme.outerPadding).padding(.vertical, 8)
                if let model {
                    grid(model: model)
                } else {
                    KidEmptyState(systemImage: "tv", title: "Noch keine Inhalte", message: "Eltern legen im Elternbereich ein Profil an.")
                    Spacer()
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Mediathek")
            .toolbar { ToolbarItem(placement: .topBarTrailing) { ParentControlButton(action: onLock) } }
            .navigationDestination(for: KidScreenFactory.self) { DetailScreen(factory: $0, onLock: onLock) }
            .kidRemoteHandle()
        }
        .onAppear { rebuild() }
        .onChange(of: segment) { _, _ in rebuild() }
        .onChange(of: kidSession.activeProfile?.id) { _, _ in rebuild() }
        .onChange(of: path.count) { _, count in if count == 0 { register() } }
    }

    private func grid(model: LibraryModel) -> some View {
        let items = model.cards
        return ScrollViewReader { proxy in
        ScrollView {
            if items.isEmpty {
                KidEmptyState(systemImage: segment == .channel ? "person.crop.rectangle.stack" : (segment == .video ? "play.rectangle" : "list.and.film"),
                              title: "Noch keine \(segment.pluralLabel)",
                              message: "Eltern können im Elternbereich \(segment.pluralLabel) per Link freigeben.")
            } else {
                LazyVGrid(columns: segment == .channel ? channelColumns : columns, alignment: .leading, spacing: KidTheme.cardSpacing) {
                    ForEach(Array(items.enumerated()), id: \.element.id) { index, row in
                        Group {
                            if segment == .channel {
                                ChannelAvatar(row: row, isSelected: remote.isSelected(model, index: index)) { activate(row, model: model) }
                            } else {
                                LibraryTile(row: row, isSelected: remote.isSelected(model, index: index)) { activate(row, model: model) }
                            }
                        }
                        .id(row.id)
                    }
                }
                .padding(.horizontal, KidTheme.outerPadding).padding(.vertical, 8)
            }
        }
        .remoteAutoScroll(model: model, proxy: proxy, ids: items.map(\.id))
        }
    }

    private func rebuild() {
        guard let profile = kidSession.activeProfile else { model = nil; return }
        model = LibraryModel(profile: profile, type: segment, context: KidContext(modelContext: modelContext, youtube: services.youtube, resolver: services.resolver))
        register()
    }

    private func register() {
        guard let model else { return }
        remote.target = RemoteTargetBinding(model: model) { row in activate(row, model: model) }
    }

    private func activate(_ row: KidRow, model: LibraryModel) {
        switch row.action {
        case .push(let factory): path.append(factory)
        case .play: playerCoordinator.play(row, in: model.allItems, profile: kidSession.activeProfile, context: modelContext)
        case .none: break
        }
    }
}

/// Kachel für Videos/Playlists im Raster.
struct LibraryTile: View {
    let row: KidRow
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 6) {
                GeometryReader { proxy in
                    KidThumbnail(url: row.thumbnailUrl, systemImage: row.systemImage, size: CGSize(width: proxy.size.width, height: proxy.size.width * 9 / 16))
                }
                .aspectRatio(16 / 9, contentMode: .fit)
                Text(row.title).font(.subheadline).lineLimit(2).multilineTextAlignment(.leading).foregroundStyle(.primary)
                if let subtitle = row.subtitle { Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
            }
            .padding(6)
        }
        .buttonStyle(.plain)
        .remoteSelected(isSelected)
        .recommendContextMenu(for: row)
        .accessibilityLabel([row.title, row.subtitle].compactMap { $0 }.joined(separator: ", "))
    }
}

/// Inhalte eines Bereichs (nur Karten) – Radziel der Mediathek.
@Observable
final class LibraryModel: KidScreenModel {
    let id: String
    let title: String
    let menu = WheelMenuModel(count: 0)
    private let profile: KidProfile
    private let type: YouTubeContentType
    private let context: KidContext

    init(profile: KidProfile, type: YouTubeContentType, context: KidContext) {
        self.profile = profile
        self.type = type
        self.context = context
        id = "library-\(type.rawValue)-\(profile.id)"
        title = type.pluralLabel
        syncMenuCount()
    }

    var cards: [KidRow] {
        let items = WhitelistRepository(context: context.modelContext).visibleItems(of: profile, type: type).map { KidRows.row(for: $0, context: context) }
        menu.setCount(items.count)
        return items
    }

    var rows: [KidRow] { [] }
}

extension YouTubeContentType {
    var pluralLabel: String {
        switch self {
        case .channel: "Kanäle"
        case .video: "Videos"
        case .playlist: "Playlists"
        }
    }
}

/// Kanal- oder Playlist-Detail: Kopf, Liste der Videos, Nachladen am Ende; Kanäle mit Suche.
struct DetailScreen: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(KidSession.self) private var kidSession
    @Environment(RemoteController.self) private var remote
    @Environment(PlayerCoordinator.self) private var playerCoordinator
    @State private var model: any KidScreenModel
    @State private var searchText = ""
    let onLock: () -> Void

    init(factory: KidScreenFactory, onLock: @escaping () -> Void) {
        _model = State(initialValue: factory.make())
        self.onLock = onLock
    }

    var body: some View {
        let rows = model.rows
        ScrollViewReader { proxy in
        List {
            if let hero = model.hero {
                HStack(spacing: KidTheme.cardSpacing) {
                    KidThumbnail(url: hero.thumbnailUrl, style: hero.style, systemImage: hero.systemImage,
                                 size: hero.style == .avatar ? CGSize(width: 64, height: 64) : CGSize(width: 112, height: 63))
                    VStack(alignment: .leading, spacing: 3) {
                        Text(hero.title).font(.headline).lineLimit(2)
                        if let subtitle = hero.subtitle { Text(subtitle).font(.subheadline).foregroundStyle(.secondary) }
                    }
                }
                .listRowBackground(Color.clear)
                .accessibilityElement(children: .combine)
            }
            if rows.isEmpty, !model.isLoading {
                KidEmptyState(systemImage: "play.rectangle", title: searchText.isEmpty ? "Noch keine Videos" : "Keine Treffer",
                              message: model.footerHint ?? (searchText.isEmpty ? "Hier ist gerade nichts zu sehen." : "Probiere ein anderes Wort."))
                    .listRowBackground(Color.clear)
            }
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                RecentVideoRow(row: row, isSelected: remote.isSelected(model, index: index), showsSubtitle: false) { activate(row) }
                    .id(row.id)
                    .listRowInsets(EdgeInsets(top: 2, leading: 8, bottom: 2, trailing: 8))
                    .listRowSeparator(.hidden)
                    .onAppear { if index >= rows.count - 3 { model.onSelectionChanged(index: rows.count - 1) } }
            }
            if model.isLoading {
                HStack { Spacer(); ProgressView(); Spacer() }.listRowBackground(Color.clear)
            } else if let hint = model.footerHint, !rows.isEmpty {
                Text(hint).font(.footnote).foregroundStyle(.secondary).frame(maxWidth: .infinity).listRowBackground(Color.clear)
            }
        }
        .listStyle(.plain)
        .remoteAutoScroll(model: model, proxy: proxy, ids: rows.map(\.id))
        }
        .navigationTitle(model.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { ParentControlButton(action: onLock) } }
        .modifier(OptionalSearchable(enabled: model.supportsSearch, text: $searchText, prompt: "Im Kanal suchen"))
        .onChange(of: searchText) { _, text in model.searchText = text }
        .task { await model.onAppear() }
        .onAppear { remote.target = RemoteTargetBinding(model: model) { row in activate(row) } }
        .kidRemoteHandle()
    }

    private func activate(_ row: KidRow) {
        if case .play = row.action {
            playerCoordinator.play(row, in: model.allItems, profile: kidSession.activeProfile, context: modelContext)
        }
    }
}

private struct OptionalSearchable: ViewModifier {
    let enabled: Bool
    @Binding var text: String
    let prompt: String

    func body(content: Content) -> some View {
        if enabled {
            content.searchable(text: $text, placement: .navigationBarDrawer(displayMode: .always), prompt: prompt)
        } else {
            content
        }
    }
}

/// Remote-Handle als Safe-Area-Einsatz über der Tab-Leiste (erscheint in jedem Tab, nicht bei geöffneter Fernbedienung).
/// `hidden` blendet ihn aus, solange die Tastatur unten steht – zwei Leisten übereinander wären nur im Weg.
struct KidRemoteHandleModifier: ViewModifier {
    @Environment(RemoteController.self) private var remote
    var hidden = false

    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom, spacing: 0) {
            if remote.isPresented {
                Color.clear.frame(height: remote.sheetInset)   // Platz, damit die Radauswahl ueber dem Sheet sichtbar bleibt
            } else if !hidden {
                RemoteHandle { remote.isPresented = true }
            }
        }
    }
}

extension View {
    func kidRemoteHandle(hidden: Bool = false) -> some View { modifier(KidRemoteHandleModifier(hidden: hidden)) }
}

/// Scrollt die Radauswahl ins Bild (oberes Drittel), solange die Fernbedienung offen ist – so ist immer sichtbar, wo man steht.
struct RemoteAutoScrollModifier: ViewModifier {
    @Environment(RemoteController.self) private var remote
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let model: any KidScreenModel
    let proxy: ScrollViewProxy
   /// IDs in Auswahlreihenfolge (Index der Radauswahl → Element-ID).
    let ids: [String]

    func body(content: Content) -> some View {
        content.onChange(of: model.menu.selectedIndex) { _, index in
            guard remote.isPresented, index >= 0, index < ids.count else { return }
            withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) {
                proxy.scrollTo(ids[index], anchor: UnitPoint(x: 0.5, y: 0.3))
            }
        }
    }
}

extension View {
    func remoteAutoScroll(model: any KidScreenModel, proxy: ScrollViewProxy, ids: [String]) -> some View {
        modifier(RemoteAutoScrollModifier(model: model, proxy: proxy, ids: ids))
    }
}
