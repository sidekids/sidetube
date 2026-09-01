import Foundation
import SwiftData

/// Startbibliothek aus `seed-library.json`: legt Quellen an und Videos als REVIEW_REQUIRED
/// (nie APPROVED). Eltern prüfen anschließend in der Redaktionsansicht. Idempotent pro Profil.
struct SeedLibraryImporter {
    struct SeedVideo: Codable {
        var id: String
        var provider: ContentProvider?
        var title: String
        var channelId: String
        var channelTitle: String
        var category: ContentCategory?
        var ageMin: Int
        var ageMax: Int?
        var isNews: Bool?
        var newsStatus: NewsStatus?
        var isShort: Bool?
        var sensitiveTopics: [SensitiveTopic]?
        var sourceUrl: String?
        var durationSeconds: Int?
        var note: String?
    }

   /// Ganzer Kanal statt Einzelvideo – so lassen sich Freigaben von einem anderen Gerät übernehmen.
    struct SeedChannel: Codable {
        var id: String
        var title: String
        var provider: ContentProvider?
        var thumbnailUrl: String?
        var category: ContentCategory?
        var ageMin: Int?
        var note: String?
    }

   /// Profilvorgaben einer Bibliothek (Altersstufe und Inhaltsregeln passend zur Auswahl).
    struct ProfilePreset: Codable, Equatable {
        var ageBand: AgeBand
        var allowNews: Bool
        var allowManga: Bool
        var allowMangaEntertainment: Bool
        var allowShorts: Bool
        var autoplayNext: Bool
        var bedtimeEnabled: Bool?
        var bedtimeStartMinutes: Int?
        var bedtimeEndMinutes: Int?
        var bedtimeWeekendOffsetMinutes: Int?

        func apply(to profile: KidProfile) {
            profile.ageBand = ageBand
            profile.allowNews = allowNews
            profile.allowManga = allowManga
            profile.allowMangaEntertainment = allowMangaEntertainment
            profile.allowShorts = allowShorts
            profile.autoplayNext = autoplayNext
            if let bedtimeEnabled { profile.bedtimeEnabled = bedtimeEnabled }
            if let bedtimeStartMinutes { profile.bedtimeStartMinutes = bedtimeStartMinutes }
            if let bedtimeEndMinutes { profile.bedtimeEndMinutes = bedtimeEndMinutes }
            if let bedtimeWeekendOffsetMinutes { profile.bedtimeWeekendOffsetMinutes = bedtimeWeekendOffsetMinutes }
        }
    }

    struct SeedLibrary: Codable {
        var version: Int
        var createdAt: String
        var id: String?
        var title: String?
        var note: String?
        var profilePreset: ProfilePreset?
        var channels: [SeedChannel]?
        var videos: [SeedVideo]
    }

   /// Ein mitgeliefertes Startpaket. Welche es gibt, entscheiden die Dateien unter `content/libraries` –
   /// so kommt eine neue Bibliothek ohne Codeänderung dazu, und eine private bleibt einfach weg.
    struct Catalog: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String?

        var fileName: String { id }
    }

    struct Result: Equatable { var imported = 0; var skipped = 0; var blocked = 0 }

    let context: ModelContext

    static func load(_ catalog: Catalog, from bundle: Bundle = .main) throws -> SeedLibrary {
        try load(named: catalog.id, from: bundle)
    }

    static func load(named name: String, from bundle: Bundle = .main) throws -> SeedLibrary {
        try ContentBundle.load(SeedLibrary.self, name, in: .libraries, bundle: bundle)
    }

   /// Die vorhandenen Startpakete mit Titel und Kurzbeschreibung aus der Datei selbst.
    static func catalogs(from bundle: Bundle = .main) -> [Catalog] {
        ContentBundle.names(in: .libraries, bundle: bundle).compactMap { name in
            guard let library = try? load(named: name, from: bundle) else { return nil }
            return Catalog(id: name, title: library.title ?? name, subtitle: library.note)
        }
    }

   /// Importiert eine Bibliothek. `applyProfilePreset` setzt zusätzlich Altersstufe und Inhaltsregeln des Profils.
    @discardableResult
    func importLibrary(_ library: SeedLibrary, into profile: KidProfile, applyProfilePreset: Bool = false, actor: String = "Seed") throws -> Result {
        let curation = CurationRepository(context: context)
        try curation.ensureSources(SourceRegistry.allDefinitions)
        if applyProfilePreset, let preset = library.profilePreset { preset.apply(to: profile) }
        let whitelist = WhitelistRepository(context: context)
        var result = Result()
        for channel in library.channels ?? [] {
            if whitelist.contains(youtubeId: channel.id, in: profile) { result.skipped += 1; continue }
            if curation.source(channelId: channel.id)?.trust == .blocked { result.blocked += 1; continue }
            let provider = channel.provider ?? .youtube
            let draft = WhitelistItemDraft(type: .channel, youtubeId: channel.id, title: channel.title,
                                           thumbnailUrl: channel.thumbnailUrl ?? "", channelTitle: channel.title,
                                           provider: provider, sourceChannelId: channel.id)
            let item = try curation.discover(draft, for: profile, sourceChannelId: channel.id, actor: actor)
            item.category = channel.category ?? item.category
            if let ageMin = channel.ageMin { item.ageMin = max(item.ageMin, ageMin) }
            if let note = channel.note { item.editorialNotes = [item.editorialNotes, note].compactMap { $0 }.joined(separator: " · ") }
            if item.approvalStatus == .approved { item.approvalStatus = .reviewRequired }
            result.imported += 1
        }
        for video in library.videos {
            if whitelist.contains(youtubeId: video.id, in: profile) { result.skipped += 1; continue }
            if curation.source(channelId: video.channelId)?.trust == .blocked { result.blocked += 1; continue }
            let provider = video.provider ?? .youtube
            let draft = WhitelistItemDraft(type: .video, youtubeId: video.id, title: video.title,
                                           thumbnailUrl: provider == .youtube ? YouTubeIDs.defaultThumbnail(videoId: video.id) : "",
                                           channelTitle: video.channelTitle, provider: provider,
                                           sourceChannelId: video.channelId, sourceUrl: video.sourceUrl,
                                           durationSeconds: video.durationSeconds)
            let item = try curation.discover(draft, for: profile, sourceChannelId: video.channelId, durationSeconds: video.durationSeconds, actor: actor)
   // Redaktionsvorschläge aus der Seed-Datei (Eltern bestätigen sie bei der Freigabe)
            item.category = video.category ?? item.category
            item.ageMin = max(item.ageMin, video.ageMin)
            item.ageMax = video.ageMax
            if let isNews = video.isNews { item.isNews = isNews }
            if let status = video.newsStatus { item.newsStatus = status }
            if video.isShort == true { item.isShort = true }
            if let topics = video.sensitiveTopics { item.sensitiveTopics.formUnion(topics) }
            if let url = video.sourceUrl { item.sourceUrl = url }
            if let note = video.note { item.editorialNotes = [item.editorialNotes, note].compactMap { $0 }.joined(separator: " · ") }
            if item.approvalStatus == .approved { item.approvalStatus = .reviewRequired }   // Sicherheitsnetz: Seeds nie freigegeben
            result.imported += 1
        }
        try context.save()
        return result
    }
}
