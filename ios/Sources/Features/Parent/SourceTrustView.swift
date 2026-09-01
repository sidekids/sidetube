// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Quellen und ihre Sicherheitsstufe: Eltern können Quellen sperren oder hochstufen.
struct SourceTrustView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var sources: [CuratedSource] = []
    @State private var unclassified: [UnclassifiedSource] = []
    @State private var error: String?
    @State private var showAddInstance = false
    @State private var newInstanceHost = ""
    @State private var newInstanceTitle = ""

    var body: some View {
        List {
            Section {
                Button("PeerTube-Instanz hinzufügen", systemImage: "plus.circle") { showAddInstance = true }
            } footer: {
                Text("PeerTube ist föderiert: jede Instanz moderiert selbst. Nur eingetragene Instanzen dürfen Inhalte liefern; eine eigene Familien-Instanz kann als vertrauenswürdige Kinderquelle eingestuft werden.")
            }
            Section {
                ForEach(sources, id: \.channelId) { source in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(source.title).font(.headline)
                            Spacer()
                            Text(source.provider.title).font(.caption2).foregroundStyle(.secondary)
                        }
                        Picker("Stufe", selection: Binding(get: { source.trust }, set: { setTrust($0, for: source) })) {
                            ForEach(SourceTrust.allCases) { Text($0.title).tag($0) }
                        }
                        .labelsHidden()
                        if let notes = source.notes { Text(notes).font(.caption).foregroundStyle(.secondary) }
                        if source.isNewsSource { Text("Nachrichtenquelle – jede Nachricht wird einzeln eingestuft.").font(.caption2).foregroundStyle(.orange) }
                    }
                    .padding(.vertical, 4)
                }
            } footer: {
                Text("„Vertrauenswürdige Kinderquelle“ erlaubt das Stöbern im Kanal (mit Risikofilter). Alle anderen Stufen zeigen nur einzeln freigegebene Videos. „Gesperrt“ blendet die Quelle überall aus.")
            }
            if !unclassified.isEmpty {
                Section {
                    ForEach(unclassified) { entry in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(entry.title).font(.headline)
                                Spacer()
                                Text(entry.provider.title).font(.caption2).foregroundStyle(.secondary)
                            }
                            Menu {
                                ForEach(SourceTrust.allCases) { level in
                                    Button(level.title) { classify(entry, as: level) }
                                }
                            } label: {
                                Label("Stufe wählen", systemImage: "shield.lefthalf.filled")
                            }
                        }
                        .padding(.vertical, 4)
                    }
                } header: {
                    Text("Noch nicht eingestuft")
                } footer: {
                    Text("Diese Kanäle stehen in einer Whitelist, aber nicht im Quellenregister – etwa von einem anderen Gerät übernommen. Ohne Einstufung zeigt SideTube daraus nur einzeln freigegebene Videos.")
                }
            }
            if let error { Text(error).foregroundStyle(.red) }
        }
        .navigationTitle("Quellen")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: load)
        .alert("PeerTube-Instanz", isPresented: $showAddInstance) {
            TextField("Adresse, z. B. tube.example.org", text: $newInstanceHost)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
            TextField("Name (optional)", text: $newInstanceTitle)
            Button("Hinzufügen", action: addInstance)
            Button("Abbrechen", role: .cancel) { newInstanceHost = ""; newInstanceTitle = "" }
        } message: {
            Text("Die Instanz wird mit der Stufe „nur einzeln geprüfte Videos“ angelegt. Für eine eigene Familien-Instanz kann die Stufe danach erhöht werden.")
        }
    }

    private func load() {
        let repo = CurationRepository(context: modelContext)
        try? repo.ensureSources(SourceRegistry.allDefinitions)
        sources = repo.allSources()

        // Kanäle aus den Whitelists, die das Register nicht kennt – sonst bleiben sie unsichtbar
        // und lassen sich nie einstufen.
        let known = Set(sources.map(\.channelId))
        let items = (try? modelContext.fetch(FetchDescriptor<WhitelistItem>())) ?? []
        var found: [String: UnclassifiedSource] = [:]
        for item in items {
            guard let channelId = item.sourceChannelId ?? (item.type == .channel ? item.youtubeId : nil),
                  !known.contains(channelId), repo.effectiveSource(channelId: channelId) == nil else { continue }
            found[channelId] = UnclassifiedSource(channelId: channelId,
                                                  title: item.type == .channel ? item.title : (item.channelTitle ?? item.title),
                                                  provider: item.provider)
        }
        unclassified = found.values.sorted { $0.title.localizedCompare($1.title) == .orderedAscending }
    }

   /// Eltern stufen einen bisher unbekannten Kanal ein; dabei wird kein Mindestalter erfunden.
    private func classify(_ entry: UnclassifiedSource, as trust: SourceTrust) {
        do {
            try CurationRepository(context: modelContext).ensureSources([
                SourceDefinition(channelId: entry.channelId, handle: nil, title: entry.title, provider: entry.provider,
                                 trust: trust, notes: "Aus der Whitelist übernommen und von Eltern eingestuft.")
            ])
            load()
        } catch { self.error = error.localizedDescription }
    }

    private func addInstance() {
        let host = newInstanceHost.trimmingCharacters(in: .whitespaces).lowercased()
            .replacingOccurrences(of: "https://", with: "").replacingOccurrences(of: "http://", with: "")
            .split(separator: "/").first.map(String.init) ?? ""
        newInstanceHost = ""; let title = newInstanceTitle.isEmpty ? host : newInstanceTitle; newInstanceTitle = ""
        guard host.contains("."), !host.contains(" ") else { error = "Bitte eine Adresse wie tube.example.org eingeben."; return }
        do {
            try CurationRepository(context: modelContext).ensureSources([
                SourceDefinition(channelId: PeerTubeIDs.instanceId(host: host), handle: nil, title: title, provider: .peertube,
                                 trust: .perVideoReview, defaultAgeMin: 6,
                                 notes: "Von Eltern hinzugefügt – Standard: nur einzeln geprüfte Videos.")
            ])
            load()
        } catch { self.error = error.localizedDescription }
    }

    private func setTrust(_ trust: SourceTrust, for source: CuratedSource) {
        do { try CurationRepository(context: modelContext).setTrust(trust, for: source, actor: "Eltern"); load() } catch { self.error = error.localizedDescription }
    }
}

/// Kanal aus einer Whitelist, zu dem noch keine Quelle im Register steht.
struct UnclassifiedSource: Identifiable, Equatable {
    let channelId: String
    let title: String
    let provider: ContentProvider
    var id: String { channelId }
}
