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
                        Text("· übersprungen: \(last)").foregroundStyle(.orange).lineLimit(1)
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
        case .ended: "Zu Ende"
        case .allUnavailable: "Keines dieser Videos lässt sich abspielen."
        case .engineFailed: "Player konnte nicht geladen werden (Internet?)."
        }
    }
}

/// Querformat: nur das Video, ohne Rad und Statusleiste. Zurückdrehen beendet das Vollbild.
struct FullscreenPlayerView: View {
    let model: PlayerModel
    let webView: WKWebView?
    /// Sichtbarer Ausgang: Doppeltippen kommt an der WebView nicht immer an.
    var onExit: (() -> Void)?

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let webView {
                PlayerWebView(webView: webView).ignoresSafeArea()
            } else {
                Image(systemName: "play.slash").foregroundStyle(.white)
            }
            if model.status == .loading || model.status == .allUnavailable || model.status == .engineFailed {
                VStack {
                    Spacer()
                    Text(model.status == .loading ? "Lädt …" : "Nicht abspielbar")
                        .font(.footnote).foregroundStyle(.white.opacity(0.8)).padding(8)
                        .background(Capsule().fill(.black.opacity(0.5))).padding(.bottom, 12)
                }
            }
        }
        .overlay(alignment: .topLeading) {
            if let onExit {
                Button("Verkleinern", systemImage: "arrow.down.right.and.arrow.up.left") { onExit() }
                    .labelStyle(.iconOnly)
                    .font(.title3)
                    .foregroundStyle(.white)
                    .frame(width: 48, height: 48)
                    .background(Circle().fill(.black.opacity(0.45)))
                    .padding(12)
                    .accessibilityIdentifier("player.fullscreenExit")
            }
        }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("player.fullscreen")
    }
}
