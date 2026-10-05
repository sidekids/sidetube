// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

/// Wünsche von Kindern (ADR 0001): Tagesgrenze, Dubletten, Stand, Elternaktionen, Verlauf.
struct WishRepositoryTests {
    private let channelId = "UCseriesChannel000000000"

    private func setUp(now: Date = Date(timeIntervalSince1970: 1_790_000_000)) throws -> (ModelContext, KidProfile, WishRepository) {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Kind")
        var repo = WishRepository(context: context, now: { now })
        repo.calendar = Calendar(identifier: .gregorian)
        return (context, profile, repo)
    }

    private func episode(_ videoId: String, title: String = "Neue Folge") -> WishDraft {
        WishDraft(kind: .neueFolge, videoId: videoId, videoTitle: title, thumbnailUrl: "", channelId: channelId, channelTitle: "Reihe")
    }

    @Test func dailyLimitIsThreePerProfileAndDay() throws {
        let (context, profile, repo) = try setUp()
        #expect(repo.remainingToday(for: profile.id) == 3)
        for topic in ["Dinos", "Vulkane", "Pferde"] { _ = try repo.submit(.thema(topic), for: profile) }
        #expect(repo.remainingToday(for: profile.id) == 0)
        #expect(throws: WishRepository.WishError.dailyLimitReached) { try repo.submit(.thema("Haie"), for: profile) }
        #expect(repo.wishes(of: profile.id).count == 3)

        // Anderes Profil: eigene Grenze.
        let other = try ProfileRepository(context: context).create(name: "Ole")
        #expect(repo.remainingToday(for: other.id) == 3)
        _ = try repo.submit(.thema("Haie"), for: other)

        // Am nächsten Tag geht es weiter.
        var tomorrow = WishRepository(context: context, now: { repo.now().addingTimeInterval(86_400) })
        tomorrow.calendar = repo.calendar
        #expect(tomorrow.remainingToday(for: profile.id) == 3)
        #expect(throws: Never.self) { try tomorrow.submit(.thema("Haie"), for: profile) }
    }

    @Test func duplicatesAreNotCreatedAndDoNotCountAgainstTheLimit() throws {
        let (_, profile, repo) = try setUp()
        let first = try repo.submit(.thema("Dinos"), for: profile)
        guard case .created = first else { Issue.record("erster Wunsch nicht angelegt"); return }
        for variant in [" dinos ", "DINOS", "Dinös"] {
            let again = try repo.submit(.thema(variant), for: profile)
            #expect(again == .duplicate(first.wish))
        }
        let video = try repo.submit(episode("vid00000001"), for: profile)
        #expect(try repo.submit(episode("vid00000001"), for: profile) == .duplicate(video.wish))
        // Andere Art zum selben Video ist ein anderer Wunsch.
        let more = try repo.submit(WishDraft(kind: .mehrDavon, videoId: "vid00000001", channelId: channelId), for: profile)
        guard case .created = more else { Issue.record("mehrDavon wurde als Dublette gewertet"); return }
        #expect(repo.wishes(of: profile.id).count == 3)
        // An der Grenze bleibt eine Dublette eine Dublette, kein Fehler.
        #expect(try repo.submit(.thema("dinos"), for: profile) == .duplicate(first.wish))
    }

    @Test func decidedWishCanBeWishedAgain() throws {
        let (_, profile, repo) = try setUp()
        let first = try repo.submit(.thema("Dinos"), for: profile).wish
        try repo.decide(first, status: .abgelehnt, reply: "Nicht jetzt")
        guard case .created = try repo.submit(.thema("Dinos"), for: profile) else {
            Issue.record("entschiedener Wunsch blockiert neuen"); return
        }
    }

