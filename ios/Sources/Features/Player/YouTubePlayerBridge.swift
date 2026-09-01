import Foundation
import OSLog
import WebKit

private let log = Logger(subsystem: "xyz.steier.sidetube", category: "player")

/// Besitzt die WKWebView mit `Player.html`, leitet JS-Ereignisse weiter und sperrt jede Navigation
/// aus dem Player heraus: keine „Auf YouTube ansehen"-Links, keine neuen Fenster.
final class YouTubePlayerBridge: NSObject, PlayerEngine {
    let webView: WKWebView
    var onEvent: ((PlayerEngineEvent) -> Void)?
    private var pageLoaded = false
    private var pendingVideoId: String?

    override init() {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.allowsPictureInPictureMediaPlayback = false
        let controller = WKUserContentController()
        configuration.userContentController = controller
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init()
        controller.add(WeakMessageHandler(target: self), name: "player")
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.bounces = false
        webView.allowsBackForwardNavigationGestures = false
        webView.allowsLinkPreview = false
        webView.isOpaque = false
        webView.backgroundColor = .black
        loadPage()
    }

   /// Herkunft der Player-Seite. NICHT youtube.com: damit antwortet die IFrame-API sofort mit Fehler 152
   /// (im Simulator reproduzierbar). Eine App-eigene Herkunft aus der Bundle-ID (wie in Googles
   /// youtube-ios-player-helper) wird akzeptiert; `origin` in den playerVars muss identisch sein.
    static let defaultOrigin = URL(string: "http://\((Bundle.main.bundleIdentifier ?? "sidetube").lowercased())")!

    #if DEBUG
   /// Diagnosedatei im App-Container (Documents/player-diagnose.log) – Simulator-stdout ist nicht zuverlässig lesbar.
    static func diagnose(_ line: String) {
        let url = URL.documentsDirectory.appending(path: "player-diagnose.log")
        let entry = "\(Date().formatted(date: .omitted, time: .standard)) \(line)\n"
        if let handle = try? FileHandle(forWritingTo: url) {
            handle.seekToEndOfFile(); handle.write(Data(entry.utf8)); try? handle.close()
        } else {
            try? entry.write(to: url, atomically: true, encoding: .utf8)
        }
    }
    #endif

    private func loadPage() {
        guard let url = Bundle.main.url(forResource: "Player", withExtension: "html"),
              let html = try? String(contentsOf: url, encoding: .utf8) else {
            onEvent?(.apiFailed)
            return
        }
   // baseURL: die IFrame-API verlangt eine gültige Herkunft (file:// liefert Fehler 153).
        var origin = Self.defaultOrigin
        #if DEBUG
        if let override = UserDefaults.standard.string(forKey: "sidetube.devPlayerOrigin"), let url = URL(string: override) { origin = url }
        Self.diagnose("Origin: \(origin.absoluteString)")
        #endif
        let page = html.replacingOccurrences(of: "__ORIGIN__", with: origin.absoluteString)
        webView.loadHTMLString(page, baseURL: origin)
    }

   // MARK: PlayerEngine

    func load(videoId: String) {
        pendingVideoId = videoId
        guard pageLoaded else { return }
        evaluate("loadVideo(\(Self.jsString(videoId)))")
    }

    func play() { evaluate("playVideo()") }
    func pause() { evaluate("pauseVideo()") }
    func togglePlayback() { evaluate("togglePlayback()") }
    func seek(by seconds: Double) { evaluate("seekBy(\(seconds))") }
    func setVolume(_ percent: Int) { evaluate("setVolume(\(percent))") }
    func stop() { evaluate("stopVideo()") }

    private func evaluate(_ script: String) {
        webView.evaluateJavaScript(script) { _, _ in }
    }

    private static func jsString(_ value: String) -> String {
        let escaped = value.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "'", with: "\\'")
        return "'\(escaped)'"
    }

    fileprivate func receive(_ body: Any) {
        log.info("JS → App: \(String(describing: body), privacy: .public)")
        #if DEBUG
        Self.diagnose("JS → App: \(body)")
        #endif
        guard let dictionary = body as? [String: Any], let event = dictionary["event"] as? String else { return }
        let value = (dictionary["value"] as? Int) ?? (dictionary["value"] as? String).flatMap(Int.init) ?? -1
        switch event {
        case "ready": onEvent?(.ready)
        case "state": onEvent?(.state(value))
        case "error": onEvent?(.error(value))
        case "apiFailed": onEvent?(.apiFailed)
        case "time": onEvent?(.time(value))
        default: break
        }
    }
}

extension YouTubePlayerBridge: WKNavigationDelegate, WKUIDelegate {
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
   // Unterframes (der YouTube-IFrame selbst) dürfen laden; das Hauptdokument nur einmal (unser HTML).
        if navigationAction.targetFrame?.isMainFrame == false {
            decisionHandler(.allow)
        } else if !pageLoaded, navigationAction.navigationType == .other {
            decisionHandler(.allow)
        } else {
            log.info("Navigation gesperrt: \(navigationAction.request.url?.absoluteString ?? "-", privacy: .public)")
            decisionHandler(.cancel)
        }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        log.error("Navigation fehlgeschlagen: \(error.localizedDescription, privacy: .public)")
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        log.error("Laden fehlgeschlagen: \(error.localizedDescription, privacy: .public)")
        #if DEBUG
        Self.diagnose("Laden fehlgeschlagen: \(error.localizedDescription)")
        #endif
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        log.info("Player-Seite geladen")
        #if DEBUG
        Self.diagnose("Seite geladen")
        #endif
        pageLoaded = true
        if let pendingVideoId { load(videoId: pendingVideoId) }
    }

   /// target="_blank" (z. B. Titel-Link im Player) → nichts öffnen.
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        nil
    }
}

/// Verhindert den Retain-Zyklus WKUserContentController → Handler → Bridge.
private final class WeakMessageHandler: NSObject, WKScriptMessageHandler {
    weak var target: YouTubePlayerBridge?
    init(target: YouTubePlayerBridge) { self.target = target }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.receive(message.body)
    }
}
