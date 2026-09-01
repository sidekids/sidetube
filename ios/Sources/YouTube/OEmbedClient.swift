import Foundation

/// YouTube-oEmbed: kostenlos, ohne Schlüssel, 0 Quota. Liefert Titel, Autor, Vorschaubild.
/// Hinweis: `author_url` ist inzwischen meist `youtube.com/@handle`, eine Kanal-ID gibt es nur bei `/channel/UC…`.
struct OEmbedClient {
    let http: HTTPClient

    private struct Response: Decodable {
        var title: String
        var author_name: String
        var author_url: String
        var thumbnail_url: String
        var type: String
    }

    func video(id: String) async throws -> VideoMetadata {
        let response = try await fetch(target: "https://www.youtube.com/watch?v=\(id)")
        return VideoMetadata(id: id, title: response.title, thumbnailUrl: response.thumbnail_url,
                             channelId: Self.channelId(fromAuthorURL: response.author_url),
                             channelTitle: response.author_name, description: "", duration: nil)
    }

    func playlist(id: String) async throws -> PlaylistMetadata {
        let response = try await fetch(target: "https://www.youtube.com/playlist?list=\(id)")
        return PlaylistMetadata(id: id, title: response.title, thumbnailUrl: response.thumbnail_url,
                                channelId: Self.channelId(fromAuthorURL: response.author_url),
                                channelTitle: response.author_name, description: "")
    }

    private func fetch(target: String) async throws -> Response {
        var components = URLComponents(string: "https://www.youtube.com/oembed")!
        components.queryItems = [URLQueryItem(name: "url", value: target), URLQueryItem(name: "format", value: "json")]
        let (data, status) = try await http.get(components.url!)
        guard status == 200 else { throw status == 404 || status == 401 ? YouTubeError.notFound : YouTubeError.http(status: status) }
        do { return try JSONDecoder().decode(Response.self, from: data) } catch { throw YouTubeError.decoding("oEmbed") }
    }

    static func channelId(fromAuthorURL url: String) -> String? {
        guard let range = url.range(of: "/channel/") else { return nil }
        let id = url[range.upperBound...].split(separator: "/").first.map(String.init) ?? ""
        return id.isEmpty ? nil : id
    }
}
