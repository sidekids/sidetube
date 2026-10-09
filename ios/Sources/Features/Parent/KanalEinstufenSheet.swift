// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Ein Kanal, der gerade eingestuft werden soll (für `.sheet(item:)`).
struct KanalKandidat: Identifiable, Equatable {
    let draft: WhitelistItemDraft
    var id: String { draft.youtubeId }
}

/// Eigene Maske zum Einstufen eines Kanals – für Wege ohne Link-Vorschau (Kanalsuche, „Kanal prüfen"
/// aus einem Wunsch). Dieselben Wahlen und dieselbe Speicherlogik wie in `AddWhitelistItemView` (ADR 0003).
struct KanalEinstufenSheet: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    let kandidat: KanalKandidat
    let profile: KidProfile
    /// Meldet das Ergebnis, nachdem gespeichert wurde.
    var onErgebnis: ((CurationRepository.KanalAufnahme) -> Void)? = nil

    @State private var einstufung = Kanaleinstufung()
    @State private var sperrMeldung: String?
    @State private var error: String?
    @State private var vorbereitet = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 12) {
                        Thumbnail(url: kandidat.draft.thumbnailUrl, isChannel: true)
                        VStack(alignment: .leading) {
                            Text(kandidat.draft.title).font(.headline).lineLimit(3)
                            Text(kandidat.draft.provider.title).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                if let sperrMeldung {
                    Label(sperrMeldung, systemImage: "hand.raised.fill").foregroundStyle(.red)
                        .accessibilityIdentifier("kanal.gesperrtMeldung")
                } else {
                    KanaleinstufungFelder(einstufung: $einstufung)
                    Section {
                        KanalEinstufenKnopf(einstufung: einstufung,
                                            gesperrtDurchFilter: RiskScreen.assess(title: kandidat.draft.title).isHardBlocked,
                                            aktion: speichern)
                    }
                }
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle("Kanal einstufen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(sperrMeldung == nil ? "Abbrechen" : "Fertig") { dismiss() }
                }
            }
            .onAppear(perform: vorbereiten)
        }
    }

   /// Vorauswahl einmalig aus der Quelle – ein erneutes Erscheinen darf die Wahl der Eltern nicht zurücksetzen.
    private func vorbereiten() {
        guard !vorbereitet else { return }
        vorbereitet = true
        let curation = CurationRepository(context: context)
        try? curation.ensureSources(SourceRegistry.allDefinitions)
        einstufung = .vorauswahl(aus: curation.effectiveSource(channelId: kandidat.draft.youtubeId))
    }

    private func speichern() {
        do {
            let ergebnis = try CurationRepository(context: context).kanalAufnehmen(kandidat.draft, als: einstufung,
                                                                                 for: profile, actor: "Eltern")
            onErgebnis?(ergebnis)
            switch ergebnis {
            case .gesperrt: sperrMeldung = Kanaleinstufung.sperrMeldung(kanal: kandidat.draft.title, profil: profile.name)
            case .aufgenommen: dismiss()
            }
        } catch {
            self.error = String(localized: "Konnte nicht speichern: \(error.localizedDescription)")
        }
    }
}
