// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Observation

/// Auswahlzustand einer per Rad bedienten Liste: Index bewegen (mit Anschlag), auswählen.
@Observable
final class WheelMenuModel {
    private(set) var count: Int
    private(set) var selectedIndex: Int

    init(count: Int, selectedIndex: Int = 0) {
        self.count = max(0, count)
        self.selectedIndex = count > 0 ? min(max(0, selectedIndex), count - 1) : 0
    }

    func setCount(_ newCount: Int) {
        count = max(0, newCount)
        selectedIndex = count > 0 ? min(selectedIndex, count - 1) : 0
    }

    func move(by steps: Int) {
        guard count > 0 else { return }
        selectedIndex = min(max(0, selectedIndex + steps), count - 1)
    }

    func select(_ index: Int) {
        guard index >= 0, index < count else { return }
        selectedIndex = index
    }
}
