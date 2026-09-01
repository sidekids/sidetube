import Foundation
import Testing
@testable import sidetube

/// Echte Netzaufrufe – nur mit `TEST_RUNNER_SIDETUBE_LIVE=1 xcodebuild test …`.
/// Data-API-Fälle laufen zusätzlich nur, wenn `TEST_RUNNER_YOUTUBE_API_KEY` gesetzt ist.
@Suite(.enabled(if: ProcessInfo.processInfo.environment["SIDETUBE_LIVE"] == "1"))
struct LiveYouTubeTests {
    private var apiKey: String? {
        let key = ProcessInfo.processInfo.environment["YOUTUBE_API_KEY"] ?? ""
        return key.isEmpty ? nil : key
    }

    @Test func oEmbedVideoLive() async throws {
        let repo = YouTubeRepository(http: URLSessionHTTPClient(), apiKey: nil)
        let draft = try await repo.resolve(urlString: "https://youtu.be/dQw4w9WgXcQ")
        #expect(draft.type == .video)
        #expect(draft.title.contains("Rick Astley"))
        #expect(draft.thumbnailUrl.hasPrefix("https://"))
    }

    @Test func rssUploadsFirstPageLive() async throws {
        let repo = YouTubeRepository(http: URLSessionHTTPClient(), apiKey: nil)
        let page = try await repo.playlistItems(playlistId: "UU_x5XG1OV2P6uZZ5FSM9Ttw")
        #expect(!page.videos.isEmpty)
        #expect(page.videos.allSatisfy { $0.channelTitle == "Google for Developers" && !$0.videoId.isEmpty })
        #expect(page.nextPageToken == PlaylistPage.continueWithAPIToken)
    }

    @Test func channelPageWithoutKeyLive() async throws {
        let repo = YouTubeRepository(http: URLSessionHTTPClient(), apiKey: nil)
        let kika = try await repo.channel(handle: "KiKA")
        #expect(kika.id == "UCxFvLj7FDoMChztQTSRDAbw")
        #expect(kika.title.contains("KiKA"))
        let maus = try await repo.channel(id: "UCRWSxXBnz9IRS4SgRhG2wpQ")
        #expect(maus.title == "Die Maus")
        #expect(maus.thumbnailUrl.hasPrefix("https://"))
    }

    @Test func peerTubeVideoAndChannelLive() async throws {
        let client = PeerTubeClient(http: URLSessionHTTPClient())
        // Ein beliebiges aktuelles Video der Instanz auflösen (IDs ändern sich, deshalb erst listen).
        let http = URLSessionHTTPClient()
        let (data, status) = try await http.get(URL(string: "https://framatube.org/api/v1/videos?count=1&nsfw=false&sort=-publishedAt")!)
        #expect(status == 200)
        struct List: Decodable { struct Item: Decodable { var shortUUID: String? ; var uuid: String }; var data: [Item] }
        let list = try JSONDecoder().decode(List.self, from: data)
        let id = list.data.first.map { $0.shortUUID ?? $0.uuid }
        #expect(id != nil)
        let video = try await client.video(host: "framatube.org", id: id!)
        #expect(PeerTubeIDs.isPeerTube(video.id))
        #expect(!video.title.isEmpty)
        #expect(!video.nsfw, "Liste war auf nsfw=false gefiltert")
        #expect(PeerTubeIDs.embedURL(videoId: video.id)?.query?.contains("p2p=0") == true)
    }

    @Test func dataAPIChannelByHandleLive() async throws {
        guard let apiKey else { return }
        let repo = YouTubeRepository(http: URLSessionHTTPClient(), apiKey: apiKey)
        let channel = try await repo.channel(handle: "GoogleDevelopers")
        #expect(channel.id == "UC_x5XG1OV2P6uZZ5FSM9Ttw")
        let page = try await repo.playlistItems(playlistId: channel.uploadsPlaylistId!, pageToken: PlaylistPage.continueWithAPIToken)
        #expect(page.videos.count > 10)
    }
}
