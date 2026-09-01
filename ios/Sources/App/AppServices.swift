import Foundation
import Observation

/// Dienste, die die Views brauchen und die nicht in SwiftData liegen.
@Observable
final class AppServices {
    let youtube: YouTubeRepository
    let resolver: MediaResolver

    init(youtube: YouTubeRepository, http: HTTPClient) {
        self.youtube = youtube
        self.resolver = MediaResolver(youtube: youtube, peertube: PeerTubeClient(http: http))
    }

    static func live() -> AppServices {
        let http = URLSessionHTTPClient()
        return AppServices(youtube: YouTubeRepository(http: http, apiKey: AppConfig.youTubeAPIKey), http: http)
    }
}
