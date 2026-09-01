// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

/// Testfälle aus – die Sichtbarkeitsregel des Kinderprofils.
struct ContentPolicyTests {
    private func makeWorld() throws -> (ModelContext, KidProfile, CurationRepository) {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia")
        let curation = CurationRepository(context: context)
        try curation.ensureSources(SourceRegistry.definitions)
        return (context, profile, curation)
    }

    private func video(_ id: String, _ title: String, channel: String = "UCRWSxXBnz9IRS4SgRhG2wpQ") -> WhitelistItemDraft {
        WhitelistItemDraft(type: .video, youtubeId: id, title: title, thumbnailUrl: "", channelTitle: "Quelle")
    }

    @Test func newContentIsNeverApprovedAndInvisible() throws {
        let (context, profile, curation) = try makeWorld()
        let item = try curation.discover(video("a", "Wie entstehen Ebbe und Flut?"), for: profile, sourceChannelId: "UCRWSxXBnz9IRS4SgRhG2wpQ")
        #expect(item.approvalStatus == .reviewRequired)
        #expect(!ContentPolicy.isVisible(item, for: profile, source: curation.source(channelId: item.sourceChannelId)))
        #expect(WhitelistRepository(context: context).visibleItems(of: profile).isEmpty)
        #expect(curation.events(for: "a").map(\.decision) == [.discovered])
    }

    @Test func approvedContentIsVisibleAndAudited() throws {
        let (context, profile, curation) = try makeWorld()
        let item = try curation.discover(video("a", "Wieso ist ein Regenbogen rund?"), for: profile, sourceChannelId: "UCRWSxXBnz9IRS4SgRhG2wpQ")
        try curation.approve(item, with: .init(ageMin: 5, ageMax: nil, category: .knowledge, newsStatus: nil, parentNotes: "gut"), actor: "Eltern")
        #expect(item.approvalStatus == .approved)
        #expect(item.approvedBy == "Eltern")
        #expect(WhitelistRepository(context: context).visibleItems(of: profile).map(\.youtubeId) == ["a"])
        let events = curation.events(for: "a")
        #expect(events.first?.decision == .approved)
        #expect(events.first?.itemVersion == item.reviewVersion)
        #expect(events.first?.note == "gut")
    }

    @Test func wrongAgeGroupIsInvisible() throws {
        let (_, profile, curation) = try makeWorld()
        profile.ageBand = .younger   // 6
        let item = try curation.discover(video("a", "Der Zahlen-Check"), for: profile, sourceChannelId: "UCQtsd17U8NOM1VRI8oxdwiQ")
        try curation.approve(item, with: .init(ageMin: 8, ageMax: nil, category: .knowledge, newsStatus: nil, parentNotes: nil), actor: "Eltern")
        #expect(!ContentPolicy.isVisible(item, for: profile, source: nil))
        profile.ageBand = .kids
        #expect(ContentPolicy.isVisible(item, for: profile, source: nil))
        try curation.approve(item, with: .init(ageMin: 3, ageMax: 8, category: .stories, newsStatus: nil, parentNotes: nil), actor: "Eltern")
        #expect(!ContentPolicy.isVisible(item, for: profile, source: nil), "Höchstalter greift")
    }

