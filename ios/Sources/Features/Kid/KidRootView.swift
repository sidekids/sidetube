// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Kindermodus: native Tabs Home · Mediathek · Suche; Fernbedienung als Bottom Sheet; Player als Full-Screen Cover.
/// Überwacht sekündlich Schlaf-Timer und Tageslimit (FR-09/FR-10) und legt Overlays darüber, die nur die Eltern-PIN entfernt.
struct KidRootView: View {
    enum Tab: Hashable { case home, library, search }

    @Environment(\.modelContext) private var modelContext
    @Environment(AppServices.self) private var services
    @Environment(SessionState.self) private var session
    @Query(sort: \KidProfile.createdAt) private var profiles: [KidProfile]
    @State private var kidSession = KidSession()
    @State private var remote = RemoteController()
    @State private var playerCoordinator = PlayerCoordinator()
    @State private var tab: Tab = .home
    @State private var librarySegment: YouTubeContentType = .channel
    @State private var overlay: KidOverlay?
    @State private var showOverlayPIN = false
    @State private var now = Date()
    @State private var bedtime: BedtimeState = .off
    #if DEBUG
    /// Nur für die erzwungene Ruhezeit im Test: merkt die Eltern-Ausnahme, ohne von der Uhrzeit abzuhängen.
    @State private var devBedtimeSkipped = false
    #endif
    let onLockTapped: () -> Void
    let onParentUnlocked: () -> Void

