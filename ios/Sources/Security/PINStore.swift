// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Security

/// Persistenz für PIN-Hash und Brute-Force-Zähler. Austauschbar für Tests.
protocol PINStore: AnyObject {
    var pinHash: String? { get set }
    var failCount: Int { get set }
    var lockoutUntil: Date? { get set }
}

final class InMemoryPINStore: PINStore {
    var pinHash: String?
    var failCount = 0
    var lockoutUntil: Date?
}

/// PIN-Hash im Keychain (nur dieses Gerät, nach erstem Entsperren), Zähler in UserDefaults
/// (wie `SharedPreferences("pin_brute_force")` in Android).
final class KeychainPINStore: PINStore {
    private let service = "xyz.steier.sidetube"
    private let account = "parent_pin"
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    var pinHash: String? {
        get { readKeychain() }
        set {
            if let newValue { writeKeychain(newValue) } else { deleteKeychain() }
        }
    }

    var failCount: Int {
        get { defaults.integer(forKey: "pin_fail_count") }
        set { defaults.set(newValue, forKey: "pin_fail_count") }
    }

    var lockoutUntil: Date? {
        get {
            let value = defaults.double(forKey: "pin_lockout_until")
            return value > 0 ? Date(timeIntervalSince1970: value) : nil
        }
        set { defaults.set(newValue?.timeIntervalSince1970 ?? 0, forKey: "pin_lockout_until") }
    }

    private var baseQuery: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: account]
    }

    private func readKeychain() -> String? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func writeKeychain(_ value: String) {
        let data = Data(value.utf8)
        let update: [String: Any] = [kSecValueData as String: data]
        let status = SecItemUpdate(baseQuery as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var add = baseQuery
            add[kSecValueData as String] = data
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(add as CFDictionary, nil)
        }
    }

    private func deleteKeychain() {
        SecItemDelete(baseQuery as CFDictionary)
    }
}
