// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftUI

/// Die drei Wahlen für einen Kanal (ADR 0003) als Form-Abschnitte. Alle fünf Stufen stehen offen
/// mit ihrer Erklärung da – ein Menü würde die Sätze verstecken, die vor zu großzügiger Wahl schützen.
struct KanaleinstufungFelder: View {
    @Binding var einstufung: Kanaleinstufung

    var body: some View {
        Section {
            Picker("Vertrauensstufe", selection: $einstufung.trust) {
                ForEach(SourceTrust.allCases) { stufe in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(stufe.title).foregroundStyle(stufe == .blocked ? .red : .primary)
                        Text(stufe.erklaerung).font(.caption).foregroundStyle(.secondary)
                    }
                    .tag(stufe)
                    .accessibilityIdentifier("kanal.stufe.\(stufe.rawValue)")
                }
            }
            .pickerStyle(.inline)
            .labelsHidden()
        } header: {
            Text("Vertrauensstufe")
        }

        if einstufung.ergebnis == .hinzufuegen {
            Section {
                Stepper("Ab \(einstufung.ageMin) Jahren", value: $einstufung.ageMin, in: Kanaleinstufung.altersbereich)
                    .accessibilityIdentifier("kanal.alter")
                Picker("Kategorie", selection: $einstufung.category) {
                    Text("Keine").tag(ContentCategory?.none)
                    ForEach(ContentCategory.allCases) { Label($0.title, systemImage: $0.systemImage).tag(Optional($0)) }
                }
                .accessibilityIdentifier("kanal.kategorie")
                if einstufung.kategorieHebtAlterAn, let category = einstufung.category {
                    Text("„\(category.title)“ wird erst ab \(category.minimumAge) gezeigt – für diesen Kanal gilt deshalb ab \(einstufung.effektivesMindestalter).")
                        .font(.footnote).foregroundStyle(.orange)
                }
            } header: {
                Text("Alter und Kategorie")
            } footer: {
                Text("Gilt für den Kanal und als Vorgabe für neue Videos daraus.")
            }
        }
    }
}

/// Abschluss-Knopf passend zur Wahl: „Gesperrt" ist eine eigene, rot beschriftete Handlung, damit
/// niemand glaubt, der Kanal käme trotzdem dazu.
struct KanalEinstufenKnopf: View {
    let einstufung: Kanaleinstufung
    /// Eindeutige Treffer des Risikofilters: Hinzufügen geht nicht, Sperren schon.
    var gesperrtDurchFilter = false
    let aktion: () -> Void

    var body: some View {
        switch einstufung.ergebnis {
        case .hinzufuegen:
            Button("Kanal hinzufügen", systemImage: "checkmark.circle.fill", action: aktion)
                .buttonStyle(.borderedProminent)
                .disabled(gesperrtDurchFilter)
                .accessibilityIdentifier("kanal.hinzufuegen")
        case .sperren:
            Button("Kanal sperren", systemImage: "hand.raised.fill", role: .destructive, action: aktion)
                .buttonStyle(.bordered)
                .accessibilityIdentifier("kanal.sperren")
        }
    }
}
