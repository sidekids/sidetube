// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import CryptoKit
import Foundation

/// Ergebnis einer Meldung an die Eltern. Ein Fehlschlag ändert nichts am Wunsch.
nonisolated enum ParentNotifyResult: Equatable {
    case sent
    case notConfigured
    /// HTTP-Status, oder nil bei Netzfehler.
    case failed(status: Int?)
}

/// Meldet den Eltern, dass ein Wunsch angelegt wurde (ADR 0005).
nonisolated protocol ParentNotifier: Sendable {
    func notifyNewWish(openCount: Int) async -> ParentNotifyResult
}

/// Ohne Einrichtung: meldet nichts.
nonisolated struct NoParentNotifier: ParentNotifier {
    func notifyNewWish(openCount: Int) async -> ParentNotifyResult { .notConfigured }
}

/// Schmaler POST nur für den Elternkanal – getrennt vom `HTTPClient` der YouTube-Abfragen.
nonisolated protocol HTTPPoster: Sendable {
    func post(_ url: URL, headers: [String: String], body: Data) async throws -> Int
}

nonisolated enum TalkBot {
    /// Was in das Gespräch geschrieben wird – ohne Namen des Kindes, ohne Titel oder Thema.
    static func wishMessage(mentions: [String], openCount: Int) -> String {
        let prefix = mentions.map(mention).joined(separator: " ")
        return "\(prefix) SideTube: neuer Wunsch (\(max(openCount, 1)) offen)"
    }

    /// `@anna`; Namen mit Leer- oder Sonderzeichen in Anführungszeichen, wie Talk sie erwartet.
    static func mention(_ user: String) -> String {
        user.fullyMatches("[A-Za-z0-9_.\\-]+") ? "@\(user)" : "@\"\(user)\""
    }

    /// HMAC-SHA256(Schlüssel, Zufallswert + Nachrichtentext), klein-hex – so prüft Talk Bot-Nachrichten.
    static func signature(secret: String, random: String, message: String) -> String {
        let key = SymmetricKey(data: Data(secret.utf8))
        let mac = HMAC<SHA256>.authenticationCode(for: Data((random + message).utf8), using: key)
        return mac.map { String(format: "%02x", $0) }.joined()
    }

    /// 32 Byte Zufall als Hex.
    static func randomHex() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return bytes.map { String(format: "%02x", $0) }.joined()
    }
}

/// Meldet über einen Nextcloud-Talk-Bot. Die Einrichtung wird bei jeder Meldung frisch gelesen,
/// damit „Entfernen" sofort wirkt.
nonisolated struct TalkBotNotifier: ParentNotifier {
    let channel: @Sendable () -> ParentChannel?
    let poster: HTTPPoster
    var random: @Sendable () -> String = { TalkBot.randomHex() }

    func notifyNewWish(openCount: Int) async -> ParentNotifyResult {
        guard let channel = channel() else { return .notConfigured }
        return await send(TalkBot.wishMessage(mentions: channel.mentions, openCount: openCount), via: channel)
    }

    /// Auch für „Test senden" in den Einstellungen.
    func send(_ message: String, via channel: ParentChannel) async -> ParentNotifyResult {
        let nonce = random()
        let headers = [
            "Content-Type": "application/json",
            "Accept": "application/json",
            "OCS-APIRequest": "true",
            "X-Nextcloud-Talk-Bot-Random": nonce,
            "X-Nextcloud-Talk-Bot-Signature": TalkBot.signature(secret: channel.secret, random: nonce, message: message),
        ]
        guard let body = try? JSONSerialization.data(withJSONObject: ["message": message]) else { return .failed(status: nil) }
        do {
            let status = try await poster.post(channel.messageURL, headers: headers, body: body)
            return status == 201 || status == 200 ? .sent : .failed(status: status)
        } catch {
            return .failed(status: nil)
        }
    }
}

/// Eigene Sitzung ohne Cache, Cookies und Weiterleitungen: Die Signatur soll an keinen anderen
/// Server gehen als den eingetragenen.
nonisolated final class URLSessionPoster: HTTPPoster, Sendable {
    private let session: URLSession

    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 15
        configuration.waitsForConnectivity = false
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        session = URLSession(configuration: configuration, delegate: NoRedirects(), delegateQueue: nil)
    }

    func post(_ url: URL, headers: [String: String], body: Data) async throws -> Int {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = body
        request.cachePolicy = .reloadIgnoringLocalCacheData
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
        let (_, response) = try await session.data(for: request)
        return (response as? HTTPURLResponse)?.statusCode ?? 0
    }

    /// Lehnt jede Weiterleitung ab. Rückruf-Fassung statt `async`: Die async-Variante bringt
    /// Swift 6.3 in einem nonisolated Typ zum Absturz.
    private final class NoRedirects: NSObject, URLSessionTaskDelegate, Sendable {
        func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                        newRequest request: URLRequest, completionHandler: @escaping @Sendable (URLRequest?) -> Void) {
            completionHandler(nil)
        }
    }
}
