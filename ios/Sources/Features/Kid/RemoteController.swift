// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Observation

/// Was das Rad gerade steuert: der sichtbare Listen-Screen (Auswahl + Öffnen) …
struct RemoteTargetBinding {
    let model: any KidScreenModel
    let activate: (KidRow) -> Void
}

/// Fernbedienung als eigener Modus (Bottom Sheet). Übersetzt Radereignisse für den aktiven Kontext
/// nach SideUI (ADR 0006/0014/0015):
/// - Player: Mitte = Abspielen/Pause, links/rechts = Video, hoch/runter und Drehen = 10 s spulen.
///   Die Lautstärke liegt nicht am Ring, sie bleibt bei den Tasten des Telefons.
/// - Sonst: hoch/runter = Reihe, links/rechts und Drehen = Objekt in der Reihe, Mitte = öffnen, kein Umlauf.
/// - Zurück: kurz eine Ebene, lang zum Start (ADR 0007).
@Observable
final class RemoteController {
    var isPresented = false
    /// Elternschalter des aktiven Profils (ADR 0008–0010): ab Werk aus. Ohne ihn gibt es weder Griff
    /// noch Knopf noch Fokus; wird er abgeschaltet, schließt das Rad.
    var isEnabled = false {
        didSet { if !isEnabled { isPresented = false } }
    }
    var target: RemoteTargetBinding?
    var player: PlayerModel?
    var goBack: (() -> Void)?
    var goHome: (() -> Void)?
    var seekStepSeconds: Double = 10
    /// Anteil der Bildschirmhöhe für die Fernbedienung: 60 % Inhalt, 40 % Rad.
    static let sheetFraction = 0.40
    /// Höhe des geöffneten Sheets, damit Listen die Auswahl oberhalb davon zeigen können.
    var sheetInset: CGFloat = 320

   /// Auswahlzustand wird nur bei geöffneter Fernbedienung angezeigt (Touch braucht keinen Fokus).
    func isSelected(_ model: any KidScreenModel, index: Int) -> Bool {
        isEnabled && isPresented && target?.model === model && model.menu.selectedIndex == index
    }

   /// Zurück-Taste: kurz eine Ebene, lang zum Start.
    func back(long: Bool) {
        if long { goHome?() } else { goBack?() }
    }

    func handle(_ event: WheelEvent) {
        if let player {
            switch event {
            case .rotate(let steps): player.seek(by: Double(steps) * seekStepSeconds)
            case .select, .playPause: player.togglePlayback()
            case .up: player.seek(by: -seekStepSeconds)
            case .down: player.seek(by: seekStepSeconds)
            case .previous: player.previous()
            case .next: player.next()
            case .menu: goBack?()
            }
            return
        }
        guard let target else { return }
        let model = target.model
        switch event {
        case .rotate(let steps): model.menu.moveInRow(by: steps)
        case .previous: model.menu.moveInRow(by: -1)
        case .next: model.menu.moveInRow(by: 1)
        case .up: model.menu.moveRow(by: -1)
        case .down: model.menu.moveRow(by: 1)
        case .select:
            if let item = model.selectedItem { target.activate(item) }
            return
        case .playPause:
            if let item = model.selectedItem, case .play = item.action { target.activate(item) }
            return
        case .menu:
            goBack?()
            return
        }
        model.onSelectionChanged(index: model.menu.selectedIndex)
    }
}
