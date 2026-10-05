// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// Hybride Auflösung wie `HybridYouTubeRepositoryImpl` (Android, „Strategie E"):
/// kostenlose Quellen (oEmbed, RSS) zuerst, Data API danach. Invidious-Fallback folgt in v0.2.
final class YouTubeRepository {
    private let oEmbed: OEmbedClient
    private let rss: RSSFeedClient
    private let page: ChannelPageClient
    private let api: YouTubeDataAPIClient?

    init(http: HTTPClient, apiKey: String?) {
        oEmbed = OEmbedClient(http: http)
        rss = RSSFeedClient(http: http)
        page = ChannelPageClient(http: http)
        api = apiKey.map { YouTubeDataAPIClient(http: http, apiKey: $0) }
    }

    var hasAPIKey: Bool { api != nil }

   // MARK: Metadaten

    func video(id: String) async throws -> VideoMetadata {
        if let result = try? await oEmbed.video(id: id) { return result }
        guard let result = try await requireAPI().video(id: id) else { throw YouTubeError.notFound }
        return result
    }

    func playlist(id: String) async throws -> PlaylistMetadata {
        if let result = try? await oEmbed.playlist(id: id) { return result }
        guard let result = try await requireAPI().playlist(id: id) else { throw YouTubeError.notFound }
        return result
    }

   /// Data API zuerst (vollständige Daten), sonst Kanalseite (schlüssellos, 0 Quota).
    func channel(id: String) async throws -> ChannelMetadata {
        if let api, let result = try? await api.channel(id: id) { return result }
        return try await page.channel(id: id)
    }

   /// Handle (`@name`) und Legacy-`/c/name` werden beide über `forHandle` aufgelöst (wie Android); Fallback Kanalseite.
    func channel(handle: String) async throws -> ChannelMetadata {
        if let api, let result = try? await api.channel(handle: handle) { return result }
        return try await page.channel(handle: handle)
    }

   // MARK: Playlist-Inhalte

   /// Kanalsuche über die Data API – braucht einen Schlüssel und kostet 100 Kontingenteinheiten je Aufruf.
    func searchChannels(query: String) async throws -> [ChannelMetadata] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return [] }
        if let api {
            return try await api.searchChannels(query: trimmed)
        }

        // Without a Data-API key we can still find channels already present in
        // SideTube's curated source register. This is deliberately not a
        // general YouTube search and does not approve any content.
        let needle = trimmed.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
        return SourceRegistry.definitions
            .filter { source in
                let title = source.title.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
                let handle = (source.handle ?? "").folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
                return title.contains(needle) || handle.contains(needle)
            }
            .map { source in
                ChannelMetadata(id: source.channelId, title: source.title, thumbnailUrl: "",
                                description: source.notes ?? "", subscriberCount: nil, videoCount: nil,
                                uploadsPlaylistId: YouTubeIDs.uploadsPlaylistId(forChannel: source.channelId))
            }
    }

   /// Neueste Uploads eines bekannten Kanals mit Datum (schlüsselfreier Feed, 0 Quota) – für „Neu bei deinen Kanälen".
    func channelFeed(channelId: String) async throws -> [FeedEntry] {
        try await rss.channelFeed(channelId: channelId)
    }

   /// Erste Seite aus dem schlüsselfreien Feed (Kanal-Uploads `UU…` bzw. Playlist), danach Data API.
    func playlistItems(playlistId: String, pageToken: String? = nil) async throws -> PlaylistPage {
        if pageToken == nil, let channelId = YouTubeIDs.channelId(forUploadsPlaylist: playlistId),
           let videos = try? await rss.channelVideos(channelId: channelId), !videos.isEmpty {
            return PlaylistPage(videos: videos, nextPageToken: PlaylistPage.continueWithAPIToken)
        }
        // Registered per-video sources (for example Wissen macht Ah!) are not
        // browsable channels. An empty RSS feed therefore means “no channel
        // list”, not “show an API-key error”. Individual approved videos can
        // still be opened from the Videos area.
        if pageToken == nil,
           let channelId = YouTubeIDs.channelId(forUploadsPlaylist: playlistId),
           let source = SourceRegistry.definitions.first(where: { $0.channelId == channelId }),
           !source.trust.allowsChannelBrowsing {
            return PlaylistPage(videos: [], nextPageToken: nil)
        }
        // Gewöhnliche Playlist: der schlüsselfreie Playlist-Feed liefert die erste Seite (bis zu 15
        // Einträge, 0 Quota). Weiterblättern nur mit Schlüssel über die Data API; ohne Schlüssel ist
        // die Liste hier zu Ende statt einen Fehler zu zeigen.
        if pageToken == nil, YouTubeIDs.channelId(forUploadsPlaylist: playlistId) == nil {
            do {
                let videos = try await rss.playlistVideos(playlistId: playlistId)
                if !videos.isEmpty || api == nil {
                    return PlaylistPage(videos: videos, nextPageToken: api == nil || videos.isEmpty ? nil : PlaylistPage.continueWithAPIToken)
                }
            } catch {
                if api == nil { throw error }
            }
        }
        let token = pageToken == PlaylistPage.continueWithAPIToken ? nil : pageToken
        return try await requireAPI().playlistItems(playlistId: playlistId, pageToken: token)
    }

   // MARK: URL → Whitelist-Eintrag

    func resolve(urlString: String) async throws -> WhitelistItemDraft {
        guard let parsed = YouTubeURLParser.parse(urlString) else { throw YouTubeError.invalidURL }
        return try await resolve(parsed)
    }

    func resolve(_ parsed: ParsedYouTubeURL) async throws -> WhitelistItemDraft {
        switch parsed {
        case .video(let id):
            let v = try await video(id: id)
            return WhitelistItemDraft(type: .video, youtubeId: v.id, title: v.title, thumbnailUrl: v.thumbnailUrl, channelTitle: v.channelTitle)
        case .playlist(let id):
            let p = try await playlist(id: id)
            return WhitelistItemDraft(type: .playlist, youtubeId: p.id, title: p.title, thumbnailUrl: p.thumbnailUrl, channelTitle: p.channelTitle)
        case .channel(let id):
            return Self.draft(for: try await channel(id: id))
        case .channelHandle(let handle), .channelCustomName(let handle):
            return Self.draft(for: try await channel(handle: handle))
        }
    }

    private static func draft(for c: ChannelMetadata) -> WhitelistItemDraft {
        WhitelistItemDraft(type: .channel, youtubeId: c.id, title: c.title, thumbnailUrl: c.thumbnailUrl, channelTitle: nil)
    }

    private func requireAPI() throws -> YouTubeDataAPIClient {
        guard let api else { throw YouTubeError.missingAPIKey }
        return api
    }
}
