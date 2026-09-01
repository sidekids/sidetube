// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData
import Testing
@testable import sidetube

struct RepositoryTests {
    private func makeContext() throws -> ModelContext {
        ModelContext(try ModelContainerFactory.make(inMemory: true))
    }

    @Test func profileCreateValidatesName() throws {
        let repo = ProfileRepository(context: try makeContext())
        #expect(throws: ProfileRepository.ValidationError.emptyName) { try repo.create(name: "   ") }
        let profile = try repo.create(name: "  Mia ")
        #expect(profile.name == "Mia")
        #expect(try repo.count() == 1)
    }

    @Test func whitelistAddFilterDuplicateRemove() throws {
        let context = try makeContext()
        let profile = try ProfileRepository(context: context).create(name: "Mia")
        let repo = WhitelistRepository(context: context)
        let video = WhitelistItemDraft(type: .video, youtubeId: "v1", title: "Video", thumbnailUrl: "https://t/v1", channelTitle: "Kanal")
        let channel = WhitelistItemDraft(type: .channel, youtubeId: "UC1", title: "Kanal", thumbnailUrl: "https://t/c1", channelTitle: nil)
        try repo.add(video, to: profile)
        try repo.add(channel, to: profile)
        #expect(throws: WhitelistRepository.AddError.duplicate) { try repo.add(video, to: profile) }
        #expect(repo.contains(youtubeId: "UC1", in: profile))
        #expect(repo.items(of: profile).count == 2)
        #expect(repo.items(of: profile, type: .channel).map(\.youtubeId) == ["UC1"])
        try repo.remove(repo.items(of: profile, type: .video)[0])
        #expect(repo.items(of: profile).count == 1)
    }

    @Test func deletingProfileCascadesItems() throws {
        let context = try makeContext()
        let profiles = ProfileRepository(context: context)
        let profile = try profiles.create(name: "Mia")
        try WhitelistRepository(context: context).add(
            WhitelistItemDraft(type: .playlist, youtubeId: "PL1", title: "Liste", thumbnailUrl: "", channelTitle: nil), to: profile)
        try profiles.delete(profile)
        #expect(try context.fetchCount(FetchDescriptor<WhitelistItem>()) == 0)
    }

    @Test func watchTimePerDayAndRemaining() throws {
        let context = try makeContext()
        let profile = try ProfileRepository(context: context).create(name: "Mia", dailyLimitMinutes: 10)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Berlin")!
        let repo = WatchTimeRepository(context: context, calendar: calendar)
        let today = calendar.date(from: DateComponents(year: 2026, month: 8, day: 30, hour: 15))!
        let yesterday = calendar.date(byAdding: .day, value: -1, to: today)!
        try repo.record(videoId: "a", title: "A", seconds: 300, for: profile, at: today)
        try repo.record(videoId: "b", title: "B", seconds: 120, for: profile, at: today)
        try repo.record(videoId: "c", title: "C", seconds: 999, for: profile, at: yesterday)
        try repo.record(videoId: "d", title: "D", seconds: 0, for: profile, at: today)
        #expect(repo.secondsWatched(by: profile, on: today) == 420)
        #expect(repo.totalSeconds(of: profile) == 1419)
        #expect(repo.remainingSeconds(for: profile, now: today) == 180)
        profile.dailyLimitMinutes = nil
        #expect(repo.remainingSeconds(for: profile, now: today) == nil)
    }
}
