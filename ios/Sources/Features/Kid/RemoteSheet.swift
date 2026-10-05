// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftUI

/// Fernbedienung als Bottom Sheet: Wheel groß und zentral, darunter nur Zurück und Home.
struct RemoteSheet: View {
    @Environment(RemoteController.self) private var remote
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 8) {

            if let player = remote.player {
                VStack(spacing: 2) {
                    Text(player.current.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                    Text("\(player.positionText) · \(player.status == .playing ? "Spielt" : (player.status == .paused ? "Pause" : "Lädt …"))")
                        .font(.caption).foregroundStyle(.secondary)
                }
                .padding(.horizontal, KidTheme.outerPadding)
                .accessibilityElement(children: .combine)
            } else if let target = remote.target, let item = target.model.selectedItem {
                Text(item.title).font(.subheadline).foregroundStyle(.secondary).lineLimit(1).padding(.horizontal, KidTheme.outerPadding)
                    .accessibilityLabel("Ausgewählt: \(item.title)")
            }

            // Ohne Kopfzeile bekommt das Rad die ganze Höhe; die Ecken des Quadrats bleiben beim
            // runden Rad frei und tragen die drei Tasten, ohne den Ring zu überdecken.
            ClickWheelView(inPlayer: remote.player != nil, onEvent: remote.handle)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(.horizontal, 8)
                .padding(.bottom, 8)
                .overlay(alignment: .topTrailing) {
                    Button { dismiss() } label: {
                        Image(systemName: "chevron.down")
                            .font(.body.weight(.semibold))
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .accessibilityLabel("Schließen")
                    .accessibilityIdentifier("remote.close")
                }
                .overlay(alignment: .bottomLeading) {
                    // Zurück: kurz eine Ebene, lang zum Start (SideUI ADR 0007).
                    remoteButton("Zurück", systemImage: "arrow.uturn.backward", identifier: "remote.back",
                                 onLongPress: { remote.back(long: true) }) { remote.back(long: false) }
                }
                .overlay(alignment: .bottomTrailing) {
                    remoteButton("Start", systemImage: "house", identifier: "remote.home") { remote.goHome?() }
                }
        }
        .padding(.top, 8)
        .tint(.primary)   // Bedientasten sind kein ACTIVE-Zustand
        .presentationDetents([.fraction(RemoteController.sheetFraction), .large])
        .presentationDragIndicator(.visible)
        .presentationBackgroundInteraction(.enabled(upThrough: .fraction(RemoteController.sheetFraction)))
        .presentationBackground(.regularMaterial)
        .sensoryFeedback(.impact(weight: .light), trigger: remote.isPresented) { _, new in new }
    }

    @State private var longPressFired = false

    private func remoteButton(_ title: String, systemImage: String, identifier: String,
                              onLongPress: (() -> Void)? = nil, action: @escaping () -> Void) -> some View {
        Button {
            // Nach einem langen Druck zählt das Loslassen nicht noch einmal als kurzer.
            if longPressFired { longPressFired = false } else { action() }
        } label: {
            VStack(spacing: 2) {
                Image(systemName: systemImage).font(.subheadline)
                Text(title).font(.caption2.weight(.medium))
            }
            .frame(width: 52, height: 44)   // schmal genug, damit die Taste auf kleinen Geräten den Ring nicht berührt
        }
        .buttonStyle(.bordered)
        .simultaneousGesture(LongPressGesture(minimumDuration: 0.5).onEnded { _ in
            guard let onLongPress else { return }
            longPressFired = true
            onLongPress()
        })
        .accessibilityIdentifier(identifier)
        .accessibilityActions {
            if let onLongPress { Button("Zum Start", action: onLongPress) }
        }
    }
}
