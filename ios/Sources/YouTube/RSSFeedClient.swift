// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// Atom-Feed eines Kanals (`feeds/videos.xml?channel_id=`): kostenlos, max. 15 neueste Videos.
struct RSSFeedClient {
    let http: HTTPClient

    func channelVideos(channelId: String) async throws -> [PlaylistVideo] {
        let url = URL(string: "https://www.youtube.com/feeds/videos.xml?channel_id=\(channelId)")!
        let (data, status) = try await http.get(url)
        guard status == 200 else { throw status == 404 ? YouTubeError.notFound : YouTubeError.http(status: status) }
        return RSSFeedParser.parse(data)
    }
}

/// Liest `feed/title` sowie pro `entry`: `yt:videoId`, `title`, `media:thumbnail`. Ohne externe Entities.
enum RSSFeedParser {
    static func parse(_ data: Data) -> [PlaylistVideo] {
        let delegate = Delegate()
        let parser = XMLParser(data: data)
        parser.shouldResolveExternalEntities = false
        parser.delegate = delegate
        parser.parse()
        return delegate.videos
    }

    private final class Delegate: NSObject, XMLParserDelegate {
        var videos: [PlaylistVideo] = []
        private var feedTitle = ""
        private var inEntry = false
        private var currentElement = ""
        private var text = ""
        private var videoId = ""
        private var entryTitle = ""
        private var thumbnail = ""

        func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?,
                    qualifiedName qName: String?, attributes: [String: String] = [:]) {
            currentElement = elementName
            text = ""
            switch elementName {
            case "entry":
                inEntry = true
                videoId = ""; entryTitle = ""; thumbnail = ""
            case "media:thumbnail" where inEntry:
                if let url = attributes["url"], thumbnail.isEmpty { thumbnail = url }
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
            case "yt:videoId" where inEntry:
                videoId = value
            case "entry":
                inEntry = false
                guard !videoId.isEmpty else { return }
                videos.append(PlaylistVideo(
                    videoId: videoId, title: entryTitle,
                    thumbnailUrl: thumbnail.isEmpty ? YouTubeIDs.defaultThumbnail(videoId: videoId) : thumbnail,
                    channelTitle: feedTitle, position: videos.count))
            default: break
            }
            text = ""
        }
    }
}
