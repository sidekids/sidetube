// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

// Fachliches Vokabular der kuratierten Mediathek. Rohwerte sind stabil (SwiftData, Seed-JSON).

/// Altersprofil eines Kindes.
enum AgeBand: String, Codable, CaseIterable, Identifiable, Sendable {
    case preschool, younger, kids, older
    var id: String { rawValue }

    var title: String {
        switch self {
        case .preschool: String(localized: "Vorschule (3–5)")
        case .younger: String(localized: "Jüngere Kinder (6–8)")
        case .kids: String(localized: "Kinder (9–11)")
        case .older: String(localized: "Ab 12")
        }
    }

   /// Alter, gegen das `ageMin`/`ageMax` der Inhalte geprüft werden (Untergrenze der Gruppe).
    var age: Int {
        switch self {
        case .preschool: 3
        case .younger: 6
        case .kids: 9
        case .older: 12
        }
    }
}

/// Pädagogische Kategorien. Manga bewusst geteilt: Zeichnen (kreativ) vs. Anime-&-Manga-Unterhaltung (12+).
enum ContentCategory: String, Codable, CaseIterable, Identifiable, Sendable {
    case knowledge, nature, technology, mediaLiteracy, news, creative, drawing, mangaDrawing, animeManga
    case stories, music, humor, society, environment
    var id: String { rawValue }

    var title: String {
        switch self {
        case .knowledge: String(localized: "Wissen")
        case .nature: String(localized: "Natur")
        case .technology: String(localized: "Technik")
        case .mediaLiteracy: String(localized: "Medienkompetenz")
        case .news: String(localized: "Nachrichten")
        case .creative: String(localized: "Kreativ")
        case .drawing: String(localized: "Zeichnen")
        case .mangaDrawing: String(localized: "Manga zeichnen")
        case .animeManga: String(localized: "Anime & Manga")
        case .stories: String(localized: "Geschichten")
        case .music: String(localized: "Musik")
        case .humor: String(localized: "Humor")
        case .society: String(localized: "Gesellschaft")
        case .environment: String(localized: "Umwelt")
        }
    }

    var systemImage: String {
        switch self {
        case .knowledge: "lightbulb"
        case .nature: "leaf"
        case .technology: "gearshape"
        case .mediaLiteracy: "iphone"
        case .news: "newspaper"
        case .creative: "paintpalette"
        case .drawing: "pencil.and.outline"
        case .mangaDrawing: "pencil.and.scribble"
        case .animeManga: "sparkles.tv"
        case .stories: "book"
        case .music: "music.note"
        case .humor: "face.smiling"
        case .society: "person.3"
        case .environment: "globe.europe.africa"
        }
    }

   /// Mindestalter, unter dem die Kategorie nie gezeigt wird.
    var minimumAge: Int {
        switch self {
        case .mangaDrawing: 8
        case .animeManga: 12
        default: 0
        }
    }
}

/// Sicherheitsstufe einer Quelle – kein simples allow/deny.
enum SourceTrust: String, Codable, CaseIterable, Identifiable, Sendable {
    case trustedChildSource, trustedSeries, perVideoReview, parentOnly, blocked
    var id: String { rawValue }

    var title: String {
        switch self {
        case .trustedChildSource: String(localized: "Vertrauenswürdige Kinderquelle")
        case .trustedSeries: String(localized: "Vertrauenswürdige Reihe")
        case .perVideoReview: String(localized: "Nur einzeln geprüfte Videos")
        case .parentOnly: String(localized: "Nur für Eltern")
        case .blocked: String(localized: "Gesperrt")
        }
    }

   /// Darf der Kanal im Kindermodus dynamisch durchstöbert werden (RSS/API-Liste ohne Einzelprüfung)?
    var allowsChannelBrowsing: Bool { self == .trustedChildSource }
}

