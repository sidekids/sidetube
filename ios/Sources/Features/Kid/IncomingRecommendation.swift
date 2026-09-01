import SwiftData
import SwiftUI

/// Eingehender Empfehlungslink `sidetube://add?v=<videoId>`.
enum IncomingLink {
    static let scheme = "sidetube"

    static func url(videoId: String) -> URL {
        URL(string: "\(scheme)://add?v=\(videoId)")!
    }

   /// Liefert die Video-ID, wenn die URL eine gültige Empfehlung ist.
    static func videoId(from url: URL) -> String? {
        guard url.scheme?.lowercased() == scheme, url.host?.lowercased() == "add",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              let value = components.queryItems?.first(where: { $0.name == "v" })?.value,
              !value.isEmpty, value.allSatisfy({ $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" }) else { return nil }
        return value
    }
}

/// Empfehlung annehmen: Vorschau, dann Eltern-PIN, dann in die Whitelist (Videos) des gewählten Profils.
struct IncomingRecommendationView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(AppServices.self) private var services
    @Environment(\.dismiss) private var dismiss
   /// Profile direkt aus dem Kontext (eine @Query liefert im Sheet der Wurzel keine Daten).
    @State private var profiles: [KidProfile] = []
    let videoId: String

    private enum Phase: Equatable { case loading, preview(WhitelistItemDraft), failed(String), pin(WhitelistItemDraft), done(String) }
    @State private var phase: Phase = .loading
    @State private var selectedProfileId: UUID?
    @State private var showLinkGate = false

    var body: some View {
        NavigationStack {
            Form {
                switch phase {
                case .loading:
                    HStack { ProgressView(); Text("Video wird geprüft …").foregroundStyle(.secondary) }
                case .failed(let message):
                    Label(message, systemImage: "exclamationmark.triangle").foregroundStyle(.red)
                case .preview(let draft), .pin(let draft):
                    Section("Empfohlenes Video") {
                        HStack(spacing: 12) {
                            Thumbnail(url: draft.thumbnailUrl)
                            VStack(alignment: .leading) {
                                Text(draft.title).font(.headline).lineLimit(3)
                                if let channel = draft.channelTitle { Text(channel).font(.caption).foregroundStyle(.secondary) }
                            }
                        }
                        Button("Original auf YouTube (Eltern-PIN)", systemImage: "arrow.up.right.square") { showLinkGate = true }.font(.footnote)
                    }
                    if profiles.count > 1 {
                        Section("Für wen?") {
                            Picker("Profil", selection: $selectedProfileId) {
                                ForEach(profiles) { Text($0.name).tag(Optional($0.id)) }
                            }
                        }
                    }
                    Section {
                        Button("Zur Prüfung übernehmen (Eltern-PIN)", systemImage: "lock.open") { phase = .pin(draft) }
                            .buttonStyle(.borderedProminent)
                            .disabled(profiles.isEmpty)
                    } footer: {
                        Text(profiles.isEmpty ? "Erst im Elternbereich ein Profil anlegen." : "Nur Eltern geben Inhalte frei – deshalb die PIN.")
                    }
                case .done(let message):
                    Label(message, systemImage: "checkmark.circle.fill").foregroundStyle(.green)
                }
            }
            .navigationTitle("Empfehlung")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig") { dismiss() } } }
            .task { await load() }
            .sheet(isPresented: $showLinkGate) {
                PINEntryView {
                    showLinkGate = false
                    UIApplication.shared.open(RecommendLink.url(videoId: videoId))   // Kids-Kategorie: Verlassen der App nur mit Elternschranke
                }
            }
            .sheet(isPresented: Binding(get: { if case .pin = phase { true } else { false } },
                                        set: { if !$0, case .pin(let draft) = phase { phase = .preview(draft) } })) {
                if case .pin(let draft) = phase {
                    PINEntryView { add(draft) }
                }
            }
        }
    }

    private func load() async {
        profiles = (try? ProfileRepository(context: modelContext).all()) ?? []
        selectedProfileId = profiles.first?.id
        do {
            let video = try await services.youtube.video(id: videoId)
            phase = .preview(WhitelistItemDraft(type: .video, youtubeId: video.id, title: video.title,
                                                thumbnailUrl: video.thumbnailUrl, channelTitle: video.channelTitle))
        } catch {
            phase = .failed(AddWhitelistItemView.message(for: error))
        }
    }

    private func add(_ draft: WhitelistItemDraft) {
        guard let profile = profiles.first(where: { $0.id == selectedProfileId }) ?? profiles.first else { return }
        do {
            _ = try CurationRepository(context: modelContext).discover(draft, for: profile, actor: "Empfehlung")
            phase = .done("„\(draft.title)“ liegt jetzt bei \(profile.name) unter „Prüfen“ – nach der Freigabe erscheint es unter Videos.")
        } catch CurationRepository.DiscoverError.duplicate {
            phase = .done("„\(draft.title)“ war bei \(profile.name) schon freigegeben.")
        } catch CurationRepository.DiscoverError.blockedSource {
            phase = .failed("Diese Quelle ist für Kinder gesperrt.")
        } catch {
            phase = .failed("Konnte nicht speichern: \(error.localizedDescription)")
        }
    }
}
