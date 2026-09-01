import Foundation
import Testing
@testable import sidetube

/// Antwortet nach URL-Teilstring; protokolliert Aufrufe, um Quota-Verhalten zu prüfen.
final class StubHTTPClient: HTTPClient {
    var routes: [(match: String, status: Int, body: String)] = []
    var requested: [String] = []

    func on(_ match: String, status: Int = 200, body: String) { routes.append((match, status, body)) }

    func get(_ url: URL, headers: [String: String]) async throws -> (data: Data, status: Int) {
        let text = url.absoluteString
        requested.append(text)
        guard let route = routes.first(where: { text.contains($0.match) }) else { return (Data("Not Found".utf8), 404) }
        return (Data(route.body.utf8), route.status)
    }

    func calls(containing part: String) -> Int { requested.filter { $0.contains(part) }.count }
}

enum Fixtures {
    // Echte oEmbed-Antwort (2026-08-30), gekürzt.
    static let oEmbedVideo = """
    {"title":"Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)","author_name":"Rick Astley",
     "author_url":"https://www.youtube.com/@RickAstleyYT","type":"video","height":113,"width":200,"version":"1.0",
     "provider_name":"YouTube","provider_url":"https://www.youtube.com/","thumbnail_height":360,"thumbnail_width":480,
     "thumbnail_url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg","html":"<iframe></iframe>"}
    """
    static let oEmbedPlaylist = """
    {"title":"Gute-Nacht-Lieder","author_name":"Kinderkanal","author_url":"https://www.youtube.com/channel/UCkinder123",
     "type":"video","thumbnail_url":"https://i.ytimg.com/vi/abc/hqdefault.jpg","version":"1.0"}
    """
    // Echter Atom-Feed (2026-08-30), auf zwei Einträge gekürzt.
    static let rss = """
    <?xml version="1.0" encoding="UTF-8"?>
    <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
     <link rel="self" href="http://www.youtube.com/feeds/videos.xml?channel_id=UC_x5XG1OV2P6uZZ5FSM9Ttw"/>
     <id>yt:channel:_x5XG1OV2P6uZZ5FSM9Ttw</id>
     <yt:channelId>_x5XG1OV2P6uZZ5FSM9Ttw</yt:channelId>
     <title>Google for Developers</title>
     <author><name>Google for Developers</name><uri>https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw</uri></author>
     <published>2007-08-23T00:34:43+00:00</published>
     <entry>
      <id>yt:video:qzLrKKjdsPU</id>
      <yt:videoId>qzLrKKjdsPU</yt:videoId>
      <yt:channelId>UC_x5XG1OV2P6uZZ5FSM9Ttw</yt:channelId>
      <title>Build voice-first apps with Gemini 3.5 Transcribe</title>
      <link rel="alternate" href="https://www.youtube.com/shorts/qzLrKKjdsPU"/>
      <author><name>Google for Developers</name><uri>https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw</uri></author>
      <published>2026-08-27T01:00:08+00:00</published>
      <media:group>
       <media:title>Build voice-first apps with Gemini 3.5 Transcribe</media:title>
       <media:thumbnail url="https://i2.ytimg.com/vi/qzLrKKjdsPU/hqdefault.jpg" width="480" height="360"/>
       <media:description>Beschreibung &amp; mehr</media:description>
      </media:group>
     </entry>
     <entry>
      <yt:videoId>zweiteID</yt:videoId>
      <title>Zweites Video</title>
      <published>2026-08-20T01:00:08+00:00</published>
     </entry>
    </feed>
    """
    static let apiChannel = """
    {"kind":"youtube#channelListResponse","items":[{"kind":"youtube#channel","id":"UC_x5XG1OV2P6uZZ5FSM9Ttw",
     "snippet":{"title":"Google for Developers","description":"Dev Kanal","thumbnails":{"default":{"url":"https://yt3/d.jpg"},"medium":{"url":"https://yt3/m.jpg"},"high":{"url":"https://yt3/h.jpg"}}},
     "contentDetails":{"relatedPlaylists":{"uploads":"UU_x5XG1OV2P6uZZ5FSM9Ttw"}},
     "statistics":{"subscriberCount":"2500000","videoCount":"6000"}}]}
    """
    static let apiVideo = """
    {"items":[{"id":"vid42","snippet":{"title":"API Video","description":"d","channelId":"UCx","channelTitle":"Kanal X",
     "thumbnails":{"default":{"url":"https://i/d.jpg"},"medium":{"url":"https://i/m.jpg"}}},"contentDetails":{"duration":"PT4M13S"}}]}
    """
    static let apiPlaylist = """
    {"items":[{"id":"PL999","snippet":{"title":"API Liste","channelId":"UCx","channelTitle":"Kanal X","thumbnails":{"high":{"url":"https://i/h.jpg"}}}}]}
    """
    static let apiPlaylistItemsPage1 = """
    {"nextPageToken":"TOKEN2","items":[
     {"snippet":{"title":"Erstes","position":0,"channelTitle":"Kanal X","videoOwnerChannelTitle":"Kanal X","thumbnails":{"medium":{"url":"https://i/1.jpg"}},"resourceId":{"kind":"youtube#video","videoId":"v1"}}},
     {"snippet":{"title":"Private video","position":1,"channelTitle":"Kanal X","thumbnails":{},"resourceId":{"kind":"youtube#video","videoId":"vPrivat"}}},
     {"snippet":{"title":"Drittes","position":2,"channelTitle":"Kanal X","thumbnails":{"default":{"url":"https://i/3.jpg"}},"resourceId":{"kind":"youtube#video","videoId":"v3"}}}]}
    """
    static let apiEmpty = #"{"items":[]}"#
}

