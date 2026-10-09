// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Observation
import SwiftData

/// Bereiche der festen Leiste unten (Sidephone: Musik / Hörspiele / Podcasts → hier Inhaltstypen + Suche).
enum KidTab: CaseIterable, Equatable {
    case channels, videos, playlists, search

    var title: String {
        switch self {
        case .channels: "Kanäle"
        case .videos: "Videos"
        case .playlists: "Sendungen"
        case .search: "Suche"
        }
    }

    var systemImage: String {
        switch self {
        case .channels: "person.crop.rectangle.stack"
        case .videos: "play.rectangle"
        case .playlists: "list.and.film"
        case .search: "magnifyingglass"
        }
    }

    var contentType: YouTubeContentType? {
        switch self {
        case .channels: .channel
        case .videos: .video
        case .playlists: .playlist
        case .search: nil
        }
    }
}

/// Gemeinsame Abhängigkeiten der Kindermodus-Bildschirme.
struct KidContext {
    let modelContext: ModelContext
    let youtube: YouTubeRepository
    var resolver: MediaResolver? = nil
   /// Aktives Kinderprofil – Kanalansichten prüfen damit Alter und zeigen Einzelfreigaben.
    var profile: KidProfile? = nil
    var cache: ChannelVideoCacheRepository { ChannelVideoCacheRepository(context: modelContext) }
   /// Anbieterneutrale Kanalseite (PeerTube oder YouTube).
    func channelPage(channelId: String, pageToken: String?) async throws -> PlaylistPage {
        if let resolver { return try await resolver.channelPage(channelId: channelId, pageToken: pageToken) }
        guard let uploads = YouTubeIDs.uploadsPlaylistId(forChannel: channelId) else { throw YouTubeError.invalidURL }
        return try await youtube.playlistItems(playlistId: uploads, pageToken: pageToken)
    }
}

/// Zeilen/Karten aus Whitelist-Einträgen und Sehverlauf.
enum KidRows {
    static func row(for item: WhitelistItem, context: KidContext) -> KidRow {
        let action: KidRow.Action = switch item.type {
        case .video where item.provider.isPlayable: .play(videoId: item.youtubeId, title: item.title)
        case .video: .none   // fremder Anbieter (z. B. ZDF-Mediathek): in v0.1 nicht abspielbar, nur sichtbar
        case .channel: .push(KidScreenFactory(id: "channel-\(item.youtubeId)") {
            ChannelModel(channelId: item.youtubeId, channelTitle: item.title, thumbnailUrl: item.thumbnailUrl, context: context) })
        case .playlist: .push(KidScreenFactory(id: "playlist-\(item.youtubeId)") {
            PlaylistModel(playlistId: item.youtubeId, playlistTitle: item.title, thumbnailUrl: item.thumbnailUrl, context: context) })
        }
        return KidRow(id: item.youtubeId, title: item.title, subtitle: item.channelTitle ?? item.type.label,
                      thumbnailUrl: item.thumbnailUrl, thumbnailStyle: item.type == .channel ? .avatar : .video,
                      sourceChannelId: item.sourceChannelId, action: action)
    }

   /// Freigegebene Videos einer Kategorie für Home-Sektionen (belastende Nachrichten nie hervorheben).
   /// Wenige Kacheln je Reihe: die Startseite soll die Frage „Was kann ich jetzt schauen?" beantworten,
   /// nicht die ganze Übersicht wiederholen.
    static func categoryRows(profile: KidProfile, category: ContentCategory, context: KidContext, limit: Int = 6) -> [KidRow] {
        WhitelistRepository(context: context.modelContext).visibleItems(of: profile, type: .video)
            .filter { $0.category == category && ContentPolicy.isHomeHighlightable($0) }
            .prefix(limit)
            .map { row(for: $0, context: context) }
    }

