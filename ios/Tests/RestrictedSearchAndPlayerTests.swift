// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

struct RestrictedSearchTests {
    @Test func searchFindsOnlyApprovedContent() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia")
        let curation = CurationRepository(context: context)
        try curation.ensureSources(SourceRegistry.definitions)
        let approved = try curation.discover(WhitelistItemDraft(type: .video, youtubeId: "a", title: "Naruto zeichnen lernen", thumbnailUrl: "", channelTitle: "KritzelPixel"),
                                             for: profile, sourceChannelId: "UCArSticZ19uxwbcFNdF-uuw")
        try curation.approve(approved, with: .init(ageMin: 8, ageMax: nil, category: .mangaDrawing, newsStatus: nil, parentNotes: nil), actor: "Eltern")
        _ = try curation.discover(WhitelistItemDraft(type: .video, youtubeId: "b", title: "Naruto Folge 1 komplett", thumbnailUrl: "", channelTitle: "Irgendwer"), for: profile)
        // gecachte Kanalvideos zaehlen nur fuer sichtbare, vertrauenswuerdige Kinderquellen
        try WhitelistRepository(context: context).add(WhitelistItemDraft(type: .channel, youtubeId: "UCRWSxXBnz9IRS4SgRhG2wpQ", title: "Die Maus", thumbnailUrl: "", channelTitle: nil), to: profile)
        try context.insert(CachedChannelVideo(channelId: "UCRWSxXBnz9IRS4SgRhG2wpQ", videoId: "m", title: "Naruto-Kostüm basteln | Die Maus", thumbnailUrl: "", channelTitle: "Die Maus", position: 0))
        try context.insert(CachedChannelVideo(channelId: "UCArSticZ19uxwbcFNdF-uuw", videoId: "kp", title: "Naruto Speedpaint", thumbnailUrl: "", channelTitle: "KritzelPixel", position: 0))
        try context.save()

        let http = StubHTTPClient()
        let model = SearchModel(profile: profile, context: KidContext(modelContext: context, youtube: YouTubeRepository(http: http, apiKey: nil)))
        model.searchText = "Naruto"
        #expect(model.rows.map(\.id) == ["a", "m"], "nur freigegeben (a) + Cache der vertrauenswuerdigen Kinderquelle (m); b (Prüfung) und kp (Einzelprüfungs-Quelle) fehlen")
        #expect(http.requested.isEmpty, "keine Netzanfrage – niemals eine offene YouTube-Suche")
        model.searchText = "Chainsaw"
        #expect(model.rows.isEmpty)
        #expect(model.footerHint == "Keine freigegebenen Videos gefunden.")
    }

    @Test func channelBrowsingOnlyForTrustedChildSources() async throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        try CurationRepository(context: context).ensureSources(SourceRegistry.definitions)
        let http = StubHTTPClient()
        http.on("feeds/videos.xml", body: Fixtures.rss)
        let kid = KidContext(modelContext: context, youtube: YouTubeRepository(http: http, apiKey: nil))
        let perVideo = ChannelModel(channelId: "UCArSticZ19uxwbcFNdF-uuw", channelTitle: "KritzelPixel", context: kid)
        await perVideo.onAppear()
        #expect(perVideo.rows.isEmpty)
        #expect(http.requested.isEmpty, "Einzelpruefungs-Quelle wird nicht dynamisch geladen")
        #expect(perVideo.footerHint?.contains("einzeln freigegeben") == true)
        let trusted = ChannelModel(channelId: "UC_x5XG1OV2P6uZZ5FSM9Ttw", channelTitle: "Test", context: kid)
        await trusted.onAppear()
        #expect(trusted.rows.isEmpty, "unbekannte Quelle = keine Kinderquelle → kein Stöbern")
    }
}

struct AutoplayPolicyTests {
    @Test func playbackStopsAtEndByDefault() {
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: [.init(videoId: "a", title: "A"), .init(videoId: "b", title: "B")], startIndex: 0, engine: engine)
        model.start()
        engine.emit(.state(1)); engine.emit(.state(0))
        #expect(model.status == .ended)
        #expect(model.current.videoId == "a", "kein automatisches naechstes Video")
        #expect(engine.commands.last == "stop", "Vorschaubild statt Empfehlungsraster")
        model.next()
        #expect(model.current.videoId == "b", "das Kind entscheidet bewusst")
    }

    @Test func autoAdvanceOnlyWhenParentsEnableIt() {
        let engine = FakePlayerEngine()
        let model = PlayerModel(queue: [.init(videoId: "a", title: "A"), .init(videoId: "b", title: "B")], startIndex: 0, engine: engine)
        model.autoAdvance = true
        model.start()
        engine.emit(.state(0))
        #expect(model.current.videoId == "b")
    }
}
