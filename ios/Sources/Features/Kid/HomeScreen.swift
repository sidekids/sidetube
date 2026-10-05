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
    @State private var newEpisodes: NewEpisodesModel?
    @State private var showWishes = false
   /// Inhalt aus „Meine Wünsche"; geöffnet erst, wenn das Blatt zu ist (sonst kollidiert es mit dem Player-Cover).
    @State private var openAfterWishes: KidRow?
    let onLock: () -> Void
    let onShowAllChannels: () -> Void

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if let model, let profile = kidSession.activeProfile {
                    content(model: model, profile: profile)
                } else {
                    KidEmptyState(systemImage: "tv", title: "Noch keine Videos",
                                  message: "Deine Eltern richten SideTube erst noch ein.")
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
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if kidSession.activeProfile != nil { MyWishesButton { showWishes = true } }
                    RemoteToolbarButton { remote.isPresented = true }
                    ParentControlButton(action: onLock)
                }
            }
            .navigationDestination(for: KidScreenFactory.self) { DetailScreen(factory: $0, onLock: onLock) }
            .kidRemoteHandle()
        }
        .sheet(isPresented: $showWishes, onDismiss: {
            newEpisodes?.refreshWishes()
            if let row = openAfterWishes { openAfterWishes = nil; openWished(row) }
        }) {
            if let profile = kidSession.activeProfile {
                MyWishesView(profile: profile, context: kidContext(profile)) { row in openAfterWishes = row }
            }
        }
        // „Neu bei deinen Kanälen": Netz nur für Feeds der Kanäle mit Stufe „Vertrauenswürdige Reihe".
        .task(id: kidSession.activeProfile?.id) {
            guard let profile = kidSession.activeProfile else { newEpisodes = nil; return }
            let loader = NewEpisodesModel(profile: profile, context: modelContext, youtube: services.youtube,
                                          notifier: services.parentNotifier)
            await loader.load()
            if kidSession.activeProfile?.id == profile.id { newEpisodes = loader }
        }
        .onAppear { rebuild(); register() }
        .onChange(of: kidSession.activeProfile?.id) { _, _ in rebuild() }
        .onChange(of: path.count) { _, count in if count == 0 { register() } }
        // Nach jeder Wiedergabe steht der Verlauf („Weiterschauen“) neu – einmal lesen, nicht bei jedem Body.
        .onChange(of: playerCoordinator.isPresented) { _, presented in if !presented { model?.refresh() } }
    }

    private func content(model: HomeModel, profile: KidProfile) -> some View {
        let channels = model.cards
        let recent = model.rows
        let lead = model.lead
        let ids = model.allItems.map(\.id)
        return ScrollViewReader { proxy in
        ScrollView {
            VStack(alignment: .leading, spacing: KidTheme.sectionSpacing) {
                // Reihenfolge wie die Radauswahl (Weiterschauen · Kanäle · Zuletzt geschaut): das
                // Angefangene steht oben, damit die häufigste Entscheidung die erste ist.
                if let lead, case .play = lead.action {
                    section("Weiterschauen") {
                        ContinueWatchingCard(row: lead, isSelected: remote.isSelected(model, index: 0)) { activate(lead, model: model) }
                            .padding(.horizontal, KidTheme.outerPadding)
                            .id(lead.id)
                    }
                }
                section("Kanäle", trailing: channels.isEmpty ? nil : ("Alle", onShowAllChannels)) {
                    if channels.isEmpty {
                        KidEmptyState(systemImage: "person.crop.rectangle.stack", title: "Noch keine Kanäle",
                                      message: "Deine Eltern können hier Kanäle für dich aussuchen.")
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
                if let newEpisodes {
                    NewEpisodesSection(model: newEpisodes)
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
                section("Zuletzt geschaut") {
                    if lead == nil && recent.isEmpty {
                        KidEmptyState(systemImage: "clock", title: "Noch nichts geschaut",
                                      message: "Was du anschaust, erscheint hier zum Wiederfinden.")
                    } else {
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
            .padding(.top, 8)
            .padding(.bottom, KidTheme.sectionSpacing)
        }
        .background(Color(.systemGroupedBackground))
        .remoteAutoScroll(model: model, proxy: proxy, ids: ids)
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
                        .accessibilityLabel("Alle Kanäle anzeigen")
                }
            }
            .padding(.horizontal, KidTheme.outerPadding)
            content()
        }
    }

    /// Inhalt aus „Meine Wünsche": ein Video spielt allein (es steht in keiner Liste dieses Bildschirms).
    private func openWished(_ row: KidRow) {
        switch row.action {
        case .push(let factory): path.append(factory)
        case .play: playerCoordinator.play(row, in: [row], profile: kidSession.activeProfile, context: modelContext)
        case .none: break
        }
    }

    private func rebuild() {
        kidSession.resolve(from: profiles)
        guard let profile = kidSession.activeProfile else { model = nil; return }
        model = HomeModel(profile: profile, tab: .channels, context: kidContext(profile))
        register()
    }

    private func kidContext(_ profile: KidProfile) -> KidContext {
        KidContext(modelContext: modelContext, youtube: services.youtube, resolver: services.resolver, profile: profile)
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
