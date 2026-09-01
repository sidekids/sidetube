import Foundation

/// Ein Einstieg für alle Anbieter: Link → `WhitelistItemDraft` (YouTube oder PeerTube); Kanalseiten je Anbieter.
final class MediaResolver {
    let youtube: YouTubeRepository
    let peertube: PeerTubeClient

    init(youtube: YouTubeRepository, peertube: PeerTubeClient) {
        self.youtube = youtube
        self.peertube = peertube
    }

    func resolve(urlString: String) async throws -> WhitelistItemDraft {
        if let parsed = YouTubeURLParser.parse(urlString) { return try await youtube.resolve(parsed) }
        if let parsed = PeerTubeURLParser.parse(urlString) { return try await resolve(parsed) }
        throw YouTubeError.invalidURL
    }

    func resolve(_ parsed: ParsedPeerTubeURL) async throws -> WhitelistItemDraft {
        switch parsed {
        case .video(let host, let id):
            let video = try await peertube.video(host: host, id: id)
            return WhitelistItemDraft(type: .video, youtubeId: video.id, title: video.title, thumbnailUrl: video.thumbnailUrl,
                                      channelTitle: video.channelTitle, provider: .peertube, sourceChannelId: video.channelId,
                                      sourceUrl: video.watchUrl, description: video.description, durationSeconds: video.durationSeconds,
                                      isNSFW: video.nsfw, isLive: video.isLive)
        case .channel(let host, let name):
            let channel = try await peertube.channel(host: host, name: name)
            return WhitelistItemDraft(type: .channel, youtubeId: channel.id, title: channel.title, thumbnailUrl: channel.thumbnailUrl,
                                      channelTitle: nil, provider: .peertube, sourceChannelId: channel.id,
                                      sourceUrl: "https://\(host)/c/\(name)@\(host)/videos")
        }
    }

   /// Kanalvideos seitenweise, anbieterneutral (PeerTube: `start` als Token; YouTube: RSS→API wie bisher).
    func channelPage(channelId: String, pageToken: String?) async throws -> PlaylistPage {
        if let (host, name) = PeerTubeIDs.split(channelId), !name.isEmpty {
            let start = pageToken.flatMap(Int.init) ?? 0
            let result = try await peertube.channelVideos(host: host, name: name, start: start)
            let videos = result.videos.enumerated().map { offset, video in
                PlaylistVideo(videoId: video.id, title: video.title, thumbnailUrl: video.thumbnailUrl, channelTitle: video.channelTitle, position: start + offset)
            }
            let next = start + result.videos.count
            return PlaylistPage(videos: videos, nextPageToken: next < result.total && !result.videos.isEmpty ? String(next) : nil)
        }
        guard let uploads = YouTubeIDs.uploadsPlaylistId(forChannel: channelId) else { throw YouTubeError.invalidURL }
        return try await youtube.playlistItems(playlistId: uploads, pageToken: pageToken)
    }
}
