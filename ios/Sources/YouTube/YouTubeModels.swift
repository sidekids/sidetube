// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

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
}