    @Test func blockedSourceIsNeverVisibleAndCannotBeDiscovered() throws {
        let (_, profile, curation) = try makeWorld()
        #expect(throws: CurationRepository.DiscoverError.blockedSource) {
            try curation.discover(video("r", "Top 10 Anime"), for: profile, sourceChannelId: "UCUkS6puSkMpiUtO7qk6Ms7g")
        }
        // selbst ein (fälschlich) freigegebener Eintrag einer gesperrten Quelle bleibt unsichtbar
        let item = WhitelistItem(type: .video, youtubeId: "k", title: "Kurono Clip", thumbnailUrl: "", approvalStatus: .approved)
        item.profile = profile
        item.sourceChannelId = "UCUP5c992W1Gel8ylUnljpUQ"
        #expect(!ContentPolicy.isVisible(item, for: profile, source: curation.source(channelId: item.sourceChannelId)))
        #expect(!ContentPolicy.isVisible(item, for: profile, source: curation.source(channelId: "UCXcrFHACw1Wyk83bawK2Arg")), "parentOnly (NinotakuTV) ebenfalls unsichtbar")
    }

    @Test func shortsAndLivesAreExcludedByDefault() throws {
        let (_, profile, curation) = try makeWorld()
        let short = try curation.discover(video("s", "Krass Nass Buzz #shorts"), for: profile, sourceChannelId: "UCxFvLj7FDoMChztQTSRDAbw")
        #expect(short.isShort)
        try curation.approve(short, with: .init(ageMin: 6, ageMax: nil, category: .humor, newsStatus: nil, parentNotes: nil), actor: "Eltern")
        #expect(!ContentPolicy.isVisible(short, for: profile, source: nil))
        profile.allowShorts = true
        #expect(ContentPolicy.isVisible(short, for: profile, source: nil))
        let live = try curation.discover(video("l", "🔴 Livestream: Kinderdisco"), for: profile, sourceChannelId: "UCxFvLj7FDoMChztQTSRDAbw")
        try curation.approve(live, with: .init(ageMin: 6, ageMax: nil, category: .music, newsStatus: nil, parentNotes: nil), actor: "Eltern")
        #expect(live.isLive)
        #expect(!ContentPolicy.isVisible(live, for: profile, source: nil), "Lives nie im Kinderprofil")
    }

    @Test func newsSensitivityFlagWorks() throws {
        let (_, profile, curation) = try makeWorld()
        profile.ageBand = .kids
        let safe = try curation.discover(video("n1", "Immer mehr Kinobesuche | logo!-Nachrichten"), for: profile, sourceChannelId: "UCuziK4bUFRr3z62bE7FMDPQ")
        let heavy = try curation.discover(video("n2", "Heftige Flutwelle in Nepal | logo!-Nachrichten"), for: profile, sourceChannelId: "UCuziK4bUFRr3z62bE7FMDPQ")
        #expect(safe.isNews && safe.newsStatus == .safe)
        #expect(heavy.isNews && heavy.newsStatus == .sensitive)
        #expect(heavy.sensitiveTopics.contains(.disaster))
        for item in [safe, heavy] {
            try curation.approve(item, with: .init(ageMin: 8, ageMax: nil, category: .news, newsStatus: item.newsStatus, parentNotes: nil), actor: "Eltern")
        }
        #expect(ContentPolicy.isVisible(safe, for: profile, source: nil))
        #expect(!ContentPolicy.isVisible(heavy, for: profile, source: nil), "belastende Nachricht bleibt trotz Freigabe verborgen, bis Eltern sie auf „unbedenklich“ setzen")
        #expect(!ContentPolicy.isHomeHighlightable(heavy))
        profile.allowNews = false
        #expect(!ContentPolicy.isVisible(safe, for: profile, source: nil))
    }

    @Test func mangaAgeGatesAndTanoshiiUnder12Hidden() throws {
        let (_, profile, curation) = try makeWorld()
        let drawing = try curation.discover(video("m", "Manga Figuren zeichnen lernen"), for: profile, sourceChannelId: "UCArSticZ19uxwbcFNdF-uuw")
        try curation.approve(drawing, with: .init(ageMin: 8, ageMax: nil, category: .mangaDrawing, newsStatus: nil, parentNotes: nil), actor: "Eltern")
        let tanoshii = try curation.discover(WhitelistItemDraft(type: .video, youtubeId: "zdf:tanoshii-1", title: "Tanoshii – Pokémon Creator Special", thumbnailUrl: "", channelTitle: "Tanoshii"),
                                             for: profile, sourceChannelId: "zdf:tanoshii")
        try curation.approve(tanoshii, with: .init(ageMin: 12, ageMax: nil, category: .animeManga, newsStatus: nil, parentNotes: nil), actor: "Eltern")
        profile.allowMangaEntertainment = true

        profile.ageBand = .younger
        #expect(!ContentPolicy.isVisible(drawing, for: profile, source: nil), "Manga zeichnen unter 8 unsichtbar")
        #expect(!ContentPolicy.isVisible(tanoshii, for: profile, source: nil))
        profile.ageBand = .kids
        #expect(ContentPolicy.isVisible(drawing, for: profile, source: nil))
        #expect(!ContentPolicy.isVisible(tanoshii, for: profile, source: nil), "Tanoshii unter 12 unsichtbar")
        #expect(ContentPolicy.homeCategorySections(for: profile) == [.knowledge, .mangaDrawing])
        profile.ageBand = .older
        #expect(ContentPolicy.isVisible(tanoshii, for: profile, source: curation.source(channelId: "zdf:tanoshii")))
        #expect(ContentPolicy.homeCategorySections(for: profile) == [.knowledge, .mangaDrawing, .animeManga])
        profile.allowManga = false
        #expect(!ContentPolicy.isVisible(drawing, for: profile, source: nil))
        #expect(!ContentPolicy.isVisible(tanoshii, for: profile, source: nil))
        #expect(ContentPolicy.homeCategorySections(for: profile) == [.knowledge])
    }

    @Test func parentCanRejectAndReviewBecomesDue() throws {
        let (_, profile, curation) = try makeWorld()
        var clock = Date(timeIntervalSince1970: 1_700_000_000)
        var repo = curation; repo.now = { clock }
        let item = try repo.discover(video("a", "Der Töpfer-Check"), for: profile, sourceChannelId: "UCQtsd17U8NOM1VRI8oxdwiQ")
        clock.addTimeInterval(1)
        try repo.approve(item, with: .init(ageMin: 8, ageMax: nil, category: .creative, newsStatus: nil, parentNotes: nil), actor: "Eltern")
        #expect(!ContentPolicy.reviewIsDue(item, source: repo.source(channelId: item.sourceChannelId), now: clock))
        clock = Calendar.current.date(byAdding: .month, value: 13, to: clock)!
        #expect(ContentPolicy.reviewIsDue(item, source: repo.source(channelId: item.sourceChannelId), now: clock))
        #expect(try repo.markExpiredReviews(in: profile) == 1)
        #expect(item.approvalStatus == .expiredReview, "fällig ≠ abgelehnt")
        #expect(!ContentPolicy.isVisible(item, for: profile, source: nil))
        clock.addTimeInterval(1)
        try repo.reject(item, actor: "Eltern", note: "nicht mehr passend")
        #expect(item.approvalStatus == .rejected)
        #expect(repo.events(for: "a").map(\.decision) == [.rejected, .statusExpired, .approved, .discovered])
    }

    @Test func hardRiskTermsAutoRejectAndNeverApprove() throws {
        let (_, profile, curation) = try makeWorld()
        let item = try curation.discover(video("x", "Die Lizenz zum G00nen | Gartic Phone"), for: profile, sourceChannelId: "UCIUmF5e2-BRVx31HDxExQoA")
        #expect(item.approvalStatus == .rejected)
        #expect(curation.events(for: "x").first?.decision == .autoRejected)
        let risk = RiskScreen.assess(title: "Attack on Titan Reaction – brutal!", description: "Chainsaw Man Spoiler")
        #expect(risk.topics.isSuperset(of: [.adultMedia, .violence]))
        #expect(!risk.isHardBlocked, "Marker ≠ Ablehnung: Eltern entscheiden")
        #expect(RiskScreen.assess(title: "Perspektive zeichnen lernen in 60 Sekunden", durationSeconds: 58).isShort)
    }

    @Test func seedLibraryImportsOnlyForReviewAndSkipsBlocked() throws {
        let (context, profile, curation) = try makeWorld()
        var library = try SeedLibraryImporter.load(named: "general", from: .main)   // Test-Host = App-Bundle
        #expect(library.videos.count >= 20 && library.videos.count <= 40, "20–40 sorgfältig ausgewählte Inhalte")
        library.videos.append(.init(id: "bad", title: "Raafey Top 10", channelId: "UCUkS6puSkMpiUtO7qk6Ms7g", channelTitle: "Raafey", ageMin: 12))
        let result = try SeedLibraryImporter(context: context).importLibrary(library, into: profile)
        #expect(result.imported == library.videos.count - 1)
        #expect(result.blocked == 1)
        #expect(profile.whitelistItems.allSatisfy { $0.approvalStatus != .approved }, "Seeds sind nie freigegeben")
        #expect(WhitelistRepository(context: context).visibleItems(of: profile).isEmpty)
        #expect(curation.pendingReview(in: profile).count == result.imported - 0)
        let tanoshii = profile.whitelistItems.first { $0.youtubeId.hasPrefix("zdf:") }
        #expect(tanoshii?.provider == .zdf && tanoshii?.ageMin == 12 && tanoshii?.category == .animeManga)
        let again = try SeedLibraryImporter(context: context).importLibrary(library, into: profile)
        #expect(again.imported == 0 && again.skipped == library.videos.count - 1, "idempotent")
    }
}

