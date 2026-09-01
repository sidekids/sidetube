// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftUI

enum KidOverlay: Equatable {
    case goodNight
    case timeUp
    case bedtime
}

/// Dunkles Vollbild-Overlay. Nur die Eltern-PIN führt heraus.
struct KidOverlayView: View {
    let kind: KidOverlay
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
                    Label("Eltern-PIN", systemImage: "lock.fill").padding(.horizontal, 8)
                }
                .buttonStyle(.bordered).tint(.white).padding(.top, 12)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(identifier)
    }

    private var symbol: String {
        switch kind {
        case .goodNight: "moon.zzz.fill"
        case .timeUp: "hourglass.bottomhalf.filled"
        case .bedtime: "bed.double.fill"
        }
    }

    private var title: String {
        switch kind {
        case .goodNight: "Gute Nacht!"
        case .timeUp: "Die Zeit ist um"
        case .bedtime: "Schlafenszeit"
        }
    }

    private var message: String {
        switch kind {
        case .goodNight: "Der Schlaf-Timer ist abgelaufen. Bis morgen!"
        case .timeUp: "Für heute ist die Schauzeit aufgebraucht. Morgen geht es weiter."
        case .bedtime: "Jetzt ist Ruhezeit. Morgen früh geht es weiter."
        }
    }

    private var identifier: String {
        switch kind {
        case .goodNight: "overlay.goodNight"
        case .timeUp: "overlay.timeUp"
        case .bedtime: "overlay.bedtime"
        }
    }
}
