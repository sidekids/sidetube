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
        case .playlists: "Playlists"
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
                      thumbnailUrl: item.thumbnailUrl, thumbnailStyle: item.type == .channel ? .avatar : .video, action: action)
    }

   /// Freigegebene Videos einer Kategorie für Home-Sektionen (belastende Nachrichten nie hervorheben).
    static func categoryRows(profile: KidProfile, category: ContentCategory, context: KidContext, limit: Int = 12) -> [KidRow] {
        WhitelistRepository(context: context.modelContext).visibleItems(of: profile, type: .video)
            .filter { $0.category == category && ContentPolicy.isHomeHighlightable($0) }
            .prefix(limit)
            .map { row(for: $0, context: context) }
    }

   /// „Zuletzt geschaut": neueste zuerst, jedes Video einmal, max. `limit`.
    static func recentlyWatched(profile: KidProfile, limit: Int = 12) -> [KidRow] {
        var seen: Set<String> = []
        var result: [KidRow] = []
        for entry in profile.watchHistory.sorted(by: { $0.watchedAt > $1.watchedAt }) where !seen.contains(entry.videoId) {
            seen.insert(entry.videoId)
            result.append(KidRow(id: "recent-\(entry.videoId)", title: entry.videoTitle, subtitle: nil,
                                 thumbnailUrl: YouTubeIDs.defaultThumbnail(videoId: entry.videoId),
                                 action: .play(videoId: entry.videoId, title: entry.videoTitle)))
            if result.count == limit { break }
        }
        return result
    }

    static func playRow(_ video: PlaylistVideo) -> KidRow {
        KidRow(id: video.videoId, title: video.title, subtitle: video.channelTitle, thumbnailUrl: video.thumbnailUrl,
               action: .play(videoId: video.videoId, title: video.title))
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

    init(profile: KidProfile, tab: KidTab, context: KidContext) {
        self.profile = profile
        self.tab = tab
        self.context = context
        id = "home-\(tab)-\(profile.id)"
        title = tab.title
        UserDefaults.standard.set(profile.id.uuidString, forKey: "kid.lastProfileId")
        syncMenuCount()
    }

   /// „Weiterschauen": letztes gespieltes Video; ohne Verlauf keine Kachel (die Auswahl beginnt dann bei den Kanaelen).
    var lead: KidRow? {
        guard let last = KidRows.recentlyWatched(profile: profile, limit: 1).first else { return nil }
        return KidRow(id: "lead-\(last.id)", title: last.title, subtitle: last.subtitle == "Zuletzt geschaut" ? nil : last.subtitle,
                      thumbnailUrl: last.thumbnailUrl, action: last.action)
    }

    var cards: [KidRow] {
        guard let type = tab.contentType else { return [] }
        return WhitelistRepository(context: context.modelContext).visibleItems(of: profile, type: type).map { KidRows.row(for: $0, context: context) }
    }

    var cardsTitle: String? { tab.contentType == nil ? nil : title }

   /// Kategorie-Sektionen nach Altersprofil, nur mit Inhalt.
    var categorySections: [(category: ContentCategory, rows: [KidRow])] {
        ContentPolicy.homeCategorySections(for: profile)
            .map { ($0, KidRows.categoryRows(profile: profile, category: $0, context: context)) }
            .filter { !$0.1.isEmpty }
    }

    var rows: [KidRow] {
        let recent = KidRows.recentlyWatched(profile: profile)
        // Der oberste Eintrag ist bereits „Weiterschauen“ – er darf nicht ein zweites Mal in der Liste stehen.
        let rest = lead == nil ? recent : Array(recent.dropFirst())
        menu.setCount((lead == nil ? 0 : 1) + cards.count + rest.count)
        return rest
    }

    var rowsTitle: String? { rows.isEmpty ? nil : (lead == nil ? "Zuletzt geschaut" : "Weiterschauen") }

    var footerHint: String? {
        if tab.contentType != nil, cards.isEmpty { return "Noch nichts freigegeben. Eltern können im Elternbereich Inhalte hinzufügen." }
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
    var rowsTitle: String? { searchText.isEmpty ? "Videos" : "Treffer" }

    private let channelId: String
    private let context: KidContext
    private var nextPageToken: String?
    private var hasMore = true
    private var started = false
   /// Kanal darf nur dynamisch durchstöbert werden, wenn die Quelle als vertrauenswürdige Kinderquelle gilt.
    private var browsingAllowed: Bool {
        CurationRepository(context: context.modelContext).effectiveSource(channelId: channelId)?.trust.allowsChannelBrowsing ?? false
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
            footerHint = "Von diesem Kanal sind nur einzeln freigegebene Videos zu sehen."
            refreshRows()
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
                ? "Videos konnten nicht geladen werden (API-Schlüssel fehlt)."
                : "Ältere Videos brauchen den YouTube-API-Schlüssel."
        } catch {
            hasMore = false
            footerHint = "Videos konnten gerade nicht geladen werden."
        }
    }

    private func refreshRows() {
        let videos = searchText.isEmpty ? context.cache.videos(channelId: channelId)
                                        : context.cache.search(channelId: channelId, query: searchText)
        rows = videos.map { KidRow(id: $0.videoId, title: $0.title, subtitle: $0.channelTitle, thumbnailUrl: $0.thumbnailUrl,
                                   action: .play(videoId: $0.videoId, title: $0.title)) }
        menu.setCount(rows.count)
        if rows.isEmpty, !searchText.isEmpty { footerHint = "Kein Video passt zu „\(searchText)“." }
    }
}

