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
    private var watchTimeBlocked = false
    private(set) var failedWatchRecord: PlayerModel.UncommittedWatchTime?
    var hasWatchTimeFailure: Bool { watchTimeBlocked || player?.hasWatchTimeFailure == true }
    var makeWatchTimeRecorder: (ModelContext) -> any WatchTimeRecording = { WatchTimeRepository(context: $0) }
    var makeAdmissionRecorder: (ModelContext) -> any PlaybackAdmissionRecording = { PlaybackAdmissionRepository(context: $0) }
   /// Set when playback for this profile was refused because an earlier session was never
   /// cleanly closed (app kill/crash while playing). Only a parent (PIN-gated UI) can clear it;
   /// other profiles are never affected because the marker is scoped to this one `profileID`.
    private(set) var interruptedSessionProfileID: UUID?
    var isBlockedByInterruptedSession: Bool { interruptedSessionProfileID != nil }
    private var admissions: (any PlaybackAdmissionRecording)?
    private var admissionProfileID: UUID?
    private var admissionToken: UUID?
    private let now: (() -> Date)?

    init(now: (() -> Date)? = nil) { self.now = now }
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
                queue.append(PlayerModel.Item(videoId: id, title: title, thumbnailURL: candidate.thumbnailUrl,
                                              channelTitle: candidate.subtitle, sourceChannelId: candidate.sourceChannelId,
                                              sourcePlaylistId: candidate.sourcePlaylistId))
            }
        }
        if queue.isEmpty {
            queue = [PlayerModel.Item(videoId: videoId, title: row.title, thumbnailURL: row.thumbnailUrl,
                                      channelTitle: row.subtitle, sourceChannelId: row.sourceChannelId,
                                      sourcePlaylistId: row.sourcePlaylistId)]
        }
        if let profile {
            queue = queue.filter { SafeRecommendationService.allowed($0, for: profile, context: context) }
            guard let selected = queue.firstIndex(where: { $0.videoId == videoId }) else { return }
            let additional = SafeRecommendationService.candidates(for: profile, currentVideoId: videoId,
                                                                   contextQueue: queue, context: context)
                // In a channel/playlist context, never append global candidates:
                // an embed failure must not make playback jump into another channel.
                .filter { candidate in
                    guard row.sourcePlaylistId == nil, let sourceChannelId = row.sourceChannelId else { return false }
                    return candidate.channelId == sourceChannelId
                }
                .map { PlayerModel.Item(videoId: $0.videoId, title: $0.title, thumbnailURL: $0.thumbnailURL,
                                        channelTitle: $0.channelTitle, sourceChannelId: $0.channelId) }
            for item in additional where !queue.contains(where: { $0.videoId == item.videoId }) { queue.append(item) }
            play(queue: queue, startIndex: selected, profile: profile, context: context)
            return
        }
        let start = queue.firstIndex { $0.videoId == videoId } ?? 0
        play(queue: queue, startIndex: start, profile: profile, context: context)
    }

    func play(queue: [PlayerModel.Item], startIndex: Int, profile: KidProfile?, context: ModelContext) {
        guard !queue.isEmpty else { return }
        let safeQueue: [PlayerModel.Item]
        if let profile {
            safeQueue = queue.enumerated().filter { SafeRecommendationService.allowed($0.element, for: profile, context: context) }.map(\.element)
            guard !safeQueue.isEmpty,
                  let safeStart = safeQueue.firstIndex(where: { $0.videoId == queue[min(max(0, startIndex), queue.count - 1)].videoId }) else { return }
            playUnchecked(queue: safeQueue, startIndex: safeStart, profile: profile, context: context)
            return
        }
        playUnchecked(queue: queue, startIndex: startIndex, profile: profile, context: context)
    }

    /// Handles a child tap on a recommendation. Re-evaluating here protects against
    /// policy changes while the player screen is still open.
    @discardableResult
    func recommendedVideoTapped(_ item: PlayerModel.Item, profile: KidProfile, context: ModelContext) -> Bool {
        guard let player, SafeRecommendationService.allowed(item, for: profile, context: context),
              let index = player.queue.firstIndex(where: { $0.videoId == item.videoId }) else { return false }
        player.jump(to: index)
        return true
    }

    private func playUnchecked(queue: [PlayerModel.Item], startIndex: Int, profile: KidProfile?, context: ModelContext) {
        close()
        guard !watchTimeBlocked else { return }
        if let profile {
            let recorder = makeAdmissionRecorder(context)
            guard (try? recorder.isPending(for: profile.id)) == false else {
                interruptedSessionProfileID = profile.id
                return
            }
            let token = UUID()
            guard (try? recorder.begin(for: profile, token: token, at: now?() ?? Date())) != nil else {
                // Could not durably admit the session (e.g. store unavailable): fail closed,
                // the same way a mid-session storage failure already does.
                interruptedSessionProfileID = profile.id
                return
            }
            admissions = recorder
            admissionProfileID = profile.id
            admissionToken = token
        }
        interruptedSessionProfileID = nil
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
                                watchTime: makeWatchTimeRecorder(context), profile: profile, now: now)
        model.autoAdvance = profile?.autoplayNext ?? false   // Standard aus
        player = model
        model.start()
    }

   /// Bucht offene Sehzeit und schließt den Player.
    func close() {
        player?.close()
        if let failed = player?.failedWatchRecord { failedWatchRecord = failed }
        let watchTimeSucceeded = player?.hasWatchTimeFailure != true
        watchTimeBlocked = hasWatchTimeFailure
        // Only remove this session's own marker, and only once its watch time is durably
        // saved (`player?.close()` already flushed it above). A storage failure must leave
        // the marker in place instead of clearing it on an unclean/uncertain exit.
        if watchTimeSucceeded, let admissions, let admissionProfileID, let admissionToken {
            try? admissions.finish(profileID: admissionProfileID, token: admissionToken)
        }
        admissions = nil
        admissionProfileID = nil
        admissionToken = nil
        player = nil
        bridge?.shutdown()
        peerTubeBridge?.shutdown()
        bridge = nil
        peerTubeBridge = nil
        fullscreenRequested = false
    }

   /// UI entry point exists only behind the parent PIN, never from child-mode startup.
    @discardableResult
    func acknowledgeInterruptedSession(context: ModelContext) -> Bool {
        guard let profileID = interruptedSessionProfileID else { return false }
        do {
            try makeAdmissionRecorder(context).acknowledgeByParent(profileID: profileID)
        } catch {
            // A PIN authorizes recovery; it does not prove that recovery was saved.
            // Keep the profile blocked and let the parent retry after storage recovers.
            return false
        }
        interruptedSessionProfileID = nil
        return true
    }
}
