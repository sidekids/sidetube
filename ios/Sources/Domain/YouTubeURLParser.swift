import Foundation

/// Inhaltstypen der Whitelist. Rohwerte entsprechen der Android-App (Export-Kompatibilität).
enum YouTubeContentType: String, Codable, CaseIterable, Sendable {
    case channel = "CHANNEL"
    case video = "VIDEO"
    case playlist = "PLAYLIST"
}

/// Ergebnis der URL-Analyse. Handle und Custom-Name müssen später per API zu einer Kanal-ID aufgelöst werden.
enum ParsedYouTubeURL: Equatable, Sendable {
    case video(id: String)
    case playlist(id: String)
    case channel(id: String)
    case channelHandle(String)
    case channelCustomName(String)
}

/// Spiegelt die Regeln von `YouTubeUrlParser.kt` der Android-App:
/// - `youtu.be/<id>` → Video
/// - `list=` auf `/playlist` oder `/watch` hat Vorrang vor `v=`
/// - `/watch?v=`, `/shorts/`, `/embed/`, `/live/` → Video
/// - `/channel/<id>`, `/c/<name>`, `/@handle` → Kanal
/// Erweiterung gegenüber Android: fehlendes Schema (`youtube.com/...`) wird ergänzt.
enum YouTubeURLParser {
    private static let youtubeHosts: Set<String> = [
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
    ]
    private static let shortHost = "youtu.be"

    static func parse(_ input: String) -> ParsedYouTubeURL? {
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        if !text.contains("://") { text = "https://" + text }
        guard let components = URLComponents(string: text),
              let host = components.host?.lowercased() else { return nil }

        let segments = components.path.split(separator: "/").map(String.init).filter { !$0.isEmpty }
        var query: [String: String] = [:]
        for item in components.queryItems ?? [] {
            if let value = item.value, !value.isEmpty, query[item.name] == nil { query[item.name] = value }
        }

        if host == shortHost {
            guard let id = segments.first else { return nil }
            return .video(id: id)
        }
        guard youtubeHosts.contains(host) else { return nil }

        if let list = query["list"], segments.first == "playlist" || segments.first == "watch" {
            return .playlist(id: list)
        }
        guard let first = segments.first else { return nil }
        let second = segments.dropFirst().first

        switch first {
        case "watch":
            return query["v"].map { .video(id: $0) }
        case "shorts", "embed", "live":
            return second.map { .video(id: $0) }
        case "channel":
            return second.map { .channel(id: $0) }
        case "c":
            return second.map { .channelCustomName($0) }
        default:
            if first.hasPrefix("@"), first.count > 1 {
                return .channelHandle(String(first.dropFirst()))
            }
            return nil
        }
    }
}