    @Test func invalidInputIsRejected() throws {
        let (context, profile, repo) = try setUp()
        #expect(throws: WishRepository.WishError.emptyTopic) { try repo.submit(.thema("   "), for: profile) }
        #expect(throws: WishRepository.WishError.missingVideo) { try repo.submit(WishDraft(kind: .mehrDavon), for: profile) }
        let long = String(repeating: "a", count: 200)
        let wish = try repo.submit(.thema("  viele   \n Leerzeichen "), for: profile).wish
        #expect(wish.topic == "viele Leerzeichen")
        #expect(try repo.submit(.thema(long), for: profile).wish.topic?.count == WishDraft.maxTopicLength)
        // Gesperrte Quelle: kein Wunsch.
        context.insert(CuratedSource(channelId: "UCblocked", title: "Gesperrt", trust: .blocked))
        try context.save()
        #expect(throws: WishRepository.WishError.blockedSource) {
            try repo.submit(WishDraft(kind: .mehrDavon, videoId: "v", channelId: "UCblocked"), for: profile)
        }
    }

    @Test func statusTransitions() {
        #expect(WishStatus.offen.canMove(to: .erfuellt))
        #expect(WishStatus.offen.canMove(to: .abgelehnt))
        #expect(WishStatus.offen.canMove(to: .besprechen))
        #expect(!WishStatus.offen.canMove(to: .offen))
        #expect(WishStatus.besprechen.canMove(to: .erfuellt))
        #expect(WishStatus.besprechen.canMove(to: .abgelehnt))
        #expect(WishStatus.besprechen.canMove(to: .besprechen))
        #expect(!WishStatus.besprechen.canMove(to: .offen))
        for final in [WishStatus.erfuellt, .abgelehnt] {
            for next in WishStatus.allCases { #expect(!final.canMove(to: next)) }
        }
        #expect(WishStatus.offen.isPending && WishStatus.besprechen.isPending)
        #expect(!WishStatus.erfuellt.isPending && !WishStatus.abgelehnt.isPending)
    }

    @Test func decisionsKeepReplyAndAreLoggedInTheHistory() throws {
        let (context, profile, _) = try setUp()
        let clock = TestClock()
        let repo = WishRepository(context: context, now: { clock.now })
        let wish = try repo.submit(.thema("Dinos"), for: profile).wish
        clock.advance()
        try repo.decide(wish, status: .besprechen, reply: "  Lass uns am Samstag schauen. ")
        clock.advance()
        #expect(wish.status == .besprechen)
        #expect(wish.parentReply == "Lass uns am Samstag schauen.")
        #expect(repo.pending(of: profile.id).map(\.id) == [wish.id])
        try repo.decide(wish, status: .erfuellt, reply: "")
        #expect(wish.parentReply == "Lass uns am Samstag schauen.")   // leere Antwort ersetzt nichts
        #expect(wish.decidedAt != nil)
        #expect(repo.pending(of: profile.id).isEmpty)
        #expect(throws: WishRepository.WishError.invalidTransition) { try repo.decide(wish, status: .abgelehnt) }
        let decisions = repo.history(of: wish).map(\.decision)
        #expect(decisions == [.wishFulfilled, .wishDiscuss, .wished])
        #expect(repo.history(of: wish).allSatisfy { $0.profileId == profile.id && $0.itemYoutubeId == "thema:dinos" })
    }

    @Test func pendingListsOpenBeforeDiscussNewestFirst() throws {
        let (context, profile, _) = try setUp()
        let clock = TestClock()
        let repo = WishRepository(context: context, now: { clock.now })
        let a = try repo.submit(.thema("A"), for: profile).wish
        clock.advance()
        let b = try repo.submit(.thema("B"), for: profile).wish
        clock.advance()
        let c = try repo.submit(.thema("C"), for: profile).wish
        clock.advance()
        try repo.decide(c, status: .besprechen)
        #expect(repo.pending(of: profile.id).map(\.id) == [b.id, a.id, c.id])
    }

    @Test func approveEpisodeCreatesApprovedVisibleItem() throws {
        let (context, profile, repo) = try setUp()
        context.insert(CuratedSource(channelId: channelId, title: "Reihe", trust: .trustedSeries, defaultAgeMin: 6, defaultCategory: .knowledge))
        try context.save()
        let wish = try repo.submit(episode("vid00000002", title: "Wie fliegt eine Rakete?"), for: profile).wish
        let item = try repo.approveEpisode(wish, in: profile, reply: "Viel Spaß!")
        #expect(item.approvalStatus == .approved)
        #expect(item.category == .knowledge)
        #expect(item.ageMin == 6)
        #expect(item.sourceChannelId == channelId)
        #expect(WhitelistRepository(context: context).visibleItems(of: profile, type: .video).map(\.youtubeId) == ["vid00000002"])
        #expect(wish.status == .erfuellt)
        #expect(wish.fulfilledYoutubeId == "vid00000002")
        #expect(wish.parentReply == "Viel Spaß!")
        // Verlauf: Wunsch und Freigabe stehen beim Video.
        let videoEvents = CurationRepository(context: context).events(for: "vid00000002").map(\.decision)
        #expect(videoEvents.contains(.wished) && videoEvents.contains(.approved) && videoEvents.contains(.wishFulfilled))
        #expect(throws: WishRepository.WishError.invalidTransition) { try repo.approveEpisode(wish, in: profile) }
    }

    @Test func approveEpisodeRefusesHardBlockedTitlesAndWrongKinds() throws {
        let (_, profile, repo) = try setUp()
        let wish = try repo.submit(episode("vid00000003", title: "Hentai Folge"), for: profile).wish
        #expect(throws: WishRepository.WishError.notApprovable) { try repo.approveEpisode(wish, in: profile) }
        #expect(wish.status == .offen)
        let topic = try repo.submit(.thema("Dinos"), for: profile).wish
        #expect(throws: WishRepository.WishError.wrongKind) { try repo.approveEpisode(topic, in: profile) }
    }

    /// „Kanal prüfen" legt nichts mehr ungefragt an: Erst die Einstufung der Eltern nimmt den Kanal auf (ADR 0003).
    @Test func channelFromWishIsOnlyADraftUntilParentsDecide() throws {
        let (context, profile, repo) = try setUp()
        let wish = try repo.submit(WishDraft(kind: .mehrDavon, videoId: "vid00000004", videoTitle: "Paxi", channelId: "UCnewChannel", channelTitle: "Neu"), for: profile).wish
        let draft = try #require(repo.kanalEntwurf(of: wish))
        #expect(draft.type == .channel && draft.youtubeId == "UCnewChannel" && draft.title == "Neu")
        #expect(profile.whitelistItems.isEmpty)
        #expect(CurationRepository(context: context).source(channelId: "UCnewChannel") == nil)

        let curation = CurationRepository(context: context)
        let result = try curation.kanalAufnehmen(draft, als: Kanaleinstufung(trust: .trustedSeries, ageMin: 6, category: .knowledge),
                                                 for: profile, actor: "Eltern")
        #expect(result == .aufgenommen(youtubeId: "UCnewChannel"))
        #expect(WhitelistRepository(context: context).visibleItems(of: profile, type: .channel).map(\.youtubeId) == ["UCnewChannel"])
        #expect(curation.source(channelId: "UCnewChannel")?.trust == .trustedSeries)
        #expect(wish.status == .offen)   // Eltern entscheiden danach selbst („Erledigt")

        let ohneKanal = try repo.submit(.thema("Dinos"), for: profile).wish
        #expect(repo.kanalEntwurf(of: ohneKanal) == nil)
    }

    @Test func excludedEpisodesContainWhitelistAndRejectedWishes() throws {
        let (context, profile, repo) = try setUp()
        let item = WhitelistItem(type: .video, youtubeId: "known", title: "K", thumbnailUrl: "", approvalStatus: .rejected)
        item.profile = profile
        context.insert(item)
        let rejected = try repo.submit(episode("rejected"), for: profile).wish
        try repo.decide(rejected, status: .abgelehnt)
        _ = try repo.submit(episode("pending"), for: profile)
        #expect(repo.excludedEpisodeIds(for: profile) == ["known", "rejected"])
        #expect(Set(repo.pendingVideoWishes(of: profile.id).keys) == ["pending"])
    }

    @Test func deletingProfileRemovesItsWishes() throws {
        let (context, profile, repo) = try setUp()
        let other = try ProfileRepository(context: context).create(name: "Ole")
        _ = try repo.submit(.thema("Dinos"), for: profile)
        _ = try repo.submit(.thema("Dinos"), for: other)
        try ProfileRepository(context: context).delete(profile)
        #expect(try context.fetch(FetchDescriptor<KidWish>()).map(\.profileID) == [other.id])
    }

    @Test func deletingProfileRemovesItsHistoryEntriesIncludingWishTexts() throws {
        let (context, profile, repo) = try setUp()
        let other = try ProfileRepository(context: context).create(name: "Ole")
        let secret = try repo.submit(.thema("Geheimes Thema"), for: profile).wish
        try repo.decide(secret, status: .abgelehnt, reply: "Nein")
        _ = try repo.submit(.thema("Dinos"), for: other)
        context.insert(ReviewEvent(itemYoutubeId: "source:UC1", profileId: nil, decision: .blockedSource, actor: "Eltern",
                                   itemVersion: "source"))
        try context.save()
        try ProfileRepository(context: context).delete(profile)
        let left = try context.fetch(FetchDescriptor<ReviewEvent>())
        #expect(Set(left.map(\.itemYoutubeId)) == ["thema:dinos", "source:UC1"])
        #expect(!left.contains { ($0.note ?? "").contains("Geheimes") || $0.itemVersion.contains("geheimes") })
        #expect(left.first { $0.itemYoutubeId == "thema:dinos" }?.actor == "Kind")
    }

    @Test func parentOnlySourceCannotBeWishedLikeBlocked() throws {
        let (context, profile, repo) = try setUp()
        context.insert(CuratedSource(channelId: "UCparents", title: "Nur Eltern", trust: .parentOnly))
        try context.save()
        #expect(throws: WishRepository.WishError.blockedSource) {
            try repo.submit(WishDraft(kind: .mehrDavon, videoId: "v", channelId: "UCparents"), for: profile)
        }
        #expect(try context.fetchCount(FetchDescriptor<KidWish>()) == 0)
    }

    @Test func historyShowsGermanLabels() throws {
        let (_, profile, repo) = try setUp()
        let wish = try repo.submit(.thema("Dinos"), for: profile).wish
        try repo.decide(wish, status: .erfuellt)
        #expect(Set(repo.history(of: wish).map(\.headline)) == ["Wunsch erfüllt · Eltern", "Gewünscht · Kind"])
        let unknown = ReviewEvent(itemYoutubeId: "x", profileId: nil, decision: .approved, actor: "Eltern", itemVersion: "v")
        unknown.decisionRaw = "trustChanged"
        #expect(unknown.headline == "trustChanged · Eltern", "Unbekannte Kennungen bleiben lesbar stehen")
    }

    @Test func myWishesHideForeignVideoAfterRejectionOrBlockedSource() throws {
        let (context, profile, repo) = try setUp()
        let open = try repo.submit(episode("v1", title: "Fremder Titel"), for: profile).wish
        #expect(WishDisplay.showsForeignVideo(open, context: context))
        try repo.decide(open, status: .abgelehnt, reply: "Nicht jetzt")
        #expect(!WishDisplay.showsForeignVideo(open, context: context))
        #expect(WishDisplay.neutralHeadline(open) == "Eine neue Folge")

        let other = try repo.submit(episode("v2", title: "Anderer Titel"), for: profile).wish
        context.insert(CuratedSource(channelId: channelId, title: "Reihe", trust: .blocked))
        try context.save()
        #expect(!WishDisplay.showsForeignVideo(other, context: context), "Quelle inzwischen gesperrt")
        let topic = try repo.submit(.thema("Dinos"), for: profile).wish
        #expect(WishDisplay.showsForeignVideo(topic, context: context), "das eigene Stichwort bleibt")
    }
}