   /// Alles, was das Kind gerade abspielen darf: sichtbare Whitelist-Videos plus die gecachten Videos
   /// vertrauenswürdiger Kanäle. Grundlage für „Zuletzt geschaut“ – ein einmal gesehenes, später entzogenes
   /// Video darf über den Verlauf nicht wieder erreichbar sein.
    static func playableVideoIds(profile: KidProfile, context: KidContext) -> Set<String> {
        let whitelist = WhitelistRepository(context: context.modelContext)
        let curation = CurationRepository(context: context.modelContext)
        var ids = Set(whitelist.visibleItems(of: profile, type: .video).filter { $0.provider.isPlayable }.map(\.youtubeId))
        for channel in whitelist.visibleItems(of: profile, type: .channel)
        where curation.effectiveSource(channelId: channel.youtubeId)?.trust.allowsChannelBrowsing == true {
            ids.formUnion(context.cache.videos(channelId: channel.youtubeId).map(\.videoId))
        }
        ids.formUnion(PlaylistPlayability.cachedPlayableVideoIds(profile: profile, context: context.modelContext))
        return ids
    }

   /// „Zuletzt geschaut": neueste zuerst, jedes Video einmal, max. `limit` – und nur, was heute noch erlaubt ist.
   /// `context` ist optional: nur wer die gecachten Kanalvideos für den Empfehlungsabgleich braucht, gibt ihn mit.
    static func recentlyWatched(profile: KidProfile, allowed: Set<String>, context: ModelContext? = nil, limit: Int = 12) -> [KidRow] {
        var seen: Set<String> = []
        var result: [KidRow] = []
        for entry in profile.watchHistory.sorted(by: { $0.watchedAt > $1.watchedAt })
        where !seen.contains(entry.videoId) && allowed.contains(entry.videoId) {
            seen.insert(entry.videoId)
            let approvedItem = profile.whitelistItems.first { $0.youtubeId == entry.videoId && $0.type == .video }
            let cached = context.flatMap { ChannelVideoCacheRepository(context: $0).video(videoId: entry.videoId) }
            // Aus dem Playlist-Speicher: der Schlüssel ist `playlist:<id>`, keine Kanal-ID.
            let cachedPlaylistId = cached.flatMap { ChannelVideoCacheRepository.playlistId(fromKey: $0.channelId) }
            result.append(KidRow(id: "recent-\(entry.videoId)", title: entry.videoTitle,
                                 subtitle: cachedPlaylistId == nil ? nil : cached?.channelTitle,
                                 thumbnailUrl: YouTubeIDs.defaultThumbnail(videoId: entry.videoId),
                                 sourceChannelId: approvedItem?.sourceChannelId ?? (cachedPlaylistId == nil ? cached?.channelId : nil),
                                 sourcePlaylistId: approvedItem == nil ? cachedPlaylistId : nil,
                                 action: .play(videoId: entry.videoId, title: entry.videoTitle)))
            if result.count == limit { break }
        }
        return result
    }
}

// MARK: Home-Daten (FR-05): lead = Weiterschauen, cards = Inhalte des Bereichs (Home: Kanaele), rows = Zuletzt geschaut

@Observable
final class HomeModel: KidScreenModel {
    let id: String
    let title: String
    let tab: KidTab
    let profile: KidProfile
    let menu = WheelMenuModel(count: 0)
    let usesSplitHeader = true
    private let context: KidContext

   // Snapshot der Startseite: einmal pro `refresh()` aus der Datenbank gelesen, nie im View-Body.
   /// „Weiterschauen": letztes gespieltes Video; ohne Verlauf keine Kachel (die Auswahl beginnt dann bei den Kanaelen).
    private(set) var lead: KidRow?
    private(set) var cards: [KidRow] = []
   /// Kategorie-Sektionen nach Altersprofil, nur mit Inhalt.
    private(set) var categorySections: [(category: ContentCategory, rows: [KidRow])] = []
    private(set) var rows: [KidRow] = []

    init(profile: KidProfile, tab: KidTab, context: KidContext) {
        self.profile = profile
        self.tab = tab
        self.context = context
        id = "home-\(tab)-\(profile.id)"
        title = tab.title
        UserDefaults.standard.set(profile.id.uuidString, forKey: "kid.lastProfileId")
        refresh()
    }

