import Foundation

// Fachliches Vokabular der kuratierten Mediathek. Rohwerte sind stabil (SwiftData, Seed-JSON).

/// Altersprofil eines Kindes.
enum AgeBand: String, Codable, CaseIterable, Identifiable, Sendable {
    case preschool, younger, kids, older
    var id: String { rawValue }

    var title: String {
        switch self {
        case .preschool: "Vorschule (3–5)"
        case .younger: "Jüngere Kinder (6–8)"
        case .kids: "Kinder (9–11)"
        case .older: "Ab 12"
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
        case .knowledge: "Wissen"
        case .nature: "Natur"
        case .technology: "Technik"
        case .mediaLiteracy: "Medienkompetenz"
        case .news: "Nachrichten"
        case .creative: "Kreativ"
        case .drawing: "Zeichnen"
        case .mangaDrawing: "Manga zeichnen"
        case .animeManga: "Anime & Manga"
        case .stories: "Geschichten"
        case .music: "Musik"
        case .humor: "Humor"
        case .society: "Gesellschaft"
        case .environment: "Umwelt"
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
        case .trustedChildSource: "Vertrauenswürdige Kinderquelle"
        case .trustedSeries: "Vertrauenswürdige Reihe"
        case .perVideoReview: "Nur einzeln geprüfte Videos"
        case .parentOnly: "Nur für Eltern"
        case .blocked: "Gesperrt"
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
        case .discovered: "Gefunden"
        case .reviewRequired: "Prüfung nötig"
        case .approved: "Freigegeben"
        case .rejected: "Abgelehnt"
        case .expiredReview: "Prüfung abgelaufen"
        }
    }
}

/// Sonderstatus für Nachrichten.
enum NewsStatus: String, Codable, CaseIterable, Sendable {
    case safe, sensitive, parentReview

    var title: String {
        switch self {
        case .safe: "Nachricht: unbedenklich"
        case .sensitive: "Nachricht: belastend"
        case .parentReview: "Nachricht: Eltern prüfen"
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
        case .war: "Krieg"
        case .violence: "Gewalt"
        case .death: "Tod"
        case .disaster: "Katastrophe"
        case .crime: "Verbrechen"
        case .fear: "Angst"
        case .politics: "Politik"
        case .sexual: "Sexualisierung"
        case .coarseLanguage: "Derbe Sprache"
        case .horror: "Horror"
        case .adultMedia: "Medien ab 16/18"
        case .advertising: "Werbung"
        case .other: "Sonstiges"
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
}
