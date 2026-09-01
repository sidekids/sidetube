import Foundation

/// Zugriff auf Build-Konfiguration. Der API-Schlüssel kommt über xcconfig -> Info.plist
/// und wird nirgends protokolliert.
enum AppConfig {
    static var youTubeAPIKey: String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "YouTubeAPIKey") as? String,
              !value.isEmpty, value != "DEIN_YOUTUBE_DATA_API_KEY" else { return nil }
        return value
    }

    static var hasYouTubeAPIKey: Bool { youTubeAPIKey != nil }

    static var appVersion: String {
        let short = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
        return "\(short) (\(build))"
    }
}
