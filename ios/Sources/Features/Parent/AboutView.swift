import SwiftUI

/// FR-15 (ohne Spenden-Karte).
struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 14) {
                        Image("SideTubeMark").resizable().scaledToFit().frame(width: 56, height: 56).accessibilityHidden(true)
                        SideWordmark(product: .tube)
                    }
                    .listRowBackground(Color.clear)
                    .frame(maxWidth: .infinity)
                }
                Section {
                    LabeledContent("App", value: Brand.appName)
                    LabeledContent("Anbieter", value: Brand.publisher)
                    LabeledContent("Version", value: AppConfig.appVersion)
                    LabeledContent("Lizenz", value: "GPLv3")
                    LabeledContent("YouTube-API-Schlüssel", value: AppConfig.hasYouTubeAPIKey ? "konfiguriert" : "fehlt")
                }
                Section("Idee & Autor") {
                    LabeledContent(Brand.authorRole, value: Brand.author)
                    Text("SideTube ist ein SideKids-Projekt: aus dem Wunsch entstanden, Kindern gute Videos zu geben – ohne die Maschine drumherum.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Section("Was SideTube macht") {
                    Text("Eltern stellen pro Kind eine Whitelist aus YouTube-Kanälen, -Videos und -Playlists zusammen. Im Kindermodus gibt es nur diese Inhalte – ohne Empfehlungen, Kommentare oder Werbung-Links. Alles bleibt auf diesem Gerät.")
                }
                Section("Datenschutz & Support") {
                    Link("Datenschutzerklärung", destination: Brand.privacyPolicyURL)
                    Link("Support", destination: Brand.supportURL)
                }
                Section("Quellcode") {
                    Link("github.com/sidekids", destination: Brand.sourceURL)
                    Link("Quelltext: github.com/sidekids", destination: URL(string: "https://github.com/sidekids")!)
                }
            }
            .navigationTitle("Über SideTube")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig") { dismiss() } } }
        }
    }
}