/// Der Risikofilter darf nicht in Wortmitten treffen – sonst erzeugt er Fehlalarme und stumpft ab.
struct RiskScreenWordBoundaryTests {
    @Test func termsCountOnlyAtWordStart() {
        // Belege aus einem echten Kanalbestand, die vorher falsch anschlugen
        #expect(RiskScreen.assess(title: "Alle Halten Ihn Für Schwach Doch Er Ist Der Stärkste Attentäter").topics.isEmpty)
        #expect(RiskScreen.assess(title: "Verlassen Als Schwach Doch Ist SS Rang Und Knackt Alle").topics.isEmpty)
        #expect(RiskScreen.assess(title: "Ohne Magie Geboren Doch Wird Er Zum Stärksten Krieger").topics.isEmpty)
        #expect(RiskScreen.assess(title: "Neuer Schüler Schockt Die Schule Mit Seinen Gewaltigen Kampfkünsten").topics.isEmpty)
    }

    @Test func realHitsStillFire() {
        #expect(RiskScreen.assess(title: "Krieg in der Ukraine").topics.contains(.war))
        #expect(RiskScreen.assess(title: "Kriegsgebiet aus der Luft").topics.contains(.war))
        #expect(RiskScreen.assess(title: "Gewalt an Schulen").topics.contains(.violence))
        #expect(RiskScreen.assess(title: "Entführung aufgeklärt").topics.contains(.crime))
        #expect(RiskScreen.assess(title: "Er entführt die Prinzessin").topics.contains(.crime))
        #expect(RiskScreen.assess(title: "Bombenangriff auf die Stadt").topics.contains(.war))
        #expect(RiskScreen.assess(title: "Chainsaw Man Reaction").topics.contains(.adultMedia))
        #expect(RiskScreen.assess(title: "Hentai Compilation").isHardBlocked)
        #expect(!RiskScreen.assess(title: "Wie man Schokolade macht").isHardBlocked)
    }

