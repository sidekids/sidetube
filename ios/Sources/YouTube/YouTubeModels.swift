// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import CoreGraphics
import Foundation

/// Aufgelöste Metadaten – entsprechen `YouTubeMetadata` der Android-App.
struct ChannelMetadata: Equatable, Sendable {
    var id: String
    var title: String
    var thumbnailUrl: String
    var description: String
    var subscriberCount: String?
    var videoCount: String?
    var uploadsPlaylistId: String?
}

struct VideoMetadata: Equatable, Sendable {
    var id: String
    var title: String
    var thumbnailUrl: String
    var channelId: String?
    var channelTitle: String
    var description: String
    var duration: String?
}

struct PlaylistMetadata: Equatable, Sendable {
    var id: String
    var title: String
    var thumbnailUrl: String
    var channelId: String?
    var channelTitle: String
    var description: String
}

/// Ein Video innerhalb einer Playlist/Kanal-Uploads (`PlaylistVideo`).
struct PlaylistVideo: Equatable, Sendable {
    var videoId: String
    var title: String
    var thumbnailUrl: String
    var channelTitle: String
    var position: Int
   /// Kanal des Videos, soweit bekannt. Im Playlist-Feed nennt jeder Eintrag seinen eigenen Kanal –
   /// eine Playlist darf Videos fremder Kanäle enthalten.
    var channelId: String? = nil
   /// Der Feed verlinkt das Video unter `/shorts/` (nur aus dem Feed bekannt).
    var isShort: Bool = false
   /// Angekündigte Premiere oder Livestream (`views="0"` im Feed), noch nicht gesendet.
    var isUpcoming: Bool = false
}

/// Seite einer Playlist. `nextPageToken == PlaylistPage.continueWithAPIToken` heißt: die erste
/// Seite kam aus dem RSS-Feed, die nächste Seite muss die Data API von vorn liefern
/// (der Cache dedupliziert per (channelId, videoId)).
struct PlaylistPage: Equatable, Sendable {
    static let continueWithAPIToken = "__api_first_page__"
    var videos: [PlaylistVideo]
    var nextPageToken: String?
    var hasMorePages: Bool { nextPageToken != nil }
}

enum YouTubeError: Error, Equatable {
    case invalidURL
    case notFound
    case missingAPIKey
    case http(status: Int)
    case decoding(String)
    case network(String)
}

enum YouTubeIDs {
   /// Uploads-Playlist eines Kanals: `UC…` → `UU…` (und zurück).
    static func uploadsPlaylistId(forChannel channelId: String) -> String? {
        guard channelId.hasPrefix("UC") else { return nil }
        return "UU" + channelId.dropFirst(2)
    }

    static func channelId(forUploadsPlaylist playlistId: String) -> String? {
        guard playlistId.hasPrefix("UU") else { return nil }
        return "UC" + playlistId.dropFirst(2)
    }

    static func defaultThumbnail(videoId: String) -> String {
        "https://i.ytimg.com/vi/\(videoId)/hqdefault.jpg"
    }

   /// Vorschaubild in der Größe, in der es gezeigt wird. YouTube liefert dieselbe Datei in vier
   /// Stufen; `hqdefault` misst 480 × 360 und wiegt 40 kB, `default` misst 120 × 90 und wiegt 5 kB.
   /// Für eine Listenzeile von rund 100 px ist die große Fassung acht Mal zu schwer – das kostet
   /// Funkzeit, Speicher und Dekodierarbeit, besonders auf kleinen Geräten.
    static func thumbnail(_ url: String, forWidth width: CGFloat, scale: CGFloat = 3) -> String {
        guard url.contains("i.ytimg.com/vi/") else { return url }   // fremde Anbieter unverändert lassen
        let pixels = width * scale
        let variant = pixels <= 120 ? "default" : (pixels <= 320 ? "mqdefault" : "hqdefault")
        for known in ["maxresdefault", "hqdefault", "mqdefault", "sddefault", "default"] where url.contains("/\(known).") {
            return url.replacingOccurrences(of: "/\(known).", with: "/\(variant).")
        }
        return url
    }
}
