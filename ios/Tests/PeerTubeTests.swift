// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

/// Fixtures = echte Antworten von framatube.org (2026-08-31), gekürzt.
enum PeerTubeFixtures {
    static let video = """
    {"uuid":"15859262-4b6f-4591-ab91-1faa1a630795","shortUUID":"3E9avCLqJ4f2CBeSMZu3RB","name":"Cb 19",
     "description":"Kleine Bastelrunde","duration":15,"nsfw":false,"isLive":false,
     "thumbnailPath":"/lazy-static/thumbnails/172e5d87.jpg","previewPath":"/lazy-static/thumbnails/3be80e04.jpg",
     "publishedAt":"2026-08-31T12:46:25.415Z",
     "channel":{"name":"karcio_channel","displayName":"Main karcio channel","host":"tube.tchncs.de"}}
    """
    static let nsfwVideo = """
    {"uuid":"u2","shortUUID":"s2","name":"Nicht jugendfrei","duration":120,"nsfw":true,"isLive":false,
     "thumbnailPath":"/t.jpg","channel":{"name":"karcio_channel","displayName":"Main karcio channel","host":"tube.tchncs.de"}}
    """
    static let channel = """
    {"name":"karcio_channel","displayName":"Main karcio channel","host":"tube.tchncs.de","description":"Basteln",
     "avatars":[{"path":"/lazy-static/avatars/abc.png"}]}
    """
    static let channelVideos = """
    {"total":8,"data":[
      {"uuid":"u1","shortUUID":"s1","name":"Cb 19","duration":15,"nsfw":false,"isLive":false,"previewPath":"/p1.jpg",
       "channel":{"name":"karcio_channel","displayName":"Main karcio channel","host":"tube.tchncs.de"}},
      {"uuid":"u3","shortUUID":"s3","name":"Live: Bastelstunde","duration":0,"nsfw":false,"isLive":true,"previewPath":"/p3.jpg",
       "channel":{"name":"karcio_channel","displayName":"Main karcio channel","host":"tube.tchncs.de"}}]}
    """
}

struct PeerTubeURLParserTests {
    @Test(arguments: [
        ("https://framatube.org/w/3E9avCLqJ4f2CBeSMZu3RB", ParsedPeerTubeURL.video(host: "framatube.org", id: "3E9avCLqJ4f2CBeSMZu3RB")),
        ("https://tube.tchncs.de/videos/watch/15859262-4b6f-4591-ab91-1faa1a630795", .video(host: "tube.tchncs.de", id: "15859262-4b6f-4591-ab91-1faa1a630795")),
        ("framatube.org/videos/embed/abc", .video(host: "framatube.org", id: "abc")),
        ("https://framatube.org/c/karcio_channel@tube.tchncs.de/videos", .channel(host: "tube.tchncs.de", name: "karcio_channel")),
        ("https://framatube.org/video-channels/lokal", .channel(host: "framatube.org", name: "lokal")),
    ])
    func parses(input: String, expected: ParsedPeerTubeURL) {
        #expect(PeerTubeURLParser.parse(input) == expected)
    }

    @Test(arguments: ["", "kein link", "https://www.youtube.com/watch?v=abc", "https://framatube.org/", "https://framatube.org/w/"])
    func rejectsOthers(input: String) {
        #expect(PeerTubeURLParser.parse(input) == nil)
    }

    @Test func identifiersAndEmbedURL() {
        let id = PeerTubeIDs.videoId(host: "Framatube.org", id: "abc")
        #expect(id == "pt:framatube.org:abc")
        #expect(PeerTubeIDs.isPeerTube(id))
        #expect(!PeerTubeIDs.isPeerTube("dQw4w9WgXcQ"))
        #expect(PeerTubeIDs.split(id)?.host == "framatube.org")
        #expect(PeerTubeIDs.instanceId(of: id) == "pt:framatube.org")
        let embed = PeerTubeIDs.embedURL(videoId: id)?.absoluteString
        #expect(embed == "https://framatube.org/videos/embed/abc?p2p=0&peertubeLink=0&warningTitle=0&autoplay=1&title=0",
                "kein P2P, kein Instanz-Link, kein Warnhinweis")
        #expect(PeerTubeIDs.watchURL(videoId: id)?.absoluteString == "https://framatube.org/w/abc")
        #expect(PeerTubeIDs.embedURL(videoId: "dQw4w9WgXcQ") == nil)
    }
}