/// Uhr, die nur auf Anweisung weiterläuft.
final class TestClock {
    var now = Date(timeIntervalSince1970: 1_790_000_000)
    func advance(_ seconds: TimeInterval = 60) { now += seconds }
}

/// „Neu bei deinen Kanälen": Feed-Filter.
struct NewEpisodesPolicyTests {
    private let now = Date(timeIntervalSince1970: 1_790_000_000)
    private let channelId = "UCseries"

    private func entry(_ id: String, title: String = "Folge", daysAgo: Double = 1, isShort: Bool = false,
                       isUpcoming: Bool = false, channel: String? = nil, dated: Bool = true) -> FeedEntry {
        FeedEntry(video: PlaylistVideo(videoId: id, title: title, thumbnailUrl: "", channelTitle: "Reihe", position: 0,
                                       channelId: channel ?? channelId),
                  publishedAt: dated ? now.addingTimeInterval(-daysAgo * 86_400) : nil, isShort: isShort, isUpcoming: isUpcoming)
    }

    /// Hält den Container am Leben, solange die Modelle gebraucht werden.
    private static var keepAlive: [ModelContext] = []

    private func setUp(trust: SourceTrust = .trustedSeries, ageMin: Int = 0, news: Bool = false) throws -> (KidProfile, CuratedSource) {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        Self.keepAlive.append(context)
        let profile = try ProfileRepository(context: context).create(name: "Kind")
        let source = CuratedSource(channelId: channelId, title: "Reihe", trust: trust, isNewsSource: news, defaultAgeMin: ageMin)
        context.insert(source)
        return (profile, source)
    }