    var body: some View {
        ZStack {
            TabView(selection: $tab) {
                HomeScreen(onLock: onLockTapped, onShowAllChannels: { librarySegment = .channel; tab = .library })
                    .tabItem { Label("Start", systemImage: "house") }
                    .tag(Tab.home)
                LibraryScreen(segment: $librarySegment, onLock: onLockTapped)
                    .tabItem { Label("Mediathek", systemImage: "rectangle.stack") }
                    .tag(Tab.library)
                SearchScreen(onLock: onLockTapped)
                    .tabItem { Label("Suche", systemImage: "magnifyingglass") }
                    .tag(Tab.search)
            }
            .tint(KidTheme.accent)
            if let overlay {
                KidOverlayView(kind: overlay) { showOverlayPIN = true }
            }
        }
        .preferredColorScheme(.dark)   // Bernstein-Akzent traegt nur auf Dunkel 
        .environment(kidSession)
        .environment(remote)
        .environment(playerCoordinator)
        .background(GeometryReader { proxy in Color.clear.onAppear { remote.sheetInset = proxy.size.height * RemoteController.sheetFraction } })
        .sheet(isPresented: remoteBinding(whenPlaying: false)) { RemoteSheet().environment(remote).preferredColorScheme(.dark) }
        .fullScreenCover(isPresented: Binding(get: { playerCoordinator.player != nil }, set: { if !$0 { playerCoordinator.close() } })) {
            if let player = playerCoordinator.player {
                PlayerScreen(model: player, onLock: onLockTapped)
                    .environment(playerCoordinator).environment(remote).environment(kidSession)
                    .sheet(isPresented: remoteBinding(whenPlaying: true)) { RemoteSheet().environment(remote).preferredColorScheme(.dark) }
                    .tint(KidTheme.accent)
                    .preferredColorScheme(.dark)
            }
        }
        .sheet(isPresented: $showOverlayPIN) {
            PINEntryView {
                showOverlayPIN = false
                parentDismissedOverlay()
            }
        }
        .onAppear(perform: bootstrap)
        .onChange(of: profiles.count) { _, _ in kidSession.resolve(from: profiles) }
        .onChange(of: session.kidModeRequests) { _, _ in bootstrap() }
        .onChange(of: playerCoordinator.player == nil) { _, _ in remote.player = playerCoordinator.player }
        .task {
            while !Task.isCancelled {
                tick()
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }

    private func remoteBinding(whenPlaying: Bool) -> Binding<Bool> {
        Binding(get: { remote.isPresented && (playerCoordinator.player != nil) == whenPlaying },
                set: { remote.isPresented = $0 })
    }

   // MARK: Sekündliche Überwachung

    private func tick() {
        now = Date()
        if session.sleepTimer.tick(now: now) {
            overlay = .goodNight
            playerCoordinator.close()   // FR-09.6 (Sehzeit wird gebucht)
            remote.isPresented = false
        } else if let volume = session.sleepTimer.fadeVolume(at: now) {
            playerCoordinator.player?.setVolume(volume)   // Ausblenden in der letzten Minute
        }
        // Ruhezeiten: wie in der Android-App, Warnung 15 bzw. 5 Minuten vorher
        if let profile = kidSession.activeProfile {
            #if DEBUG
            // UI-Tests brauchen eine feste Uhrzeitlage: `-sidetube.devBedtimeNow 1` erzwingt die
            // Ruhezeit, `-sidetube.devBedtimeOff 1` schaltet sie ab (sonst sperrt jeder Lauf nach 20 Uhr).
            let defaults = UserDefaults.standard
            if defaults.bool(forKey: "sidetube.devBedtimeNow") {
                // Die echte Uhrzeit bleibt außen vor: außerhalb eines echten Fensters liefert
                // endOfCurrentWindow nichts, und die Ausnahme der Eltern liefe ins Leere.
                bedtime = devBedtimeSkipped ? .off : .active
            } else if defaults.bool(forKey: "sidetube.devBedtimeOff") {
                bedtime = .off
            } else {
                bedtime = BedtimeEvaluator.evaluate(profile.bedtime, now: now)
            }
            #else
            bedtime = BedtimeEvaluator.evaluate(profile.bedtime, now: now)
            #endif
            if bedtime.isActive, overlay == nil || overlay == .bedtime {
                if overlay != .bedtime {
                    overlay = .bedtime
                    playerCoordinator.close()
                    remote.isPresented = false
                }
            } else if !bedtime.isActive, overlay == .bedtime {
                overlay = nil
            }
        }
        if let remaining = dailyRemainingSeconds() {
            if remaining <= 0, overlay == nil {
                overlay = .timeUp
                playerCoordinator.close()
                remote.isPresented = false
            } else if remaining > 0, overlay == .timeUp {
                overlay = nil   // Limit erhöht oder Mitternacht überschritten
            }
        }
    }

    private func dailyRemainingSeconds() -> Int? {
        guard let profile = kidSession.activeProfile else { return nil }
        return DailyLimit.remainingSeconds(profile: profile, watchTime: WatchTimeRepository(context: modelContext),
                                           liveSeconds: playerCoordinator.player?.unrecordedSeconds ?? 0, now: now)
    }

    private func parentDismissedOverlay() {
        switch overlay {
        case .goodNight:
            session.endSleepMode()   // FR-09.7
            overlay = nil
        case .bedtime:
            // Ausnahme bis zum Ende der laufenden Ruhezeit
            if let profile = kidSession.activeProfile {
                profile.bedtimeSkipUntil = BedtimeEvaluator.endOfCurrentWindow(profile.bedtime, now: now)
                try? modelContext.save()
            }
            #if DEBUG
            devBedtimeSkipped = true
            #endif
            bedtime = .off
            overlay = nil
        case .timeUp:
            overlay = nil
            onParentUnlocked()   // Eltern können das Limit im Profil anpassen
        case nil:
            break
        }
    }

   // MARK: Aufbau

    private func bootstrap() {
        kidSession.resolve(from: profiles)
        remote.goBack = { [remote, playerCoordinator] in
            if playerCoordinator.player != nil { playerCoordinator.close() } else { remote.isPresented = false }
        }
        remote.goHome = { [remote, playerCoordinator] in
            playerCoordinator.close()
            remote.isPresented = false
            tab = .home
        }
        let context = KidContext(modelContext: modelContext, youtube: services.youtube, resolver: services.resolver)
        #if DEBUG
        if let videoId = UserDefaults.standard.string(forKey: "sidetube.devAutoplay"), playerCoordinator.player == nil {
            UserDefaults.standard.removeObject(forKey: "sidetube.devAutoplay")
            playerCoordinator.play(KidRow(id: videoId, title: "Diagnose", action: .play(videoId: videoId, title: "Diagnose")), in: [],
                                   profile: kidSession.activeProfile, context: modelContext)
        }
        #endif
   // Schlafmodus aus dem Elternbereich: Profil wählen, Schlaf-Playlist laden und abspielen
        if let sleepProfile = profiles.first(where: { $0.id == session.sleepProfileId }), session.sleepTimer.isRunning {
            kidSession.select(sleepProfile)
            if let playlistId = session.consumePendingSleepPlaylist() {
                let playlist = PlaylistModel(playlistId: playlistId, playlistTitle: "Schlaf-Playlist", context: context)
                Task {
                    await playlist.onAppear()
                    if let first = playlist.rows.first {
                        playerCoordinator.play(first, in: playlist.rows, profile: sleepProfile, context: modelContext)
                    }
                }
            }
        }
    }
}