    @Test func hardBlockAlsoRespectsWordStart() {
        // „goon“ steckt in „Dragoon“ – das darf keine Ablehnung auslösen
        #expect(!RiskScreen.assess(title: "Dragoon baut eine Burg").isHardBlocked)
        #expect(RiskScreen.assess(title: "Die Lizenz zum Goonen").isHardBlocked)
    }
}

/// Unbekannte Kanäle müssen im Elternbereich einstufbar sein, sonst bleiben sie für immer gesperrt.
struct UnclassifiedSourceTests {
    @Test func aChannelWithoutRegistryEntryCanBeClassifiedByParents() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = KidProfile(name: "Mira")
        context.insert(profile)
        let curation = CurationRepository(context: context)
        try curation.ensureSources(SourceRegistry.allDefinitions)

        let unknown = "UCKxXras0ymEhXM3opBA4hiQ"   // Aniblitz – steht nicht im Register
        #expect(curation.effectiveSource(channelId: unknown) == nil)
        #expect(curation.source(channelId: unknown)?.trust.allowsChannelBrowsing != true, "unbekannt ⇒ kein Stöbern")

        try curation.ensureSources([
            SourceDefinition(channelId: unknown, handle: nil, title: "Aniblitz", trust: .trustedChildSource,
                             notes: "Aus der Whitelist übernommen und von Eltern eingestuft.")
        ])
        let source = try #require(curation.source(channelId: unknown))
        #expect(source.trust.allowsChannelBrowsing, "nach der Einstufung darf gestöbert werden")
        #expect(source.defaultAgeMin == 0, "kein erfundenes Mindestalter")

