// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// Elternkanal (ADR 0005): wohin SideTube neue Wünsche meldet – ein Nextcloud-Talk-Gespräch, in
/// dem ein Bot schreiben darf. Kommt als Einrichtungscode aus `scripts/talk-wunschkanal.sh`.
nonisolated struct ParentChannel: Codable, Equatable {
    /// Wurzel der Nextcloud, immer https.
    let server: URL
    /// Gesprächs-Token in Talk.
    let conversation: String
    /// Gemeinsamer Schlüssel des Bots. Nie anzeigen, nie loggen.
    let secret: String
    /// Nextcloud-Nutzer, die in jeder Meldung erwähnt werden – nur Erwähnungen melden in Talk sicher.
    let mentions: [String]

    var messageURL: URL {
        server.appending(path: "ocs/v2.php/apps/spreed/api/v1/bot/\(conversation)/message")
    }
}

nonisolated enum ParentChannelError: Error, Equatable, LocalizedError {
    case invalidCode(String)

    var errorDescription: String? {
        switch self {
        case .invalidCode(let reason): "Der Einrichtungscode passt nicht: \(reason)"
        }
    }
}

nonisolated extension ParentChannel {
    /// Liest den Einrichtungscode (JSON, Version 1, Art „talk") und prüft jedes Feld.
    static func parse(_ code: String) throws -> ParentChannel {
        guard let data = code.trimmingCharacters(in: .whitespacesAndNewlines).data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ParentChannelError.invalidCode("kein Einrichtungscode")
        }
        guard object["v"] as? Int == 1 else { throw ParentChannelError.invalidCode("unbekannte Version") }
        guard object["art"] as? String == "talk" else { throw ParentChannelError.invalidCode("unbekannte Art") }

        guard let text = object["server"] as? String, let components = URLComponents(string: text),
              components.scheme == "https", let host = components.host, !host.isEmpty,
              components.user == nil, components.password == nil,
              components.query == nil, components.fragment == nil,
              var server = components.url else {
            throw ParentChannelError.invalidCode("Server muss mit https:// beginnen")
        }
        // Ohne abschließenden Schrägstrich, damit `appending(path:)` keinen doppelten erzeugt.
        if server.absoluteString.hasSuffix("/"), let trimmed = URL(string: String(server.absoluteString.dropLast())) {
            server = trimmed
        }

        guard let conversation = object["gespraech"] as? String, conversation.fullyMatches("[A-Za-z0-9]{4,32}") else {
            throw ParentChannelError.invalidCode("Gespräch fehlt")
        }
        guard let secret = object["schluessel"] as? String, (40...128).contains(secret.count),
              secret.allSatisfy({ $0.isASCII && !$0.isWhitespace && !$0.isNewline }) else {
            throw ParentChannelError.invalidCode("Schlüssel fehlt oder ist zu kurz")
        }
        guard let mentions = object["erwaehnen"] as? [String], !mentions.isEmpty, mentions.count <= 8,
              mentions.allSatisfy({ $0.fullyMatches("[A-Za-z0-9._@ \\-]{1,64}") }) else {
            throw ParentChannelError.invalidCode("keine gültigen Nutzernamen zum Erwähnen")
        }
        return ParentChannel(server: server, conversation: conversation, secret: secret, mentions: mentions)
    }
}

nonisolated extension String {
    /// Ganzer Text passt auf das Muster (`\z`: auch kein Zeilenende am Schluss).
    func fullyMatches(_ pattern: String) -> Bool {
        range(of: "^(?:\(pattern))\\z", options: .regularExpression) != nil
    }
}
