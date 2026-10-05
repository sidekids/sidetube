// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Wiedergabe als eigener Screen: Video, Titel, native Steuerung, Warteschlange, Remote-Handle. Querformat = Vollbild.
/// Am Videoende zeigt SideTube einen eigenen „Fertig“-Zustand mit höchstens drei freigegebenen Vorschlägen –
/// der YouTube-Endscreen wird vorher gestoppt (siehe `PlayerModel`, `NextUpPolicy`, docs/PLAYER_SECURITY.md).
struct PlayerScreen: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(AppServices.self) private var services
    @Environment(KidSession.self) private var kidSession
    @Environment(PlayerCoordinator.self) private var coordinator
    @Environment(RemoteController.self) private var remote
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var remoteWasOpen = false
   /// Vorschläge für den „Fertig“-Zustand; werden einmal pro Videoende berechnet, nie im Body.
    @State private var nextUp: [NextUpPolicy.Candidate] = []
   /// Rückmeldung nach „Mehr davon" (ADR 0001, Weg 2).
    @State private var wishFeedback: String?
    let model: PlayerModel
    let onLock: () -> Void

    private var fullscreen: Bool { verticalSizeClass == .compact || coordinator.fullscreenRequested }
    private var isEnded: Bool { model.status == .ended }

    var body: some View {
        NavigationStack {
            Group {
                if fullscreen {
                    ZStack {
                        FullscreenPlayerView(model: model, webView: coordinator.webView,
                                             onExit: verticalSizeClass == .compact ? nil : { coordinator.fullscreenRequested = false })
                            .onTapGesture(count: 2) { coordinator.fullscreenRequested.toggle() }
                        if isEnded {
                            ScrollView {
                                endedPanel(compact: true)
                                    .padding(20)
                            }
                            .scrollBounceBehavior(.basedOnSize)
                            .frame(maxWidth: 520)
                            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                            .padding(24)
                        } else {
                            // Ohne YouTubes Leiste braucht auch das Vollbild eine Bedienung –
                            // sonst ließe sich dort weder pausieren noch spulen.
                            VStack {
                                Spacer()
                                controls
                                    .padding(.vertical, 8)
                                    .background(.ultraThinMaterial)
                            }
                            .ignoresSafeArea(edges: .bottom)
                        }
                    }
                } else {
                    portrait
                }
            }
            .toolbar(fullscreen ? .hidden : .visible, for: .navigationBar)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Fertig", systemImage: "chevron.down") { coordinator.close() }
                        .accessibilityIdentifier("player.close")
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    if kidSession.activeProfile != nil {
                        Button("Mehr davon", systemImage: "star", action: wishMoreLikeThis)
                            .accessibilityHint("Wunsch an die Eltern: mehr wie dieses Video")
                            .accessibilityIdentifier("wish.moreLikeThis")
                    }
                    RecommendMenu(title: model.current.title, videoId: model.current.videoId)
                    RemoteToolbarButton { remote.isPresented = true }
                    ParentControlButton(action: onLock)
                }
            }
        }
        // Im Querformat legen sich Sheets auf dem iPhone über den ganzen Schirm – die Fernbedienung
        // würde also das Vollbild verdecken. Sie tritt zur Seite und kommt im Hochformat zurück.
        .onChange(of: fullscreen) { _, isFullscreen in
            if isFullscreen {
                if remote.isPresented { remoteWasOpen = true }
                remote.isPresented = false
            } else if remoteWasOpen {
                remoteWasOpen = false
                remote.isPresented = true
            }
        }
        .onChange(of: model.status, initial: true) { _, status in
            if status == .ended { nextUp = computeNextUp() }
        }
        .alert("Mehr davon", isPresented: Binding(get: { wishFeedback != nil }, set: { if !$0 { wishFeedback = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(wishFeedback ?? "") }
    }

    private var moreLikeThisAction: (() -> Void)? {
        guard kidSession.activeProfile != nil else { return nil }
        return { wishMoreLikeThis() }
    }

    private func wishMoreLikeThis() {
        guard let profile = kidSession.activeProfile else { return }
        wishFeedback = WishFeedback.submit(MoreLikeThis.draft(for: model.current, profile: profile), profile: profile, context: modelContext,
                                       notifier: services.parentNotifier)
    }

    private var portrait: some View {
        VStack(spacing: 0) {
            PlayerView(model: model, webView: coordinator.webView)
            if isEnded {
                ScrollView {
                    endedPanel(compact: false)
                        .padding(.horizontal, KidTheme.outerPadding)
                        .padding(.vertical, 12)
                }
            } else {
                controls
                    .padding(.vertical, 8)
                queueList
            }
        }
        .kidRemoteHandle()
    }

    private var queueList: some View {
        ScrollViewReader { proxy in
            List {
                Section("Als Nächstes") {
                    ForEach(Array(model.queue.enumerated()).filter { $0.offset != model.index }, id: \.element.videoId) { index, item in
                        Button {
                            guard let profile = kidSession.activeProfile else { return }
                            _ = coordinator.recommendedVideoTapped(item, profile: profile, context: modelContext)
                        } label: {
                            HStack(spacing: KidTheme.cardSpacing) {
                                KidThumbnail(url: item.thumbnailURL ?? YouTubeIDs.defaultThumbnail(videoId: item.videoId), size: CGSize(width: 80, height: 45))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.title).font(.body).lineLimit(2).foregroundStyle(.primary)
                                    if let channelTitle = item.channelTitle {
                                        Text(channelTitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                    }
                                }
                                Spacer()
                            }
                        }
                        .id(item.videoId)
                        .accessibilityIdentifier("recommendation.\(item.videoId)")
                    }
                }
            }
            .listStyle(.insetGrouped)
            // Warteschlange folgt dem laufenden Video (Rad ⏮/⏭, Auto-Weiter) – so ist immer sichtbar, wo man ist.
            .onChange(of: model.index, initial: true) { _, index in
                guard index < model.queue.count else { return }
                withAnimation(reduceMotion ? nil : .easeOut(duration: 0.2)) {
                    proxy.scrollTo(model.queue[index].videoId, anchor: UnitPoint(x: 0.5, y: 0.25))
                }
            }
        }
    }

   // MARK: „Fertig“-Zustand

    private func endedPanel(compact: Bool) -> some View {
        PlayerEndedView(nextUp: nextUp, compact: compact,
                        onPlay: { candidate in model.play(PlayerModel.Item(videoId: candidate.videoId, title: candidate.title)) },
                        onReplay: { model.replay() },
                        onClose: { coordinator.close() },
                        onMoreLikeThis: moreLikeThisAction)
    }

   /// Nur Videos, die die Freigabelogik passiert haben, und nur solche, die die laufende Engine abspielen kann.
    private func computeNextUp() -> [NextUpPolicy.Candidate] {
        let wantsPeerTube = coordinator.usesPeerTube
        let queue = model.queue.filter { PeerTubeIDs.isPeerTube($0.videoId) == wantsPeerTube }
        let currentIndex = queue.firstIndex { $0.videoId == model.current.videoId } ?? 0
        guard let profile = kidSession.activeProfile else {
            return NextUpPolicy.suggestions(queue: queue, currentIndex: currentIndex, approved: [])
        }
        let visible = WhitelistRepository(context: modelContext).visibleItems(of: profile, type: .video)
            .filter { $0.provider.isPlayable && PeerTubeIDs.isPeerTube($0.youtubeId) == wantsPeerTube }
        let current = visible.first { $0.youtubeId == model.current.videoId }
        return NextUpPolicy.suggestions(queue: queue, currentIndex: currentIndex,
                                        approved: visible.map { NextUpPolicy.Candidate(item: $0) },
                                        currentChannelId: current?.sourceChannelId, currentChannelTitle: current?.channelTitle)
    }

   // MARK: Steuerung

   /// Eigene Steuerung statt YouTubes Leiste (`controls: 0` in `Player.html`): breiter Balken zum
   /// Spulen, große Ziele, keine Verweise nach draußen und beim Pausieren keine fremden Vorschläge.
    private var controls: some View {
        VStack(spacing: 4) {
            PlayerScrubBar(model: model)
            HStack(spacing: 18) {
                control("backward.end.fill", "Voriges Video") { model.previous() }
                control("gobackward.10", "10 Sekunden zurück") { model.seek(by: -10) }
                control(model.status == .playing ? "pause.fill" : "play.fill", model.status == .playing ? "Pause" : "Abspielen", large: true) { model.togglePlayback() }
                control("goforward.10", "10 Sekunden vor") { model.seek(by: 10) }
                control("forward.end.fill", "Nächstes Video") { model.next() }
                if model.captionsAvailable {
                    control(model.captionsEnabled ? "captions.bubble.fill" : "captions.bubble",
                            model.captionsEnabled ? "Untertitel ausschalten" : "Untertitel einschalten",
                            identifier: "player.captions") { model.toggleCaptions() }
                }
                // Vollbild auch ohne Drehen – hilft, wenn die Drehsperre des iPhones an ist.
                control("arrow.up.left.and.arrow.down.right", "Vollbild", identifier: "player.fullscreenToggle") {
                    coordinator.fullscreenRequested = true
                }
            }
            .frame(maxWidth: .infinity)
        }
    }

    private func control(_ symbol: String, _ label: String, large: Bool = false, identifier: String? = nil,
                         action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(large ? .largeTitle : .title2)
                .frame(width: large ? 64 : KidTheme.minimumTouchTarget, height: large ? 64 : KidTheme.minimumTouchTarget)
        }
        .tint(large ? KidTheme.accent : .primary)
        .accessibilityLabel(label)
        .accessibilityIdentifier(identifier ?? "player.\(symbol)")
    }
}

