// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// Mehrere Einträge der Prüfliste auf einmal prüfen (ADR 0004). Reine Regel ohne Speicher:
/// welcher Eintrag überhaupt gesammelt freigegeben werden darf, und was er aus den Sammelwerten bekommt.
/// Gleich auf Android; Speichern steht in `CurationRepository.sammelFreigeben`.
enum Sammelpruefung {
    /// Vermerk im Verlauf jedes einzelnen Eintrags.
    static let vermerk = "Sammelprüfung"

    /// Warum ein Eintrag eine Einzelprüfung braucht. Reihenfolge = Vorrang, wenn mehreres zutrifft
    /// (gleich Android `Einzelpruefungsgrund`).
    enum Grund: Equatable, Sendable, CaseIterable {
        /// Steht nicht mehr zur Prüfung (inzwischen freigegeben oder abgelehnt).
        case nichtOffen
        case quelleGesperrt
        case quelleNurFuerEltern
        /// Themen- oder harter Treffer des Filters – am Eintrag, im Vermerk oder in der frischen Vorprüfung.
        case filtertreffer
        /// Nachricht mit Status „Eltern prüfen" oder „belastend".
        case nachricht

        var text: String {
            switch self {
            case .nichtOffen: "nicht mehr offen"
            case .quelleGesperrt: "Quelle gesperrt"
            case .quelleNurFuerEltern: "nur für Eltern"
            case .filtertreffer: "Filtertreffer"
            case .nachricht: "Nachricht prüfen"
            }
        }
    }

    /// Was die Regel über einen Eintrag wissen muss – ohne SwiftData, damit sie sich ohne Speicher prüfen lässt.
    struct Pruefling: Equatable, Sendable {
        var istKanal = false
        var istOffen = true
        var ageMin = 0
        var ageMax: Int?
        var category: ContentCategory?
        /// Stufe der wirksamen Quelle (bei PeerTube ggf. der Instanz); `nil` = nie eingestuft.
        var quellenStufe: SourceTrust?
        var quellenAlter: Int?
        var quellenKategorie: ContentCategory?
        /// Themen am Eintrag (aus der Entdeckung).
        var risikoThemen: Set<SensitiveTopic> = []
        /// Der Vermerk nennt Treffer („Risikobegriffe: …").
        var vermerkteTreffer = false
        /// Frische Vorprüfung des Titels – fängt harte Treffer, die über „Zurück zur Prüfung" wieder offen sind.
        var vorpruefung: RiskAssessment?
        var isNews = false
        var newsStatus: NewsStatus?
    }

    /// `nil` = sammelbar. Sonst der (erste) Grund für die Einzelprüfung. Shorts und Live zählen nicht als Treffer.
    static func grund(_ p: Pruefling) -> Grund? {
        if !p.istOffen { return .nichtOffen }
        if p.quellenStufe == .blocked { return .quelleGesperrt }
        if p.quellenStufe == .parentOnly { return .quelleNurFuerEltern }
        if !p.risikoThemen.isEmpty || p.vermerkteTreffer
            || p.vorpruefung?.isHardBlocked == true || p.vorpruefung?.topics.isEmpty == false { return .filtertreffer }
        if p.isNews, p.newsStatus == .parentReview || p.newsStatus == .sensitive { return .nachricht }
        return nil
    }

    static func istSammelbar(_ p: Pruefling) -> Bool { grund(p) == nil }

    // MARK: Sammelwerte

    /// Kategorie in der Sammelmaske: „wie vorgeschlagen" lässt jedem Eintrag seine eigene.
    enum KategorieWahl: Hashable, Sendable {
        case wieVorgeschlagen
        case keine
        case kategorie(ContentCategory)
    }

    /// „Gesperrt" ist nicht wählbar – Sperren bleibt eine Einzelentscheidung (ADR 0004, 2.).
    static let waehlbareKanalStufen = SourceTrust.allCases.filter { $0 != .blocked }

    struct Werte: Equatable, Sendable {
        /// `nil` = wie vorgeschlagen.
        var alter: Int?
        var kategorie: KategorieWahl = .wieVorgeschlagen
        /// Gilt nur für Kanäle; voreingestellt die vorsichtigste Stufe, die etwas zeigt.
        var kanalStufe: SourceTrust = Kanaleinstufung.standardStufe

        init(alter: Int? = nil, kategorie: KategorieWahl = .wieVorgeschlagen,
             kanalStufe: SourceTrust = Kanaleinstufung.standardStufe) {
            self.alter = alter
            self.kategorie = kategorie
            self.kanalStufe = kanalStufe
        }
    }

