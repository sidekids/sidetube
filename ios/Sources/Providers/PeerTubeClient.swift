// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// PeerTube-REST-API (ohne Schlüssel): Video, Kanal, Kanalvideos. Föderierte Videos: Embed/Thumbnail vom Ursprungs-Host.
struct PeerTubeClient {
    let http: HTTPClient

    struct VideoDTO: Decodable {
        struct Channel: Decodable { var name: String; var displayName: String; var host: String }
        var uuid: String
        var shortUUID: String?
        var name: String
        var description: String?
        var duration: Int
        var nsfw: Bool
        var isLive: Bool?
        var thumbnailPath: String?
        var previewPath: String?
        var publishedAt: String?
        var channel: Channel
    }

    struct ChannelDTO: Decodable {
        struct Avatar: Decodable { var path: String }
        var name: String
        var displayName: String
        var host: String
        var description: String?
        var avatars: [Avatar]?
    }

    struct Page<T: Decodable>: Decodable { var total: Int; var data: [T] }

   /// Aufgelöstes Video mit stabiler Kennung `pt:<ursprungs-host>:<shortUUID>`.
    struct Video: Equatable, Sendable {
        var id: String
        var title: String
        var description: String?
        var durationSeconds: Int
        var nsfw: Bool
        var isLive: Bool
        var thumbnailUrl: String
        var channelId: String
        var channelTitle: String
        var watchUrl: String
    }

    func video(host: String, id: String) async throws -> Video {
        let dto: VideoDTO = try await get(host: host, path: "/api/v1/videos/\(id)")
        return Self.map(dto, queriedHost: host)
    }

    func channel(host: String, name: String) async throws -> ChannelMetadata {
        let dto: ChannelDTO = try await get(host: host, path: "/api/v1/video-channels/\(name)@\(host)")
        let avatar = dto.avatars?.last.map { "https://\(dto.host)\($0.path)" } ?? ""
        return ChannelMetadata(id: PeerTubeIDs.channelId(host: dto.host, name: dto.name), title: dto.displayName, thumbnailUrl: avatar,
                               description: dto.description ?? "", subscriberCount: nil, videoCount: nil, uploadsPlaylistId: nil)
    }

   /// Seite der Kanalvideos (nsfw=false serverseitig, Lives werden zusätzlich hier verworfen).
    func channelVideos(host: String, name: String, start: Int = 0, count: Int = 25) async throws -> (videos: [Video], total: Int) {
        let page: Page<VideoDTO> = try await get(host: host, path: "/api/v1/video-channels/\(name)@\(host)/videos",
                                                 query: ["start": String(start), "count": String(count), "sort": "-publishedAt", "nsfw": "false"])
        return (page.data.map { Self.map($0, queriedHost: host) }.filter { !$0.isLive }, page.total)
    }

    static func map(_ dto: VideoDTO, queriedHost: String) -> Video {
        let originHost = dto.channel.host.lowercased()
        let id = dto.shortUUID ?? dto.uuid
        let thumb = (dto.previewPath ?? dto.thumbnailPath).map { "https://\(queriedHost)\($0)" } ?? ""
        return Video(id: PeerTubeIDs.videoId(host: originHost, id: id), title: dto.name, description: dto.description,
                     durationSeconds: dto.duration, nsfw: dto.nsfw, isLive: dto.isLive ?? false, thumbnailUrl: thumb,
                     channelId: PeerTubeIDs.channelId(host: originHost, name: dto.channel.name), channelTitle: dto.channel.displayName,
                     watchUrl: "https://\(originHost)/w/\(id)")
    }

    private func get<T: Decodable>(host: String, path: String, query: [String: String] = [:]) async throws -> T {
        var components = URLComponents()
        components.scheme = "https"; components.host = host; components.path = path
        if !query.isEmpty { components.queryItems = query.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) } }
        guard let url = components.url else { throw YouTubeError.invalidURL }
        let (data, status) = try await http.get(url, headers: ["Accept": "application/json"])
        guard status == 200 else { throw status == 404 ? YouTubeError.notFound : YouTubeError.http(status: status) }
        do { return try JSONDecoder().decode(T.self, from: data) } catch { throw YouTubeError.decoding("peertube \(path)") }
    }
}
