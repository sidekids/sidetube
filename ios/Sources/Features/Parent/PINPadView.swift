// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftUI

/// Ziffernblock mit Punktanzeige. Ruft `onComplete` auf, sobald `length` Ziffern eingegeben sind.
struct PINPadView: View {
    let length: Int
    let onComplete: (String) -> Void
    var disabled = false
    @State private var digits = ""

    var body: some View {
        VStack(spacing: 28) {
            HStack(spacing: 14) {
                ForEach(0..<length, id: \.self) { index in
                    Circle()
                        .strokeBorder(.primary, lineWidth: 1.5)
                        .background(Circle().fill(index < digits.count ? Color.primary : .clear))
                        .frame(width: 16, height: 16)
                }
            }
            .accessibilityLabel("\(digits.count) von \(length) Ziffern eingegeben")

            let rows: [[String]] = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["", "0", "⌫"]]
            VStack(spacing: 12) {
                ForEach(rows, id: \.self) { row in
                    HStack(spacing: 12) {
                        ForEach(row, id: \.self) { key in
                            if key.isEmpty {
                                Color.clear.frame(width: 72, height: 72)
                            } else {
                                Button { tap(key) } label: {
                                    Text(key)
                                        .font(.title)
                                        .frame(width: 72, height: 72)
                                        .background(Circle().fill(Color(.secondarySystemBackground)))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
            .disabled(disabled)
            .opacity(disabled ? 0.4 : 1)
        }
    }

    func clear() { digits = "" }

    private func tap(_ key: String) {
        if key == "⌫" {
            if !digits.isEmpty { digits.removeLast() }
            return
        }
        guard digits.count < length else { return }
        digits.append(key)
        if digits.count == length {
            let entered = digits
            digits = ""
            onComplete(entered)
        }
    }
}
