// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// „Als Nächstes“ am Videoende: SideTube schlägt selbst vor, statt dem YouTube-Endscreen das Feld zu überlassen.
///
/// Es kommen ausschließlich Videos in Frage, die die Freigabelogik bereits passiert haben – die laufende
/// Warteschlange (aus einer sichtbaren Liste gestartet) und `WhitelistRepository.visibleItems`. Die Regel
/// selbst kennt keine Datenbank und ist dadurch ohne SwiftData testbar. Reihenfolge:
/// 1. die nächsten Einträge der Warteschlange (zyklisch, ohne das laufende Video),
/// 2. freigegebene Videos desselben Kanals,
/// 3. weitere freigegebene Videos in Listenreihenfolge (neueste zuerst).
/// Kein Ranking, keine Sehzeit-Auswertung, keine Endlosliste: höchstens `defaultLimit` Karten.
enum NextUpPolicy {
    struct Candidate: Equatable, Identifiable, Sendable {
        var videoId: String
        var title: String
        var thumbnailUrl: String?
        var channelTitle: String?
        var sourceChannelId: String?
        var id: String { videoId }

        init(videoId: String, title: String, thumbnailUrl: String? = nil, channelTitle: String? = nil, sourceChannelId: String? = nil) {
            self.videoId = videoId
            self.title = title
            self.thumbnailUrl = thumbnailUrl
            self.channelTitle = channelTitle
            self.sourceChannelId = sourceChannelId
        }

        init(queueItem: PlayerModel.Item) {
            self.init(videoId: queueItem.videoId, title: queueItem.title, thumbnailUrl: YouTubeIDs.defaultThumbnail(videoId: queueItem.videoId))
        }

       /// Aus einem Whitelist-Eintrag – der Aufrufer reicht nur `visibleItems` herein.
        init(item: WhitelistItem) {
            self.init(videoId: item.youtubeId, title: item.title, thumbnailUrl: item.thumbnailUrl,
                      channelTitle: item.channelTitle, sourceChannelId: item.sourceChannelId)
        }
    }

   /// Wenige, klare Karten – ein Kind soll auswählen, nicht scrollen.
    static let defaultLimit = 3

    static func suggestions(queue: [PlayerModel.Item], currentIndex: Int, approved: [Candidate],
                            currentChannelId: String? = nil, currentChannelTitle: String? = nil,
                            limit: Int = defaultLimit) -> [Candidate] {
        guard limit > 0, !queue.isEmpty, currentIndex >= 0, currentIndex < queue.count else { return [] }
        let currentId = queue[currentIndex].videoId
        var seen: Set<String> = [currentId]
        var result: [Candidate] = []

        func add(_ candidate: Candidate) {
            guard result.count < limit, !seen.contains(candidate.videoId) else { return }
            seen.insert(candidate.videoId)
            result.append(candidate)
        }

        for offset in 1..<queue.count {
            add(Candidate(queueItem: queue[(currentIndex + offset) % queue.count]))
        }
        let sameChannel = approved.filter { candidate in
            if let currentChannelId, let id = candidate.sourceChannelId, id == currentChannelId { return true }
            if let currentChannelTitle, let title = candidate.channelTitle, title == currentChannelTitle { return true }
            return false
        }
        sameChannel.forEach(add)
        approved.forEach(add)
        return result
    }
}