struct RSSFeedParserTests {
    @Test func parsesEntriesWithFeedTitleAndThumbnailFallback() {
        let videos = RSSFeedParser.parse(Data(Fixtures.rss.utf8))
        #expect(videos.count == 2)
        #expect(videos[0] == PlaylistVideo(videoId: "qzLrKKjdsPU", title: "Build voice-first apps with Gemini 3.5 Transcribe",
                                           thumbnailUrl: "https://i2.ytimg.com/vi/qzLrKKjdsPU/hqdefault.jpg",
                                           channelTitle: "Google for Developers", position: 0))
        #expect(videos[1].thumbnailUrl == "https://i.ytimg.com/vi/zweiteID/hqdefault.jpg")
        #expect(videos[1].position == 1)
    }

    @Test func garbageYieldsEmptyList() {
        #expect(RSSFeedParser.parse(Data("<html>nope".utf8)).isEmpty)
    }
}

struct YouTubeIDTests {
    @Test func uploadsPlaylistRoundTrip() {
        #expect(YouTubeIDs.uploadsPlaylistId(forChannel: "UC_x5XG1OV2P6uZZ5FSM9Ttw") == "UU_x5XG1OV2P6uZZ5FSM9Ttw")
        #expect(YouTubeIDs.channelId(forUploadsPlaylist: "UU_x5XG1OV2P6uZZ5FSM9Ttw") == "UC_x5XG1OV2P6uZZ5FSM9Ttw")
        #expect(YouTubeIDs.channelId(forUploadsPlaylist: "PL123") == nil)
    }

    @Test func oEmbedChannelIdOnlyFromChannelURL() {
        #expect(OEmbedClient.channelId(fromAuthorURL: "https://www.youtube.com/channel/UCkinder123") == "UCkinder123")
        #expect(OEmbedClient.channelId(fromAuthorURL: "https://www.youtube.com/@RickAstleyYT") == nil)
    }
}

