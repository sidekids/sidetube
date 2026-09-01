// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

/// Kinderprofil. Felder wie `KidProfileEntity` (Android), ohne Elternkonto (kein OAuth in iOS).
@Model
final class KidProfile {
    @Attribute(.unique) var id: UUID
    var name: String
    var avatarUrl: String?
    var dailyLimitMinutes: Int?
    var sleepPlaylistId: String?
    var createdAt: Date
   // Kuratierung: Altersprofil und Inhaltsregeln der Eltern
    var ageBandRaw: String = AgeBand.kids.rawValue
    var allowNews: Bool = true
    var allowManga: Bool = true
    var allowMangaEntertainment: Bool = false
    var allowShorts: Bool = false
    var autoplayNext: Bool = false
    var disabledCategoriesRaw: [String] = []
    // Ruhezeiten (Minuten seit Mitternacht), Feldnamen wie in der Android-Datenbank
    var bedtimeEnabled: Bool = true
    var bedtimeStartMinutes: Int = BedtimeSettings.defaultStartMinutes
    var bedtimeEndMinutes: Int = BedtimeSettings.defaultEndMinutes
    var bedtimeWeekendOffsetMinutes: Int = BedtimeSettings.defaultWeekendOffsetMinutes
    var bedtimeSkipUntil: Date?
    @Relationship(deleteRule: .cascade, inverse: \WhitelistItem.profile)
    var whitelistItems: [WhitelistItem] = []
    @Relationship(deleteRule: .cascade, inverse: \WatchHistoryEntry.profile)
    var watchHistory: [WatchHistoryEntry] = []

    init(id: UUID = UUID(), name: String, avatarUrl: String? = nil,
         dailyLimitMinutes: Int? = nil, sleepPlaylistId: String? = nil, createdAt: Date = .now) {
        self.id = id
        self.name = name
        self.avatarUrl = avatarUrl
        self.dailyLimitMinutes = dailyLimitMinutes
        self.sleepPlaylistId = sleepPlaylistId
        self.createdAt = createdAt
    }

    var ageBand: AgeBand {
        get { AgeBand(rawValue: ageBandRaw) ?? .kids }
        set { ageBandRaw = newValue.rawValue }
    }

    var bedtime: BedtimeSettings {
        get { BedtimeSettings(enabled: bedtimeEnabled, startMinutes: bedtimeStartMinutes, endMinutes: bedtimeEndMinutes,
                              weekendOffsetMinutes: bedtimeWeekendOffsetMinutes, skipUntil: bedtimeSkipUntil) }
        set {
            bedtimeEnabled = newValue.enabled
            bedtimeStartMinutes = newValue.startMinutes
            bedtimeEndMinutes = newValue.endMinutes
            bedtimeWeekendOffsetMinutes = newValue.weekendOffsetMinutes
            bedtimeSkipUntil = newValue.skipUntil
        }
    }

    var disabledCategories: Set<ContentCategory> {
        get { Set(disabledCategoriesRaw.compactMap(ContentCategory.init(rawValue:))) }
        set { disabledCategoriesRaw = newValue.map(\.rawValue).sorted() }
    }
}

/// Whitelist-Eintrag (`WhitelistItemEntity`). Typ als Rohstring gespeichert, damit Prädikate funktionieren.
@Model
final class WhitelistItem {
    @Attribute(.unique) var id: UUID
    var typeRaw: String
    var youtubeId: String
    var title: String
    var thumbnailUrl: String
    var channelTitle: String?
    var addedAt: Date
    var profile: KidProfile?

   // Kuratierung. Standardwerte gelten nur für Einträge aus der Zeit vor der Kuratierung
   // (Migration: von Eltern bewusst angelegt → freigegeben). Neue Einträge setzen den Status IMMER explizit.
    var providerRaw: String = ContentProvider.youtube.rawValue
    var approvalStatusRaw: String = ApprovalStatus.approved.rawValue
    var categoryRaw: String?
    var subcategoriesRaw: [String] = []
    var ageMin: Int = 0
    var ageMax: Int?
    var language: String? = "de"
    var sensitiveTopicsRaw: [String] = []
    var containsAdvertising: Bool = false
    var containsProductPlacement: Bool = false
    var containsViolence: Bool = false
    var containsFear: Bool = false
    var containsSexualContent: Bool = false
    var containsCoarseLanguage: Bool = false
    var isShort: Bool = false
    var isLive: Bool = false
    var isNews: Bool = false
    var newsStatusRaw: String?
    var autoplayAllowed: Bool = false
    var madeForKidsRaw: String = MadeForKidsStatus.unknown.rawValue
    var educationalValue: Int?
    var durationSeconds: Int?
    var videoDescription: String?
    var parentNotes: String?
    var editorialNotes: String?
    var approvedBy: String?
    var approvedAt: Date?
    var lastReviewedAt: Date?
    var sourceChannelId: String?
    var sourceChannelHandle: String?
    var sourceUrl: String?

    var type: YouTubeContentType {
        get { YouTubeContentType(rawValue: typeRaw) ?? .video }
        set { typeRaw = newValue.rawValue }
    }

    init(id: UUID = UUID(), type: YouTubeContentType, youtubeId: String, title: String,
         thumbnailUrl: String, channelTitle: String? = nil, addedAt: Date = .now,
         approvalStatus: ApprovalStatus = .approved) {
        self.id = id
        self.typeRaw = type.rawValue
        self.youtubeId = youtubeId
        self.title = title
        self.thumbnailUrl = thumbnailUrl
        self.channelTitle = channelTitle
        self.addedAt = addedAt
        self.approvalStatusRaw = approvalStatus.rawValue
    }

