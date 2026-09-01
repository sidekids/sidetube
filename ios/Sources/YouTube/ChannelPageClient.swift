// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// Schlüsselloser Ausweichweg für Kanäle: liest die öffentliche Kanalseite (`youtube.com/@handle`,
/// `/channel/UC…`, `/c/name`) und zieht Kanal-ID, Titel und Bild aus den Meta-Tags. 0 Quota, aber
/// abhängig vom YouTube-HTML – deshalb nur Fallback hinter der Data API.
struct ChannelPageClient {
    let http: HTTPClient

    func channel(id: String) async throws -> ChannelMetadata {
        try await fetch(path: "/channel/\(id)", expectedId: id)
    }

    func channel(handle: String) async throws -> ChannelMetadata {
        try await fetch(path: "/@\(handle)", expectedId: nil)
    }

    private func fetch(path: String, expectedId: String?) async throws -> ChannelMetadata {
        guard let url = URL(string: "https://www.youtube.com\(path)") else { throw YouTubeError.invalidURL }
   // Ohne diese Cookies liefert YouTube in der EU nur die Consent-Zwischenseite („Bevor Sie zu YouTube
   // weitergehen", ~34 KB, keine Meta-Tags). SOCS=CAI = Minimal-Zustimmung; wird nur für diese Anfrage gesendet,
   // nichts wird gespeichert (ephemere Session).
        let (data, status) = try await http.get(url, headers: ["Cookie": "SOCS=CAI; CONSENT=YES+cb", "Accept-Language": "de-DE,de;q=0.9"])
        guard status == 200 else { throw status == 404 ? YouTubeError.notFound : YouTubeError.http(status: status) }
        let html = String(decoding: data, as: UTF8.self)   // tolerant: ein ungültiges Byte darf die Seite nicht verwerfen
        guard let meta = Self.parse(html) else {
            throw YouTubeError.decoding("Kanalseite ohne Meta-Tags (\(data.count) Bytes)")
        }
        if let expectedId, meta.id != expectedId { throw YouTubeError.notFound }
        return meta
    }

   /// Meta-Tags: `itemprop="identifier"` (Kanal-ID), `og:title`, `og:image`. Reihenfolge der Attribute egal.
    static func parse(_ html: String) -> ChannelMetadata? {
        guard let id = metaContent(html, attribute: "itemprop", name: "identifier"), id.hasPrefix("UC"),
              let title = metaContent(html, attribute: "property", name: "og:title") else { return nil }
        let image = metaContent(html, attribute: "property", name: "og:image") ?? ""
        return ChannelMetadata(id: id, title: decodeEntities(title), thumbnailUrl: image, description: "",
                               subscriberCount: nil, videoCount: nil, uploadsPlaylistId: YouTubeIDs.uploadsPlaylistId(forChannel: id))
    }

    private static func metaContent(_ html: String, attribute: String, name: String) -> String? {
        let patterns = [
            #"<meta\s+\#(attribute)="\#(name)"\s+content="([^"]*)""#,
            #"<meta\s+content="([^"]*)"\s+\#(attribute)="\#(name)""#,
        ]
        for pattern in patterns {
            if let match = html.firstMatch(of: try! Regex(pattern)), let value = match.output[1].substring {
                return String(value)
            }
        }
        return nil
    }

    private static func decodeEntities(_ text: String) -> String {
        text.replacingOccurrences(of: "&amp;", with: "&").replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'").replacingOccurrences(of: "&lt;", with: "<").replacingOccurrences(of: "&gt;", with: ">")
    }
}