    @Test func onlyTrustedSeriesChannelsQualify() throws {
        for trust in SourceTrust.allCases {
            let (profile, source) = try setUp(trust: trust)
            let result = NewEpisodesPolicy.filter([entry("a")], channel: source, profile: profile, excludedVideoIds: [], now: now)
            #expect(result.isEmpty == (trust != .trustedSeries), "Stufe \(trust)")
        }
        let (profile, _) = try setUp()
        #expect(NewEpisodesPolicy.filter([entry("a")], channel: nil, profile: profile, excludedVideoIds: [], now: now).isEmpty)
    }

    @Test func atMostSixNewestFirst() throws {
        let (profile, source) = try setUp()
        let entries = (0..<15).map { entry("v\($0)", daysAgo: Double($0)) }.shuffled()
        let result = NewEpisodesPolicy.filter(entries + [entry("v0", daysAgo: 0)], channel: source, profile: profile,
                                              excludedVideoIds: [], now: now)
        #expect(result.map(\.video.videoId) == ["v0", "v1", "v2", "v3", "v4", "v5"])
    }

    @Test func notOlderThanSixtyDaysAndDateRequired() throws {
        let (profile, source) = try setUp()
        let result = NewEpisodesPolicy.filter([entry("fresh", daysAgo: 59.9), entry("old", daysAgo: 60.1), entry("undated", dated: false)],
                                              channel: source, profile: profile, excludedVideoIds: [], now: now)
        #expect(result.map(\.video.videoId) == ["fresh"])
    }

