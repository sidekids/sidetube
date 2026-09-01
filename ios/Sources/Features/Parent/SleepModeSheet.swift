import SwiftUI

/// Schlafmodus starten: Dauer wählen, dann direkt in den Kindermodus (mit Schlaf-Playlist, falls gesetzt).
struct SleepModeSheet: View {
    @Environment(SessionState.self) private var session
    @Environment(\.dismiss) private var dismiss
    let profile: KidProfile
    @State private var minutes = 30

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Stepper("\(minutes) Minuten", value: $minutes, in: 1...180, step: minutes < 10 ? 1 : 5)
                } header: { Text("Dauer") } footer: {
                    Text("Nach Ablauf erscheint „Gute Nacht“, das Video pausiert. In der letzten Minute wird die Lautstärke ausgeblendet. Nur die Eltern-PIN beendet den Schlafmodus vorzeitig.")
                }
                Section("Schlaf-Playlist") {
                    Text(profile.sleepPlaylistId.map { "Wird geöffnet: \($0)" } ?? "Keine hinterlegt – das Kind wählt selbst. (Im Profil einstellbar.)")
                        .foregroundStyle(.secondary)
                }
                Section {
                    Button("Schlafmodus starten und in den Kindermodus", systemImage: "moon.zzz.fill", action: start)
                        .buttonStyle(.borderedProminent)
                }
            }
            .navigationTitle("Schlafmodus – \(profile.name)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Abbrechen") { dismiss() } } }
        }
    }

    private func start() {
        var seconds = minutes * 60
        #if DEBUG
        let override = UserDefaults.standard.integer(forKey: "sidetube.devSleepSeconds")   // nur für UI-Tests
        if override > 0 { seconds = override }
        #endif
        session.startSleepMode(profile: profile, seconds: seconds)
        dismiss()
    }
}