struct YouTubeRepositoryTests {
    @Test func videoResolvedViaOEmbedWithoutTouchingAPI() async throws {
        let http = StubHTTPClient()
        http.on("oembed?url=https://www.youtube.com/watch?v%3DdQw4w9WgXcQ", body: Fixtures.oEmbedVideo)
        let repo = YouTubeRepository(http: http, apiKey: "KEY")
        let draft = try await repo.resolve(urlString: "https://youtu.be/dQw4w9WgXcQ")
        #expect(draft == WhitelistItemDraft(type: .video, youtubeId: "dQw4w9WgXcQ",
                                            title: "Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)",
                                            thumbnailUrl: "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", channelTitle: "Rick Astley"))
        #expect(http.calls(containing: "googleapis.com") == 0)
    }

    @Test func videoFallsBackToAPIWhenOEmbedFails() async throws {
        let http = StubHTTPClient()
        http.on("/youtube/v3/videos?", body: Fixtures.apiVideo)
        let repo = YouTubeRepository(http: http, apiKey: "KEY")
        let video = try await repo.video(id: "vid42")
        #expect(video.title == "API Video")
        #expect(video.thumbnailUrl == "https://i/m.jpg")
        #expect(video.duration == "PT4M13S")
        #expect(http.calls(containing: "oembed") == 1)
        #expect(http.requested.last?.contains("key=KEY") == true)
    }

    @Test func withoutAPIKeyFallbackReportsMissingKey() async throws {
        let repo = YouTubeRepository(http: StubHTTPClient(), apiKey: nil)
        await #expect(throws: YouTubeError.missingAPIKey) { try await repo.video(id: "x") }
        // Kanäle: ohne Key wird die Kanalseite versucht (hier 404 → notFound), kein missingAPIKey mehr
        await #expect(throws: YouTubeError.notFound) { try await repo.resolve(.channelHandle("MrBeast")) }
    }

    @Test func playlistViaOEmbedKeepsChannelId() async throws {
        let http = StubHTTPClient()
        http.on("oembed?url=https://www.youtube.com/playlist", body: Fixtures.oEmbedPlaylist)
        let playlist = try await YouTubeRepository(http: http, apiKey: nil).playlist(id: "PL1")
        #expect(playlist.channelId == "UCkinder123")
        #expect(playlist.channelTitle == "Kinderkanal")
    }

    @Test func channelHandleAndCustomNameUseForHandle() async throws {
        let http = StubHTTPClient()
        http.on("channels?", body: Fixtures.apiChannel)
        let repo = YouTubeRepository(http: http, apiKey: "KEY")
        let draft = try await repo.resolve(urlString: "https://www.youtube.com/@GoogleDevelopers")
        #expect(draft == WhitelistItemDraft(type: .channel, youtubeId: "UC_x5XG1OV2P6uZZ5FSM9Ttw", title: "Google for Developers",
                                            thumbnailUrl: "https://yt3/h.jpg", channelTitle: nil))
        #expect(http.requested.last?.contains("forHandle=GoogleDevelopers") == true)
        _ = try await repo.resolve(.channelCustomName("Legacy"))
        #expect(http.requested.last?.contains("forHandle=Legacy") == true)
        let channel = try await repo.channel(id: "UC_x5XG1OV2P6uZZ5FSM9Ttw")
        #expect(channel.uploadsPlaylistId == "UU_x5XG1OV2P6uZZ5FSM9Ttw")
        #expect(channel.subscriberCount == "2500000")
    }

    @Test func unknownChannelIsNotFound() async throws {
        let http = StubHTTPClient()
        http.on("channels?", body: Fixtures.apiEmpty)
        let repo = YouTubeRepository(http: http, apiKey: "KEY")
        await #expect(throws: YouTubeError.notFound) { try await repo.channel(id: "UCnix") }
    }

    @Test func uploadsPlaylistFirstPageFromRSSThenAPI() async throws {
        let http = StubHTTPClient()
        http.on("feeds/videos.xml?channel_id=UC_x5XG1OV2P6uZZ5FSM9Ttw", body: Fixtures.rss)
        http.on("playlistItems?", body: Fixtures.apiPlaylistItemsPage1)
        let repo = YouTubeRepository(http: http, apiKey: "KEY")

        let first = try await repo.playlistItems(playlistId: "UU_x5XG1OV2P6uZZ5FSM9Ttw")
        #expect(first.videos.count == 2)
        #expect(first.nextPageToken == PlaylistPage.continueWithAPIToken)
        #expect(http.calls(containing: "googleapis.com") == 0)

        let second = try await repo.playlistItems(playlistId: "UU_x5XG1OV2P6uZZ5FSM9Ttw", pageToken: first.nextPageToken)
        #expect(second.videos.map(\.videoId) == ["v1", "v3"], "privates Video ohne Vorschaubild wird übersprungen")
        #expect(second.nextPageToken == "TOKEN2")
        #expect(http.requested.last?.contains("pageToken") == false, "API-Fortsetzung nach RSS startet bei Seite 1")

        _ = try await repo.playlistItems(playlistId: "UU_x5XG1OV2P6uZZ5FSM9Ttw", pageToken: "TOKEN2")
        #expect(http.requested.last?.contains("pageToken=TOKEN2") == true)
    }

    @Test func normalPlaylistSkipsRSS() async throws {
        let http = StubHTTPClient()
        http.on("playlistItems?", body: Fixtures.apiPlaylistItemsPage1)
        let page = try await YouTubeRepository(http: http, apiKey: "KEY").playlistItems(playlistId: "PL999")
        #expect(page.videos.count == 2)
        #expect(http.calls(containing: "feeds/videos.xml") == 0)
    }

    @Test func httpErrorsSurfaceAsStatus() async throws {
        let http = StubHTTPClient()
        http.on("playlistItems?", status: 403, body: #"{"error":{"code":403,"message":"quotaExceeded"}}"#)
        let repo = YouTubeRepository(http: http, apiKey: "KEY")
        await #expect(throws: YouTubeError.http(status: 403)) { try await repo.playlistItems(playlistId: "PL999") }
    }

    @Test func invalidURLRejected() async throws {
        let repo = YouTubeRepository(http: StubHTTPClient(), apiKey: nil)
        await #expect(throws: YouTubeError.invalidURL) { try await repo.resolve(urlString: "https://vimeo.com/1") }
    }
}