    /// Was der Eintrag ohne jede Änderung bekäme – dasselbe wie ein unverändertes „Freigeben" im Einzelweg:
    /// Videos ihr eigenes Alter (auch 0) und ihre Kategorie, Kanäle die Vorauswahl aus Eintrag und Quelle
    /// (ADR 0003, wie Android `Kanaleinstufung.vorauswahl(quelle, eintrag)`): das erste Alter ab 3 – erst
    /// des Eintrags, dann der Quelle –, sonst 6; die Kategorie des Eintrags.
    static func vorschlag(_ p: Pruefling) -> (ageMin: Int, category: ContentCategory?) {
        if p.istKanal {
            let bereich = Kanaleinstufung.altersbereich
            let alter = [p.ageMin, p.quellenAlter].compactMap { $0 }.first { $0 >= bereich.lowerBound }
                .map { min($0, bereich.upperBound) } ?? Kanaleinstufung.standardAlter
            return (alter, p.category)
        }
        return (p.ageMin, p.category)
    }

    struct Freigabe: Equatable, Sendable {
        var ageMin: Int
        var ageMax: Int?
        var category: ContentCategory?
        /// Nur bei Kanälen: die Stufe, die die Quelle bekommt.
        var trust: SourceTrust?

        /// Hebt die Kategorie das Alter an? (Für den Hinweis in der Maske.)
        var kategorieHebtAn = false
    }

    /// Freigabe je Eintrag: gesetzte Werte gelten für alle, sonst der Vorschlag; das Kategorie-Mindestalter
    /// hebt je Eintrag an (wie im Einzelweg). Ein Höchstalter fällt nie unter das Mindestalter.
    static func freigabe(_ p: Pruefling, mit werte: Werte) -> Freigabe {
        let v = vorschlag(p)
        let bereich = Kanaleinstufung.altersbereich
        let alter = werte.alter.map { min(max($0, bereich.lowerBound), bereich.upperBound) } ?? v.ageMin
        let kategorie: ContentCategory? = switch werte.kategorie {
        case .wieVorgeschlagen: v.category
        case .keine: nil
        case .kategorie(let c): c
        }
        let effektiv = max(alter, kategorie?.minimumAge ?? 0)
        if p.istKanal {
            let stufe = waehlbareKanalStufen.contains(werte.kanalStufe) ? werte.kanalStufe : Kanaleinstufung.standardStufe
            return Freigabe(ageMin: effektiv, ageMax: p.ageMax, category: kategorie, trust: stufe, kategorieHebtAn: effektiv > alter)
        }
        return Freigabe(ageMin: effektiv, ageMax: p.ageMax.map { max($0, effektiv) }, category: kategorie, trust: nil,
                        kategorieHebtAn: effektiv > alter)
    }

    /// Hinweis in der Maske, z. B. „2 brauchen eine Einzelprüfung: Risikotreffer des Filters (1), Quelle gesperrt (1)".
    static func hinweis(_ gruende: [Grund]) -> String? {
        guard !gruende.isEmpty else { return nil }
        let teile = Grund.allCases.compactMap { g -> String? in
            let n = gruende.filter { $0 == g }.count
            return n > 0 ? "\(g.text) (\(n))" : nil
        }
        let wer = gruende.count == 1 ? "1 Eintrag braucht" : "\(gruende.count) brauchen"
        return "\(wer) eine Einzelprüfung: " + teile.joined(separator: ", ")
    }
}

extension Sammelpruefung.Pruefling {
    /// Liest einen Eintrag samt wirksamer Quelle; der Filter läuft dabei frisch über den Titel.
    init(item: WhitelistItem, source: CuratedSource?) {
        let offen: Set<ApprovalStatus> = [.discovered, .reviewRequired, .expiredReview]
        self.init(istKanal: item.type == .channel, istOffen: offen.contains(item.approvalStatus),
                  ageMin: item.ageMin, ageMax: item.ageMax, category: item.category,
                  quellenStufe: source?.trust, quellenAlter: source?.defaultAgeMin, quellenKategorie: source?.defaultCategory,
                  risikoThemen: item.sensitiveTopics,
                  vermerkteTreffer: item.editorialNotes?.contains(Self.trefferVermerk) == true,
                  vorpruefung: RiskScreen.assess(title: item.title),
                  isNews: item.isNews, newsStatus: item.newsStatus)
    }

    /// So vermerkt `CurationRepository.discover` Treffer des Filters im Vermerk.
    static let trefferVermerk = "Risikobegriffe:"
}
