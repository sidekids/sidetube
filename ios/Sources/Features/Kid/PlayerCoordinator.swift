// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Observation
import SwiftData
import WebKit

/// Startet und beendet Wiedergaben; hält eine WebView-Bridge pro Sitzung.
@Observable
final class PlayerCoordinator {
    private(set) var player: PlayerModel?
    private(set) var bridge: YouTubePlayerBridge?
    private(set) var peerTubeBridge: PeerTubePlayerBridge?
   /// WebView des aktuell laufenden Anbieters (YouTube oder PeerTube).
    var webView: WKWebView? { usesPeerTube ? peerTubeBridge?.webView : bridge?.webView }
    private(set) var usesPeerTube = false
    var fullscreenRequested = false
   /// Engine-Fabrik (Tests ersetzen die WebView).
    var makeEngine: () -> any PlayerEngine = { YouTubePlayerBridge() }
    var makePeerTubeEngine: () -> any PlayerEngine = { PeerTubePlayerBridge() }

    var isPresented: Bool { player != nil }

   /// Spielt `row` aus der Liste `items` (alle abspielbaren Einträge bilden die Warteschlange, FR-06.4).
    func play(_ row: KidRow, in items: [KidRow], profile: KidProfile?, context: ModelContext) {
        guard case .play(let videoId, _) = row.action else { return }
        var queue: [PlayerModel.Item] = []
        for candidate in items {
            if case .play(let id, let title) = candidate.action, !queue.contains(where: { $0.videoId == id }) {
                queue.append(PlayerModel.Item(videoId: id, title: title))
            }
        }
        if queue.isEmpty { queue = [PlayerModel.Item(videoId: videoId, title: row.title)] }
        let start = queue.firstIndex { $0.videoId == videoId } ?? 0
        play(queue: queue, startIndex: start, profile: profile, context: context)
    }

    func play(queue: [PlayerModel.Item], startIndex: Int, profile: KidProfile?, context: ModelContext) {
        close()
        let engine: any PlayerEngine
        usesPeerTube = PeerTubeIDs.isPeerTube(queue[startIndex].videoId)
        if usesPeerTube {
            if let peerTubeBridge { engine = peerTubeBridge } else {
                let created = makePeerTubeEngine()
                peerTubeBridge = created as? PeerTubePlayerBridge
                engine = created
            }
        } else if let bridge { engine = bridge } else {
            let created = makeEngine()
            bridge = created as? YouTubePlayerBridge
            engine = created
        }
        let model = PlayerModel(queue: queue, startIndex: startIndex, engine: engine,
                                watchTime: WatchTimeRepository(context: context), profile: profile)
        model.autoAdvance = profile?.autoplayNext ?? false   // Standard aus
        player = model
        model.start()
    }

   /// Bucht offene Sehzeit und schließt den Player.
    func close() {
        player?.close()
        player = nil
        fullscreenRequested = false
    }
}
