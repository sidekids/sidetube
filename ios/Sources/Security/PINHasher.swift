// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// Speicherformat `base64(salt):base64(hash)` – identisch zur Android-App.
enum PINHasher {
    static func hash(_ pin: String) -> String {
        let salt = PBKDF2.randomSalt()
        guard let key = PBKDF2.deriveKey(password: pin, salt: salt) else {
            preconditionFailure("PBKDF2 fehlgeschlagen")
        }
        return "\(salt.base64EncodedString()):\(key.base64EncodedString())"
    }

    static func verify(_ pin: String, against stored: String) -> Bool {
        let parts = stored.split(separator: ":", maxSplits: 1).map(String.init)
        guard parts.count == 2,
              let salt = Data(base64Encoded: parts[0]),
              let expected = Data(base64Encoded: parts[1]),
              let actual = PBKDF2.deriveKey(password: pin, salt: salt, keyLength: expected.count) else { return false }
        return constantTimeEquals(actual, expected)
    }

    private static func constantTimeEquals(_ a: Data, _ b: Data) -> Bool {
        guard a.count == b.count else { return false }
        var difference: UInt8 = 0
        for (x, y) in zip(a, b) { difference |= x ^ y }
        return difference == 0
    }
}
