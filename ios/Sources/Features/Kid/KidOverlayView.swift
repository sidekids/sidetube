// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftUI

enum KidOverlay: Equatable {
    case goodNight
    case timeUp
    case bedtime
    case storageError
    case interruptedSession
}

/// Dunkles Vollbild-Overlay. Nur die Eltern-PIN führt heraus.
struct KidOverlayView: View {
    let kind: KidOverlay
   /// Uhrzeit, ab der es nach der Ruhezeit weitergeht („6:30“) – nimmt dem Overlay das Endgültige.
    var resumesAt: String? = nil
    let onParentPIN: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.94).ignoresSafeArea()
            VStack(spacing: 20) {
                Image(systemName: symbol)
                    .font(.system(size: 72)).foregroundStyle(.white.opacity(0.9))
                Text(title)
                    .font(.largeTitle.bold()).foregroundStyle(.white)
                Text(message)
                    .font(.body).foregroundStyle(.white.opacity(0.7)).multilineTextAlignment(.center).padding(.horizontal, 32)
                Button(action: onParentPIN) {
                    Label("Für Eltern", systemImage: "lock.fill").padding(.horizontal, 8)
                        .frame(minHeight: 44)
                }
                .buttonStyle(.bordered).tint(.white).padding(.top, 12)
                .accessibilityHint("Öffnet die PIN-Eingabe für Eltern")
                .accessibilityIdentifier("overlay.parentPIN")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityIdentifier(identifier)
    }

    private var symbol: String {
        switch kind {
        case .goodNight: "moon.zzz.fill"
        case .timeUp: "hourglass.bottomhalf.filled"
        case .bedtime: "bed.double.fill"
        case .storageError: "exclamationmark.shield"
        case .interruptedSession: "exclamationmark.triangle.fill"
        }
    }

    private var title: String {
        switch kind {
        case .goodNight: String(localized: "Gute Nacht!")
        case .timeUp: String(localized: "Die Zeit ist um")
        case .bedtime: String(localized: "Schlafenszeit")
        case .storageError: String(localized: "Wiedergabe angehalten")
        case .interruptedSession: String(localized: "Wiedergabe unterbrochen")
        }
    }

    private var message: String {
        switch kind {
        case .goodNight: String(localized: "Die Schlafzeit ist da. Bis morgen!")
        case .timeUp: String(localized: "Deine Zeit für heute ist um. Morgen geht es weiter.")
        case .bedtime: resumesAt.map { String(localized: "Jetzt ist Ruhezeit. Ab \($0) Uhr geht es weiter.") } ?? String(localized: "Jetzt ist Ruhezeit. Morgen früh geht es weiter.")
        case .storageError: String(localized: "Die Sehzeit konnte nicht gespeichert werden. Bitte die Eltern fragen.")
        case .interruptedSession: String(localized: "Die letzte Wiedergabe wurde nicht sicher beendet. Bereits gespeicherte Sehzeit bleibt erhalten; fehlende Zeit wird nicht geschätzt. Bitte die Eltern fragen.")
        }
    }

    private var identifier: String {
        switch kind {
        case .goodNight: "overlay.goodNight"
        case .timeUp: "overlay.timeUp"
        case .bedtime: "overlay.bedtime"
        case .storageError: "overlay.storageError"
        case .interruptedSession: "overlay.interruptedSession"
        }
    }
}
