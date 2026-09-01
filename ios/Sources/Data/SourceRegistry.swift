import Foundation

/// Bekannte Quellen mit Sicherheitsstufe. Alle YouTube-IDs über die öffentliche Kanalseite verifiziert
/// (`<meta itemprop="identifier">`). Begründungen: docs/content-research.md. Popularität ist kein Qualitätsmerkmal.
enum SourceRegistry {
    private struct File: Decodable {
        var verifiedAt: String?
        var sources: [Entry]
    }

    private struct Entry: Decodable {
        var channelId: String
        var handle: String?
        var title: String
        var provider: ContentProvider = .youtube
        var trust: SourceTrust
        var isNews: Bool = false
        var defaultAgeMin: Int = 0
        var defaultCategory: ContentCategory?
        var notes: String?
        var verified: Bool = false
    }

    /// Alle bekannten Quellen inklusive PeerTube-Instanzen.
    static let allDefinitions: [SourceDefinition] = load()

    static var definitions: [SourceDefinition] { allDefinitions.filter { $0.provider != .peertube } }
    static var peerTubeInstances: [SourceDefinition] { allDefinitions.filter { $0.provider == .peertube } }
    static var blockedChannelIds: Set<String> { Set(allDefinitions.filter { $0.trust == .blocked }.map(\.channelId)) }

    private static func load(bundle: Bundle = .main) -> [SourceDefinition] {
        guard let file = try? ContentBundle.load(File.self, "sources", bundle: bundle) else { return [] }
        let verifiedAt = file.verifiedAt.flatMap { ISO8601DateFormatter().date(from: $0 + "T00:00:00Z") }
        return file.sources.map { entry in
            SourceDefinition(channelId: entry.channelId, handle: entry.handle, title: entry.title, provider: entry.provider,
                             trust: entry.trust, isNews: entry.isNews, defaultAgeMin: entry.defaultAgeMin,
                             defaultCategory: entry.defaultCategory, notes: entry.notes,
                             verifiedAt: entry.verified ? verifiedAt : nil)
        }
    }
}
