import SwiftUI

/// Wurzel: ohne PIN → Einrichtung; sonst Kindermodus als Start, Elternbereich hinter PIN.
struct RootView: View {
    @Environment(PINManager.self) private var pinManager
    @Environment(SessionState.self) private var session
    @Environment(AppServices.self) private var services
    @Environment(\.modelContext) private var modelContext
    @State private var parentUnlocked = Self.developerStartsInParentMode
    @State private var showPINEntry = false

   /// Nur DEBUG: `-sidetube.devParent 1` startet entsperrt im Elternbereich (Screenshots, UI-Tests).
    private static var developerStartsInParentMode: Bool {
        #if DEBUG
        UserDefaults.standard.bool(forKey: "sidetube.devParent")
        #else
        false
        #endif
    }

    @State private var incomingVideoId: String?

    var body: some View {
        content
            .task { seedLibraryIfRequested(); await seedIfRequested() }
            .onOpenURL { url in incomingVideoId = IncomingLink.videoId(from: url) }
            .sheet(isPresented: Binding(get: { incomingVideoId != nil }, set: { if !$0 { incomingVideoId = nil } })) {
                if let incomingVideoId { IncomingRecommendationView(videoId: incomingVideoId) }
            }
    }

   /// Nur DEBUG: `-sidetube.devSeedLibrary <Dateiname> [-sidetube.devSeedProfile <Name>]` importiert ein Startpaket
   /// (Profil wird angelegt, falls nötig) und setzt dessen Profil-Vorgaben. Ergebnis in `Documents/player-diagnose.log`.
    private func seedLibraryIfRequested() {
        #if DEBUG
        guard let raw = UserDefaults.standard.string(forKey: "sidetube.devSeedLibrary"), !raw.isEmpty else { return }
        UserDefaults.standard.removeObject(forKey: "sidetube.devSeedLibrary")
        let name = UserDefaults.standard.string(forKey: "sidetube.devSeedProfile") ?? "Kind"
        do {
            let profiles = ProfileRepository(context: modelContext)
            let profile = try profiles.all().first { $0.name == name } ?? profiles.create(name: name)
            let library = try SeedLibraryImporter.load(named: raw)
            let result = try SeedLibraryImporter(context: modelContext).importLibrary(library, into: profile, applyProfilePreset: true)
            YouTubePlayerBridge.diagnose("SeedLibrary \(raw) → \(profile.name): "
                + "\(result.imported) neu, \(result.skipped) vorhanden, \(result.blocked) blockiert")
        } catch {
            YouTubePlayerBridge.diagnose("SeedLibrary FEHLER \(error)")
        }
        #endif
    }

   /// Nur DEBUG: `-sidetube.devSeedChannels "UC…,UC…"` legt die Kanäle im ersten Profil an (erstellt „Beispiel", falls keins).
    private func seedIfRequested() async {
        #if DEBUG
        guard let list = UserDefaults.standard.string(forKey: "sidetube.devSeedChannels"), !list.isEmpty else { return }
        UserDefaults.standard.removeObject(forKey: "sidetube.devSeedChannels")
        let profiles = ProfileRepository(context: modelContext)
        let whitelist = WhitelistRepository(context: modelContext)
        guard let profile = try? (profiles.all().first ?? profiles.create(name: "Beispiel")) else { return }
        for id in list.split(separator: ",").map({ $0.trimmingCharacters(in: .whitespaces) }) where !id.isEmpty {
            guard !whitelist.contains(youtubeId: id, in: profile) else { YouTubePlayerBridge.diagnose("Seed: \(id) schon vorhanden"); continue }
            do {
                let draft = try await services.youtube.resolve(.channel(id: id))
                try whitelist.add(draft, to: profile)
                YouTubePlayerBridge.diagnose("Seed: \(id) → \(draft.title)")
            } catch {
                YouTubePlayerBridge.diagnose("Seed: \(id) FEHLER \(error)")
            }
        }
        #endif
    }

    @ViewBuilder
    private var content: some View {
        if !pinManager.isPINSet {
            PINSetupView { parentUnlocked = true }
        } else if parentUnlocked {
            ParentDashboardView { parentUnlocked = false }
                .onChange(of: session.kidModeRequests) { _, _ in parentUnlocked = false }
        } else {
            KidRootView(onLockTapped: { showPINEntry = true }, onParentUnlocked: { parentUnlocked = true })
                .onChange(of: session.kidModeRequests) { _, _ in parentUnlocked = false }
                .sheet(isPresented: $showPINEntry) {
                    PINEntryView {
                        showPINEntry = false
                        parentUnlocked = true
                    }
                }
        }
    }
}

#Preview {
    RootView().environment(PINManager(store: InMemoryPINStore())).environment(AppServices.live()).environment(SessionState())
}