    var provider: ContentProvider {
        get { ContentProvider(rawValue: providerRaw) ?? .youtube }
        set { providerRaw = newValue.rawValue }
    }
    var approvalStatus: ApprovalStatus {
        get { ApprovalStatus(rawValue: approvalStatusRaw) ?? .reviewRequired }
        set { approvalStatusRaw = newValue.rawValue }
    }
    var category: ContentCategory? {
        get { categoryRaw.flatMap(ContentCategory.init(rawValue:)) }
        set { categoryRaw = newValue?.rawValue }
    }
    var sensitiveTopics: Set<SensitiveTopic> {
        get { Set(sensitiveTopicsRaw.compactMap(SensitiveTopic.init(rawValue:))) }
        set { sensitiveTopicsRaw = newValue.map(\.rawValue).sorted() }
    }
    var newsStatus: NewsStatus? {
        get { newsStatusRaw.flatMap(NewsStatus.init(rawValue:)) }
        set { newsStatusRaw = newValue?.rawValue }
    }
    var madeForKids: MadeForKidsStatus {
        get { MadeForKidsStatus(rawValue: madeForKidsRaw) ?? .unknown }
        set { madeForKidsRaw = newValue.rawValue }
    }
   /// Fingerabdruck der fachlich relevanten Felder (Audit-Trail: welche Version wurde freigegeben?).
    var reviewVersion: String {
        "\(title)|\(categoryRaw ?? "-")|\(ageMin)|\(ageMax.map(String.init) ?? "-")|\(sensitiveTopicsRaw.joined(separator: ","))|\(isShort)|\(isLive)|\(isNews)"
    }
}

/// Quelle (Kanal) mit Sicherheitsstufe – geräteweit, unabhängig vom Profil.
@Model
final class CuratedSource {
    @Attribute(.unique) var channelId: String
    var handle: String?
    var title: String
    var providerRaw: String = ContentProvider.youtube.rawValue
    var trustRaw: String = SourceTrust.perVideoReview.rawValue
    var isNewsSource: Bool = false
    var defaultAgeMin: Int = 0
    var defaultCategoryRaw: String?
    var notes: String?
    var lastReviewedAt: Date?
    var createdAt: Date = Date()

    init(channelId: String, handle: String? = nil, title: String, provider: ContentProvider = .youtube,
         trust: SourceTrust, isNewsSource: Bool = false, defaultAgeMin: Int = 0,
         defaultCategory: ContentCategory? = nil, notes: String? = nil, lastReviewedAt: Date? = nil) {
        self.channelId = channelId
        self.handle = handle
        self.title = title
        self.providerRaw = provider.rawValue
        self.trustRaw = trust.rawValue
        self.isNewsSource = isNewsSource
        self.defaultAgeMin = defaultAgeMin
        self.defaultCategoryRaw = defaultCategory?.rawValue
        self.notes = notes
        self.lastReviewedAt = lastReviewedAt
    }

    var trust: SourceTrust {
        get { SourceTrust(rawValue: trustRaw) ?? .perVideoReview }
        set { trustRaw = newValue.rawValue }
    }
    var provider: ContentProvider { ContentProvider(rawValue: providerRaw) ?? .youtube }
    var defaultCategory: ContentCategory? { defaultCategoryRaw.flatMap(ContentCategory.init(rawValue:)) }
}

/// Audit-Trail einer Freigabe-Entscheidung: wer, wann, welche Version, welche Entscheidung.
@Model
final class ReviewEvent {
    var itemYoutubeId: String
    var profileId: UUID?
    var decisionRaw: String
    var actor: String
    var at: Date
    var itemVersion: String
    var note: String?

    init(itemYoutubeId: String, profileId: UUID?, decision: ReviewDecision, actor: String, at: Date = .now, itemVersion: String, note: String? = nil) {
        self.itemYoutubeId = itemYoutubeId
        self.profileId = profileId
        self.decisionRaw = decision.rawValue
        self.actor = actor
        self.at = at
        self.itemVersion = itemVersion
        self.note = note
    }

    var decision: ReviewDecision { ReviewDecision(rawValue: decisionRaw) ?? .deferred }
}

/// Sehzeit-Eintrag (`WatchHistoryEntity`).
@Model
final class WatchHistoryEntry {
    @Attribute(.unique) var id: UUID
    var videoId: String
    var videoTitle: String
    var watchedSeconds: Int
    var watchedAt: Date
    var profile: KidProfile?

    init(id: UUID = UUID(), videoId: String, videoTitle: String, watchedSeconds: Int, watchedAt: Date = .now) {
        self.id = id
        self.videoId = videoId
        self.videoTitle = videoTitle
        self.watchedSeconds = watchedSeconds
        self.watchedAt = watchedAt
    }
}

/// Lokaler Cache der Kanalvideos (`CachedChannelVideoEntity`); Eindeutigkeit (channelId, videoId)
/// wird im Repository sichergestellt (iOS 17 kennt kein zusammengesetztes #Unique).
@Model
final class CachedChannelVideo {
    var channelId: String
    var videoId: String
    var title: String
    var thumbnailUrl: String
    var channelTitle: String
    var position: Int

    init(channelId: String, videoId: String, title: String, thumbnailUrl: String, channelTitle: String, position: Int) {
        self.channelId = channelId
        self.videoId = videoId
        self.title = title
        self.thumbnailUrl = thumbnailUrl
        self.channelTitle = channelTitle
        self.position = position
    }
}
