import Foundation
import Observation

/// Was das Rad gerade steuert: der sichtbare Listen-Screen (Auswahl + Öffnen) …
struct RemoteTargetBinding {
    let model: any KidScreenModel
    let activate: (KidRow) -> Void
}

/// Fernbedienung als eigener Modus (Bottom Sheet). Übersetzt Radereignisse für den aktiven Kontext:
/// Player (spulen, Play/Pause, Lautstärke, Video wechseln) oder sichtbarer Screen (Auswahl, Öffnen).
@Observable
final class RemoteController {
    var isPresented = false
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
        isPresented && target?.model === model && model.menu.selectedIndex == index
    }

    func handle(_ event: WheelEvent) {
        if let player {
            switch event {
            case .rotate(let steps): player.seek(by: Double(steps) * seekStepSeconds)
            case .select, .playPause: player.togglePlayback()
            case .up: player.adjustVolume(by: 10)
            case .down: player.adjustVolume(by: -10)
            case .previous: player.previous()
            case .next: player.next()
            case .menu: goBack?()
            }
            return
        }
        guard let target else { return }
        let model = target.model
        switch event {
        case .rotate(let steps):
            model.menu.move(by: steps)
            model.onSelectionChanged(index: model.menu.selectedIndex)
        case .up:
            model.menu.move(by: -1)
            model.onSelectionChanged(index: model.menu.selectedIndex)
        case .down:
            model.menu.move(by: 1)
            model.onSelectionChanged(index: model.menu.selectedIndex)
        case .select:
            if let item = model.selectedItem { target.activate(item) }
        case .playPause:
            if let item = model.selectedItem, case .play = item.action { target.activate(item) }
        case .previous:
            model.menu.select(0)
        case .next:
            model.menu.select(max(0, model.menu.count - 1))
            model.onSelectionChanged(index: model.menu.selectedIndex)
        case .menu:
            goBack?()
        }
    }
}
