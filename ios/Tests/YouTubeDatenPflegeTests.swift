// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

/// ADR 0010 – gleiche Fälle wie `YouTubeDatenPflegeTest.kt`.
@MainActor
struct YouTubeDatenPflegeTests {
    let tag: TimeInterval = 24 * 60 * 60
    var jetzt: Date { Date(timeIntervalSince1970: 100 * tag) }
    var alt: Date { jetzt.addingTimeInterval(-31 * tag) }
    var frisch: Date { jetzt.addingTimeInterval(-2 * tag) }

    private func item(_ id: String, _ at: Date, provider: ContentProvider = .youtube, metadataAt: Date? = nil, in ctx: ModelContext) -> WhitelistItem {
        let item = WhitelistItem(type: .video, youtubeId: "c-\(id)", title: "alt \(id)", thumbnailUrl: "bild-\(id)", approvalStatus: .approved)
        item.addedAt = at; item.providerRaw = provider.rawValue; item.metadataAt = metadataAt; item.channelTitle = "Kanal alt"
        ctx.insert(item); return item
    }

    @Test func einLaufFrischtAufErsetztVerschwundenesUndLoeschtAltes() async throws {
        let container = try ModelContainerFactory.make(inMemory: true)
        let ctx = container.mainContext
        let a = item("a", alt, in: ctx), b = item("b", alt, in: ctx), c = item("c", alt, in: ctx)
        let d = item("d", alt, metadataAt: frisch, in: ctx), e = item("e", frisch, in: ctx), f = item("f", alt, provider: .peertube, in: ctx)
        let altCache = CachedChannelVideo(channelId: "UC1", videoId: "v-alt", title: "T", thumbnailUrl: "", channelTitle: "K", position: 0)
        altCache.cachedAt = alt; ctx.insert(altCache)
        let neuCache = CachedChannelVideo(channelId: "UC1", videoId: "v-neu", title: "T", thumbnailUrl: "", channelTitle: "K", position: 1)
        neuCache.cachedAt = frisch; ctx.insert(neuCache)
        ctx.insert(WatchHistoryEntry(videoId: "x", videoTitle: "alt", watchedSeconds: 60, watchedAt: alt))
        ctx.insert(WatchHistoryEntry(videoId: "y", videoTitle: "neu", watchedSeconds: 30, watchedAt: frisch))
        let w1 = KidWish(profileID: UUID(), kind: .mehrDavon, videoId: "v", videoTitle: "Titel", createdAt: alt); w1.status = .erfuellt
        let w2 = KidWish(profileID: UUID(), kind: .mehrDavon, videoId: "v", videoTitle: "Titel", thumbnailUrl: "bild", channelTitle: "Kanal", createdAt: alt)
        let w3 = KidWish(profileID: UUID(), kind: .thema, topic: "Dinos", createdAt: alt)
        [w1, w2, w3].forEach(ctx.insert)
        try ctx.save()

        var geholt: [String] = []
        let pflege = YouTubeDatenPflege(context: ctx, holen: { type, id in
            geholt.append(id)
            switch id {
            case "c-a": return WhitelistItemDraft(type: type, youtubeId: id, title: "neu a", thumbnailUrl: "bild-neu", channelTitle: "Kanal neu")
            case "c-b": throw YouTubeError.notFound
            default: throw YouTubeError.network("offline")
            }
        }, now: { jetzt })
        let bericht = try await pflege.lauf()

        #expect(Set(geholt) == ["c-a", "c-b", "c-c"])
        #expect(bericht == .init(aufgefrischt: 1, verschwunden: 1, spaeter: 1, zwischenspeicher: 1, sehverlauf: 1, wuenscheGeloescht: 1, wuenscheGekuerzt: 1))
        #expect(a.title == "neu a" && a.channelTitle == "Kanal neu" && a.thumbnailUrl == "bild-neu" && a.metadataAt == jetzt)
        #expect(b.title.isEmpty && b.thumbnailUrl.isEmpty && b.approvalStatus == .expiredReview)
        #expect(b.displayTitle == String(localized: "Nicht mehr bei YouTube verfügbar"))
        #expect(c.title == "alt c" && c.metadataAt == nil)
        #expect(d.title == "alt d" && e.title == "alt e" && f.title == "alt f")
        #expect(try ctx.fetch(FetchDescriptor<CachedChannelVideo>()).map(\.videoId) == ["v-neu"])
        #expect(try ctx.fetch(FetchDescriptor<WatchHistoryEntry>()).map(\.videoId) == ["y"])
        let wuensche = try ctx.fetch(FetchDescriptor<KidWish>())
        #expect(wuensche.count == 2)
        #expect(w2.videoTitle == nil && w2.channelTitle == nil && w2.thumbnailUrl == nil)
        #expect(w3.topic == "Dinos")
    }

    @Test func endgueltigNurBeiNichtGefundenOder403() {
        #expect(YouTubeDatenPflege.endgueltigWeg(YouTubeError.notFound))
        #expect(YouTubeDatenPflege.endgueltigWeg(YouTubeError.http(status: 403)))
        #expect(!YouTubeDatenPflege.endgueltigWeg(YouTubeError.http(status: 500)))
        #expect(!YouTubeDatenPflege.endgueltigWeg(YouTubeError.network("offline")))
    }
}
