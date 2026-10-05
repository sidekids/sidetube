// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

struct NextUpPolicyTests {
    typealias Item = PlayerModel.Item
    typealias Candidate = NextUpPolicy.Candidate

    let queue = [Item(videoId: "a", title: "A"), Item(videoId: "b", title: "B"), Item(videoId: "c", title: "C")]

    @Test func queueSuccessorsComeFirstAndWrapAround() {
        let result = NextUpPolicy.suggestions(queue: queue, currentIndex: 2, approved: [])
        #expect(result.map(\.videoId) == ["a", "b"], "nach dem letzten Video kommen die ersten der Warteschlange")
        #expect(result.first?.thumbnailUrl == YouTubeIDs.defaultThumbnail(videoId: "a"))
    }

    @Test func fillsUpWithSameChannelThenOthersAndStopsAtLimit() {
        let approved = [
            Candidate(videoId: "x", title: "X", channelTitle: "Anderer"),
            Candidate(videoId: "y", title: "Y", channelTitle: "Maus", sourceChannelId: "UCmaus"),
            Candidate(videoId: "z", title: "Z", sourceChannelId: "UCmaus"),
        ]
        let result = NextUpPolicy.suggestions(queue: [queue[0]], currentIndex: 0, approved: approved,
                                              currentChannelId: "UCmaus", currentChannelTitle: "Maus")
        #expect(result.map(\.videoId) == ["y", "z", "x"])
        let limited = NextUpPolicy.suggestions(queue: queue, currentIndex: 0, approved: approved, limit: 2)
        #expect(limited.map(\.videoId) == ["b", "c"], "die Warteschlange füllt das Limit bereits")
    }

    @Test func neverSuggestsCurrentVideoOrDuplicates() {
        let approved = [Candidate(videoId: "a", title: "A nochmal"), Candidate(videoId: "b", title: "B doppelt"), Candidate(videoId: "d", title: "D")]
        let result = NextUpPolicy.suggestions(queue: queue, currentIndex: 0, approved: approved)
        #expect(result.map(\.videoId) == ["b", "c", "d"])
        #expect(!result.contains { $0.videoId == "a" })
    }

    @Test func singleVideoWithoutApprovedContentYieldsNothing() {
        #expect(NextUpPolicy.suggestions(queue: [queue[0]], currentIndex: 0, approved: []).isEmpty)
        #expect(NextUpPolicy.suggestions(queue: queue, currentIndex: 7, approved: []).isEmpty, "ungültiger Index → keine Vorschläge, kein Absturz")
    }

   /// Ende-zu-Ende gegen SwiftData: nur sichtbare Whitelist-Videos werden zu Kandidaten (Kanal gesperrt, nicht
   /// freigegeben oder Mindestalter zu hoch → ausgeschlossen). Die Regel bleibt auf `visibleItems` angewiesen.
    @Test func onlyVisibleWhitelistItemsBecomeCandidates() throws {
        let context = ModelContext(try ModelContainerFactory.make(inMemory: true))
        let profile = try ProfileRepository(context: context).create(name: "Mia")
        let whitelist = WhitelistRepository(context: context)
        let approved = try whitelist.add(WhitelistItemDraft(type: .video, youtubeId: "ok", title: "Freigegeben", thumbnailUrl: "t"), to: profile)
        approved.sourceChannelId = "UCok"
        let pending = try whitelist.add(WhitelistItemDraft(type: .video, youtubeId: "pending", title: "Prüfung", thumbnailUrl: "t"), to: profile)
        pending.approvalStatus = .reviewRequired
        let tooOld = try whitelist.add(WhitelistItemDraft(type: .video, youtubeId: "old", title: "Ab 12", thumbnailUrl: "t"), to: profile)
        tooOld.ageMin = 12
        let blocked = try whitelist.add(WhitelistItemDraft(type: .video, youtubeId: "blocked", title: "Gesperrt", thumbnailUrl: "t"), to: profile)
        blocked.sourceChannelId = "UCblocked"
        context.insert(CuratedSource(channelId: "UCblocked", title: "Gesperrt", trust: .blocked))
        try context.save()

        let candidates = whitelist.visibleItems(of: profile, type: .video).map(NextUpPolicy.Candidate.init(item:))
        let result = NextUpPolicy.suggestions(queue: [Item(videoId: "current", title: "Läuft")], currentIndex: 0, approved: candidates)
        #expect(result.map(\.videoId) == ["ok"])
        #expect(result.first?.sourceChannelId == "UCok")
    }
}
