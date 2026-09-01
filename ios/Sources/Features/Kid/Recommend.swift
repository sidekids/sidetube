import MessageUI
import SwiftUI

/// Empfehlung eines Videos: immer der Original-Link auf YouTube (Kanalinhalt bleibt die Quelle).
enum RecommendLink {
    static func url(videoId: String) -> URL {
        PeerTubeIDs.watchURL(videoId: videoId) ?? URL(string: "https://www.youtube.com/watch?v=\(videoId)")!
    }

   /// Text: Original-Link fuer alle, darunter der sidetube-Link (tippbar nur mit installierter App).
    static func message(title: String, videoId: String) -> String {
        PeerTubeIDs.isPeerTube(videoId)
            ? "Schau mal: \(title)\n\(url(videoId: videoId).absoluteString)"
            : "Schau mal: \(title)\n\(url(videoId: videoId).absoluteString)\nMit SideTube öffnen: \(IncomingLink.url(videoId: videoId).absoluteString)"
    }

   /// Signal hat keine Compose-URL: Text in die Zwischenablage, App öffnen, dort einfügen.
    static let signalScheme = URL(string: "sgnl://")!
}

/// Kids-Kategorie (Apple 1.3/5.1.4): Aktionen, die die App verlassen, brauchen im Kinderkontext eine Elternschranke.
struct ParentalGateRequiredKey: EnvironmentKey { static let defaultValue = true }
extension EnvironmentValues {
    var parentalGateRequired: Bool {
        get { self[ParentalGateRequiredKey.self] }
        set { self[ParentalGateRequiredKey.self] = newValue }
    }
}

/// Menü „Empfehlen": iMessage/Nachrichten, Signal, Link kopieren. Bewusst kein System-Teilen-Blatt (WhatsApp nie).
/// Im Kinderkontext steht vor jeder Aktion die Eltern-PIN (Parental Gate); im Elternbereich nicht.
struct RecommendMenu: View {
    let title: String
    let videoId: String
    @Environment(\.parentalGateRequired) private var gateRequired
    @State private var showMessageComposer = false
    @State private var notice: String?
    @State private var pendingAction: (() -> Void)?

    var body: some View {
        Menu {
            Button("Per Nachricht (iMessage)", systemImage: "message") { gated { recommendViaMessages() } }
            Button("Per Signal", systemImage: "paperplane") { gated { recommendViaSignal() } }
            Button("Link kopieren", systemImage: "doc.on.doc") { gated { copyLink() } }
        } label: {
            Label("Empfehlen", systemImage: "square.and.arrow.up")
        }
        .accessibilityIdentifier("recommend.menu")
        .sheet(isPresented: Binding(get: { pendingAction != nil }, set: { if !$0 { pendingAction = nil } })) {
            PINEntryView {
                let action = pendingAction
                pendingAction = nil
                action?()
            }
        }
        .sheet(isPresented: $showMessageComposer) {
            MessageComposeView(body: RecommendLink.message(title: title, videoId: videoId))
                .ignoresSafeArea()
        }
        .alert("Empfehlen", isPresented: Binding(get: { notice != nil }, set: { if !$0 { notice = nil } })) {
            Button("OK", role: .cancel) {}
        } message: { Text(notice ?? "") }
    }

   /// Elternschranke: im Kinderkontext erst die PIN, dann die Aktion.
    private func gated(_ action: @escaping () -> Void) {
        if gateRequired { pendingAction = action } else { action() }
    }

    private func recommendViaMessages() {
        if MFMessageComposeViewController.canSendText() {
            showMessageComposer = true
        } else if let url = URL(string: "sms:&body=\(RecommendLink.message(title: title, videoId: videoId).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"),
                  UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        } else {
            copyLink()
            notice = "Nachrichten ist auf diesem Gerät nicht verfügbar. Der Link wurde kopiert."
        }
    }

    private func recommendViaSignal() {
        UIPasteboard.general.string = RecommendLink.message(title: title, videoId: videoId)
        if UIApplication.shared.canOpenURL(RecommendLink.signalScheme) {
            UIApplication.shared.open(RecommendLink.signalScheme)
            notice = "Link kopiert – in Signal den Chat wählen und einfügen."
        } else {
            notice = "Signal ist nicht installiert. Der Link wurde kopiert."
        }
    }

    private func copyLink() {
        UIPasteboard.general.string = RecommendLink.url(videoId: videoId).absoluteString
        notice = "Link kopiert: \(RecommendLink.url(videoId: videoId).absoluteString)"
    }
}

/// Nachrichten-Composer (iMessage/SMS) mit vorausgefülltem Text.
struct MessageComposeView: UIViewControllerRepresentable {
    let body: String
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> MFMessageComposeViewController {
        let controller = MFMessageComposeViewController()
        controller.body = body
        controller.messageComposeDelegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: MFMessageComposeViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(dismiss: { dismiss() }) }

    final class Coordinator: NSObject, MFMessageComposeViewControllerDelegate {
        let dismiss: () -> Void
        init(dismiss: @escaping () -> Void) { self.dismiss = dismiss }
        func messageComposeViewController(_ controller: MFMessageComposeViewController, didFinishWith result: MessageComposeResult) {
            dismiss()
        }
    }
}
