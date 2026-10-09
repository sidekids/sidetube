// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Sammelfreigabe (ADR 0004): einmal Alter, Kategorie und – bei Kanälen – Stufe für alle ausgewählten
/// Einträge. Voreingestellt ist „wie vorgeschlagen", dann behält jeder Eintrag seine eigene Vorgabe.
/// Nicht sammelbare Einträge werden genannt und bleiben unberührt in der Prüfliste.
struct SammelFreigabeSheet: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    let items: [WhitelistItem]
    /// Meldet nach dem Speichern, was erledigt ist.
    var onFertig: (CurationRepository.SammelErgebnis) -> Void = { _ in }

    @State private var alterWieVorgeschlagen = true
    @State private var alter = Kanaleinstufung.standardAlter
    @State private var kategorie: Sammelpruefung.KategorieWahl = .wieVorgeschlagen
    @State private var kanalStufe = Kanaleinstufung.standardStufe
    @State private var error: String?

    private var curation: CurationRepository { CurationRepository(context: modelContext) }
    private var gepruefte: [(item: WhitelistItem, pruefling: Sammelpruefung.Pruefling, grund: Sammelpruefung.Grund?)] {
        items.map { item in
            let p = curation.pruefling(item)
            return (item, p, Sammelpruefung.grund(p))
        }
    }
    private var werte: Sammelpruefung.Werte {
        .init(alter: alterWieVorgeschlagen ? nil : alter, kategorie: kategorie, kanalStufe: kanalStufe)
    }

    var body: some View {
        let alle = gepruefte
        let sammelbar = alle.filter { $0.grund == nil }
        let einzeln = alle.filter { $0.grund != nil }
        let kanaele = sammelbar.filter { $0.pruefling.istKanal }.count
        let angehoben = sammelbar.filter { Sammelpruefung.freigabe($0.pruefling, mit: werte).kategorieHebtAn }.count
        NavigationStack {
            Form {
                Section {
                    Text(sammelbar.count == 1 ? String(localized: "1 Eintrag wird freigegeben.") : String(localized: "\(sammelbar.count) Einträge werden freigegeben."))
                        .font(.headline)
                        .accessibilityIdentifier("sammel.anzahl")
                } footer: {
                    Text("Jeder Eintrag bekommt einen eigenen Verlaufseintrag (Eltern, „Sammelprüfung“). Unklares bitte einzeln prüfen.")
                }

                if let hinweis = Sammelpruefung.hinweis(einzeln.compactMap(\.grund)) {
                    Section {
                        Label(hinweis, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                            .accessibilityIdentifier("sammel.hinweis")
                        ForEach(einzeln, id: \.item.id) { eintrag in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(eintrag.item.title).font(.subheadline).lineLimit(2)
                                Text(eintrag.grund?.text ?? "").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    } header: {
                        Text("Bleiben in der Prüfliste")
                    }
                }

                Section {
                    Toggle("Wie vorgeschlagen", isOn: $alterWieVorgeschlagen)
                        .accessibilityIdentifier("sammel.alterWieVorgeschlagen")
                    if !alterWieVorgeschlagen {
                        Stepper("Ab \(alter) Jahren", value: $alter, in: Kanaleinstufung.altersbereich)
                            .accessibilityIdentifier("sammel.alter")
                    }
                } header: {
                    Text("Mindestalter")
                } footer: {
                    Text(alterWieVorgeschlagen ? String(localized: "Jeder Eintrag behält sein vorgeschlagenes Alter.") : String(localized: "Gilt für alle ausgewählten Einträge."))
                }

                Section {
                    Picker("Kategorie", selection: $kategorie) {
                        Text("Wie vorgeschlagen").tag(Sammelpruefung.KategorieWahl.wieVorgeschlagen)
                        Text("Keine").tag(Sammelpruefung.KategorieWahl.keine)
                        ForEach(ContentCategory.allCases) {
                            Label($0.title, systemImage: $0.systemImage).tag(Sammelpruefung.KategorieWahl.kategorie($0))
                        }
                    }
                    .accessibilityIdentifier("sammel.kategorie")
                    if angehoben > 0 {
                        Text(angehoben == 1
                             ? String(localized: "Bei 1 Eintrag hebt die Kategorie das Mindestalter an – dort gilt das höhere.")
                             : String(localized: "Bei \(angehoben) Einträgen hebt die Kategorie das Mindestalter an – dort gilt das höhere."))
                            .font(.footnote).foregroundStyle(.orange)
                    }
                } header: {
                    Text("Kategorie")
                }

                if kanaele > 0 {
                    Section {
                        Picker("Vertrauensstufe", selection: $kanalStufe) {
                            ForEach(Sammelpruefung.waehlbareKanalStufen) { stufe in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(stufe.title)
                                    Text(stufe.erklaerung).font(.caption).foregroundStyle(.secondary)
                                }
                                .tag(stufe)
                                .accessibilityIdentifier("sammel.stufe.\(stufe.rawValue)")
                            }
                        }
                        .pickerStyle(.inline)
                        .labelsHidden()
                    } header: {
                        Text(kanaele == 1 ? String(localized: "Vertrauensstufe für 1 Kanal") : String(localized: "Vertrauensstufe für \(kanaele) Kanäle"))
                    } footer: {
                        Text("„Gesperrt“ geht nur einzeln: Kanal in der Prüfliste antippen.")
                    }
                }

                Section {
                    Button(sammelbar.count == 1 ? String(localized: "1 Eintrag freigeben") : String(localized: "\(sammelbar.count) Einträge freigeben"),
                           systemImage: "checkmark.circle.fill") { freigeben() }
                        .buttonStyle(.borderedProminent)
                        .disabled(sammelbar.isEmpty)
                        .accessibilityIdentifier("sammel.freigeben")
                }
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle("Gemeinsam freigeben")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Abbrechen") { dismiss() } } }
        }
    }

    /// Übergibt alle ausgewählten – das Speichern prüft die Regel selbst noch einmal und lässt Nicht-Sammelbares stehen.
    private func freigeben() {
        do {
            let ergebnis = try curation.sammelFreigeben(items, mit: werte, actor: "Eltern")
            onFertig(ergebnis)
            dismiss()
        } catch {
            self.error = String(localized: "Konnte nicht speichern: \(error.localizedDescription)")
        }
    }
}
