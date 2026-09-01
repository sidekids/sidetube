// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Home: schnelle Entscheidungen – Kanäle (Carousel) und eine einzige Verlaufsliste.
/// Angefangenes steht als Karte obenauf, Älteres darunter – nicht zweimal derselbe Titel.
struct HomeScreen: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(AppServices.self) private var services
    @Environment(KidSession.self) private var kidSession
    @Environment(RemoteController.self) private var remote
    @Environment(PlayerCoordinator.self) private var playerCoordinator
    @Query(sort: \KidProfile.createdAt) private var profiles: [KidProfile]
    @State private var path = NavigationPath()
    @State private var model: HomeModel?
    let onLock: () -> Void
    let onShowAllChannels: () -> Void

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if let model, let profile = kidSession.activeProfile {
                    content(model: model, profile: profile)
                } else {
                    KidEmptyState(systemImage: "tv", title: "Noch keine Inhalte",
                                  message: "Eltern legen im Elternbereich ein Profil und eine Whitelist an.")
                }
            }
            .navigationTitle(kidSession.activeProfile?.name ?? "Start")
            .toolbar {
                if profiles.count > 1, let active = kidSession.activeProfile {
                    ToolbarItem(placement: .topBarLeading) {
                        Menu {
                            ForEach(profiles) { profile in
                                Button { kidSession.select(profile); rebuild() } label: {
                                    Label(profile.name, systemImage: profile.id == active.id ? "checkmark" : "person")
                                }
                            }
                        } label: {
                            Label(active.name, systemImage: "person.crop.circle")
                        }
                        .accessibilityLabel("Profil wechseln, aktuell \(active.name)")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) { ParentControlButton(action: onLock) }
            }
            .navigationDestination(for: KidScreenFactory.self) { DetailScreen(factory: $0, onLock: onLock) }
            .kidRemoteHandle()
        }
        .onAppear { rebuild(); register() }
        .onChange(of: kidSession.activeProfile?.id) { _, _ in rebuild() }
        .onChange(of: path.count) { _, count in if count == 0 { register() } }
    }

    private func content(model: HomeModel, profile: KidProfile) -> some View {
        let context = KidContext(modelContext: modelContext, youtube: services.youtube, resolver: services.resolver)
        let channels = model.cards
        let recent = model.rows
        let lead = model.lead
        let ids = model.allItems.map(\.id)
        return ScrollViewReader { proxy in
        ScrollView {
            VStack(alignment: .leading, spacing: KidTheme.sectionSpacing) {
                section("Kanäle", trailing: channels.isEmpty ? nil : ("Alle", onShowAllChannels)) {
                    if channels.isEmpty {
                        KidEmptyState(systemImage: "person.crop.rectangle.stack", title: "Noch keine Kanäle",
                                      message: "Eltern können im Elternbereich Kanäle freigeben.")
                    } else {
                        ScrollViewReader { rowProxy in
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(alignment: .top, spacing: KidTheme.cardSpacing) {
                                    ForEach(Array(channels.enumerated()), id: \.element.id) { index, channel in
                                        ChannelAvatar(row: channel, isSelected: remote.isSelected(model, index: index + (lead == nil ? 0 : 1))) {
                                            activate(channel, model: model)
                                        }
                                        .id(channel.id)
                                    }
                                }
                                .padding(.horizontal, KidTheme.outerPadding - 6)
                            }
                            .id("channels")
                            .remoteAutoScroll(model: model, proxy: rowProxy, ids: ids)
                        }
                    }
                }
                ForEach(model.categorySections, id: \.category) { entry in
                    section(entry.category.title) {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(alignment: .top, spacing: KidTheme.cardSpacing) {
                                ForEach(entry.rows) { row in
                                    LibraryTile(row: row, isSelected: false) { activate(row, model: model) }
                                        .frame(width: 180)
                                }
                            }
                            .padding(.horizontal, KidTheme.outerPadding - 6)
                        }
                    }
                }
                // Eine Liste statt zwei: Angefangenes als Karte, darunter das ältere Geschaute.
                section(lead == nil ? "Zuletzt geschaut" : "Weiterschauen") {
                    if lead == nil && recent.isEmpty {
                        KidEmptyState(systemImage: "clock", title: "Noch nichts geschaut",
                                      message: "Was du anschaust, erscheint hier zum Wiederfinden.")
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            if let lead, case .play = lead.action {
                                ContinueWatchingCard(row: lead, isSelected: remote.isSelected(model, index: 0)) { activate(lead, model: model) }
                                    .padding(.horizontal, KidTheme.outerPadding)
                                    .id(lead.id)
                            }
                            LazyVStack(spacing: 2) {
                                ForEach(Array(recent.enumerated()), id: \.element.id) { index, row in
                                    RecentVideoRow(row: row, isSelected: remote.isSelected(model, index: model.rowsStartIndex + index)) {
                                        activate(row, model: model)
                                    }
                                    .id(row.id)
                                }
                            }
                            .padding(.horizontal, KidTheme.outerPadding - 8)
                        }
                    }
                }
            }
            .padding(.top, 8)
            .padding(.bottom, KidTheme.sectionSpacing)
        }
        .background(Color(.systemGroupedBackground))
        .remoteAutoScroll(model: model, proxy: proxy, ids: ids)
        .task(id: profile.id) { _ = context }
        }
    }

    private func section<Content: View>(_ title: String, trailing: (String, () -> Void)? = nil, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text(LocalizedStringKey(title)).font(.title3.weight(.semibold)).accessibilityAddTraits(.isHeader)
                Spacer()
                if let trailing {
                    Button(LocalizedStringKey(trailing.0), action: trailing.1).font(.subheadline).tint(KidTheme.accent)
                        .frame(minHeight: KidTheme.minimumTouchTarget)
                }
            }
            .padding(.horizontal, KidTheme.outerPadding)
            content()
        }
    }

    private func rebuild() {
        kidSession.resolve(from: profiles)
        guard let profile = kidSession.activeProfile else { model = nil; return }
        model = HomeModel(profile: profile, tab: .channels, context: KidContext(modelContext: modelContext, youtube: services.youtube, resolver: services.resolver))
        register()
    }

    private func register() {
        guard let model else { return }
        remote.target = RemoteTargetBinding(model: model) { row in activate(row, model: model) }
    }

    private func activate(_ row: KidRow, model: HomeModel) {
        switch row.action {
        case .push(let factory): path.append(factory)
        case .play: playerCoordinator.play(row, in: model.allItems, profile: kidSession.activeProfile, context: modelContext)
        case .none: break
        }
    }
}
