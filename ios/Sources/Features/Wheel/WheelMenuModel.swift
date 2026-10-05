// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Observation

/// Auswahlzustand einer per Rad bedienten Seite: Einträge in Reihen, Index bewegen (mit Anschlag), auswählen.
///
/// SideUI (ADR 0006/0014): hoch/runter wechseln die Reihe, links/rechts und Drehen das Objekt in der Reihe,
/// kein Umlauf. Eine Reihe ist, was auf dem Schirm nebeneinander steht (Kanäle, eine Rasterzeile);
/// eine senkrechte Liste besteht aus Reihen mit je einem Eintrag.
@Observable
final class WheelMenuModel {
    private(set) var count: Int
    private(set) var selectedIndex: Int
    /// Länge jeder Reihe in Auswahlreihenfolge; die Summe ist `count`.
    private(set) var rowLengths: [Int]

    init(count: Int, selectedIndex: Int = 0) {
        self.count = max(0, count)
        self.selectedIndex = count > 0 ? min(max(0, selectedIndex), count - 1) : 0
        rowLengths = count > 0 ? [count] : []
    }

    /// Neue Anzahl ohne eigene Reihenaufteilung: alle Einträge bilden eine Reihe.
    func setCount(_ newCount: Int) {
        setLayout(rows: newCount > 0 ? [newCount] : [])
    }

    /// Neue Reihenaufteilung; die Auswahl bleibt, soweit sie noch passt.
    func setLayout(rows: [Int]) {
        rowLengths = rows.filter { $0 > 0 }
        count = rowLengths.reduce(0, +)
        selectedIndex = count > 0 ? min(selectedIndex, count - 1) : 0
    }

    /// Flache Bewegung über alle Einträge (Nachladen, Tests); mit Anschlag.
    func move(by steps: Int) {
        guard count > 0 else { return }
        selectedIndex = min(max(0, selectedIndex + steps), count - 1)
    }

    func select(_ index: Int) {
        guard index >= 0, index < count else { return }
        selectedIndex = index
    }

    /// Links/rechts und Drehen: das Objekt in der Reihe. Steht in der Reihe nur ein Eintrag, gäbe es
    /// darin nichts zu bewegen – dann wechselt die Bewegung die Reihe, sonst wäre es eine tote Taste.
    func moveInRow(by steps: Int) {
        guard count > 0, steps != 0 else { return }
        let (row, column) = position
        let length = rowLengths[row]
        guard length > 1 else {
            selectedIndex = Self.index(row: min(max(0, row + steps), rowLengths.count - 1), column: 0, in: rowLengths)
            return
        }
        selectedIndex = Self.index(row: row, column: min(max(0, column + steps), length - 1), in: rowLengths)
    }

    /// Hoch/runter: die Reihe wechseln; die Spalte bleibt, soweit die neue Reihe reicht.
    /// Gibt es nur eine Reihe, bewegen hoch/runter das Objekt darin (sonst tote Tasten).
    func moveRow(by steps: Int) {
        guard count > 0, steps != 0 else { return }
        guard rowLengths.count > 1 else { move(by: steps); return }
        let (row, column) = position
        let target = min(max(0, row + steps), rowLengths.count - 1)
        selectedIndex = Self.index(row: target, column: min(column, rowLengths[target] - 1), in: rowLengths)
    }

    /// Reihe und Spalte der Auswahl.
    var position: (row: Int, column: Int) {
        var start = 0
        for (row, length) in rowLengths.enumerated() {
            if selectedIndex < start + length { return (row, selectedIndex - start) }
            start += length
        }
        return (max(0, rowLengths.count - 1), 0)
    }

    private static func index(row: Int, column: Int, in rows: [Int]) -> Int {
        rows.prefix(row).reduce(0, +) + column
    }
}
