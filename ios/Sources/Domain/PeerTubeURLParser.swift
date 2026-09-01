// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// PeerTube-Adressen. Instanz = Host; Videos per shortUUID (`/w/<short>`) oder UUID (`/videos/watch/<uuid>`),
/// Kanäle per `/c/<name>[@host]` oder `/video-channels/<name>[@host]`.
enum ParsedPeerTubeURL: Equatable, Sendable {
    case video(host: String, id: String)
    case channel(host: String, name: String)
}

/// Kennungen im Datenmodell: Videos `pt:<host>:<id>`, Kanäle `pt:<host>:<name>`, Instanz `pt:<host>`.
enum PeerTubeIDs {
    static let prefix = "pt:"

    static func videoId(host: String, id: String) -> String { "\(prefix)\(host.lowercased()):\(id)" }
    static func channelId(host: String, name: String) -> String { "\(prefix)\(host.lowercased()):\(name.lowercased())" }
    static func instanceId(host: String) -> String { "\(prefix)\(host.lowercased())" }

    static func isPeerTube(_ id: String) -> Bool { id.hasPrefix(prefix) }

   /// `pt:host:id` → (host, id); `pt:host` → (host, "")
    static func split(_ id: String) -> (host: String, id: String)? {
        guard id.hasPrefix(prefix) else { return nil }
        let rest = id.dropFirst(prefix.count)
        guard let colon = rest.firstIndex(of: ":") else { return (String(rest), "") }
        return (String(rest[..<colon]), String(rest[rest.index(after: colon)...]))
    }

    static func instanceId(of id: String) -> String? { split(id).map { instanceId(host: $0.host) } }

   /// Embed-URL ohne P2P, ohne Instanz-Link, ohne Warnhinweis (geschlossener Kinder-Player).
    static func embedURL(videoId: String) -> URL? {
        guard let (host, id) = split(videoId), !id.isEmpty else { return nil }
        return URL(string: "https://\(host)/videos/embed/\(id)?p2p=0&peertubeLink=0&warningTitle=0&autoplay=1&title=0")
    }

    static func watchURL(videoId: String) -> URL? {
        guard let (host, id) = split(videoId), !id.isEmpty else { return nil }
        return URL(string: "https://\(host)/w/\(id)")
    }
}

enum PeerTubeURLParser {
    static func parse(_ input: String) -> ParsedPeerTubeURL? {
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        if !text.contains("://") { text = "https://" + text }
        guard let components = URLComponents(string: text), let host = components.host?.lowercased(), host.contains(".") else { return nil }
        let segments = components.path.split(separator: "/").map(String.init).filter { !$0.isEmpty }
        guard segments.count >= 2 else { return nil }
        switch segments[0] {
        case "w":
            return .video(host: host, id: segments[1])
        case "videos" where segments[1] == "watch" || segments[1] == "embed":
            return segments.count >= 3 ? .video(host: host, id: segments[2]) : nil
        case "c", "video-channels", "a", "accounts":
            let handle = segments[1]
            let parts = handle.split(separator: "@", maxSplits: 1).map(String.init)
            let name = parts[0]
            let channelHost = parts.count == 2 ? parts[1].lowercased() : host
            guard !name.isEmpty else { return nil }
            return .channel(host: channelHost, name: name)
        default:
            return nil
        }
    }
}