// MARK: Playlist: Seiten im Speicher, nur Data API

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
    private var nextPageToken: String?
    private var hasMore = true
    private var started = false

    init(playlistId: String, playlistTitle: String, thumbnailUrl: String? = nil, context: KidContext) {
        self.playlistId = playlistId
        self.context = context
        id = "playlist-\(playlistId)"
        title = playlistTitle
        hero = KidHero(title: playlistTitle, subtitle: "Playlist", thumbnailUrl: thumbnailUrl)
    }

    func onAppear() async {
        guard !started else { return }
        started = true
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
        do {
            let page = try await context.youtube.playlistItems(playlistId: playlistId, pageToken: nextPageToken)
            rows += page.videos.map(KidRows.playRow)
            nextPageToken = page.nextPageToken
            hasMore = page.hasMorePages
            menu.setCount(rows.count)
            footerHint = rows.isEmpty ? "Diese Playlist ist leer." : nil
        } catch YouTubeError.missingAPIKey {
            hasMore = false
            footerHint = "Playlists brauchen den YouTube-API-Schlüssel."
        } catch {
            hasMore = false
            footerHint = "Playlist konnte gerade nicht geladen werden."
        }
    }
}

// MARK: Suche (FR-07): nur lokal – Whitelist-Titel/Kanalnamen + gecachte Kanalvideos

@Observable
final class SearchModel: KidScreenModel {
    let id: String
    let title = "Suche"
    let hero: KidHero? = KidHero(title: "Suche", subtitle: "In deinen Inhalten", systemImage: "magnifyingglass")
    let menu = WheelMenuModel(count: 0)
    let supportsSearch = true
    let tab: KidTab = .search
    let profile: KidProfile
    private(set) var rows: [KidRow] = []
    private(set) var footerHint: String? = "Tippe oben, um in den freigegebenen Inhalten zu suchen."
    var rowsTitle: String? { rows.isEmpty ? nil : "Treffer" }
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
            footerHint = "Tippe oben, um in den freigegebenen Inhalten zu suchen."
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
                          action: .play(videoId: $0.videoId, title: $0.title)) }
        rows = items + cached
        menu.setCount(rows.count)
        footerHint = rows.isEmpty ? "Keine freigegebenen Videos gefunden." : nil
    }
}
