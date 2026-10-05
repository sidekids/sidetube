// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

@main
struct SidetubeApp: App {
    private let container: ModelContainer
    private let pinManager: PINManager
    private let services = AppServices.live()
    private let session = SessionState()

    init() {
        #if DEBUG
   // Reset MUSS vor dem Anlegen des PINManagers laufen, sonst hält der noch den alten Zustand.
        if UserDefaults.standard.bool(forKey: "sidetube.uiTestReset") {
            PINManager(store: KeychainPINStore()).reset()
            ModelContainerFactory.removeStore()
            KeychainParentChannelStore().delete()
        }
        #endif
        pinManager = PINManager(store: KeychainPINStore())
        do {
            container = try ModelContainerFactory.make()
        } catch {
            fatalError("Datenbank konnte nicht geöffnet werden: \(error)")
        }
        applyDeveloperLaunchArguments()
        #if DEBUG && targetEnvironment(simulator)
        // Synthetic UI fixture only on an explicitly reset test installation.
        // Never seed or acknowledge recovery on an ordinary launch or device build.
        if UserDefaults.standard.bool(forKey: "sidetube.uiTestReset"),
           UserDefaults.standard.bool(forKey: "sidetube.uiTestInterruptedSession") {
            do {
                let context = ModelContext(container)
                let profile = try ProfileRepository(context: context).create(name: "Recovery Test", dailyLimitMinutes: 10)
                profile.bedtimeEnabled = false
                let item = WhitelistItem(type: .video, youtubeId: "abcdefghijk", title: "Synthetic Recovery Video",
                    thumbnailUrl: "", approvalStatus: .approved)
                item.profile = profile
                context.insert(item)
                try WatchTimeRepository(context: context).record(videoId: item.youtubeId, title: item.title, seconds: 30, for: profile)
                try PlaybackAdmissionRepository(context: context).begin(for: profile, token: UUID(), at: Date())
            } catch {
                fatalError("Synthetic recovery fixture could not be created")
            }
        }
        #endif
    }

   /// Nur DEBUG: `-sidetube.devPIN 1234` setzt eine PIN, falls keine existiert (Simulator-Screenshots, UI-Tests).
    private func applyDeveloperLaunchArguments() {
        #if DEBUG
        if !pinManager.isPINSet, let pin = UserDefaults.standard.string(forKey: "sidetube.devPIN") {
            try? pinManager.setPIN(pin)
        }
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .modelContainer(container)
        .environment(pinManager)
        .environment(services)
        .environment(session)
    }
}
