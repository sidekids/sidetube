import SwiftUI

/// Wiedergabe als eigener Screen: Video, Titel, native Steuerung, Warteschlange, Remote-Handle. Querformat = Vollbild.
struct PlayerScreen: View {
    @Environment(PlayerCoordinator.self) private var coordinator
    @Environment(RemoteController.self) private var remote
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @State private var remoteWasOpen = false
    let model: PlayerModel
    let onLock: () -> Void

    private var fullscreen: Bool { verticalSizeClass == .compact || coordinator.fullscreenRequested }

    var body: some View {
        NavigationStack {
            Group {
                if fullscreen {
                    FullscreenPlayerView(model: model, webView: coordinator.webView,
                                         onExit: verticalSizeClass == .compact ? nil : { coordinator.fullscreenRequested = false })
                        .onTapGesture(count: 2) { coordinator.fullscreenRequested.toggle() }
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
                    RecommendMenu(title: model.current.title, videoId: model.current.videoId)
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
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var portrait: some View {
        VStack(spacing: 0) {
            PlayerView(model: model, webView: coordinator.webView)
            if model.status == .ended, !model.autoAdvance {
                HStack(spacing: 12) {
                    Text("Fertig geschaut.").font(.subheadline).foregroundStyle(.secondary)
                    if model.queue.count > 1 {
                        Button("Nächstes Video", systemImage: "forward.end.fill") { model.next() }.buttonStyle(.borderedProminent)
                    }
                    Button("Zur Mediathek", systemImage: "rectangle.stack") { coordinator.close() }.buttonStyle(.bordered)
                }
                .padding(.vertical, 6)
            }
            controls
                .padding(.vertical, 8)
            ScrollViewReader { proxy in
            List {
                Section("Als Nächstes") {
                    ForEach(Array(model.queue.enumerated()), id: \.element.videoId) { index, item in
                        Button {
                            if index != model.index { model.jump(to: index) }
                        } label: {
                            HStack(spacing: KidTheme.cardSpacing) {
                                KidThumbnail(url: YouTubeIDs.defaultThumbnail(videoId: item.videoId), size: CGSize(width: 80, height: 45))
                                Text(item.title).font(.body).lineLimit(2).foregroundStyle(.primary)
                                Spacer()
                                if index == model.index {
                                    Image(systemName: "speaker.wave.2.fill").foregroundStyle(KidTheme.accent).accessibilityLabel("läuft gerade")
                                }
                            }
                        }
                        .listRowBackground(index == model.index ? KidTheme.accent.opacity(0.12) : nil)
                        .id(item.videoId)
                        .accessibilityAddTraits(index == model.index ? .isSelected : [])
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
        .kidRemoteHandle()
    }

    private var controls: some View {
        HStack(spacing: 24) {
            control("backward.end.fill", "Voriges Video") { model.previous() }
            control(model.status == .playing ? "pause.fill" : "play.fill", model.status == .playing ? "Pause" : "Abspielen", large: true) { model.togglePlayback() }
            control("forward.end.fill", "Nächstes Video") { model.next() }
            // Vollbild auch ohne Drehen – hilft, wenn die Drehsperre des iPhones an ist.
            control("arrow.up.left.and.arrow.down.right", "Vollbild", identifier: "player.fullscreenToggle") {
                coordinator.fullscreenRequested = true
            }
        }
        .frame(maxWidth: .infinity)
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
        .accessibilityIdentifier(identifier ?? "")
    }
}
