// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Kindermodus: native Tabs Start · Alle Videos · Suche; Fernbedienung als Bottom Sheet; Player als Full-Screen Cover.
/// Überwacht Schutzregeln an ihren Fristen; nur die aktive Lautstärke-Ausblendung benötigt Sekundenschritte.
struct KidRootView: View {
    enum Tab: Hashable { case home, library, search }

    @Environment(\.modelContext) private var modelContext
    @Environment(\.scenePhase) private var scenePhase
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
    @State private var bedtime: BedtimeState = .off
   /// Kurze Vorwarnung „Noch 15/5 Minuten“ vor der Ruhezeit – wie in der Android-App, nichts zu bestätigen.
    @State private var bedtimeBannerMinutes: Int?
    #if DEBUG
    /// Nur für die erzwungene Ruhezeit im Test: merkt die Eltern-Ausnahme, ohne von der Uhrzeit abzuhängen.
    @State private var devBedtimeSkipped = false
    #endif
    /// Letzte gezeigte Vorwarnstufe, damit dieselbe Minute das Banner nicht mehrfach auslöst.
    @State private var lastBedtimeBanner: Int?
    @State private var clockRevision = 0
    let onLockTapped: () -> Void
    let onParentUnlocked: () -> Void

    var body: some View {
        ZStack {
            TabView(selection: $tab) {
                HomeScreen(onLock: onLockTapped, onShowAllChannels: { librarySegment = .channel; tab = .library })
                    .tabItem { Label("Start", systemImage: "house") }
                    .tag(Tab.home)
                LibraryScreen(segment: $librarySegment, onLock: onLockTapped)
                    .tabItem { Label("Alle Videos", systemImage: "rectangle.stack") }
                    .tag(Tab.library)
                SearchScreen(onLock: onLockTapped)
                    .tabItem { Label("Suche", systemImage: "magnifyingglass") }
                    .tag(Tab.search)
            }
            .tint(KidTheme.accent)
            if let minutes = bedtimeBannerMinutes, overlay == nil {
                VStack {
                    Spacer()
                    Text("Noch \(minutes) Minuten")
                        .font(.subheadline.weight(.medium)).foregroundStyle(.primary)
                        .padding(.horizontal, 18).padding(.vertical, 10)
                        .background(Capsule().fill(.regularMaterial))
                        .padding(.bottom, 96)
                }
                .allowsHitTesting(false)
                .transition(.opacity)
                .accessibilityIdentifier("bedtime.warning")
            }
            if let overlay {
                KidOverlayView(kind: overlay, resumesAt: bedtimeResumeText) { showOverlayPIN = true }
            }
        }
        .animation(.easeInOut(duration: 0.25), value: bedtimeBannerMinutes)
        .task(id: bedtimeBannerMinutes) {
            guard bedtimeBannerMinutes != nil else { return }
            try? await Task.sleep(for: .seconds(6))
            if !Task.isCancelled { bedtimeBannerMinutes = nil }
        }
        .preferredColorScheme(.dark)   // SideUI-ACTIVE #FFB74D traegt nur auf Dunkel
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
        .onChange(of: remoteWheelAllowed, initial: true) { _, allowed in remote.isEnabled = allowed }
        .onChange(of: session.kidModeRequests) { _, _ in bootstrap() }
        .onChange(of: playerCoordinator.player == nil) { _, _ in remote.player = playerCoordinator.player }
        .onChange(of: playerCoordinator.interruptedSessionProfileID) { _, profileID in
            if profileID != nil { overlay = .interruptedSession }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { playerCoordinator.close() }
        }
        .onDisappear { playerCoordinator.close() }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
            clockRevision += 1
        }
        .task(id: protectionInputs) {
            guard scenePhase == .active else { return }
            while !Task.isCancelled {
                tick()
                guard !playerCoordinator.hasWatchTimeFailure else { return }
                let now = Date()
                guard let next = KidProtectionSchedule.next(now: now, bedtime: kidSession.activeProfile?.bedtime,
                    hasDailyLimit: kidSession.activeProfile?.dailyLimitMinutes != nil,
                    remainingSeconds: dailyRemainingSeconds(now: now),
                    playing: playerCoordinator.player?.isPlaying == true,
                    hasPlayer: playerCoordinator.player != nil,
                    sleepEnd: session.sleepTimer.isRunning ? session.sleepTimer.endDate : nil) else { return }
                do { try await Task.sleep(for: .seconds(max(0.001, next.timeIntervalSinceNow))) }
                catch { return }
            }
        }
    }

   /// Elternschalter des aktiven Profils; in DEBUG schaltet `-sidetube.devRemoteWheel 1` ihn für UI-Tests ein.
    private var remoteWheelAllowed: Bool {
        #if DEBUG
        if UserDefaults.standard.bool(forKey: "sidetube.devRemoteWheel") { return true }
        #endif
        return kidSession.activeProfile?.remoteWheelEnabled == true
    }

    private func remoteBinding(whenPlaying: Bool) -> Binding<Bool> {
        Binding(get: { remote.isPresented && (playerCoordinator.player != nil) == whenPlaying },
                set: { remote.isPresented = $0 })
    }

    private struct ProtectionInputs: Equatable {
        let phase: ScenePhase
        let profileID: UUID?
        let bedtime: BedtimeSettings?
        let limit: Int?
        let sleepEnd: Date?
        let sleepExpired: Bool
        let playerID: ObjectIdentifier?
        let playing: Bool
        let videoID: String?
        let clockRevision: Int
        let watchTimeFailed: Bool
    }

    private var protectionInputs: ProtectionInputs {
        ProtectionInputs(phase: scenePhase, profileID: kidSession.activeProfile?.id,
            bedtime: kidSession.activeProfile?.bedtime, limit: kidSession.activeProfile?.dailyLimitMinutes,
            sleepEnd: session.sleepTimer.endDate, sleepExpired: session.sleepTimer.expired,
            playerID: playerCoordinator.player.map { ObjectIdentifier($0) },
            playing: playerCoordinator.player?.isPlaying == true,
            videoID: playerCoordinator.player?.current.videoId, clockRevision: clockRevision,
            watchTimeFailed: playerCoordinator.hasWatchTimeFailure)
    }

   // MARK: Ereignisgesteuerte Überwachung

   /// „Ab 6:30 geht es weiter“ für das Ruhezeit-Overlay.
    private var bedtimeResumeText: String? {
        guard let profile = kidSession.activeProfile, profile.bedtime.enabled else { return nil }
        return BedtimeEvaluator.format(minutes: profile.bedtime.endMinutes)
    }

   /// Auf Zustandsänderungen und an geplanten Fristen ausführen, nicht periodisch im Leerlauf.
    private func tick() {
        if playerCoordinator.hasWatchTimeFailure {
            overlay = .storageError
            playerCoordinator.close()
            remote.isPresented = false
            return
        }
        let now = Date()
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
            let evaluated = BedtimeEvaluator.evaluate(profile.bedtime, now: now)
            if evaluated != bedtime { bedtime = evaluated }
            #endif
            if case .warning(let minutesLeft) = bedtime,
               minutesLeft == BedtimeEvaluator.warningLeadMinutes || minutesLeft == BedtimeEvaluator.warningLastMinutes,
               bedtimeBannerMinutes != minutesLeft, lastBedtimeBanner != minutesLeft {
                lastBedtimeBanner = minutesLeft
                bedtimeBannerMinutes = minutesLeft
            }
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
        if let remaining = dailyRemainingSeconds(now: now) {
            if remaining <= 0, overlay == nil {
                overlay = .timeUp
                playerCoordinator.close()
                remote.isPresented = false
            } else if remaining > 0, overlay == .timeUp {
                overlay = nil   // Limit erhöht oder Mitternacht überschritten
            }
        }
    }

    private func dailyRemainingSeconds(now: Date) -> Int? {
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
                profile.bedtimeSkipUntil = BedtimeEvaluator.endOfCurrentWindow(profile.bedtime, now: Date())
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
        case .storageError:
            onParentUnlocked()   // A PIN does not erase the accounting-failure latch.
        case .interruptedSession:
            // Unlike storageError, the parent PIN itself is the confirmation here (same gate as
            // every other overlay dismissal) - clearing just lifts the block, nothing to fix in
            // parent settings, so the child returns straight to Kid Home instead of onParentUnlocked().
            if playerCoordinator.acknowledgeInterruptedSession(context: modelContext) {
                overlay = nil
            }
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
        let context = KidContext(modelContext: modelContext, youtube: services.youtube, resolver: services.resolver, profile: kidSession.activeProfile)
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
                let playlist = PlaylistModel(playlistId: playlistId, playlistTitle: "Schlaf-Playlist", context: context, profile: sleepProfile)
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
