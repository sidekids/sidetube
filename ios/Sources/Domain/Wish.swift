// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

// Wünsche von Kindern (docs/adr/0001-wuensche-von-kindern.md). Rohwerte wie in der ADR und auf Android.

/// Herkunft eines Wunsches.
enum WishKind: String, Codable, CaseIterable, Sendable {
    /// Stichwort aus der Suche – das Kind sieht dabei nichts Fremdes.
    case thema
    /// „Mehr davon" zu einem freigegebenen Video (Endkarte, Player-Menü).
    case mehrDavon
    /// Gesperrte neue Folge eines Kanals mit Stufe „Vertrauenswürdige Reihe".
    case neueFolge

    /// Herkunft, wie Eltern sie in der Prüfliste lesen.
    var origin: String {
        switch self {
        case .thema: "Aus der Suche"
        case .mehrDavon: "Mehr davon"
        case .neueFolge: "Neu bei deinen Kanälen"
        }
    }

    var systemImage: String {
        switch self {
        case .thema: "magnifyingglass"
        case .mehrDavon: "plus.square.on.square"
        case .neueFolge: "sparkles.tv"
        }
    }
}

/// Stand eines Wunsches. `erfuellt` und `abgelehnt` sind endgültig; `besprechen` bleibt offen fürs Gespräch.
enum WishStatus: String, Codable, CaseIterable, Sendable {
    case offen, erfuellt, abgelehnt, besprechen

    /// So liest das Kind den Stand unter „Meine Wünsche".
    var kidTitle: String {
        switch self {
        case .offen: "Wartet"
        case .erfuellt: "Freigegeben"
        case .abgelehnt: "Nicht jetzt"
        case .besprechen: "Sprechen wir drüber"
        }
    }

    var parentTitle: String {
        switch self {
        case .offen: "Offen"
        case .erfuellt: "Erledigt"
        case .abgelehnt: "Abgelehnt"
        case .besprechen: "Besprechen"
        }
    }

    var systemImage: String {
        switch self {
        case .offen: "hourglass"
        case .erfuellt: "checkmark.circle.fill"
        case .abgelehnt: "hand.raised"
        case .besprechen: "bubble.left.and.bubble.right"
        }
    }

    /// Noch in der Prüfliste der Eltern.
    var isPending: Bool { self == .offen || self == .besprechen }

    /// Erlaubte Übergänge: offen → alles; besprechen → erfüllt/abgelehnt/erneut besprechen; Endzustände bleiben.
    func canMove(to next: WishStatus) -> Bool {
        switch self {
        case .offen: next != .offen
        case .besprechen: next == .erfuellt || next == .abgelehnt || next == .besprechen
        case .erfuellt, .abgelehnt: false
        }
    }
}

/// Ein Wunsch eines Kinderprofils. Bleibt auf dem Gerät; ohne Beziehung zu `KidProfile`, damit die
/// Schemaänderung rein additiv ist (neue Entität, bestehende unverändert). Beim Löschen eines Profils
/// räumt `ProfileRepository.delete` die Wünsche mit ab.
@Model
final class KidWish {
    @Attribute(.unique) var id: UUID
    var profileID: UUID
    var kindRaw: String
    var statusRaw: String
    /// Stichwort (thema), wie das Kind es eingegeben hat.
    var topic: String?
    /// Video als Anlass (mehrDavon) bzw. gewünschte Folge (neueFolge).
    var videoId: String?
    var videoTitle: String?
    var thumbnailUrl: String?
    var channelId: String?
    var channelTitle: String?
    /// Kurze, freiwillige Antwort der Eltern an das Kind.
    var parentReply: String?
    /// Inhalt, mit dem der Wunsch erfüllt wurde (Weg zum Inhalt unter „Meine Wünsche").
    var fulfilledYoutubeId: String?
    var createdAt: Date
    var decidedAt: Date?

    init(id: UUID = UUID(), profileID: UUID, kind: WishKind, topic: String? = nil, videoId: String? = nil,
         videoTitle: String? = nil, thumbnailUrl: String? = nil, channelId: String? = nil, channelTitle: String? = nil,
         createdAt: Date = .now) {
        self.id = id
        self.profileID = profileID
        self.kindRaw = kind.rawValue
        self.statusRaw = WishStatus.offen.rawValue
        self.topic = topic
        self.videoId = videoId
        self.videoTitle = videoTitle
        self.thumbnailUrl = thumbnailUrl
        self.channelId = channelId
        self.channelTitle = channelTitle
        self.createdAt = createdAt
    }

    var kind: WishKind { WishKind(rawValue: kindRaw) ?? .thema }
    var status: WishStatus {
        get { WishStatus(rawValue: statusRaw) ?? .offen }
        set { statusRaw = newValue.rawValue }
    }

    /// Schlüssel im Verlauf der Freigaben (`ReviewEvent.itemVersion`), der alle Ereignisse dieses Wunsches bündelt.
    var historyKey: String { "wish:\(id.uuidString)" }

    /// Was das Kind sich gewünscht hat, in einem Satz.
    var headline: String {
        switch kind {
        case .thema: "„\(topic ?? "")“"
        case .mehrDavon: "Mehr wie „\(videoTitle ?? videoId ?? "")“"
        case .neueFolge: videoTitle ?? videoId ?? ""
        }
    }
}

/// Eingabe für einen neuen Wunsch – unabhängig von SwiftData prüfbar.
struct WishDraft: Equatable, Sendable {
    var kind: WishKind
    var topic: String?
    var videoId: String?
    var videoTitle: String?
    var thumbnailUrl: String?
    var channelId: String?
    var channelTitle: String?

    static func thema(_ topic: String) -> WishDraft { WishDraft(kind: .thema, topic: topic) }

    /// Vergleichsschlüssel für Dubletten: Thema ohne Groß-/Kleinschreibung, Akzente und Mehrfach-Leerzeichen;
    /// bei Videos die Video-ID.
    var duplicateKey: String? {
        switch kind {
        case .thema: topic.map(Self.normalizedTopic).flatMap { $0.isEmpty ? nil : $0 }
        case .mehrDavon, .neueFolge: videoId.flatMap { $0.isEmpty ? nil : $0 }
        }
    }

    nonisolated static func normalizedTopic(_ text: String) -> String {
        text.folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: Locale(identifier: "de"))
            .split(whereSeparator: \.isWhitespace).joined(separator: " ")
    }

    /// Längstes Stichwort – ein Wunsch ist ein Thema, kein Brief.
    static let maxTopicLength = 60
}