struct PeerTubeClientTests {
    @Test func resolvesVideoWithOriginHost() async throws {
        let http = StubHTTPClient()
        http.on("framatube.org/api/v1/videos/", body: PeerTubeFixtures.video)
        let video = try await PeerTubeClient(http: http).video(host: "framatube.org", id: "3E9avCLqJ4f2CBeSMZu3RB")
        #expect(video.id == "pt:tube.tchncs.de:3E9avCLqJ4f2CBeSMZu3RB", "Kennung trägt den Ursprungs-Host, nicht den abgefragten")
        #expect(video.channelId == "pt:tube.tchncs.de:karcio_channel")
        #expect(video.title == "Cb 19")
        #expect(video.durationSeconds == 15)
        #expect(!video.nsfw && !video.isLive)
        #expect(video.thumbnailUrl == "https://framatube.org/lazy-static/thumbnails/3be80e04.jpg")
        #expect(video.watchUrl == "https://tube.tchncs.de/w/3E9avCLqJ4f2CBeSMZu3RB")
    }

    @Test func channelAndPagingSkipLives() async throws {
        let http = StubHTTPClient()
        http.on("api/v1/video-channels/karcio_channel@tube.tchncs.de/videos", body: PeerTubeFixtures.channelVideos)
        http.on("api/v1/video-channels/karcio_channel@tube.tchncs.de", body: PeerTubeFixtures.channel)
        let client = PeerTubeClient(http: http)
        let channel = try await client.channel(host: "tube.tchncs.de", name: "karcio_channel")
        #expect(channel.id == "pt:tube.tchncs.de:karcio_channel")
        #expect(channel.thumbnailUrl == "https://tube.tchncs.de/lazy-static/avatars/abc.png")
        let page = try await client.channelVideos(host: "tube.tchncs.de", name: "karcio_channel")
        #expect(page.videos.map(\.id) == ["pt:tube.tchncs.de:s1"], "Livestream wird verworfen")
        #expect(page.total == 8)
        #expect(http.requested.last?.contains("nsfw=false") == true, "NSFW serverseitig ausgeschlossen")
    }

    @Test func resolverHandlesBothProviders() async throws {
        let http = StubHTTPClient()
        http.on("framatube.org/api/v1/videos/", body: PeerTubeFixtures.video)
        http.on("oembed?url=https://www.youtube.com/watch", body: Fixtures.oEmbedVideo)
        let resolver = MediaResolver(youtube: YouTubeRepository(http: http, apiKey: nil), peertube: PeerTubeClient(http: http))
        let peertube = try await resolver.resolve(urlString: "https://framatube.org/w/3E9avCLqJ4f2CBeSMZu3RB")
        #expect(peertube.provider == .peertube)
        #expect(peertube.youtubeId == "pt:tube.tchncs.de:3E9avCLqJ4f2CBeSMZu3RB")
        #expect(peertube.durationSeconds == 15)
        let youtube = try await resolver.resolve(urlString: "https://youtu.be/dQw4w9WgXcQ")
        #expect(youtube.provider == .youtube)
        await #expect(throws: YouTubeError.invalidURL) { try await resolver.resolve(urlString: "https://vimeo.com/12345") }
    }

    @Test func channelPageIsProviderNeutral() async throws {
        let http = StubHTTPClient()
        http.on("api/v1/video-channels/karcio_channel@tube.tchncs.de/videos", body: PeerTubeFixtures.channelVideos)
        let resolver = MediaResolver(youtube: YouTubeRepository(http: http, apiKey: nil), peertube: PeerTubeClient(http: http))
        let page = try await resolver.channelPage(channelId: "pt:tube.tchncs.de:karcio_channel", pageToken: nil)
        #expect(page.videos.map(\.videoId) == ["pt:tube.tchncs.de:s1"])
        #expect(page.nextPageToken == "1", "Fortsetzung über den Start-Offset")
        #expect(http.requested.last?.contains("start=0") == true)
    }
}

struct PeerTubePolicyTests {
    private func makeWorld() throws -> (ModelContext, KidProfile, CurationRepository) {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia")
        let curation = CurationRepository(context: context)
        try curation.ensureSources(SourceRegistry.allDefinitions)
        return (context, profile, curation)
    }