   /// Liest Whitelist und Verlauf neu – beim Erscheinen und nach jeder Wiedergabe.
    func refresh() {
        let allowed = KidRows.playableVideoIds(profile: profile, context: context)
        let recent = KidRows.recentlyWatched(profile: profile, allowed: allowed, context: context.modelContext)
        lead = recent.first.map { last in
            KidRow(id: "lead-\(last.id)", title: last.title, subtitle: last.subtitle == "Zuletzt geschaut" ? nil : last.subtitle,
                   thumbnailUrl: last.thumbnailUrl, sourceChannelId: last.sourceChannelId, action: last.action)
        }
        cards = tab.contentType.map { type in
            WhitelistRepository(context: context.modelContext).visibleItems(of: profile, type: type).map { KidRows.row(for: $0, context: context) }
        } ?? []
        categorySections = ContentPolicy.homeCategorySections(for: profile)
            .map { ($0, KidRows.categoryRows(profile: profile, category: $0, context: context)) }
            .filter { !$0.1.isEmpty }
        // Der oberste Eintrag ist bereits „Weiterschauen“ – er darf nicht ein zweites Mal in der Liste stehen.
        rows = lead == nil ? recent : Array(recent.dropFirst())
        syncMenuCount()
    }

    var cardsTitle: String? { tab.contentType == nil ? nil : title }

    var rowsTitle: String? { rows.isEmpty ? nil : (lead == nil ? "Zuletzt geschaut" : "Weiterschauen") }

    var footerHint: String? {
        if tab.contentType != nil, cards.isEmpty { return String(localized: "Deine Eltern haben noch nichts ausgesucht.") }
        return nil
    }
}

// MARK: Kanal: Cache ist die Wahrheit, RSS zuerst, dann API-Seiten

@Observable
final class ChannelModel: KidScreenModel {
    let id: String
    let title: String
    let hero: KidHero?
    let menu = WheelMenuModel(count: 0)
    let supportsSearch = true
    private(set) var rows: [KidRow] = []
    private(set) var isLoading = false
    private(set) var footerHint: String?
    var searchText = "" { didSet { refreshRows() } }
    var rowsTitle: String? { searchText.isEmpty ? String(localized: "Videos") : String(localized: "Treffer") }

    private let channelId: String
    private let context: KidContext
    private var nextPageToken: String?
    private var hasMore = true
    private var started = false
   /// Kanal darf nur dynamisch durchstöbert werden, wenn die Quelle als vertrauenswürdige Kinderquelle gilt
   /// und ihr Mindestalter zum Profil passt (die Vertrauensstufe ist geräteweit, das Alter nicht).
    private var browsingAllowed: Bool {
        guard let source = CurationRepository(context: context.modelContext).effectiveSource(channelId: channelId),
              source.trust.allowsChannelBrowsing else { return false }
        if let profile = context.profile { return source.defaultAgeMin <= profile.ageBand.age }
        return true
    }

    init(channelId: String, channelTitle: String, thumbnailUrl: String? = nil, context: KidContext) {
        self.channelId = channelId
        self.context = context
        id = "channel-\(channelId)"
        title = channelTitle
        hero = KidHero(title: channelTitle, subtitle: "Kanal", thumbnailUrl: thumbnailUrl, style: .avatar)
    }

    func onAppear() async {
        guard !started else { return }
        started = true
        guard browsingAllowed else {
            hasMore = false
            // Alte Cache-Einträge aus Zeiten, in denen der Kanal noch durchstöbert werden durfte, sind hier tabu.
            try? context.cache.clear(channelId: channelId)
            rows = approvedRows()
            syncMenuCount()
            footerHint = rows.isEmpty ? String(localized: "Deine Eltern haben von hier noch kein Video ausgesucht.")
                                      : String(localized: "Hier sind nur die Videos, die deine Eltern ausgesucht haben.")
            return
        }
        try? context.cache.clear(channelId: channelId)   // wie Android: pro Besuch frische Daten
        await loadNextPage()
    }

    func onSelectionChanged(index: Int) {
        guard searchText.isEmpty, hasMore, !isLoading, index >= rows.count - 5 else { return }
        Task { await loadNextPage() }
    }