/// Fortschrittsbalken: zeigt, wie weit das Video ist, und lässt an jede Stelle springen.
/// Während des Ziehens führt der Finger, sonst der Player – sonst springt der Griff zurück.
struct PlayerScrubBar: View {
    let model: PlayerModel
    @State private var dragging = false
    @State private var draggedSeconds: Double = 0

    var body: some View {
        if model.durationSeconds > 0 {
            VStack(spacing: 0) {
                Slider(value: Binding(
                    get: { dragging ? draggedSeconds : Double(model.currentSeconds) },
                    set: { dragging = true; draggedSeconds = $0 }
                ), in: 0...Double(model.durationSeconds)) { editing in
                    if !editing {
                        dragging = false
                        model.seek(to: draggedSeconds)
                    }
                }
                .tint(KidTheme.accent)
                .frame(minHeight: KidTheme.minimumTouchTarget)
                .accessibilityLabel("Stelle im Video")
                .accessibilityValue(Self.time(dragging ? Int(draggedSeconds) : model.currentSeconds))
                .accessibilityIdentifier("player.scrub")
                HStack {
                    Text(Self.time(dragging ? Int(draggedSeconds) : model.currentSeconds))
                    Spacer()
                    Text(Self.time(model.durationSeconds))
                }
                .font(.caption).foregroundStyle(.secondary).monospacedDigit()
            }
            .padding(.horizontal, KidTheme.outerPadding)
        }
    }

