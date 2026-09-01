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
        }
        #endif
        pinManager = PINManager(store: KeychainPINStore())
        do {
            container = try ModelContainerFactory.make()
        } catch {
            fatalError("Datenbank konnte nicht geöffnet werden: \(error)")
        }
        applyDeveloperLaunchArguments()
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
