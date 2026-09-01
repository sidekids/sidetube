import Foundation
import SwiftData
import Testing
@testable import sidetube

struct ChannelVideoCacheRepositoryTests {
    @Test func upsertDeduplicatesAndSearchIsLocal() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let cache = ChannelVideoCacheRepository(context: context)
        try cache.upsert([
            PlaylistVideo(videoId: "a", title: "Peppa im Schnee", thumbnailUrl: "t", channelTitle: "K", position: 0),
            PlaylistVideo(videoId: "b", title: "Peppa am Strand", thumbnailUrl: "t", channelTitle: "K", position: 1),
        ], channelId: "UC1")
        try cache.upsert([
            PlaylistVideo(videoId: "a", title: "Peppa im Schnee (neu)", thumbnailUrl: "t2", channelTitle: "K", position: 0),
            PlaylistVideo(videoId: "c", title: "Feuerwehrmann", thumbnailUrl: "t", channelTitle: "K", position: 2),
        ], channelId: "UC1")
        try cache.upsert([PlaylistVideo(videoId: "a", title: "anderer Kanal", thumbnailUrl: "t", channelTitle: "K2", position: 0)], channelId: "UC2")

        let videos = cache.videos(channelId: "UC1")
        #expect(videos.map(\.videoId) == ["a", "b", "c"])
        #expect(videos[0].title == "Peppa im Schnee (neu)")
        #expect(cache.search(channelId: "UC1", query: "peppa").count == 2)
        #expect(cache.search(channelId: "UC1", query: "SCHNEE").map(\.videoId) == ["a"])
        #expect(cache.searchAll(query: "kanal").map(\.videoId) == ["a"])
        try cache.clear(channelId: "UC1")
        #expect(cache.videos(channelId: "UC1").isEmpty)
        #expect(cache.videos(channelId: "UC2").count == 1)
    }
}

