// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import WebKit

/// PeerTube-Wiedergabe: die Embed-Seite der Instanz in einer WKWebView, ohne P2P, ohne Instanz-Link, ohne Warnhinweis.
/// Steuerung über die PeerTube-Embed-API (postMessage), Navigation aus dem Player ist gesperrt.
final class PeerTubePlayerBridge: NSObject, PlayerEngine {
    let webView: WKWebView
    var onEvent: ((PlayerEngineEvent) -> Void)?
    private var currentVideoId: String?

    override init() {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.allowsPictureInPictureMediaPlayback = false
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init()
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.scrollView.isScrollEnabled = false
        webView.allowsBackForwardNavigationGestures = false
        webView.allowsLinkPreview = false
        webView.isOpaque = false
        webView.backgroundColor = .black
    }

   // MARK: PlayerEngine

    func load(videoId: String) {
        currentVideoId = videoId
        guard let url = PeerTubeIDs.embedURL(videoId: videoId) else {
            onEvent?(.error(100))
            return
        }
        webView.load(URLRequest(url: url))
    }

    func play() { post("play") }
    func pause() { post("pause") }
    func togglePlayback() {
        webView.evaluateJavaScript("document.querySelector('video')?.paused") { [weak self] result, _ in
            let paused = (result as? Bool) ?? true
            paused ? self?.play() : self?.pause()
        }
    }
    func seek(by seconds: Double) {
        webView.evaluateJavaScript("(function(){var v=document.querySelector('video'); if(v){v.currentTime=Math.max(0,v.currentTime+\(seconds));} })()") { _, _ in }
    }
    func setVolume(_ percent: Int) {
        let value = min(100, max(0, percent))
        webView.evaluateJavaScript("(function(){var v=document.querySelector('video'); if(v){v.volume=\(Double(value) / 100.0);} })()") { _, _ in }
    }
    func stop() {
        webView.evaluateJavaScript("(function(){var v=document.querySelector('video'); if(v){v.pause(); v.currentTime=0;} })()") { _, _ in }
    }

    private func post(_ command: String) {
        webView.evaluateJavaScript("(function(){var v=document.querySelector('video'); if(v){v.\(command)();} })()") { _, _ in }
    }

   /// Zustand periodisch abfragen – die Embed-API meldet nicht über `webkit.messageHandlers`.
    private func startPolling() {
        Task { @MainActor [weak self] in
            var lastState = -2
            while let self, self.currentVideoId != nil {
                let script = "(function(){var v=document.querySelector('video'); if(!v){return -1;} if(v.ended){return 0;} return v.paused ? 2 : 1;})()"
                let state = await withCheckedContinuation { continuation in
                    self.webView.evaluateJavaScript(script) { result, _ in continuation.resume(returning: (result as? Int) ?? -1) }
                }
                if state != lastState { lastState = state; self.onEvent?(.state(state)) }
                let time = await withCheckedContinuation { continuation in
                    self.webView.evaluateJavaScript("Math.floor(document.querySelector('video')?.currentTime || 0)") { result, _ in
                        continuation.resume(returning: (result as? Int) ?? 0)
                    }
                }
                self.onEvent?(.time(time))
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }

    fileprivate func pageLoaded() {
        onEvent?(.ready)
        startPolling()
    }
}

extension PeerTubePlayerBridge: WKNavigationDelegate, WKUIDelegate {
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                 decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
   // Nur die Embed-Seite selbst und ihre Unterframes; kein Wechsel auf die Instanz-Website.
        let isEmbed = navigationAction.request.url?.path.contains("/videos/embed/") ?? false
        decisionHandler(isEmbed || navigationAction.targetFrame?.isMainFrame == false ? .allow : .cancel)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { pageLoaded() }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        onEvent?(.apiFailed)
    }

    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                 for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? { nil }
}