   /// „3:07“ – Minuten und Sekunden, wie sie ein Kind von jedem Player kennt.
    static func time(_ seconds: Int) -> String {
        let safe = max(0, seconds)
        let hours = safe / 3600, minutes = (safe % 3600) / 60, secs = safe % 60
        return hours > 0 ? String(format: "%d:%02d:%02d", hours, minutes, secs) : String(format: "%d:%02d", minutes, secs)
    }
}

/// „Fertig 🎉 – Als Nächstes“: eine Aufgabe, drei Karten, zwei klare Wege (Nochmal, Zurück). Keine Sackgasse,
/// kein Endlos-Raster. Identisch auf Android (`VideoEndedPanel`).
struct PlayerEndedView: View {
    let nextUp: [NextUpPolicy.Candidate]
    var compact = false
    let onPlay: (NextUpPolicy.Candidate) -> Void
    let onReplay: () -> Void
    let onClose: () -> Void
    /// „Mehr davon" – Wunsch an die Eltern (ADR 0001). Ohne Profil keine Schaltfläche.
    var onMoreLikeThis: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 8) {
                Text("Fertig").font(compact ? .title2.bold() : .title.bold())
                Text("🎉").font(compact ? .title2 : .title).accessibilityHidden(true)
            }
            .accessibilityAddTraits(.isHeader)
            if !nextUp.isEmpty {
                Text("Als Nächstes").font(.headline).foregroundStyle(.secondary).accessibilityAddTraits(.isHeader)
                VStack(spacing: 8) {
                    ForEach(nextUp) { candidate in
                        Button { onPlay(candidate) } label: {
                            HStack(spacing: KidTheme.cardSpacing) {
                                KidThumbnail(url: candidate.thumbnailUrl, size: compact ? CGSize(width: 80, height: 45) : CGSize(width: 96, height: 54))
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(candidate.title).font(.body).lineLimit(2).multilineTextAlignment(.leading).foregroundStyle(.primary)
                                    if let channel = candidate.channelTitle, !channel.isEmpty {
                                        Text(channel).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                                    }
                                }
                                Spacer(minLength: 8)
                                Image(systemName: "play.fill").font(.title3).foregroundStyle(.primary)   // Hinweis, kein ACTIVE
                                    .frame(width: KidTheme.minimumTouchTarget, height: KidTheme.minimumTouchTarget)
                            }
                            .padding(10)
                            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color(.secondarySystemGroupedBackground)))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Abspielen: \(candidate.title)\(candidate.channelTitle.map { ", \($0)" } ?? "")")
                        .accessibilityAddTraits(.startsMediaSession)
                        .accessibilityIdentifier("player.nextUp.\(candidate.videoId)")
                    }
                }
            }
            HStack(spacing: 12) {
                Button("Nochmal", systemImage: "arrow.counterclockwise", action: onReplay)
                    .accessibilityIdentifier("player.replay")
                Button("Zurück zu den Videos", systemImage: "chevron.down", action: onClose)
                    .accessibilityIdentifier("player.ended.close")
            }
            .buttonStyle(.bordered)
            .controlSize(.large)
            .frame(maxWidth: .infinity, alignment: .leading)
            if let onMoreLikeThis {
                Button("Mehr davon wünschen", systemImage: "star", action: onMoreLikeThis)
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                    .tint(KidTheme.accent)
                    .accessibilityIdentifier("player.ended.moreLikeThis")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.ended")
    }
}