    private func draft(id: String, title: String, channel: String, nsfw: Bool = false) -> WhitelistItemDraft {
        WhitelistItemDraft(type: .video, youtubeId: id, title: title, thumbnailUrl: "", channelTitle: "Kanal",
                           provider: .peertube, sourceChannelId: channel, sourceUrl: "https://x/w/1", isNSFW: nsfw)
    }

    @Test func unknownInstanceIsRejected() throws {
        let (_, profile, curation) = try makeWorld()
        #expect(PeerTubePolicy.hostAllowed("framatube.org", curation: curation))
        #expect(!PeerTubePolicy.hostAllowed("irgendeine-instanz.example", curation: curation))
        #expect(throws: CurationRepository.DiscoverError.blockedSource) {
            try curation.discover(draft(id: "pt:irgendeine-instanz.example:x", title: "Video", channel: "pt:irgendeine-instanz.example:kanal"),
                                  for: profile)
        }
    }

    @Test func nsfwFlagIsAutoRejected() throws {
        let (_, profile, curation) = try makeWorld()
        let item = try curation.discover(draft(id: "pt:framatube.org:s2", title: "Nicht jugendfrei", channel: "pt:framatube.org:kanal", nsfw: true), for: profile)
        #expect(item.approvalStatus == .rejected)
        #expect(curation.events(for: item.youtubeId).first?.decision == .autoRejected)
    }

    @Test func approvedPeerTubeVideoIsVisibleAndPlayable() throws {
        let (context, profile, curation) = try makeWorld()
        let item = try curation.discover(draft(id: "pt:framatube.org:s1", title: "Basteln mit Papier", channel: "pt:framatube.org:kanal"), for: profile)
        #expect(item.provider == .peertube)
        #expect(item.approvalStatus == .reviewRequired)
        try curation.approve(item, with: .init(ageMin: 6, ageMax: nil, category: .creative, newsStatus: nil, parentNotes: nil), actor: "Eltern")
        let source = curation.effectiveSource(channelId: item.sourceChannelId)
        #expect(source?.channelId == "pt:framatube.org", "Kanal ohne eigene Quelle fällt auf die Instanz zurück")
        #expect(ContentPolicy.isVisible(item, for: profile, source: source))
        #expect(WhitelistRepository(context: context).visibleItems(of: profile).map(\.youtubeId) == [item.youtubeId])
        #expect(item.provider.isPlayable)
        let row = KidRows.row(for: item, context: KidContext(modelContext: context, youtube: YouTubeRepository(http: StubHTTPClient(), apiKey: nil)))
        #expect(row.action == .play(videoId: item.youtubeId, title: item.title))
    }

    @Test func videoFromInstanceWithoutSourceStaysHidden() throws {
        let (_, profile, curation) = try makeWorld()
        let item = WhitelistItem(type: .video, youtubeId: "pt:fremde.example:x", title: "Fremd", thumbnailUrl: "", approvalStatus: .approved)
        item.provider = .peertube
        item.profile = profile
        item.sourceChannelId = "pt:fremde.example:kanal"
        #expect(!ContentPolicy.isVisible(item, for: profile, source: curation.effectiveSource(channelId: item.sourceChannelId)))
    }

    @Test func recommendationUsesOriginalPeerTubeLink() {
        let id = PeerTubeIDs.videoId(host: "framatube.org", id: "abc")
        #expect(RecommendLink.url(videoId: id).absoluteString == "https://framatube.org/w/abc")
        let text = RecommendLink.message(title: "Basteln", videoId: id)
        #expect(text.contains("https://framatube.org/w/abc"))
        #expect(!text.contains("sidetube://"), "kein Import-Link für Fremdanbieter")
    }

    @Test func playerCoordinatorPicksPeerTubeEngine() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia")
        let youtubeEngine = FakePlayerEngine(), peertubeEngine = FakePlayerEngine()
        let coordinator = PlayerCoordinator()
        coordinator.makeEngine = { youtubeEngine }
        coordinator.makePeerTubeEngine = { peertubeEngine }
        coordinator.play(queue: [.init(videoId: "pt:framatube.org:abc", title: "PeerTube")], startIndex: 0, profile: profile, context: context)
        #expect(peertubeEngine.loaded == ["pt:framatube.org:abc"])
        #expect(youtubeEngine.loaded.isEmpty)
        coordinator.close()
        coordinator.play(queue: [.init(videoId: "dQw4w9WgXcQ", title: "YouTube")], startIndex: 0, profile: profile, context: context)
        #expect(youtubeEngine.loaded == ["dQw4w9WgXcQ"])
    }
}
