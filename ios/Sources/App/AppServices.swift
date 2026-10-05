// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Observation

/// Dienste, die die Views brauchen und die nicht in SwiftData liegen.
@Observable
final class AppServices {
    let youtube: YouTubeRepository
    let resolver: MediaResolver
    /// Elternkanal (ADR 0005): Einrichtung und Meldung neuer Wünsche.
    let parentChannels: ParentChannelStore
    let parentNotifier: TalkBotNotifier

    init(youtube: YouTubeRepository, http: HTTPClient,
         parentChannels: ParentChannelStore = InMemoryParentChannelStore(), poster: HTTPPoster = URLSessionPoster()) {
        self.youtube = youtube
        self.resolver = MediaResolver(youtube: youtube, peertube: PeerTubeClient(http: http))
        self.parentChannels = parentChannels
        self.parentNotifier = TalkBotNotifier(channel: { [parentChannels] in parentChannels.load() }, poster: poster)
    }

    static func live() -> AppServices {
        let http = URLSessionHTTPClient()
        return AppServices(youtube: YouTubeRepository(http: http, apiKey: AppConfig.youTubeAPIKey), http: http,
                           parentChannels: KeychainParentChannelStore())
    }
}