/// Freigabestatus eines Inhalts. Neue Inhalte beginnen niemals mit `approved`.
enum ApprovalStatus: String, Codable, CaseIterable, Identifiable, Sendable {
    case discovered, reviewRequired, approved, rejected, expiredReview
    var id: String { rawValue }

    var title: String {
        switch self {
        case .discovered: String(localized: "Gefunden")
        case .reviewRequired: String(localized: "Prüfung nötig")
        case .approved: String(localized: "Freigegeben")
        case .rejected: String(localized: "Abgelehnt")
        case .expiredReview: String(localized: "Prüfung abgelaufen")
        }
    }
}

/// Sonderstatus für Nachrichten.
enum NewsStatus: String, Codable, CaseIterable, Sendable {
    case safe, sensitive, parentReview

    var title: String {
        switch self {
        case .safe: String(localized: "Nachricht: unbedenklich")
        case .sensitive: String(localized: "Nachricht: belastend")
        case .parentReview: String(localized: "Nachricht: Eltern prüfen")
        }
    }
}

/// Made-for-Kids-Kennzeichnung laut YouTube (nur mit Data API bekannt).
enum MadeForKidsStatus: String, Codable, Sendable {
    case unknown, madeForKids, notMadeForKids
}

/// Sensible Themen.
enum SensitiveTopic: String, Codable, CaseIterable, Identifiable, Sendable {
    case war, violence, death, disaster, crime, fear, politics, sexual, coarseLanguage, horror, adultMedia, advertising, other
    var id: String { rawValue }

    var title: String {
        switch self {
        case .war: String(localized: "Krieg")
        case .violence: String(localized: "Gewalt")
        case .death: String(localized: "Tod")
        case .disaster: String(localized: "Katastrophe")
        case .crime: String(localized: "Verbrechen")
        case .fear: String(localized: "Angst")
        case .politics: String(localized: "Politik")
        case .sexual: String(localized: "Sexualisierung")
        case .coarseLanguage: String(localized: "Derbe Sprache")
        case .horror: String(localized: "Horror")
        case .adultMedia: String(localized: "Medien ab 16/18")
        case .advertising: String(localized: "Werbung")
        case .other: String(localized: "Sonstiges")
        }
    }
}

/// Anbieter. Abspielbar: YouTube (IFrame-API) und PeerTube (Embed); Mediatheken werden als Quelle geführt.
enum ContentProvider: String, Codable, CaseIterable, Sendable {
    case youtube, peertube, kika, zdf, ard, arte

    var title: String {
        switch self {
        case .youtube: "YouTube"
        case .peertube: "PeerTube"
        case .kika: "KiKA"
        case .zdf: "ZDF"
        case .ard: "ARD"
        case .arte: "ARTE"
        }
    }

    var isPlayable: Bool { self == .youtube || self == .peertube }
}

/// Entscheidung im Audit-Trail.
enum ReviewDecision: String, Codable, Sendable {
    case discovered, approved, rejected, deferred, blockedSource, autoRejected, statusExpired
    /// Wünsche von Kindern (ADR 0001): Wunsch eingegangen und die Entscheidungen der Eltern dazu.
    case wished, wishFulfilled, wishRejected, wishDiscuss

    /// So steht die Entscheidung im Verlauf (wie Android `ParentLabels.decision`).
    var title: String {
        switch self {
        case .discovered: String(localized: "Aufgenommen")
        case .approved: String(localized: "Freigegeben")
        case .rejected: String(localized: "Abgelehnt")
        case .deferred: String(localized: "Zurückgestellt")
        case .blockedSource: String(localized: "Quelle gesperrt")
        case .autoRejected: String(localized: "Vom Filter abgelehnt")
        case .statusExpired: String(localized: "Prüfung fällig")
        case .wished: String(localized: "Gewünscht")
        case .wishFulfilled: String(localized: "Wunsch erfüllt")
        case .wishRejected: String(localized: "Wunsch: nicht jetzt")
        case .wishDiscuss: String(localized: "Wunsch: besprechen")
        }
    }
}
