// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// Öffentliche Atom-Feeds von YouTube (`feeds/videos.xml`): kostenlos, ohne Schlüssel, max. 15 Einträge.
struct RSSFeedClient {
    let http: HTTPClient

    /// Kanal-Feed (`?channel_id=`): die neuesten Uploads.
    func channelVideos(channelId: String) async throws -> [PlaylistVideo] {
        RSSFeedParser.parse(try await load("https://www.youtube.com/feeds/videos.xml?channel_id=\(channelId)"))
    }

    /// Kanal-Feed mit Veröffentlichungsdatum und Shorts-/Premieren-Kennzeichen – Grundlage für
    /// „Neu bei deinen Kanälen" (ADR 0001). Gleiche Anfrage wie `channelVideos`.
    func channelFeed(channelId: String) async throws -> [FeedEntry] {
        RSSFeedParser.parseEntries(try await load("https://www.youtube.com/feeds/videos.xml?channel_id=\(channelId)"))
    }

    /// Playlist-Feed (`?playlist_id=`): dasselbe Format, aber nur die **ersten fünfzehn** Einträge der
    /// Playlist (gemessen am 02.10.2026 an „Blender Open Movies"). Längere Playlists sind ohne Data API
    /// nicht vollständig.
    func playlistVideos(playlistId: String) async throws -> [PlaylistVideo] {
        guard let encoded = playlistId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            throw YouTubeError.invalidURL
        }
        return RSSFeedParser.parsePlaylist(try await load("https://www.youtube.com/feeds/videos.xml?playlist_id=\(encoded)"))
    }

    private func load(_ string: String) async throws -> Data {
        guard let url = URL(string: string) else { throw YouTubeError.invalidURL }
        let (data, status) = try await http.get(url)
        guard status == 200 else { throw status == 404 ? YouTubeError.notFound : YouTubeError.http(status: status) }
        return data
    }
}

/// Ein Feed-Eintrag mit den Angaben, die nur der Feed kennt.
struct FeedEntry: Equatable, Sendable {
    var video: PlaylistVideo
    /// `<published>`; ohne Datum lässt sich das Alter nicht prüfen.
    var publishedAt: Date?
    /// Der Eintrag verlinkt auf `/shorts/…`.
    var isShort: Bool
    /// `media:statistics views="0"`: angekündigter Livestream oder Premiere, noch nicht gesendet.
    var isUpcoming: Bool
}

/// Liest Kanal- und Playlist-Feeds (gleiches Atom-Format): `feed/title`, `feed/author/name` sowie pro
/// `entry` `yt:videoId`, `yt:channelId`, `title`, `author/name`, `media:thumbnail`. Ohne externe Entities.
enum RSSFeedParser {
    /// Kanal-Feed: Der Feed-Titel ist der Kanalname.
    static func parse(_ data: Data) -> [PlaylistVideo] {
        entries(data).map { entry in
            var video = entry.video
            video.channelTitle = entry.feedTitle
            return video
        }
    }

    /// Playlist-Feed: Der Feed-Titel ist der Playlist-Name; jeder Eintrag nennt seinen eigenen Kanal.
    /// Shorts- und Premieren-Kennzeichen kommen mit – die Regel prüft sie, nicht nur den Titel.
    /// Steht ein Video mehrfach in der Playlist, zählt der erste Platz.
    static func parsePlaylist(_ data: Data) -> [PlaylistVideo] {
        var seen: Set<String> = []
        return entries(data).compactMap { entry in
            guard seen.insert(entry.video.videoId).inserted else { return nil }
            var video = entry.video
            video.isShort = entry.isShort
            video.isUpcoming = entry.isUpcoming
            return video
        }
    }

    /// Kanal-Feed mit Datum und Kennzeichen (der Feed-Titel ist der Kanalname).
    static func parseEntries(_ data: Data) -> [FeedEntry] {
        entries(data).map { entry in
            var video = entry.video
            video.channelTitle = entry.feedTitle
            return FeedEntry(video: video, publishedAt: entry.publishedAt, isShort: entry.isShort, isUpcoming: entry.isUpcoming)
        }
    }

    private struct Entry {
        var video: PlaylistVideo
        var feedTitle: String
        var publishedAt: Date?
        var isShort: Bool
        var isUpcoming: Bool
    }

    nonisolated private static func date(_ text: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        if let date = formatter.date(from: text) { return date }
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: text)
    }

    private static func entries(_ data: Data) -> [Entry] {
        let delegate = Delegate()
        let parser = XMLParser(data: data)
        parser.shouldResolveExternalEntities = false
        parser.delegate = delegate
        parser.parse()
        return delegate.entries.map {
            Entry(video: $0.video, feedTitle: delegate.feedTitle, publishedAt: $0.published.flatMap(date),
                  isShort: $0.isShort, isUpcoming: $0.views == 0)
        }
    }

    private struct Parsed {
        var video: PlaylistVideo
        var published: String?
        var isShort: Bool
        var views: Int?
    }

    private final class Delegate: NSObject, XMLParserDelegate {
        var entries: [Parsed] = []
        private(set) var feedTitle = ""
        private var feedAuthor = ""
        private var inEntry = false
        private var inAuthor = false
        private var text = ""
        private var videoId = ""
        private var channelId = ""
        private var entryTitle = ""
        private var entryAuthor = ""
        private var thumbnail = ""
        private var published = ""
        private var isShort = false
        private var views: Int?

        func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?,
                    qualifiedName qName: String?, attributes: [String: String] = [:]) {
            text = ""
            switch elementName {
            case "entry":
                inEntry = true
                videoId = ""; channelId = ""; entryTitle = ""; entryAuthor = ""; thumbnail = ""
                published = ""; isShort = false; views = nil
            case "author":
                inAuthor = true
            case "media:thumbnail" where inEntry:
                if let url = attributes["url"], thumbnail.isEmpty { thumbnail = url }
            case "link" where inEntry:
                if attributes["href"]?.contains("/shorts/") == true { isShort = true }
            case "media:statistics" where inEntry:
                views = attributes["views"].flatMap(Int.init)
            default: break
            }
        }

        func parser(_ parser: XMLParser, foundCharacters string: String) {
            text += string
        }

        func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
            let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
            switch elementName {
            case "title" where !inEntry && feedTitle.isEmpty:
                feedTitle = value
            case "title" where inEntry && entryTitle.isEmpty:
                entryTitle = value
            case "name" where inAuthor && inEntry && entryAuthor.isEmpty:
                entryAuthor = value
            case "name" where inAuthor && !inEntry && feedAuthor.isEmpty:
                feedAuthor = value
            case "author":
                inAuthor = false
            case "yt:videoId" where inEntry:
                videoId = value
            case "yt:channelId" where inEntry:
                channelId = value
            case "published" where inEntry && published.isEmpty:
                published = value
            case "entry":
                inEntry = false
                guard !videoId.isEmpty else { return }
                entries.append(Parsed(video: PlaylistVideo(
                    videoId: videoId, title: entryTitle,
                    thumbnailUrl: thumbnail.isEmpty ? YouTubeIDs.defaultThumbnail(videoId: videoId) : thumbnail,
                    channelTitle: entryAuthor.isEmpty ? feedAuthor : entryAuthor, position: entries.count,
                    channelId: channelId.isEmpty ? nil : channelId),
                    published: published.isEmpty ? nil : published, isShort: isShort, views: views))
            default: break
            }
            text = ""
        }
    }
}
