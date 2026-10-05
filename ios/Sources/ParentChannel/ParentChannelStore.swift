// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Security

/// Ablage des Elternkanals. Der Schlüssel des Bots gehört in den Schlüsselbund, nicht in die Datenbank.
nonisolated protocol ParentChannelStore: AnyObject, Sendable {
    func load() -> ParentChannel?
    func save(_ channel: ParentChannel)
    func delete()
}

nonisolated final class InMemoryParentChannelStore: ParentChannelStore, @unchecked Sendable {
    private let lock = NSLock()
    private var channel: ParentChannel?

    init(_ channel: ParentChannel? = nil) { self.channel = channel }

    func load() -> ParentChannel? { lock.withLock { channel } }
    func save(_ channel: ParentChannel) { lock.withLock { self.channel = channel } }
    func delete() { lock.withLock { channel = nil } }
}

/// Wie `KeychainPINStore`: nur auf diesem Gerät, nach dem ersten Entsperren lesbar, nicht im Backup.
nonisolated final class KeychainParentChannelStore: ParentChannelStore, @unchecked Sendable {
    private let baseQuery: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "xyz.steier.sidetube",
        kSecAttrAccount as String: "parent_channel",
    ]

    func load() -> ParentChannel? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(ParentChannel.self, from: data)
    }

    func save(_ channel: ParentChannel) {
        guard let data = try? JSONEncoder().encode(channel) else { return }
        let status = SecItemUpdate(baseQuery as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var add = baseQuery
            add[kSecValueData as String] = data
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(add as CFDictionary, nil)
        }
    }

    func delete() {
        SecItemDelete(baseQuery as CFDictionary)
    }
}
