// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftUI

/// Ereignisse des Rads (Sundial-Belegung: Mitte, Ring oben/unten = ↑/↓, Ring links/rechts = ⏮/⏭).
enum WheelEvent: Equatable {
    case rotate(steps: Int)
    case select
    case up
    case down
    case previous
    case next
   /// Nur über Barrierefreiheits-Aktion („Zurück“) – die Zurück-Taste sitzt im Remote-Sheet.
    case menu
    case playPause
}

/// Virtuelles kapazitives Scrollrad im iPod-Stil. Drehen am Ring liefert `rotate`, kurze Berührungen
/// den Mittelknopf (`select`) oder ein Ring-Segment. Haptischer Klick pro Rastschritt.
struct ClickWheelView: View {
    var onEvent: (WheelEvent) -> Void

    @State private var tracker = WheelRotationTracker()
    @State private var pressedSegment: ClickWheelGeometry.Segment?
    @State private var travelled: CGFloat = 0
    @State private var hapticTick = 0
    @State private var rotated = false

    private let tapTravelLimit: CGFloat = 12

    var body: some View {
        GeometryReader { proxy in
            let side = min(proxy.size.width, proxy.size.height)
            let center = CGPoint(x: proxy.size.width / 2, y: proxy.size.height / 2)
            let outer = side / 2
            let inner = outer * 0.34

            ZStack {
                Circle()
                    .fill(Color(.secondarySystemBackground))
                    .overlay(Circle().strokeBorder(Color(.separator), lineWidth: 1))
                    .frame(width: side, height: side)
                    .shadow(color: .black.opacity(0.12), radius: 8, y: 4)

                label(nil, system: "chevron.up", segment: .menu).offset(y: -outer * 0.72)
                label(nil, system: "backward.fill", segment: .previous).offset(x: -outer * 0.72)
                label(nil, system: "forward.fill", segment: .next).offset(x: outer * 0.72)
                label(nil, system: "chevron.down", segment: .playPause).offset(y: outer * 0.72)

                Circle()
                    .fill(Color(.systemBackground))
                    .overlay(Circle().strokeBorder(Color(.separator), lineWidth: 1))
                    .frame(width: inner * 2, height: inner * 2)
                    .scaleEffect(pressedSegment == .center ? 0.94 : 1)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
            .contentShape(Circle().size(width: side, height: side).offset(x: center.x - outer, y: center.y - outer))
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .local)
                    .onChanged { value in
                        handleChange(value, center: center, outer: outer, inner: inner)
                    }
                    .onEnded { value in
                        handleEnd(value, center: center, outer: outer, inner: inner)
                    }
            )
            .sensoryFeedback(.selection, trigger: hapticTick)
            .animation(.easeOut(duration: 0.08), value: pressedSegment)
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Scrollrad")
        .accessibilityHint("Am Ring drehen, um die Auswahl zu bewegen; Mitte tippen zum Oeffnen oder fuer Play/Pause; oben/unten Auswahl bzw. Lautstaerke, links/rechts Video wechseln.")
        .accessibilityAdjustableAction { direction in
            onEvent(.rotate(steps: direction == .increment ? 1 : -1))
        }
        .accessibilityAction(named: "Auswählen") { onEvent(.select) }
        .accessibilityAction(named: "Zurück") { onEvent(.menu) }
        .accessibilityAction(named: "Play/Pause") { onEvent(.playPause) }
    }

    private func label(_ text: String?, system: String?, segment: ClickWheelGeometry.Segment) -> some View {
        Group {
            if let text { Text(text).font(.caption.weight(.bold)) }
            if let system { Image(systemName: system).font(.body.weight(.semibold)) }
        }
        .foregroundStyle(pressedSegment == segment ? Color.accentColor : .secondary)
    }

    private func handleChange(_ value: DragGesture.Value, center: CGPoint, outer: CGFloat, inner: CGFloat) {
        if pressedSegment == nil, travelled == 0, !rotated {
            pressedSegment = ClickWheelGeometry.segment(at: value.startLocation, center: center, outerRadius: outer, innerRadius: inner)
            tracker.begin(at: ClickWheelGeometry.angle(of: value.startLocation, center: center))
        }
        travelled = max(travelled, hypot(value.translation.width, value.translation.height))
        guard pressedSegment != .center, travelled > tapTravelLimit else { return }
        rotated = true
        pressedSegment = nil
        let steps = tracker.update(to: ClickWheelGeometry.angle(of: value.location, center: center))
        if steps != 0 {
            hapticTick += 1
            onEvent(.rotate(steps: steps))
        }
    }

    private func handleEnd(_ value: DragGesture.Value, center: CGPoint, outer: CGFloat, inner: CGFloat) {
        defer {
            pressedSegment = nil
            travelled = 0
            rotated = false
            tracker.end()
        }
        guard !rotated, travelled <= tapTravelLimit,
              let segment = ClickWheelGeometry.segment(at: value.startLocation, center: center, outerRadius: outer, innerRadius: inner)
        else { return }
        hapticTick += 1
        switch segment {
        case .center: onEvent(.select)
        case .menu: onEvent(.up)   // Ring oben = ↑ (Sundial)
        case .previous: onEvent(.previous)
        case .next: onEvent(.next)
        case .playPause: onEvent(.down)   // Ring unten = ↓ (Sundial)
        }
    }
}

#Preview {
    ClickWheelView { print($0) }.padding(32)
}
