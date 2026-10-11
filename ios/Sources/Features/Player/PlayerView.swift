// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftUI
import WebKit

struct PlayerWebView: UIViewRepresentable {
    let webView: WKWebView
    func makeUIView(context: Context) -> WKWebView { webView }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

/// Player im Hero-Bereich (iOS-2): Video, darunter eine kompakte Titel-/Statuszeile. Steuerung über das Rad.
struct PlayerView: View {
    let model: PlayerModel
    let webView: WKWebView?

    var body: some View {
        VStack(spacing: 4) {
            Group {
                if let webView {
                    PlayerWebView(webView: webView)
                } else {
                    Color.black.overlay(Image(systemName: "play.slash").foregroundStyle(.white))
                }
            }
            .aspectRatio(16 / 9, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .background(Color.black)

            VStack(alignment: .leading, spacing: 2) {
                Text(model.current.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                HStack(spacing: 6) {
                    Text(model.positionText)
                    Text("·")
                    Text(statusText)
                    if model.currentSeconds > 0 { Text("· \(model.currentSeconds / 60):\(String(format: "%02d", model.currentSeconds % 60))") }
                    if let last = model.recentlySkipped {
                        Text("· ging nicht: \(last)").foregroundStyle(.orange).lineLimit(1)
                    }
                }
                .font(.caption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
        }
        .padding(.bottom, 6)
        .background(Color(.systemBackground))
    }

    private var statusText: String {
        switch model.status {
        case .loading: "Lädt …"
        case .playing: "Spielt"
        case .paused: "Pause"
        case .ended: "Fertig"
        case .allUnavailable: String(localized: "Diese Videos gehen gerade nicht.")
        case .engineFailed: String(localized: "Das Video lädt nicht. Ist das Internet an?")
        case .storageFailed: String(localized: "Die Sehzeit konnte nicht gespeichert werden. Bitte die Eltern fragen.")
        }
    }
}

/// Querformat: nur das Video, ohne Rad und Statusleiste. Zurückdrehen beendet das Vollbild.
/// Videofläche im Vollbild – nur das Video, nichts darüber. Die YouTube API Services verbieten
/// sichtbare Elemente vor dem eingebetteten Player (Required Minimum Functionality); Steuerung,
/// Status und Verkleinern liegen deshalb neben bzw. unter der Fläche (`PlayerScreen`).
struct FullscreenPlayerView: View {
    let model: PlayerModel
    let webView: WKWebView?

    var body: some View {
        Group {
            if let webView {
                PlayerWebView(webView: webView)
            } else {
                Color.black.overlay(Image(systemName: "play.slash").foregroundStyle(.white))
            }
        }
        .aspectRatio(16 / 9, contentMode: .fit)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.fullscreen")
    }
}