        // Eine spätere Registeraktualisierung überschreibt die Elternentscheidung nicht.
        try curation.ensureSources([
            SourceDefinition(channelId: unknown, handle: nil, title: "Aniblitz", trust: .perVideoReview)
        ])
        #expect(curation.source(channelId: unknown)?.trust == .trustedChildSource)
    }
}

/// Kanalübernahme von einem anderen Gerät: ganze Kanäle statt Einzelvideos.
struct ChannelLibraryImportTests {
    private let json = """
    {
      "version": 1,
      "createdAt": "2026-09-01",
      "id": "geraeteuebernahme",
      "title": "Kanäle vom anderen Gerät",
      "channels": [
        { "id": "UCRWSxXBnz9IRS4SgRhG2wpQ", "title": "Die Maus",
          "thumbnailUrl": "https://example.org/maus.jpg", "note": "aus der Whitelist übernommen" },
        { "id": "UCunbekannt0000000000000", "title": "Unbekannter Kanal",
          "note": "nicht im Quellenregister" }
      ],
      "videos": []
    }
    """

    @Test func channelsArriveForReviewAndStayIdempotent() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = KidProfile(name: "Mira")
        context.insert(profile)
        let curation = CurationRepository(context: context)
        let library = try JSONDecoder().decode(SeedLibraryImporter.SeedLibrary.self, from: Data(json.utf8))

        let result = try SeedLibraryImporter(context: context).importLibrary(library, into: profile)
        #expect(result.imported == 2)
        #expect(profile.whitelistItems.allSatisfy { $0.type == .channel })
        #expect(profile.whitelistItems.allSatisfy { $0.approvalStatus != .approved }, "Übernahme ersetzt keine Freigabe")
        #expect(WhitelistRepository(context: context).visibleItems(of: profile).isEmpty)
        #expect(curation.pendingReview(in: profile).count == 2)

        let maus = profile.whitelistItems.first { $0.youtubeId == "UCRWSxXBnz9IRS4SgRhG2wpQ" }
        #expect(maus?.title == "Die Maus")
        #expect(maus?.thumbnailUrl == "https://example.org/maus.jpg", "Kanalbild kommt aus der Datei mit")

        let again = try SeedLibraryImporter(context: context).importLibrary(library, into: profile)
        #expect(again.imported == 0 && again.skipped == 2, "idempotent")
    }
}

/// Startpaket 9–11: Verteilung, Altersgrenzen, Profil-Vorgaben, PeerTube-Anteil.
struct AgeBandSeedLibraryTests {
    private func makeWorld() throws -> (ModelContext, KidProfile, CurationRepository) {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mira")
        let curation = CurationRepository(context: context)
        try curation.ensureSources(SourceRegistry.allDefinitions)
        return (context, profile, curation)
    }