    @Test func noShortsLivestreamsOrPremieres() throws {
        let (profile, source) = try setUp()
        let entries = [entry("short", isShort: true), entry("tagged", title: "Raumfahrt #shorts"),
                       entry("live", title: "Livestream vom Start"), entry("upcoming", isUpcoming: true), entry("ok")]
        #expect(NewEpisodesPolicy.filter(entries, channel: source, profile: profile, excludedVideoIds: [], now: now)
            .map(\.video.videoId) == ["ok"])
    }

    @Test func riskFilterOnTitles() throws {
        let (profile, source) = try setUp()
        let entries = [entry("hard", title: "NSFW Clip"), entry("topic", title: "Grusel im Weltall"), entry("ok", title: "Paxi erklärt den Mond")]
        #expect(NewEpisodesPolicy.filter(entries, channel: source, profile: profile, excludedVideoIds: [], now: now)
            .map(\.video.videoId) == ["ok"])
    }

    @Test func decidedOrKnownVideosAreExcluded() throws {
        let (profile, source) = try setUp()
        let result = NewEpisodesPolicy.filter([entry("approved"), entry("rejected"), entry("new")], channel: source, profile: profile,
                                              excludedVideoIds: ["approved", "rejected"], now: now)
        #expect(result.map(\.video.videoId) == ["new"])
    }

    @Test func blockedSourcesNeverAppear() throws {
        let (profile, source) = try setUp()
        let blocked = CuratedSource(channelId: "UCblocked", title: "X", trust: .blocked)
        let result = NewEpisodesPolicy.filter([entry("foreign", channel: "UCblocked"), entry("own")], channel: source, profile: profile,
                                              excludedVideoIds: [], now: now, videoSource: { $0 == "UCblocked" ? blocked : nil })
        #expect(result.map(\.video.videoId) == ["own"])
        source.trust = .blocked
        #expect(NewEpisodesPolicy.filter([entry("own")], channel: source, profile: profile, excludedVideoIds: [], now: now).isEmpty)
    }

    @Test func respectsSourceAgeAndNewsSources() throws {
        let (young, tooOld) = try setUp(ageMin: 12)   // Profil „Kinder (9–11)"
        #expect(NewEpisodesPolicy.filter([entry("a")], channel: tooOld, profile: young, excludedVideoIds: [], now: now).isEmpty)
        let (profile, news) = try setUp(news: true)
        #expect(NewEpisodesPolicy.filter([entry("a")], channel: news, profile: profile, excludedVideoIds: [], now: now).isEmpty)
    }

    @Test func respectsCategorySwitchesOfTheProfile() throws {   // wie Android
        let (profile, source) = try setUp()
        source.defaultCategoryRaw = ContentCategory.mangaDrawing.rawValue
        #expect(NewEpisodesPolicy.filter([entry("a")], channel: source, profile: profile, excludedVideoIds: [], now: now).count == 1)
        profile.allowManga = false
        #expect(NewEpisodesPolicy.filter([entry("a")], channel: source, profile: profile, excludedVideoIds: [], now: now).isEmpty,
                "Manga im Profil aus")
        source.defaultCategoryRaw = ContentCategory.animeManga.rawValue
        profile.allowManga = true
        profile.allowMangaEntertainment = true
        #expect(NewEpisodesPolicy.filter([entry("a")], channel: source, profile: profile, excludedVideoIds: [], now: now).isEmpty,
                "Anime & Manga erst ab 12")
        source.defaultCategoryRaw = ContentCategory.knowledge.rawValue
        profile.disabledCategories = [.knowledge]
        #expect(NewEpisodesPolicy.filter([entry("a")], channel: source, profile: profile, excludedVideoIds: [], now: now).isEmpty,
                "abgeschaltete Kategorie")
    }
}