    func loadNextPage() async {
        guard hasMore, !isLoading else { return }
        isLoading = true
        defer { isLoading = false; refreshRows() }
        do {
            let page = try await context.channelPage(channelId: channelId, pageToken: nextPageToken)
   // Risikofilter auch bei vertrauenswürdigen Quellen: Shorts, Lives und harte Treffer nie ungeprüft
            let screened = page.videos.filter { video in
                let risk = RiskScreen.assess(title: video.title)
                return !risk.isHardBlocked && !risk.isShort && !risk.isLive
            }
            try context.cache.upsert(screened, channelId: channelId)
            nextPageToken = page.nextPageToken
            hasMore = page.hasMorePages
            footerHint = nil
        } catch YouTubeError.missingAPIKey {
            hasMore = false
            footerHint = rows.isEmpty && context.cache.videos(channelId: channelId).isEmpty
                ? String(localized: "Das klappt gerade nicht. Sag deinen Eltern Bescheid.")
                : nil   // die neuesten Videos sind da; ältere Seiten brauchen den Schlüssel der Eltern
        } catch {
            hasMore = false
            footerHint = String(localized: "Das hat nicht geklappt. Ist das Internet an?")
        }
    }

   /// Einzeln freigegebene Videos dieses Kanals – der einzige Inhalt, wenn die Quelle nicht durchstöbert werden darf.
    private func approvedRows() -> [KidRow] {
        guard let profile = context.profile else { return [] }
        return WhitelistRepository(context: context.modelContext).visibleItems(of: profile, type: .video)
            .filter { $0.sourceChannelId == channelId || ($0.sourceChannelId == nil && $0.channelTitle == title) }
            .map { KidRows.row(for: $0, context: context) }
    }

    private func refreshRows() {
        let videos = searchText.isEmpty ? context.cache.videos(channelId: channelId)
                                        : context.cache.search(channelId: channelId, query: searchText)
        rows = videos.map { KidRow(id: $0.videoId, title: $0.title, subtitle: $0.channelTitle, thumbnailUrl: $0.thumbnailUrl,
                                   sourceChannelId: channelId, action: .play(videoId: $0.videoId, title: $0.title)) }
        syncMenuCount()
        if rows.isEmpty, !searchText.isEmpty { footerHint = String(localized: "Kein Video passt zu „\(searchText)“.") }
    }
}

// MARK: Playlist: Zwischenspeicher, dann schlüsselfreier Feed, Data API nur zum Weiterblättern

@Observable
final class PlaylistModel: KidScreenModel {
    let id: String
    let title: String
    let hero: KidHero?
    let menu = WheelMenuModel(count: 0)
    let rowsTitle: String? = "Videos"
    private(set) var rows: [KidRow] = []
    private(set) var isLoading = false
    private(set) var footerHint: String?

    private let playlistId: String
    private let context: KidContext
    private let profileOverride: KidProfile?
   /// Alles, was die Quelle geliefert hat; gezeigt wird nur, was `ContentPolicy.canPlayFromPlaylist` durchlässt.
    private var videos: [PlaylistVideo] = []
    private var nextPageToken: String?
    private var hasMore = true
    private var started = false

   /// `profile` überschreibt das Profil des Kontexts (Schlafmodus wählt das Profil erst beim Start).
    init(playlistId: String, playlistTitle: String, thumbnailUrl: String? = nil, context: KidContext, profile: KidProfile? = nil) {
        self.playlistId = playlistId
        self.context = context
        profileOverride = profile
        id = "playlist-\(playlistId)"
        title = playlistTitle
        hero = KidHero(title: playlistTitle, subtitle: "Playlist", thumbnailUrl: thumbnailUrl)
    }

    private var profile: KidProfile? { profileOverride ?? context.profile }

    func onAppear() async {
        guard !started else { return }
        started = true
        guard let profile, PlaylistPlayability.playlistItem(id: playlistId, profile: profile) != nil else {
            hasMore = false
            footerHint = String(localized: "Diese Sendung haben deine Eltern nicht ausgesucht.")
            return
        }
        // Zuerst der Zwischenspeicher (offline, sofort), dann frische Daten.
        videos = context.cache.playlistVideos(playlistId: playlistId)
        refreshRows()
        await loadNextPage()
    }

    func onSelectionChanged(index: Int) {
        guard hasMore, !isLoading, index >= rows.count - 5 else { return }
        Task { await loadNextPage() }
    }