    @Test func libraryShapeMatchesTheBrief() throws {
        let library = try SeedLibraryImporter.load(named: "alter-9-11")
        #expect(library.videos.count >= 30 && library.videos.count <= 40, "30–40 sorgfältig ausgewählte Inhalte")
        #expect(library.profilePreset?.ageBand == .kids)
        #expect(library.profilePreset?.allowNews == true)
        #expect(library.profilePreset?.allowManga == true)
        #expect(library.profilePreset?.allowMangaEntertainment == false, "Anime & Manga (12+) bleibt gesperrt")
        #expect(library.profilePreset?.allowShorts == false)
        #expect(library.profilePreset?.autoplayNext == false)
        // keine Inhalte über der Altersstufe, keine Shorts/Lives, kein Tanoshii
        #expect(library.videos.allSatisfy { $0.ageMin <= 9 })
        #expect(library.videos.allSatisfy { $0.isShort != true })
        #expect(!library.videos.contains { $0.channelId == "zdf:tanoshii" })
        #expect(!library.videos.contains { $0.channelId == "UCkYs7CD2fGjsrHyBafws02w" }, "Sesamstraße nicht im 10er-Startpaket")
        // Nachrichten nur als unbedenklich eingestuft
        let news = library.videos.filter { $0.isNews == true }
        #expect(news.count >= 3 && news.count <= 5)
        #expect(news.allSatisfy { $0.newsStatus == .safe })
        // Medienkompetenz ist der Schwerpunkt, PeerTube liefert die Datenschutz-Reihe
        let media = library.videos.filter { $0.category == .mediaLiteracy }
        #expect(media.count >= 6)
        let peertube = library.videos.filter { $0.provider == .peertube }
        #expect(peertube.count == 6)
        #expect(peertube.allSatisfy { $0.id.hasPrefix("pt:tube.xn--baw-joa.social:") && $0.category == .mediaLiteracy })
        #expect(library.videos.filter { $0.category == .mangaDrawing }.count >= 3)
    }

    @Test func importSetsProfileAndKeepsEverythingForReview() throws {
        let (context, profile, curation) = try makeWorld()
        let library = try SeedLibraryImporter.load(named: "alter-9-11")
        let result = try SeedLibraryImporter(context: context).importLibrary(library, into: profile, applyProfilePreset: true)
        #expect(result.imported == library.videos.count)
        #expect(result.blocked == 0, "alle Quellen sind eingetragen – auch die PeerTube-Instanz")
        #expect(profile.ageBand == .kids)
        #expect(!profile.allowMangaEntertainment && !profile.allowShorts && !profile.autoplayNext)
        #expect(profile.whitelistItems.allSatisfy { $0.approvalStatus == .reviewRequired })
        #expect(WhitelistRepository(context: context).visibleItems(of: profile).isEmpty, "nichts sichtbar vor der Freigabe")
        #expect(curation.pendingReview(in: profile).count == library.videos.count)
        // PeerTube-Einträge sind abspielbar und tragen die Instanz als Quelle
        let peertube = profile.whitelistItems.filter { $0.provider == .peertube }
        #expect(peertube.count == 6)
        #expect(peertube.allSatisfy { $0.provider.isPlayable })
        #expect(peertube.allSatisfy { curation.effectiveSource(channelId: $0.sourceChannelId)?.channelId == "pt:tube.xn--baw-joa.social" })
    }

    @Test func afterApprovalTheAgeGatesStillHold() throws {
        let (context, profile, curation) = try makeWorld()
        let library = try SeedLibraryImporter.load(named: "alter-9-11")
        try SeedLibraryImporter(context: context).importLibrary(library, into: profile, applyProfilePreset: true)
        for item in curation.pendingReview(in: profile) {
            try curation.approve(item, with: .init(ageMin: item.ageMin, ageMax: nil, category: item.category,
                                                    newsStatus: item.newsStatus, parentNotes: nil), actor: "Eltern")
        }
        let visible = WhitelistRepository(context: context).visibleItems(of: profile)
        #expect(visible.count == library.videos.count, "alle freigegebenen Inhalte sind für 9–11 sichtbar")
        // Ein 12+-Inhalt bleibt trotz Freigabe unsichtbar
        let tanoshii = try curation.discover(WhitelistItemDraft(type: .video, youtubeId: "zdf:t1", title: "Tanoshii Folge", thumbnailUrl: "", channelTitle: "Tanoshii"),
                                             for: profile, sourceChannelId: "zdf:tanoshii")
        try curation.approve(tanoshii, with: .init(ageMin: 12, ageMax: nil, category: .animeManga, newsStatus: nil, parentNotes: nil), actor: "Eltern")
        #expect(!ContentPolicy.isVisible(tanoshii, for: profile, source: curation.source(channelId: "zdf:tanoshii")))
        // Home zeigt für dieses Profil Wissen und Manga zeichnen, aber nicht Anime & Manga
        #expect(ContentPolicy.homeCategorySections(for: profile) == [.knowledge, .mangaDrawing])
    }
}