struct KidModelTests {
    private func makeContext(http: HTTPClient = StubHTTPClient(), apiKey: String? = "KEY") throws -> (KidContext, KidProfile) {
        let modelContext = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: modelContext).create(name: "Mia")
        // Testkanaele als vertrauenswuerdige Kinderquellen registrieren – sonst kein Stoebern (ContentPolicy)
        try CurationRepository(context: modelContext).ensureSources([
            SourceDefinition(channelId: "UC_x5XG1OV2P6uZZ5FSM9Ttw", handle: "GoogleDevelopers", title: "GfD", trust: .trustedChildSource),
            SourceDefinition(channelId: "UC1", handle: nil, title: "Kanal", trust: .trustedChildSource),
        ])
        let context = KidContext(modelContext: modelContext, youtube: YouTubeRepository(http: http, apiKey: apiKey))
        return (context, profile)
    }

    @Test func homeTabShowsCardsAndRecentlyWatched() throws {
        let (context, profile) = try makeContext()
        let repo = WhitelistRepository(context: context.modelContext)
        try repo.add(WhitelistItemDraft(type: .channel, youtubeId: "UC1", title: "Kanal", thumbnailUrl: "", channelTitle: nil), to: profile)
        try repo.add(WhitelistItemDraft(type: .video, youtubeId: "v1", title: "Video 1", thumbnailUrl: "", channelTitle: "Kanal"), to: profile)
        try repo.add(WhitelistItemDraft(type: .video, youtubeId: "v2", title: "Video 2", thumbnailUrl: "", channelTitle: "Kanal"), to: profile)
        let watch = WatchTimeRepository(context: context.modelContext)
        try watch.record(videoId: "v1", title: "Video 1", seconds: 30, for: profile, at: Date(timeIntervalSinceNow: -100))
        try watch.record(videoId: "v9", title: "Altes", seconds: 30, for: profile, at: Date(timeIntervalSinceNow: -50))
        try watch.record(videoId: "v1", title: "Video 1", seconds: 30, for: profile, at: Date())

        let videos = HomeModel(profile: profile, tab: .videos, context: context)
        #expect(videos.usesSplitHeader)
        #expect(videos.lead?.id == "lead-recent-v1", "Kachel links = zuletzt gespieltes Video")
        #expect(videos.lead?.subtitle == nil)
        #expect(videos.cards.map(\.id) == ["v2", "v1"])
        #expect(videos.cardsTitle == "Videos", "kein Zaehler im Titel")
        // Das zuletzt Gespielte steht als Kachel oben und darf in der Liste darunter nicht noch einmal auftauchen.
        #expect(videos.rows.map(\.id) == ["recent-v9"], "ohne den Eintrag, der schon Kachel ist")
        #expect(videos.rowsTitle == "Weiterschauen")
        #expect(videos.menu.count == 4, "Kachel + 2 Karten + 1 Zeile")
        #expect(videos.allItems.count == 4)
        #expect(videos.rowsStartIndex == 3)
        videos.menu.move(by: 3)
        #expect(videos.selectedItem?.id == "recent-v9")
        #expect(videos.selectionIsInRows)

        let fresh = try ProfileRepository(context: context.modelContext).create(name: "Neu")
        let freshHome = HomeModel(profile: fresh, tab: .videos, context: context)
        #expect(freshHome.lead == nil, "ohne Verlauf keine Weiterschauen-Kachel")
        #expect(freshHome.rowsStartIndex == 0)

        let channels = HomeModel(profile: profile, tab: .channels, context: context)
        #expect(channels.cards.count == 1)
        #expect(channels.cards[0].thumbnailStyle == .avatar)
        guard case .push(let factory) = channels.cards[0].action else { Issue.record("Kanal muss einen Bildschirm öffnen"); return }
        let channel = factory.make()
        #expect(channel is ChannelModel)
        #expect(channel.hero?.title == "Kanal")
        #expect(HomeModel(profile: profile, tab: .playlists, context: context).footerHint != nil)
    }

    @Test func channelLoadsRSSThenAPIOnDemandAndSearchesCache() async throws {
        let http = StubHTTPClient()
        http.on("feeds/videos.xml?channel_id=UC_x5XG1OV2P6uZZ5FSM9Ttw", body: Fixtures.rss)
        http.on("playlistItems?", body: Fixtures.apiPlaylistItemsPage1)
        let (context, _) = try makeContext(http: http)
        let model = ChannelModel(channelId: "UC_x5XG1OV2P6uZZ5FSM9Ttw", channelTitle: "GfD", context: context)

        await model.onAppear()
        #expect(model.rows.map(\.id) == ["qzLrKKjdsPU", "zweiteID"])
        #expect(model.menu.count == 2)
        #expect(http.calls(containing: "googleapis") == 0)

        model.onSelectionChanged(index: 1)        // Listenende → API-Seite nachladen
        try await Task.sleep(for: .milliseconds(200))
        #expect(http.calls(containing: "playlistItems") == 1)
        // Sortierung: position, dann Titel (RSS und API teilen sich die Positionen 0…; Cache dedupliziert per videoId)
        #expect(model.rows.map(\.id) == ["qzLrKKjdsPU", "v1", "zweiteID", "v3"])

        model.searchText = "erstes"
        #expect(model.rows.map(\.id) == ["v1"])
        model.searchText = "gibtsnicht"
        #expect(model.rows.isEmpty)
        #expect(model.footerHint?.contains("gibtsnicht") == true)
    }

    @Test func channelWithoutAPIKeyKeepsRSSAndExplains() async throws {
        let http = StubHTTPClient()
        http.on("feeds/videos.xml", body: Fixtures.rss)
        let (context, _) = try makeContext(http: http, apiKey: nil)
        let model = ChannelModel(channelId: "UC_x5XG1OV2P6uZZ5FSM9Ttw", channelTitle: "GfD", context: context)
        await model.onAppear()
        #expect(model.rows.count == 2)
        await model.loadNextPage()
        #expect(model.rows.count == 2)
        #expect(model.footerHint == "Ältere Videos brauchen den YouTube-API-Schlüssel.")
    }

    @Test func playlistPagesAccumulate() async throws {
        let http = StubHTTPClient()
        http.on("pageToken=TOKEN2", body: Fixtures.apiEmpty)
        http.on("playlistItems?", body: Fixtures.apiPlaylistItemsPage1)
        let (context, _) = try makeContext(http: http)
        let model = PlaylistModel(playlistId: "PL999", playlistTitle: "Liste", context: context)
        await model.onAppear()
        #expect(model.rows.count == 2)
        await model.loadNextPage()
        #expect(model.rows.count == 2)
        #expect(http.calls(containing: "playlistItems") == 2)
    }

    @Test func searchCombinesWhitelistAndCache() throws {
        let (context, profile) = try makeContext()
        let repo = WhitelistRepository(context: context.modelContext)
        try repo.add(WhitelistItemDraft(type: .video, youtubeId: "v1", title: "Peppa Wutz Folge 1", thumbnailUrl: "", channelTitle: "Peppa"), to: profile)
        try repo.add(WhitelistItemDraft(type: .channel, youtubeId: "UC1", title: "Feuerwehrmann Sam", thumbnailUrl: "", channelTitle: nil), to: profile)
        try context.cache.upsert([
            PlaylistVideo(videoId: "c1", title: "Sam rettet Norman", thumbnailUrl: "", channelTitle: "Feuerwehrmann Sam", position: 0),
            PlaylistVideo(videoId: "v1", title: "Peppa Wutz Folge 1", thumbnailUrl: "", channelTitle: "Peppa", position: 1),
        ], channelId: "UC1")
        let model = SearchModel(profile: profile, context: context)
        #expect(model.rows.isEmpty)
        model.searchText = "sam"
        #expect(model.rows.map(\.id) == ["UC1", "c1"])
        model.searchText = "peppa"
        #expect(model.rows.map(\.id) == ["v1"], "Cache-Treffer mit gleicher ID wird nicht doppelt gelistet")
        model.searchText = "zzz"
        #expect(model.footerHint == "Keine freigegebenen Videos gefunden.")
    }

    @Test func remoteControllerDrivesTargetAndPlayer() throws {
        let (context, profile) = try makeContext()
        let repo = WhitelistRepository(context: context.modelContext)
        try repo.add(WhitelistItemDraft(type: .video, youtubeId: "v1", title: "Eins", thumbnailUrl: "", channelTitle: "K"), to: profile)
        try repo.add(WhitelistItemDraft(type: .video, youtubeId: "v2", title: "Zwei", thumbnailUrl: "", channelTitle: "K"), to: profile)
        let library = LibraryModel(profile: profile, type: .video, context: context)
        let remote = RemoteController()
        var activated: [String] = []
        var backCalls = 0
        remote.target = RemoteTargetBinding(model: library) { activated.append($0.id) }
        remote.goBack = { backCalls += 1 }

        #expect(!remote.isSelected(library, index: 0), "ohne geoeffnete Fernbedienung kein Auswahlzustand")
        remote.isPresented = true
        #expect(remote.isSelected(library, index: 0))
        remote.handle(.down)
        #expect(library.menu.selectedIndex == 1)
        remote.handle(.next); #expect(library.menu.selectedIndex == 1, "Ende")
        remote.handle(.previous); #expect(library.menu.selectedIndex == 0, "Anfang")
        remote.handle(.rotate(steps: 1))
        remote.handle(.select)
        #expect(activated == ["v1"], "Karten nach addedAt absteigend: v2, v1")
        remote.handle(.menu)
        #expect(backCalls == 1)

        let engine = FakePlayerEngine()
        let player = PlayerModel(queue: [.init(videoId: "a", title: "A"), .init(videoId: "b", title: "B")], startIndex: 0, engine: engine)
        player.start()
        remote.player = player
        remote.handle(.rotate(steps: -2)); remote.handle(.next); remote.handle(.up); remote.handle(.down); remote.handle(.select)
        #expect(engine.commands == ["seek -20", "volume 100", "volume 90", "toggle"])
        #expect(player.current.videoId == "b")
        remote.handle(.menu)
        #expect(backCalls == 2)
    }

    @Test func playerCoordinatorBuildsQueueFromVisibleList() throws {
        let (context, profile) = try makeContext()
        let engine = FakePlayerEngine()
        let coordinator = PlayerCoordinator()
        coordinator.makeEngine = { engine }
        let rows = [
            KidRow(id: "c", title: "Kanal", action: .push(KidScreenFactory(id: "x") { LibraryModel(profile: profile, type: .video, context: context) })),
            KidRow(id: "v1", title: "Eins", action: .play(videoId: "v1", title: "Eins")),
            KidRow(id: "v2", title: "Zwei", action: .play(videoId: "v2", title: "Zwei")),
            KidRow(id: "recent-v1", title: "Eins", action: .play(videoId: "v1", title: "Eins")),
        ]
        coordinator.play(rows[2], in: rows, profile: profile, context: context.modelContext)
        #expect(coordinator.isPresented)
        #expect(coordinator.player?.queue.map(\.videoId) == ["v1", "v2"], "nur abspielbare, ohne Doppelte")
        #expect(coordinator.player?.current.videoId == "v2")
        #expect(engine.loaded == ["v2"])
        coordinator.fullscreenRequested = true
        coordinator.close()
        #expect(!coordinator.isPresented)
        #expect(!coordinator.fullscreenRequested)
        #expect(coordinator.bridge == nil, "Fake-Engine ist keine WebView-Bridge")
    }

    @Test func kidSessionRemembersProfile() throws {
        let (context, first) = try makeContext()
        let second = try ProfileRepository(context: context.modelContext).create(name: "Tom")
        let session = KidSession()
        session.resolve(from: [first, second])
        #expect(session.activeProfile != nil)
        session.select(second)
        let again = KidSession()
        again.resolve(from: [first, second])
        #expect(again.activeProfile?.id == second.id)
        again.resolve(from: [first])
        #expect(again.activeProfile?.id == first.id, "geloeschtes Profil faellt auf das erste zurueck")
    }
}