    func loadNextPage() async {
        guard hasMore, !isLoading else { return }
        isLoading = true
        defer { isLoading = false }
        let isFirstPage = nextPageToken == nil
        do {
            let page = try await context.youtube.playlistItems(playlistId: playlistId, pageToken: nextPageToken)
            if isFirstPage {
                videos = page.videos
                try? context.cache.storePlaylist(playlistId: playlistId, videos: page.videos)
            } else {
                // Nach dem Feed beginnt die Data API wieder bei Seite 1 – doppelte Videos fallen weg.
                let known = Set(videos.map(\.videoId))
                videos += page.videos.filter { !known.contains($0.videoId) }
            }
            nextPageToken = page.nextPageToken
            hasMore = page.hasMorePages
            refreshRows()
        } catch YouTubeError.missingAPIKey {
            hasMore = false
            refreshRows()
            if rows.isEmpty { footerHint = String(localized: "Das klappt gerade nicht. Sag deinen Eltern Bescheid.") }
        } catch {
            hasMore = false
            refreshRows()
            if rows.isEmpty { footerHint = String(localized: "Das hat nicht geklappt. Ist das Internet an?") }
        }
    }

    private func refreshRows() {
        guard let profile, let playlist = PlaylistPlayability.playlistItem(id: playlistId, profile: profile) else {
            rows = []
            menu.setCount(0)
            return
        }
        let allowed = PlaylistPlayability.allowedVideos(videos, profile: profile, playlist: playlist, context: context.modelContext)
        rows = allowed.map { video in
            KidRow(id: video.videoId, title: video.title, subtitle: video.channelTitle, thumbnailUrl: video.thumbnailUrl,
                   sourceChannelId: video.channelId, sourcePlaylistId: playlistId,
                   action: .play(videoId: video.videoId, title: video.title))
        }
        syncMenuCount()
        footerHint = videos.isEmpty ? String(localized: "Diese Playlist ist leer.")
            : rows.isEmpty ? String(localized: "Hier ist gerade nichts für dich dabei.") : nil
    }
}

// MARK: Suche (FR-07): nur lokal – Whitelist-Titel/Kanalnamen + gecachte Kanalvideos

@Observable
final class SearchModel: KidScreenModel {
    let id: String
    let title = "Suche"
    let hero: KidHero? = KidHero(title: String(localized: "Suche"), subtitle: String(localized: "In deinen Inhalten"), systemImage: "magnifyingglass")
    let menu = WheelMenuModel(count: 0)
    let supportsSearch = true
    let tab: KidTab = .search
    let profile: KidProfile
    private(set) var rows: [KidRow] = []
    private(set) var footerHint: String? = String(localized: "Tippe oben, um in den freigegebenen Inhalten zu suchen.")
    var rowsTitle: String? { rows.isEmpty ? nil : String(localized: "Treffer") }
    var searchText = "" { didSet { refresh() } }

    private let context: KidContext

    init(profile: KidProfile, context: KidContext) {
        self.profile = profile
        self.context = context
        id = "search-\(profile.id)"
    }

    private func refresh() {
        let needle = searchText.trimmingCharacters(in: .whitespaces)
        guard !needle.isEmpty else {
            rows = []
            menu.setCount(0)
            footerHint = String(localized: "Tippe oben, um in den freigegebenen Inhalten zu suchen.")
            return
        }
   // ausschließlich freigegebene Inhalte – niemals eine offene YouTube-Suche.
        let items = WhitelistRepository(context: context.modelContext).visibleItems(of: profile)
            .filter { $0.title.localizedStandardContains(needle) || ($0.channelTitle?.localizedStandardContains(needle) ?? false) }
            .sorted { $0.title < $1.title }
            .map { KidRows.row(for: $0, context: context) }
        let known = Set(items.map(\.id))
        let curation = CurationRepository(context: context.modelContext)
        let visibleChannelIds = Set(WhitelistRepository(context: context.modelContext).visibleItems(of: profile, type: .channel).map(\.youtubeId))
        let cached = context.cache.searchAll(query: needle)
            .filter { !known.contains($0.videoId) && visibleChannelIds.contains($0.channelId)
                && (curation.source(channelId: $0.channelId)?.trust.allowsChannelBrowsing ?? false) }
            .map { KidRow(id: $0.videoId, title: $0.title, subtitle: $0.channelTitle, thumbnailUrl: $0.thumbnailUrl,
                          sourceChannelId: $0.channelId, action: .play(videoId: $0.videoId, title: $0.title)) }
        rows = items + cached
        syncMenuCount()
        footerHint = rows.isEmpty ? String(localized: "Dazu gibt es kein Video.") : nil
    }
}
