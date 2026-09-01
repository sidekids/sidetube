// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftUI

/// Erstanlage der Eltern-PIN in zwei Schritten (Eingabe, Bestätigung). FR-02.1
struct PINSetupView: View {
    @Environment(PINManager.self) private var pinManager
    @State private var firstEntry: String?
    @State private var message: String?
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Image("SideTubeMark").resizable().scaledToFit().frame(width: 72, height: 72).accessibilityHidden(true)
            SideWordmark(product: .tube, font: .title3)
            Text(firstEntry == nil ? "Eltern-PIN festlegen" : "PIN wiederholen")
                .font(.title2.bold())
            Text("Die PIN schützt den Elternbereich. Mindestens \(PINManager.minimumLength) Ziffern.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            PINPadView(length: PINManager.minimumLength, onComplete: handle)
                .id(firstEntry == nil ? "first" : "confirm")
            if let message {
                Text(message).font(.footnote).foregroundStyle(.red)
            }
        }
        .padding()
    }

    private func handle(_ pin: String) {
        guard let first = firstEntry else {
            firstEntry = pin
            message = nil
            return
        }
        guard first == pin else {
            firstEntry = nil
            message = "Die Eingaben stimmen nicht überein. Bitte erneut beginnen."
            return
        }
        do {
            try pinManager.setPIN(pin)
            onDone()
        } catch {
            firstEntry = nil
            message = "PIN ungültig: nur Ziffern, mindestens \(PINManager.minimumLength)."
        }
    }
}
