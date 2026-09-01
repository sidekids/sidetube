// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// YouTube Data API v3 – nur die vier Listen-Endpunkte der Android-App. Jeder Aufruf kostet 1 Quota-Einheit.
struct YouTubeDataAPIClient {
    let http: HTTPClient
    let apiKey: String
    var baseURL = URL(string: "https://www.googleapis.com/youtube/v3")!

   // MARK: DTOs (nur benötigte Felder)

    struct ListResponse<Item: Decodable>: Decodable {
        var items: [Item]?
        var nextPageToken: String?
    }

    struct Thumbnails: Decodable {
        struct Thumbnail: Decodable { var url: String }
        var `default`: Thumbnail?
        var medium: Thumbnail?
        var high: Thumbnail?
        var standard: Thumbnail?
   /// Beste verfügbare Auflösung bis „high" (Playlist-Karten brauchen nicht mehr).
        var best: String? { (high ?? medium ?? `default`)?.url }
    }

    struct ChannelItem: Decodable {
        struct Snippet: Decodable { var title: String; var description: String?; var thumbnails: Thumbnails? }
        struct ContentDetails: Decodable {
            struct Related: Decodable { var uploads: String? }
            var relatedPlaylists: Related?
        }
        struct Statistics: Decodable { var subscriberCount: String?; var videoCount: String? }
        var id: String
        var snippet: Snippet
        var contentDetails: ContentDetails?
        var statistics: Statistics?
    }

    struct VideoItem: Decodable {
        struct Snippet: Decodable {
            var title: String; var description: String?; var thumbnails: Thumbnails?
            var channelId: String?; var channelTitle: String?
        }
        struct ContentDetails: Decodable { var duration: String? }
        var id: String
        var snippet: Snippet
        var contentDetails: ContentDetails?
    }

    struct PlaylistItem: Decodable {
        struct Snippet: Decodable {
            var title: String; var description: String?; var thumbnails: Thumbnails?
            var channelId: String?; var channelTitle: String?
        }
        var id: String
        var snippet: Snippet
    }

    struct PlaylistItemItem: Decodable {
        struct Snippet: Decodable {
            struct ResourceId: Decodable { var videoId: String? }
            var title: String
            var thumbnails: Thumbnails?
            var channelTitle: String?
            var videoOwnerChannelTitle: String?
            var position: Int?
            var resourceId: ResourceId?
        }
        var snippet: Snippet
    }

    struct SearchItem: Decodable {
        struct Identifier: Decodable { var channelId: String? }
        struct Snippet: Decodable {
            var title: String
            var description: String?
            var thumbnails: Thumbnails?
        }
        var id: Identifier
        var snippet: Snippet
    }

   // MARK: Endpunkte

    func channel(id: String) async throws -> ChannelMetadata? {
        let response: ListResponse<ChannelItem> = try await get("channels", ["part": "snippet,contentDetails,statistics", "id": id])
        return response.items?.first.map(Self.map)
    }

    func channel(handle: String) async throws -> ChannelMetadata? {
        let response: ListResponse<ChannelItem> = try await get("channels", ["part": "snippet,contentDetails,statistics", "forHandle": handle])
        return response.items?.first.map(Self.map)
    }

   /// Kanalsuche. Kostet 100 Kontingenteinheiten je Aufruf (Tagesbudget 10 000) – deshalb nur auf
   /// ausdrückliche Eingabe der Eltern, nie im Hintergrund und nie im Kindermodus.
    func searchChannels(query: String, maxResults: Int = 10) async throws -> [ChannelMetadata] {
        let response: ListResponse<SearchItem> = try await get("search", [
            "part": "snippet", "type": "channel", "q": query,
            "maxResults": String(maxResults), "safeSearch": "strict",
        ])
        return (response.items ?? []).compactMap { item in
            guard let id = item.id.channelId else { return nil }
            return ChannelMetadata(id: id, title: item.snippet.title,
                                   thumbnailUrl: item.snippet.thumbnails?.best ?? "",
                                   description: item.snippet.description ?? "",
                                   subscriberCount: nil, videoCount: nil,
                                   uploadsPlaylistId: YouTubeIDs.uploadsPlaylistId(forChannel: id))
        }
    }

    func video(id: String) async throws -> VideoMetadata? {
        let response: ListResponse<VideoItem> = try await get("videos", ["part": "snippet,contentDetails", "id": id])
        guard let item = response.items?.first else { return nil }
        return VideoMetadata(id: item.id, title: item.snippet.title,
                             thumbnailUrl: item.snippet.thumbnails?.best ?? YouTubeIDs.defaultThumbnail(videoId: item.id),
                             channelId: item.snippet.channelId, channelTitle: item.snippet.channelTitle ?? "",
                             description: item.snippet.description ?? "", duration: item.contentDetails?.duration)
    }

    func playlist(id: String) async throws -> PlaylistMetadata? {
        let response: ListResponse<PlaylistItem> = try await get("playlists", ["part": "snippet", "id": id])
        guard let item = response.items?.first else { return nil }
        return PlaylistMetadata(id: item.id, title: item.snippet.title, thumbnailUrl: item.snippet.thumbnails?.best ?? "",
                                channelId: item.snippet.channelId, channelTitle: item.snippet.channelTitle ?? "",
                                description: item.snippet.description ?? "")
    }

   /// Eine Seite (max. 50). Private/gelöschte Videos (ohne Vorschaubild) werden übersprungen – sie sind nicht abspielbar.
    func playlistItems(playlistId: String, pageToken: String? = nil, maxResults: Int = 50) async throws -> PlaylistPage {
        var query = ["part": "snippet", "playlistId": playlistId, "maxResults": String(maxResults)]
        if let pageToken { query["pageToken"] = pageToken }
        let response: ListResponse<PlaylistItemItem> = try await get("playlistItems", query)
        var videos: [PlaylistVideo] = []
        for item in response.items ?? [] {
            guard let videoId = item.snippet.resourceId?.videoId, let thumbnail = item.snippet.thumbnails?.best else { continue }
            videos.append(PlaylistVideo(videoId: videoId, title: item.snippet.title, thumbnailUrl: thumbnail,
                                        channelTitle: item.snippet.videoOwnerChannelTitle ?? item.snippet.channelTitle ?? "",
                                        position: item.snippet.position ?? videos.count))
        }
        return PlaylistPage(videos: videos, nextPageToken: response.nextPageToken)
    }

   // MARK: Intern

    private static func map(_ item: ChannelItem) -> ChannelMetadata {
        ChannelMetadata(id: item.id, title: item.snippet.title, thumbnailUrl: item.snippet.thumbnails?.best ?? "",
                        description: item.snippet.description ?? "", subscriberCount: item.statistics?.subscriberCount,
                        videoCount: item.statistics?.videoCount,
                        uploadsPlaylistId: item.contentDetails?.relatedPlaylists?.uploads ?? YouTubeIDs.uploadsPlaylistId(forChannel: item.id))
    }

    private func get<T: Decodable>(_ path: String, _ query: [String: String]) async throws -> T {
        var components = URLComponents(url: baseURL.appending(path: path), resolvingAgainstBaseURL: false)!
        components.queryItems = query.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
            + [URLQueryItem(name: "key", value: apiKey)]
        let (data, status) = try await http.get(components.url!)
        guard status == 200 else { throw status == 404 ? YouTubeError.notFound : YouTubeError.http(status: status) }
        do { return try JSONDecoder().decode(T.self, from: data) } catch { throw YouTubeError.decoding(path) }
    }
}
