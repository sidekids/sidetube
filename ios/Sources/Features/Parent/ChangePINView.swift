// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftUI

/// PIN ändern: alte PIN, neue PIN, Bestätigung. FR-02.6
struct ChangePINView: View {
    @Environment(PINManager.self) private var pinManager
    @Environment(\.dismiss) private var dismiss

    private enum Step: String { case old, new, confirm }
    @State private var step: Step = .old
    @State private var oldPIN = ""
    @State private var newPIN = ""
    @State private var message: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Text(title).font(.title2.bold())
                if let message { Text(message).font(.footnote).foregroundStyle(.red).multilineTextAlignment(.center) }
                PINPadView(length: PINManager.minimumLength, onComplete: handle).id(step)
            }
            .padding()
            .navigationTitle("PIN ändern")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Abbrechen") { dismiss() } } }
        }
    }

    private var title: String {
        switch step {
        case .old: "Aktuelle PIN"
        case .new: "Neue PIN"
        case .confirm: "Neue PIN wiederholen"
        }
    }

    private func handle(_ pin: String) {
        switch step {
        case .old:
            oldPIN = pin
            message = nil
            step = .new
        case .new:
            newPIN = pin
            step = .confirm
        case .confirm:
            guard pin == newPIN else {
                message = "Die neue PIN stimmte nicht überein. Bitte erneut eingeben."
                step = .new
                return
            }
            do {
                switch try pinManager.changePIN(from: oldPIN, to: newPIN) {
                case .success: dismiss()
                case .failure(let remaining):
                    message = "Aktuelle PIN falsch (noch \(remaining) Versuche)."
                    step = .old
                case .lockedOut(let seconds):
                    message = "Gesperrt für \(seconds) s."
                    step = .old
                }
            } catch {
                message = "Neue PIN ungültig."
                step = .new
            }
        }
    }
}