struct ChannelFeedEntryParserTests {
    private let feed = """
    <?xml version="1.0" encoding="UTF-8"?>
    <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
     <title>Reihe</title>
     <author><name>Reihe</name></author>
     <published>2008-06-03T18:44:30+00:00</published>
     <entry>
      <yt:videoId>regular0001</yt:videoId>
      <yt:channelId>UCseries</yt:channelId>
      <title>Normale Folge</title>
      <link rel="alternate" href="https://www.youtube.com/watch?v=regular0001"/>
      <published>2026-10-01T18:03:15+00:00</published>
      <media:group><media:thumbnail url="https://i1.ytimg.com/vi/regular0001/hqdefault.jpg"/>
       <media:community><media:statistics views="1234"/></media:community></media:group>
     </entry>
     <entry>
      <yt:videoId>short000001</yt:videoId>
      <yt:channelId>UCseries</yt:channelId>
      <title>Kurz</title>
      <link rel="alternate" href="https://www.youtube.com/shorts/short000001"/>
      <published>2026-09-30T10:00:00+00:00</published>
     </entry>
     <entry>
      <yt:videoId>premiere001</yt:videoId>
      <yt:channelId>UCseries</yt:channelId>
      <title>Bald</title>
      <link rel="alternate" href="https://www.youtube.com/watch?v=premiere001"/>
      <published>2026-09-29T10:00:00+00:00</published>
      <media:group><media:community><media:statistics views="0"/></media:community></media:group>
     </entry>
    </feed>
    """

    @Test func parsesDatesShortsAndPremieres() throws {
        let entries = RSSFeedParser.parseEntries(Data(feed.utf8))
        #expect(entries.map(\.video.videoId) == ["regular0001", "short000001", "premiere001"])
        #expect(entries[0].publishedAt == ISO8601DateFormatter().date(from: "2026-10-01T18:03:15Z"))
        #expect(entries.map(\.isShort) == [false, true, false])
        #expect(entries.map(\.isUpcoming) == [false, false, true])
        #expect(entries[0].video.channelTitle == "Reihe")
        #expect(entries[0].video.channelId == "UCseries")
        // Der bisherige Parser bleibt unverändert.
        #expect(RSSFeedParser.parse(Data(feed.utf8)).map(\.videoId) == entries.map(\.video.videoId))
    }
}
