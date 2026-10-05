// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// Die drei Entscheidungen der Eltern beim Hinzufügen eines Kanals (ADR 0003): Stufe, Mindestalter,
/// Kategorie. Reine Regel ohne Speicher, damit Vorauswahl, Alter und Sperre an einer Stelle stehen –
/// gleich für Link-Vorschau, Kanalsuche und „Kanal prüfen" aus Wünschen.
struct Kanaleinstufung: Equatable, Sendable {
    var trust: SourceTrust
    var ageMin: Int
    var category: ContentCategory?

    /// Wählbares Mindestalter – wie bei Videos (Freigabe-Maske).
    static let altersbereich = 3...16
    /// Ohne bekannte Quelle die vorsichtigste Stufe, die überhaupt etwas zeigt.
    static let standardStufe: SourceTrust = .perVideoReview
    static let standardAlter = 6

    init(trust: SourceTrust = Kanaleinstufung.standardStufe, ageMin: Int = Kanaleinstufung.standardAlter,
         category: ContentCategory? = nil) {
        self.trust = trust
        self.ageMin = ageMin
        self.category = category
    }

    /// Vorauswahl aus dem, was die Quelle schon trägt. Ein Mindestalter von 0 heißt im Register
    /// „nicht festgelegt" – dann gilt der Standard, statt 3 vorzuschlagen, was niemand gewählt hat.
    static func vorauswahl(trust: SourceTrust?, defaultAgeMin: Int?, defaultCategory: ContentCategory?) -> Kanaleinstufung {
        let alter: Int
        if let defaultAgeMin, defaultAgeMin > 0 {
            alter = min(max(defaultAgeMin, altersbereich.lowerBound), altersbereich.upperBound)
        } else {
            alter = standardAlter
        }
        return Kanaleinstufung(trust: trust ?? standardStufe, ageMin: alter, category: defaultCategory)
    }

    static func vorauswahl(aus source: CuratedSource?) -> Kanaleinstufung {
        vorauswahl(trust: source?.trust, defaultAgeMin: source?.defaultAgeMin, defaultCategory: source?.defaultCategory)
    }

    /// Hat die Kategorie ein höheres Mindestalter, gilt das höhere – so wie `ContentPolicy` die Kategorie
    /// ohnehin nie jünger zeigt. Gespeichert wird dieser Wert, damit Quelle und Anzeige übereinstimmen.
    var effektivesMindestalter: Int { max(ageMin, category?.minimumAge ?? 0) }

    /// Die Ansicht soll sagen, dass die Kategorie das gewählte Alter anhebt.
    var kategorieHebtAlterAn: Bool { effektivesMindestalter > ageMin }

    enum Ergebnis: Equatable, Sendable {
        /// Quelle als gesperrt speichern, der Kanal kommt nicht in die Liste des Kindes.
        case sperren
        /// Quelle speichern und den Kanal-Eintrag gleich freigeben – die Entscheidung *ist* die Prüfung.
        case hinzufuegen
    }

    var ergebnis: Ergebnis { trust == .blocked ? .sperren : .hinzufuegen }

    /// Meldung nach „Gesperrt" – sagt ausdrücklich, dass nichts beim Kind ankam und was künftig passiert.
    static func sperrMeldung(kanal: String, profil: String) -> String {
        "„\(kanal)“ ist jetzt gesperrt und steht nicht in der Liste von \(profil). Links aus diesem Kanal werden künftig abgewiesen."
    }
}

extension SourceTrust {
    /// Ein Satz, was die Stufe für das Kind bedeutet – steht bei der Wahl, weil eine zu großzügige Stufe
    /// sonst schneller gewählt als verstanden ist (ADR 0003, Begründung).
    var erklaerung: String {
        switch self {
        case .trustedChildSource: "Das Kind darf im Kanal stöbern; neue Videos erscheinen ohne Einzelprüfung, nur mit Risikofilter."
        case .trustedSeries: "Neue Folgen erscheinen gesperrt mit Bild und Titel – das Kind kann sie sich wünschen, ihr gebt sie frei."
        case .perVideoReview: "Das Kind sieht nur Videos dieses Kanals, die ihr einzeln freigegeben habt."
        case .parentOnly: "Nur für euch in den Einstellungen – das Kind sieht nichts aus diesem Kanal."
        case .blocked: "Der Kanal kommt nicht in die Liste; künftige Links dieses Kanals werden abgewiesen."
        }
    }
}
