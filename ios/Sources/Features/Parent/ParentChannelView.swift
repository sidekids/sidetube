// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Observation
import SwiftUI

/// Einrichtung des Elternkanals (ADR 0005), ohne Oberfläche testbar.
@Observable
final class ParentChannelSetup {
    private let store: ParentChannelStore
    private let notifier: TalkBotNotifier
    private(set) var channel: ParentChannel?
    private(set) var message: String?
    private(set) var sending = false

    init(store: ParentChannelStore, notifier: TalkBotNotifier) {
        self.store = store
        self.notifier = notifier
        channel = store.load()
    }

    /// Einrichtungscode übernehmen. Ein ungültiger Code ändert nichts an einer bestehenden Einrichtung.
    func apply(code: String) {
        do {
            let parsed = try ParentChannel.parse(code)
            store.save(parsed)
            channel = parsed
            message = String(localized: "Eingerichtet. Mit „Test senden“ prüfen, ob die Meldung ankommt.")
        } catch {
            message = (error as? LocalizedError)?.errorDescription ?? String(localized: "Der Einrichtungscode passt nicht.")
        }
    }

    func sendTest() async {
        guard let channel, !sending else { return }
        sending = true
        defer { sending = false }
        let text = channel.mentions.map(TalkBot.mention).joined(separator: " ") + String(localized: " SideTube: Test der Benachrichtigung")
        message = switch await notifier.send(text, via: channel) {
        case .sent: String(localized: "Gesendet. Die Meldung erscheint in der Nextcloud-App.")
        case .notConfigured: String(localized: "Noch nicht eingerichtet.")
        case .failed(let status?) where status == 401: String(localized: "Abgelehnt: Schlüssel oder Bot passen nicht (HTTP 401).")
        case .failed(let status?) where status == 404: String(localized: "Nicht gefunden: Gespräch oder Talk fehlt (HTTP 404).")
        case .failed(let status?): String(localized: "Nicht angekommen (HTTP \(status)).")
        case .failed(nil): String(localized: "Nicht angekommen: Server nicht erreichbar.")
        }
    }

    func remove() {
        store.delete()
        channel = nil
        message = String(localized: "Entfernt. SideTube meldet keine Wünsche mehr.")
    }
}

/// Einstellungen → Eltern benachrichtigen. Liegt hinter der PIN.
struct ParentChannelView: View {
    @Environment(AppServices.self) private var services
    @State private var setup: ParentChannelSetup?
    @State private var showScanner = false
    @State private var confirmRemove = false

    var body: some View {
        List {
            Section {
                Text("Bei jedem neuen Wunsch schreibt SideTube eine kurze Nachricht in ein Gespräch auf der eigenen Nextcloud – ohne Namen und ohne Titel. Die Nextcloud-App meldet sie auf dem Telefon der Eltern.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            if let setup {
                if let channel = setup.channel {
                    Section("Eingerichtet") {
                        LabeledContent("Nextcloud", value: channel.server.host() ?? channel.server.absoluteString)
                        LabeledContent("Erwähnt", value: channel.mentions.map(TalkBot.mention).joined(separator: " "))
                    }
                    Section {
                        Button {
                            Task { await setup.sendTest() }
                        } label: {
                            HStack {
                                Label("Test senden", systemImage: "paperplane")
                                if setup.sending { Spacer(); ProgressView() }
                            }
                        }
                        .disabled(setup.sending)
                        Button("Neu einrichten", systemImage: "qrcode.viewfinder") { showScanner = true }
                        Button("Entfernen", systemImage: "trash", role: .destructive) { confirmRemove = true }
                    }
                } else {
                    Section("Einrichten") {
                        Button("Einrichtungscode scannen", systemImage: "qrcode.viewfinder") { showScanner = true }
                        // PasteButton: kein Systemdialog „Einfügen erlauben?" bei jedem Mal.
                        PasteButton(payloadType: String.self) { texts in
                            setup.apply(code: texts.first ?? "")
                        }
                        .accessibilityIdentifier("elternkanal.einfuegen")
                    }
                    Section {
                        Text("Den Code erzeugt das Skript `talk-wunschkanal.sh` auf dem Server der Familie (Nextcloud mit Talk).")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                }
                if let message = setup.message {
                    Section { Text(message).accessibilityIdentifier("elternkanal.meldung") }
                }
            }
        }
        .navigationTitle("Eltern benachrichtigen")
        .onAppear {
            if setup == nil { setup = ParentChannelSetup(store: services.parentChannels, notifier: services.parentNotifier) }
        }
        .sheet(isPresented: $showScanner) {
            QRCodeScannerSheet { code in
                showScanner = false
                setup?.apply(code: code)
            }
        }
        .confirmationDialog("Benachrichtigung entfernen?", isPresented: $confirmRemove, titleVisibility: .visible) {
            Button("Entfernen", role: .destructive) { setup?.remove() }
        } message: {
            Text("Der Schlüssel wird von diesem Gerät gelöscht. Bot und Gespräch auf der Nextcloud bleiben.")
        }
    }
}
