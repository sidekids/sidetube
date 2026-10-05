// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

enum PlaylistFixtures {
    static let playlistId = "PLav47HAVZMjnTFVZL-aImCQIC0uLZtNCz"
    static let blenderStudio = "UCz75RVbH8q2jdBJ4SnwuZZQ"
    static let blender = "UCSMOQeBJ2RAnuFungnQOxLg"
    // Echter Playlist-Feed „Blender Open Movies" (02.10.2026), auf vier Einträge gekürzt
    // (dieselbe Aufnahme wie android/core/src/test/resources/playlist-feed.xml).
    static let feed = #"""
    <?xml version="1.0" encoding="UTF-8"?>
    <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
     <link rel="self" href="http://www.youtube.com/feeds/videos.xml?playlist_id=PLav47HAVZMjnTFVZL-aImCQIC0uLZtNCz"/>
     <id>yt:playlist:PLav47HAVZMjnTFVZL-aImCQIC0uLZtNCz</id>
     <yt:playlistId>PLav47HAVZMjnTFVZL-aImCQIC0uLZtNCz</yt:playlistId>
     <yt:channelId>UCz75RVbH8q2jdBJ4SnwuZZQ</yt:channelId>
     <title>Blender Open Movies</title>
     <author>
      <name>Blender Studio</name>
      <uri>https://www.youtube.com/channel/UCz75RVbH8q2jdBJ4SnwuZZQ</uri>
     </author>
     <published>2015-02-01T14:34:02+00:00</published>
      <entry>
      <id>yt:video:u9lj-c29dxI</id>
      <yt:videoId>u9lj-c29dxI</yt:videoId>
      <yt:channelId>UCz75RVbH8q2jdBJ4SnwuZZQ</yt:channelId>
      <title>WING IT! - Blender Open Movie</title>
      <link rel="alternate" href="https://www.youtube.com/watch?v=u9lj-c29dxI"/>
      <author>
       <name>Blender Studio</name>
       <uri>https://www.youtube.com/channel/UCz75RVbH8q2jdBJ4SnwuZZQ</uri>
      </author>
      <published>2023-09-12T15:11:22+00:00</published>
      <updated>2026-08-31T15:33:51+00:00</updated>
      <media:group>
       <media:title>WING IT! - Blender Open Movie</media:title>
       <media:content url="https://www.youtube.com/v/u9lj-c29dxI?version=3" type="application/x-shockwave-flash" width="640" height="390"/>
       <media:thumbnail url="https://i2.ytimg.com/vi/u9lj-c29dxI/hqdefault.jpg" width="480" height="360"/>
       <media:description>Gekürzt für den Test.</media:description>
       <media:community>
        <media:starRating count="60008" average="5.00" min="1" max="5"/>
        <media:statistics views="1183672"/>
       </media:community>
      </media:group>
     </entry>
     <entry>
      <id>yt:video:WhWc3b3KhnY</id>
      <yt:videoId>WhWc3b3KhnY</yt:videoId>
      <yt:channelId>UCz75RVbH8q2jdBJ4SnwuZZQ</yt:channelId>
      <title>Spring - Blender Open Movie</title>
      <link rel="alternate" href="https://www.youtube.com/watch?v=WhWc3b3KhnY"/>
      <author>
       <name>Blender Studio</name>
       <uri>https://www.youtube.com/channel/UCz75RVbH8q2jdBJ4SnwuZZQ</uri>
      </author>
      <published>2019-04-04T14:57:49+00:00</published>
      <updated>2026-08-31T14:52:01+00:00</updated>
      <media:group>
       <media:title>Spring - Blender Open Movie</media:title>
       <media:content url="https://www.youtube.com/v/WhWc3b3KhnY?version=3" type="application/x-shockwave-flash" width="640" height="390"/>
       <media:thumbnail url="https://i4.ytimg.com/vi/WhWc3b3KhnY/hqdefault.jpg" width="480" height="360"/>
       <media:description>Gekürzt für den Test.</media:description>
       <media:community>
        <media:starRating count="381638" average="5.00" min="1" max="5"/>
        <media:statistics views="10914950"/>
       </media:community>
      </media:group>
     </entry>
     <entry>
      <id>yt:video:SkVqJ1SGeL0</id>
      <yt:videoId>SkVqJ1SGeL0</yt:videoId>
      <yt:channelId>UCSMOQeBJ2RAnuFungnQOxLg</yt:channelId>
      <title>Caminandes 3: Llamigos</title>
      <link rel="alternate" href="https://www.youtube.com/watch?v=SkVqJ1SGeL0"/>
      <author>
       <name>Blender</name>
       <uri>https://www.youtube.com/channel/UCSMOQeBJ2RAnuFungnQOxLg</uri>
      </author>
      <published>2016-01-30T00:05:27+00:00</published>
      <updated>2026-09-30T20:20:47+00:00</updated>
      <media:group>
       <media:title>Caminandes 3: Llamigos</media:title>
       <media:content url="https://www.youtube.com/v/SkVqJ1SGeL0?version=3" type="application/x-shockwave-flash" width="640" height="390"/>
       <media:thumbnail url="https://i4.ytimg.com/vi/SkVqJ1SGeL0/hqdefault.jpg" width="480" height="360"/>
       <media:description>Gekürzt für den Test.</media:description>
       <media:community>
        <media:starRating count="13313" average="5.00" min="1" max="5"/>
        <media:statistics views="1071973"/>
       </media:community>
      </media:group>
     </entry>
     <entry>
      <id>yt:video:Z4C82eyhwgU</id>
      <yt:videoId>Z4C82eyhwgU</yt:videoId>
      <yt:channelId>UCSMOQeBJ2RAnuFungnQOxLg</yt:channelId>
      <title>&quot;Caminandes 2: Gran Dillama&quot; - Blender Animated Short</title>
      <link rel="alternate" href="https://www.youtube.com/watch?v=Z4C82eyhwgU"/>
      <author>
       <name>Blender</name>
       <uri>https://www.youtube.com/channel/UCSMOQeBJ2RAnuFungnQOxLg</uri>
      </author>
      <published>2013-11-22T16:27:49+00:00</published>
      <updated>2026-08-27T04:49:55+00:00</updated>
      <media:group>
       <media:title>&quot;Caminandes 2: Gran Dillama&quot; - Blender Animated Short</media:title>
       <media:content url="https://www.youtube.com/v/Z4C82eyhwgU?version=3" type="application/x-shockwave-flash" width="640" height="390"/>
       <media:thumbnail url="https://i3.ytimg.com/vi/Z4C82eyhwgU/hqdefault.jpg" width="480" height="360"/>
       <media:description>Gekürzt für den Test.</media:description>
       <media:community>
        <media:starRating count="12015" average="5.00" min="1" max="5"/>
        <media:statistics views="1311118"/>
       </media:community>
      </media:group>
     </entry>
    </feed>
    """#
}

// MARK: Parser und Quelle

struct PlaylistFeedParserTests {
    @Test func playlistEntriesCarryTheirOwnChannel() {
        let videos = RSSFeedParser.parsePlaylist(Data(PlaylistFixtures.feed.utf8))
        #expect(videos.map(\.videoId) == ["u9lj-c29dxI", "WhWc3b3KhnY", "SkVqJ1SGeL0", "Z4C82eyhwgU"])
        #expect(videos[0] == PlaylistVideo(videoId: "u9lj-c29dxI", title: "WING IT! - Blender Open Movie",
                                           thumbnailUrl: "https://i2.ytimg.com/vi/u9lj-c29dxI/hqdefault.jpg",
                                           channelTitle: "Blender Studio", position: 0,
                                           channelId: PlaylistFixtures.blenderStudio))
        // Fremder Kanal in derselben Playlist: Name und Kennung des Eintrags, nicht des Feeds
        #expect(videos[2].channelTitle == "Blender")
        #expect(videos[2].channelId == PlaylistFixtures.blender)
        #expect(videos[3].title == "\"Caminandes 2: Gran Dillama\" - Blender Animated Short", "Entities werden aufgelöst")
        #expect(videos.map(\.position) == [0, 1, 2, 3])
        #expect(!videos.contains { $0.channelTitle == "Blender Open Movies" }, "Der Playlist-Titel ist kein Kanalname")
    }

    @Test func channelFeedKeepsFeedTitleAsChannelName() {
        let videos = RSSFeedParser.parse(Data(PlaylistFixtures.feed.utf8))
        #expect(videos.allSatisfy { $0.channelTitle == "Blender Open Movies" })
        #expect(videos[2].channelId == PlaylistFixtures.blender)
    }

    @Test func playlistFeedCarriesShortAndUpcomingFlagsAndDropsDuplicates() {
        let marked = PlaylistFixtures.feed
            .replacingOccurrences(of: "https://www.youtube.com/watch?v=SkVqJ1SGeL0", with: "https://www.youtube.com/shorts/SkVqJ1SGeL0")
            .replacingOccurrences(of: #"<media:statistics views="1311118"/>"#, with: #"<media:statistics views="0"/>"#)
            .replacingOccurrences(of: "</feed>", with: """
             <entry><yt:videoId>WhWc3b3KhnY</yt:videoId><yt:channelId>UCz75RVbH8q2jdBJ4SnwuZZQ</yt:channelId>
              <title>Spring - Blender Open Movie</title></entry>
            </feed>
            """)
        let videos = RSSFeedParser.parsePlaylist(Data(marked.utf8))
        #expect(videos.map(\.videoId) == ["u9lj-c29dxI", "WhWc3b3KhnY", "SkVqJ1SGeL0", "Z4C82eyhwgU"])
        #expect(videos.map(\.isShort) == [false, false, true, false])
        #expect(videos.map(\.isUpcoming) == [false, false, false, true])
    }

    @Test func garbageYieldsEmptyPlaylist() {
        #expect(RSSFeedParser.parsePlaylist(Data("<html>nope".utf8)).isEmpty)
    }
}

struct PlaylistFeedRepositoryTests {
    @Test func withoutKeyTheFeedIsTheWholeList() async throws {
        let http = StubHTTPClient()
        http.on("feeds/videos.xml?playlist_id=\(PlaylistFixtures.playlistId)", body: PlaylistFixtures.feed)
        let page = try await YouTubeRepository(http: http, apiKey: nil).playlistItems(playlistId: PlaylistFixtures.playlistId)
        #expect(page.videos.count == 4)
        #expect(page.nextPageToken == nil, "ohne Schlüssel kein Weiterblättern – und kein Fehler")
        #expect(http.calls(containing: "googleapis") == 0)
    }

    @Test func withKeyTheFeedComesFirstAndTheAPIContinues() async throws {
        let http = StubHTTPClient()
        http.on("feeds/videos.xml?playlist_id=", body: PlaylistFixtures.feed)
        http.on("playlistItems?", body: Fixtures.apiPlaylistItemsPage1)
        let repo = YouTubeRepository(http: http, apiKey: "KEY")
        let first = try await repo.playlistItems(playlistId: PlaylistFixtures.playlistId)
        #expect(first.videos.count == 4)
        #expect(first.nextPageToken == PlaylistPage.continueWithAPIToken)
        #expect(http.calls(containing: "googleapis") == 0)
        let second = try await repo.playlistItems(playlistId: PlaylistFixtures.playlistId, pageToken: first.nextPageToken)
        #expect(second.videos.map(\.videoId) == ["v1", "v3"])
        #expect(http.requested.last?.contains("pageToken") == false)
    }

    @Test func feedFailureFallsBackToAPIOnlyWithKey() async throws {
        let http = StubHTTPClient()   // Feed antwortet 404
        http.on("playlistItems?", body: Fixtures.apiPlaylistItemsPage1)
        let page = try await YouTubeRepository(http: http, apiKey: "KEY").playlistItems(playlistId: "PL999")
        #expect(page.videos.count == 2)
        await #expect(throws: YouTubeError.notFound) {
            try await YouTubeRepository(http: http, apiKey: nil).playlistItems(playlistId: "PL999")
        }
    }
}

// MARK: Regel (wie Android ContentPolicy.canPlayFromPlaylist)

struct PlaylistPolicyTests {
    private func makeWorld() throws -> (ModelContext, KidProfile, WhitelistItem) {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia")   // kids = 9
        let playlist = try WhitelistRepository(context: context).add(
            WhitelistItemDraft(type: .playlist, youtubeId: PlaylistFixtures.playlistId, title: "Blender Open Movies",
                               thumbnailUrl: "", channelTitle: "Blender Studio", sourceChannelId: PlaylistFixtures.blenderStudio),
            to: profile)
        return (context, profile, playlist)
    }

    private func video(_ id: String, _ title: String = "Spring - Blender Open Movie",
                       channelId: String? = PlaylistFixtures.blenderStudio,
                       isShort: Bool = false, isUpcoming: Bool = false) -> PlaylistVideo {
        PlaylistVideo(videoId: id, title: title, thumbnailUrl: "", channelTitle: "Blender Studio", position: 0,
                      channelId: channelId, isShort: isShort, isUpcoming: isUpcoming)
    }

    private func allowed(_ v: PlaylistVideo, _ profile: KidProfile, _ playlist: WhitelistItem,
                         playlistSource: CuratedSource? = nil, videoSource: CuratedSource? = nil,
                         prior: WhitelistItem? = nil) -> Bool {
        ContentPolicy.canPlayFromPlaylist(v, for: profile, playlist: playlist, playlistSource: playlistSource,
                                          videoSource: videoSource, priorDecision: prior,
                                          risk: RiskScreen.assess(title: v.title))
    }

    private func source(_ id: String, _ trust: SourceTrust, ageMin: Int = 0) -> CuratedSource {
        CuratedSource(channelId: id, handle: nil, title: "Quelle", provider: .youtube, trust: trust, isNewsSource: false,
                      defaultAgeMin: ageMin, defaultCategory: nil, notes: nil, lastReviewedAt: nil)
    }

    @Test func approvedPlaylistCarriesItsVideos() throws {
        let (_, profile, playlist) = try makeWorld()
        #expect(allowed(video("a"), profile, playlist))
    }

    @Test func playlistMustBeApprovedAndVisible() throws {
        let (_, profile, playlist) = try makeWorld()
        playlist.approvalStatus = .reviewRequired
        #expect(!allowed(video("a"), profile, playlist))
        playlist.approvalStatus = .approved
        playlist.ageMin = 12
        #expect(!allowed(video("a"), profile, playlist), "Mindestalter der Playlist gilt für ihre Videos")
        playlist.ageMin = 0
        playlist.category = .animeManga
        #expect(!allowed(video("a"), profile, playlist), "Kategorie der Playlist gilt für ihre Videos")
    }

    @Test func notAPlaylistCarriesNothing() throws {
        let (_, profile, playlist) = try makeWorld()
        playlist.type = .channel
        #expect(!allowed(video("a"), profile, playlist))
    }

    @Test func rejectedSingleVideoNeverAppears() throws {
        let (context, profile, playlist) = try makeWorld()
        let curation = CurationRepository(context: context)
        let single = try WhitelistRepository(context: context).add(
            WhitelistItemDraft(type: .video, youtubeId: "a", title: "Spring", thumbnailUrl: ""), to: profile)
        try curation.reject(single, actor: "Eltern")
        #expect(!allowed(video("a"), profile, playlist, prior: single))
        try curation.defer_(single, actor: "Eltern")
        #expect(!allowed(video("a"), profile, playlist, prior: single), "zurückgestellt zählt wie nicht freigegeben")
    }

    @Test func ownApprovalWinsOverRiskButNeedsVisiblePlaylist() throws {
        let (context, profile, playlist) = try makeWorld()
        let single = try WhitelistRepository(context: context).add(
            WhitelistItemDraft(type: .video, youtubeId: "a", title: "Livestream", thumbnailUrl: ""), to: profile)
        #expect(allowed(video("a", "Livestream vom Set"), profile, playlist, prior: single),
                "eigene Freigabe der Eltern geht dem Risikofilter vor")
        single.ageMin = 12
        #expect(!allowed(video("a"), profile, playlist, prior: single), "eigene Altersgrenze geht vor")
        single.ageMin = 0
        playlist.approvalStatus = .rejected
        #expect(!allowed(video("a"), profile, playlist, prior: single))
    }

    @Test func riskShortsAndLiveAreFiltered() throws {
        let (_, profile, playlist) = try makeWorld()
        profile.allowShorts = true
        #expect(!allowed(video("a", "NSFW Compilation"), profile, playlist))
        #expect(!allowed(video("b", "Bericht über den Krieg in der Ukraine"), profile, playlist))
        #expect(!allowed(video("c", "Spring #shorts"), profile, playlist), "Shorts nie ungeprüft aus Playlists")
        #expect(!allowed(video("d", "Blender 🔴 Livestream"), profile, playlist))
    }

    @Test func blockedSourcesWin() throws {
        let (_, profile, playlist) = try makeWorld()
        #expect(!allowed(video("a"), profile, playlist, playlistSource: source(PlaylistFixtures.blenderStudio, .blocked)))
        #expect(!allowed(video("a", channelId: PlaylistFixtures.blender), profile, playlist,
                         videoSource: source(PlaylistFixtures.blender, .blocked)))
        #expect(!allowed(video("a"), profile, playlist, videoSource: source(PlaylistFixtures.blender, .parentOnly)))
    }

    @Test func feedShortsAndUpcomingAreFilteredEvenWithHarmlessTitle() throws {
        let (_, profile, playlist) = try makeWorld()
        #expect(!allowed(video("a", isShort: true), profile, playlist), "Short laut Feed, harmloser Titel")
        #expect(!allowed(video("b", isUpcoming: true), profile, playlist), "angekündigt laut Feed")
        profile.allowShorts = true
        #expect(allowed(video("a", isShort: true), profile, playlist), "Shorts erlaubt: dann wie auf iOS-Einzelvideos")
    }

    @Test func unknownChannelNeedsOwnApproval() throws {
        let (context, profile, playlist) = try makeWorld()
        #expect(!allowed(video("a", channelId: nil), profile, playlist))
        let single = try WhitelistRepository(context: context).add(
            WhitelistItemDraft(type: .video, youtubeId: "a", title: "Spring", thumbnailUrl: ""), to: profile)
        #expect(allowed(video("a", channelId: nil), profile, playlist, prior: single))
    }

    @Test func perVideoReviewChannelNeedsOwnApprovalInPlaylists() throws {   // ADR 0002
        let (context, profile, playlist) = try makeWorld()
        let single = source(PlaylistFixtures.blender, .perVideoReview)
        #expect(!allowed(video("a", channelId: PlaylistFixtures.blender), profile, playlist, videoSource: single))
        let approved = try WhitelistRepository(context: context).add(
            WhitelistItemDraft(type: .video, youtubeId: "a", title: "Spring", thumbnailUrl: ""), to: profile)
        #expect(allowed(video("a", channelId: PlaylistFixtures.blender), profile, playlist, videoSource: single, prior: approved))
        // Andere Stufen und unbekannte Kanäle trägt die Playlist-Freigabe weiterhin.
        #expect(allowed(video("b", channelId: PlaylistFixtures.blender), profile, playlist,
                        videoSource: source(PlaylistFixtures.blender, .trustedSeries)))
        #expect(allowed(video("b", channelId: PlaylistFixtures.blender), profile, playlist,
                        videoSource: source(PlaylistFixtures.blender, .trustedChildSource)))
        #expect(allowed(video("b", channelId: "UCunbekannt"), profile, playlist, videoSource: nil))
    }
}

// MARK: Kindermodus: Playlist-Ansicht und Player-Grenze

struct PlaylistModelTests {
    private func makeContext(http: HTTPClient, apiKey: String? = nil) throws -> (KidContext, KidProfile) {
        let modelContext = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: modelContext).create(name: "Mia")
        try WhitelistRepository(context: modelContext).add(
            WhitelistItemDraft(type: .playlist, youtubeId: PlaylistFixtures.playlistId, title: "Blender Open Movies",
                               thumbnailUrl: "", channelTitle: "Blender Studio"), to: profile)
        let context = KidContext(modelContext: modelContext, youtube: YouTubeRepository(http: http, apiKey: apiKey), profile: profile)
        return (context, profile)
    }

    @Test func withoutKeyShowsFeedAndHidesRejectedVideo() async throws {
        let http = StubHTTPClient()
        http.on("feeds/videos.xml?playlist_id=", body: PlaylistFixtures.feed)
        let (context, profile) = try makeContext(http: http)
        let rejected = try WhitelistRepository(context: context.modelContext).add(
            WhitelistItemDraft(type: .video, youtubeId: "WhWc3b3KhnY", title: "Spring", thumbnailUrl: ""), to: profile)
        try CurationRepository(context: context.modelContext).reject(rejected, actor: "Eltern")

        let model = PlaylistModel(playlistId: PlaylistFixtures.playlistId, playlistTitle: "Blender", context: context)
        await model.onAppear()
        #expect(model.rows.map(\.id) == ["u9lj-c29dxI", "SkVqJ1SGeL0", "Z4C82eyhwgU"])
        #expect(model.footerHint == nil)
        #expect(model.rows.allSatisfy { $0.sourcePlaylistId == PlaylistFixtures.playlistId })
        #expect(model.rows[1].sourceChannelId == PlaylistFixtures.blender)
        #expect(http.calls(containing: "googleapis") == 0)
        await model.loadNextPage()
        #expect(model.rows.count == 3, "ohne Schlüssel keine weitere Seite und kein Fehler")
    }

    @Test func cacheServesOfflineAndStaysFiltered() async throws {
        let online = StubHTTPClient()
        online.on("feeds/videos.xml?playlist_id=", body: PlaylistFixtures.feed)
        let (context, profile) = try makeContext(http: online)
        await PlaylistModel(playlistId: PlaylistFixtures.playlistId, playlistTitle: "B", context: context).onAppear()

        // Später abgelehnt, dann offline geöffnet: der Zwischenspeicher zeigt es trotzdem nicht.
        let rejected = try WhitelistRepository(context: context.modelContext).add(
            WhitelistItemDraft(type: .video, youtubeId: "u9lj-c29dxI", title: "Wing It", thumbnailUrl: ""), to: profile)
        try CurationRepository(context: context.modelContext).reject(rejected, actor: "Eltern")
        let offline = KidContext(modelContext: context.modelContext, youtube: YouTubeRepository(http: StubHTTPClient(), apiKey: nil),
                                 profile: profile)
        let model = PlaylistModel(playlistId: PlaylistFixtures.playlistId, playlistTitle: "B", context: offline)
        await model.onAppear()
        #expect(model.rows.map(\.id) == ["WhWc3b3KhnY", "SkVqJ1SGeL0", "Z4C82eyhwgU"])
        #expect(model.footerHint == nil)
    }

    @Test func blockedVideoChannelHidesItsVideosFromTheCache() async throws {
        let http = StubHTTPClient()
        http.on("feeds/videos.xml?playlist_id=", body: PlaylistFixtures.feed)
        let (context, profile) = try makeContext(http: http)
        let curation = CurationRepository(context: context.modelContext)
        try curation.ensureSources([SourceDefinition(channelId: PlaylistFixtures.blender, handle: nil, title: "Blender", trust: .blocked)])
        let model = PlaylistModel(playlistId: PlaylistFixtures.playlistId, playlistTitle: "B", context: context)
        await model.onAppear()
        #expect(model.rows.map(\.id) == ["u9lj-c29dxI", "WhWc3b3KhnY"])
        // Der Zwischenspeicher kennt die Kanal-ID jedes Videos: dieselbe Antwort offline.
        let cached = context.cache.playlistVideos(playlistId: PlaylistFixtures.playlistId)
        #expect(cached.map(\.channelId) == [PlaylistFixtures.blenderStudio, PlaylistFixtures.blenderStudio,
                                            PlaylistFixtures.blender, PlaylistFixtures.blender])
        let playlist = try #require(PlaylistPlayability.playlistItem(id: PlaylistFixtures.playlistId, profile: profile))
        #expect(PlaylistPlayability.allowedVideos(cached, profile: profile, playlist: playlist, context: context.modelContext)
            .map(\.videoId) == ["u9lj-c29dxI", "WhWc3b3KhnY"])
    }

    @Test func blockedChannelWithDifferentSourceTitleIsFoundByIdInTheCache() async throws {
        let http = StubHTTPClient()
        http.on("feeds/videos.xml?playlist_id=", body: PlaylistFixtures.feed)
        let (context, profile) = try makeContext(http: http)
        await PlaylistModel(playlistId: PlaylistFixtures.playlistId, playlistTitle: "B", context: context).onAppear()
        // Quellentitel ≠ Autorname im Feed („Blender"): früher fand die Namenssuche nichts und ließ zu.
        try CurationRepository(context: context.modelContext).ensureSources([
            SourceDefinition(channelId: PlaylistFixtures.blender, handle: nil, title: "Blender Foundation", trust: .blocked)])
        let offline = KidContext(modelContext: context.modelContext, youtube: YouTubeRepository(http: StubHTTPClient(), apiKey: nil),
                                 profile: profile)
        let model = PlaylistModel(playlistId: PlaylistFixtures.playlistId, playlistTitle: "B", context: offline)
        await model.onAppear()
        #expect(model.rows.map(\.id) == ["u9lj-c29dxI", "WhWc3b3KhnY"])
    }

    @Test func legacyCacheWithoutChannelIdNeedsOwnApproval() async throws {
        let (context, profile) = try makeContext(http: StubHTTPClient())
        let key = ChannelVideoCacheRepository.playlistKey(PlaylistFixtures.playlistId)
        // Eintrag von vor der Spalte `videoChannelId`.
        context.modelContext.insert(CachedChannelVideo(channelId: key, videoId: "alt", title: "Alt", thumbnailUrl: "",
                                                       channelTitle: "Blender", position: 0))
        context.modelContext.insert(CachedChannelVideo(channelId: key, videoId: "frei", title: "Frei", thumbnailUrl: "",
                                                       channelTitle: "Blender", position: 1))
        try WhitelistRepository(context: context.modelContext).add(
            WhitelistItemDraft(type: .video, youtubeId: "frei", title: "Frei", thumbnailUrl: ""), to: profile)
        let model = PlaylistModel(playlistId: PlaylistFixtures.playlistId, playlistTitle: "B", context: context)
        await model.onAppear()
        #expect(model.rows.map(\.id) == ["frei"])
    }

    @Test func duplicateVideoInPlaylistAppearsOnce() async throws {
        let doubled = PlaylistFixtures.feed.replacingOccurrences(of: "</feed>", with: """
         <entry><yt:videoId>u9lj-c29dxI</yt:videoId><yt:channelId>UCz75RVbH8q2jdBJ4SnwuZZQ</yt:channelId>
          <title>WING IT! - Blender Open Movie</title></entry>
        </feed>
        """)
        let http = StubHTTPClient()
        http.on("feeds/videos.xml?playlist_id=", body: doubled)
        let (context, profile) = try makeContext(http: http)
        let model = PlaylistModel(playlistId: PlaylistFixtures.playlistId, playlistTitle: "B", context: context)
        await model.onAppear()
        #expect(model.rows.count == 4)
        #expect(Set(model.rows.map(\.id)).count == model.rows.count, "ForEach braucht eindeutige IDs")
        // Auch eine doppelte Eingabe (Data API, Zwischenspeicher) ergibt eine Zeile.
        let playlist = try #require(PlaylistPlayability.playlistItem(id: PlaylistFixtures.playlistId, profile: profile))
        let twice = context.cache.playlistVideos(playlistId: PlaylistFixtures.playlistId)
        try context.cache.storePlaylist(playlistId: PlaylistFixtures.playlistId, videos: twice + twice)
        #expect(context.cache.playlistVideos(playlistId: PlaylistFixtures.playlistId).count == 4)
        #expect(PlaylistPlayability.allowedVideos(twice + twice, profile: profile, playlist: playlist,
                                                  context: context.modelContext).count == 4)
    }

    @Test func unapprovedPlaylistShowsNothing() async throws {
        let http = StubHTTPClient()
        http.on("feeds/videos.xml?playlist_id=", body: PlaylistFixtures.feed)
        let (context, _) = try makeContext(http: http)
        let model = PlaylistModel(playlistId: "PLfremd", playlistTitle: "Fremd", context: context)
        await model.onAppear()
        #expect(model.rows.isEmpty)
        #expect(http.requested.isEmpty, "nicht freigegebene Playlist wird gar nicht erst geladen")
    }

    @Test func sleepPlaylistCountsAsParentChoice() async throws {
        let http = StubHTTPClient()
        http.on("feeds/videos.xml?playlist_id=PLschlaf", body: PlaylistFixtures.feed)
        let (context, profile) = try makeContext(http: http)
        profile.sleepPlaylistId = "PLschlaf"
        let model = PlaylistModel(playlistId: "PLschlaf", playlistTitle: "Schlaf-Playlist",
                                  context: KidContext(modelContext: context.modelContext, youtube: context.youtube), profile: profile)
        await model.onAppear()
        #expect(model.rows.count == 4)
    }

    @Test func playerBoundaryAppliesThePlaylistRule() throws {
        let (context, profile) = try makeContext(http: StubHTTPClient())
        let db = context.modelContext
        let fromPlaylist = PlayerModel.Item(videoId: "a", title: "Spring", channelTitle: "Blender Studio",
                                            sourceChannelId: PlaylistFixtures.blenderStudio, sourcePlaylistId: PlaylistFixtures.playlistId)
        #expect(SafeRecommendationService.allowed(fromPlaylist, for: profile, context: db))

        var withoutContext = fromPlaylist
        withoutContext.sourcePlaylistId = nil
        #expect(!SafeRecommendationService.allowed(withoutContext, for: profile, context: db), "ohne Playlist keine Freigabe")

        var foreign = fromPlaylist
        foreign.sourcePlaylistId = "PLfremd"
        #expect(!SafeRecommendationService.allowed(foreign, for: profile, context: db))

        var risky = fromPlaylist
        risky.title = "NSFW Compilation"
        #expect(!SafeRecommendationService.allowed(risky, for: profile, context: db))

        let rejected = try WhitelistRepository(context: db).add(
            WhitelistItemDraft(type: .video, youtubeId: "a", title: "Spring", thumbnailUrl: ""), to: profile)
        try CurationRepository(context: db).reject(rejected, actor: "Eltern")
        #expect(!SafeRecommendationService.allowed(fromPlaylist, for: profile, context: db), "abgelehnt bleibt abgelehnt")
    }

    @Test func recentlyWatchedReachesCachedPlaylistVideos() async throws {
        let http = StubHTTPClient()
        http.on("feeds/videos.xml?playlist_id=", body: PlaylistFixtures.feed)
        let (context, profile) = try makeContext(http: http)
        await PlaylistModel(playlistId: PlaylistFixtures.playlistId, playlistTitle: "B", context: context).onAppear()
        let ids = KidRows.playableVideoIds(profile: profile, context: context)
        #expect(ids.isSuperset(of: ["u9lj-c29dxI", "WhWc3b3KhnY", "SkVqJ1SGeL0", "Z4C82eyhwgU"]))
    }
}
