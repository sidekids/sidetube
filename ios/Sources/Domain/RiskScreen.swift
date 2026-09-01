import Foundation

/// Automatische Risikoerkennung. Nur ein Filter: darf ablehnen oder markieren,
/// aber nie pädagogisch freigeben. Unauffällige Titel können trotzdem problematisch sein.
struct RiskAssessment: Equatable, Sendable {
   /// Begriffe, bei denen der Inhalt automatisch abgelehnt wird (Eltern können bewusst übersteuern).
    var hardBlockTerms: [String] = []
    var topics: Set<SensitiveTopic> = []
    var matchedTerms: [String] = []
    var isShort = false
    var isLive = false

    var requiresReview: Bool { !topics.isEmpty || isShort || isLive }
    var isHardBlocked: Bool { !hardBlockTerms.isEmpty }
}

enum RiskScreen {
    private struct Terms: Decodable {
        var hardBlock: [String]
        var topics: [String: [String]]
        var exceptions: [String]?
    }

    private static let terms: Terms = (try? ContentBundle.load(Terms.self, "risk-terms")) ?? Terms(hardBlock: [], topics: [:])

    /// Begriffe, die zur sofortigen Ablehnung führen.
    static var hardBlock: [String] { terms.hardBlock }

    /// Begriffe je Thema; ein Treffer markiert den Inhalt zur Prüfung.
    static var topicTerms: [(SensitiveTopic, [String])] {
        terms.topics.compactMap { key, value in SensitiveTopic(rawValue: key).map { ($0, value) } }
    }

   /// Wörter, die trotz passendem Wortanfang nicht zählen („Krieger“ ist kein Krieg).
    private static var exceptions: [String] { terms.exceptions ?? [] }

   /// Ein Begriff zählt nur am Wortanfang. Sonst trifft „täter“ in „Attentäter“ und „nackt“ in „knackt“;
    /// Stämme wie „entführ“ sollen dagegen weiter auf „entführt“ und „Entführung“ passen.
    static func matches(_ term: String, title: String, description: String? = nil) -> Bool {
        let haystack = " \(title) \(description ?? "") ".lowercased()
        return matches(term, in: words(of: haystack), haystack: haystack)
    }

    private static func words(of haystack: String) -> [Substring] {
        haystack.split(whereSeparator: { !$0.isLetter && !$0.isNumber })
    }

    private static func matches(_ term: String, in words: [Substring], haystack: String) -> Bool {
        guard !term.contains(" ") else { return haystack.contains(" \(term)") }
        return words.contains { word in
            word.hasPrefix(term) && !exceptions.contains { word.hasPrefix($0) }
        }
    }

    static func assess(title: String, description: String? = nil, durationSeconds: Int? = nil) -> RiskAssessment {
        let haystack = " \(title) \(description ?? "") ".lowercased()
        let words = words(of: haystack)
        var result = RiskAssessment()
        result.hardBlockTerms = hardBlock.filter { matches($0, in: words, haystack: haystack) }
        for (topic, terms) in topicTerms {
            let hits = terms.filter { matches($0, in: words, haystack: haystack) }
            if !hits.isEmpty {
                result.topics.insert(topic)
                result.matchedTerms += hits
            }
        }
        result.isShort = haystack.contains("#shorts") || haystack.contains("#short ") || (durationSeconds.map { $0 <= 60 } ?? false)
        result.isLive = haystack.contains("livestream") || haystack.contains(" live ") || haystack.contains("🔴")
        return result
    }

   /// Nachrichten-Einstufung: belastende Themen → sensitiv (nicht auf Home hervorheben, Eltern prüfen).
    static func newsStatus(for assessment: RiskAssessment) -> NewsStatus {
        let heavy: Set<SensitiveTopic> = [.war, .violence, .death, .disaster, .crime, .fear, .horror]
        if !assessment.topics.isDisjoint(with: heavy) { return .sensitive }
        if assessment.topics.contains(.politics) { return .parentReview }
        return .safe
    }
}
