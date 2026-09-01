import SwiftData
import SwiftUI

/// Link einfügen → prüfen (oEmbed/API) → Vorschau → in die Whitelist übernehmen. FR-04.1, 04.4, 04.5
struct AddWhitelistItemView: View {
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Environment(AppServices.self) private var services
    let profile: KidProfile

    enum Phase: Equatable { case idle, loading, preview(WhitelistItemDraft), failed(String) }
    @State private var url = ""
    @State private var phase: Phase = .idle
    @State private var ageMin = 6
    @State private var category: ContentCategory?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack {
                        TextField("https://www.youtube.com/…", text: $url)
                            .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                            .onSubmit(check)
                        PasteButton(payloadType: String.self) { strings in
                            if let first = strings.first { url = first; check() }
                        }
                        .labelStyle(.iconOnly).buttonBorderShape(.capsule)
                    }
                    Button("Prüfen", action: check).disabled(url.trimmingCharacters(in: .whitespaces).isEmpty || phase == .loading)
                } header: { Text("YouTube-Link") } footer: {
                    Text("YouTube (Kanal, Video, Playlist) oder PeerTube – z. B. youtube.com/@name, youtu.be/…, framatube.org/w/…")
                }

                switch phase {
                case .idle: EmptyView()
                case .loading:
                    HStack { ProgressView(); Text("Wird geprüft …").foregroundStyle(.secondary) }
                case .failed(let message):
                    Label(message, systemImage: "exclamationmark.triangle").foregroundStyle(.red)
                case .preview(let draft):
                    Section("Vorschau") {
                        HStack(spacing: 12) {
                            Thumbnail(url: draft.thumbnailUrl, isChannel: draft.type == .channel)
                            VStack(alignment: .leading) {
                                Text(draft.title).font(.headline).lineLimit(3)
                                Text([draft.type.label, draft.channelTitle].compactMap { $0 }.joined(separator: " · "))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        let risk = RiskScreen.assess(title: draft.title)
                        if !risk.topics.isEmpty {
                            Label("Hinweis des Filters: " + risk.topics.map(\.title).sorted().joined(separator: ", "), systemImage: "exclamationmark.triangle")
                                .font(.caption).foregroundStyle(.orange)
                        }
                        if draft.type == .video {
                            Stepper("Ab \(ageMin) Jahren", value: $ageMin, in: 3...16)
                            Picker("Kategorie", selection: $category) {
                                Text("Keine").tag(ContentCategory?.none)
                                ForEach(ContentCategory.allCases) { Label($0.title, systemImage: $0.systemImage).tag(Optional($0)) }
                            }
                        }
                        Button(draft.type == .video ? "Jetzt freigeben" : "Zur Whitelist hinzufügen", systemImage: "checkmark.circle.fill") { add(draft, approve: true) }
                            .buttonStyle(.borderedProminent)
                            .disabled(risk.isHardBlocked)
                        if draft.type == .video {
                            Button("Erst zur Prüfung merken", systemImage: "clock") { add(draft, approve: false) }
                        }
                        if risk.isHardBlocked { Text("Der Filter hat eindeutig nicht kindgerechte Begriffe erkannt – keine Freigabe möglich.").font(.footnote).foregroundStyle(.red) }
                    }
                }
            }
            .navigationTitle("Link hinzufügen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Abbrechen") { dismiss() } } }
        }
    }

    private func check() {
        let input = url.trimmingCharacters(in: .whitespacesAndNewlines)
        if YouTubeURLParser.parse(input) == nil, let peertube = PeerTubeURLParser.parse(input) {
            checkPeerTube(peertube)
            return
        }
        guard let parsed = YouTubeURLParser.parse(input) else {
            phase = .failed("Das ist kein unterstützter Link. Möglich: YouTube (Kanal, Video, Playlist) oder PeerTube (Video, Kanal).")
            return
        }
   // Dubletten vor dem Netzaufruf erkennen (0 Quota) – bei @handle/c/name erst nach der Auflösung möglich.
        let knownId: String? = switch parsed {
        case .video(let id), .playlist(let id), .channel(let id): id
        case .channelHandle, .channelCustomName: nil
        }
        if let knownId, WhitelistRepository(context: context).contains(youtubeId: knownId, in: profile) {
            phase = .failed("Steht schon auf der Whitelist von \(profile.name).")
            return
        }
        phase = .loading
        Task {
            do {
                let draft = try await services.youtube.resolve(parsed)
                if WhitelistRepository(context: context).contains(youtubeId: draft.youtubeId, in: profile) {
                    phase = .failed("Steht schon auf der Whitelist von \(profile.name).")
                } else {
                    phase = .preview(draft)
                }
            } catch {
                phase = .failed(Self.message(for: error))
            }
        }
    }

   /// Kanäle/Playlists: Quelle wird angelegt (Stufe aus dem Register oder „Einzelprüfung"); Videos laufen durch den
   /// Freigabe-Workflow: Eltern geben sofort frei (mit Alter/Kategorie) oder merken zur Prüfung vor.
   /// PeerTube: Video/Kanal auflösen; Instanz muss als Quelle erlaubt sein.
    private func checkPeerTube(_ parsed: ParsedPeerTubeURL) {
        phase = .loading
        Task {
            do {
                let curation = CurationRepository(context: context)
                try curation.ensureSources(SourceRegistry.allDefinitions)
                guard PeerTubePolicy.instanceAllowed(for: draft(from: parsed), curation: curation) else {
                    phase = .failed("Diese PeerTube-Instanz ist nicht freigegeben. Eltern können sie unter „Quellen & Sicherheitsstufen“ ergänzen.")
                    return
                }
                let draft = try await services.resolver.resolve(parsed)
                if curation.effectiveSource(channelId: draft.sourceChannelId)?.trust == .blocked {
                    phase = .failed("Diese Quelle ist für Kinder gesperrt."); return
                }
                if WhitelistRepository(context: context).contains(youtubeId: draft.youtubeId, in: profile) {
                    phase = .failed("Steht schon auf der Whitelist von \(profile.name)."); return
                }
                if draft.isNSFW {
                    phase = .failed("Dieses Video ist auf der Instanz als nicht jugendfrei gekennzeichnet."); return
                }
                phase = .preview(draft)
            } catch {
                phase = .failed(Self.message(for: error))
            }
        }
    }

   /// Bezeichner der Instanz aus der geparsten Adresse (vor dem Netzaufruf prüfbar).
    private func draft(from parsed: ParsedPeerTubeURL) -> String {
        switch parsed {
        case .video(let host, _), .channel(let host, _): return PeerTubeIDs.instanceId(host: host)
        }
    }

    private func add(_ draft: WhitelistItemDraft, approve: Bool) {
        let curation = CurationRepository(context: context)
        do {
            try curation.ensureSources(SourceRegistry.allDefinitions)
            if draft.type == .channel {
                if curation.effectiveSource(channelId: draft.youtubeId)?.trust == .blocked {
                    phase = .failed("Diese Quelle ist für Kinder gesperrt (siehe Quellen & Sicherheitsstufen)."); return
                }
                if curation.effectiveSource(channelId: draft.youtubeId) == nil {
                    try curation.ensureSources([SourceDefinition(channelId: draft.youtubeId, handle: nil, title: draft.title, provider: draft.provider,
                                                                  trust: .perVideoReview,
                                                                  notes: "Von Eltern hinzugefügt – Standard: nur einzeln geprüfte Videos.")])
                }
                try WhitelistRepository(context: context).add(draft, to: profile)   // Kanal-Eintrag selbst ist Navigation, kein Video
                dismiss(); return
            }
            if draft.type == .playlist {
                try WhitelistRepository(context: context).add(draft, to: profile)
                dismiss(); return
            }
            let item = try curation.discover(draft, for: profile, actor: "Eltern")
            if approve, item.approvalStatus != .rejected {
                try curation.approve(item, with: .init(ageMin: max(ageMin, category?.minimumAge ?? 0), ageMax: nil, category: category,
                                                       newsStatus: category == .news ? .parentReview : nil, parentNotes: nil), actor: "Eltern")
            }
            dismiss()
        } catch CurationRepository.DiscoverError.duplicate, WhitelistRepository.AddError.duplicate {
            phase = .failed("Steht schon auf der Whitelist.")
        } catch CurationRepository.DiscoverError.blockedSource {
            phase = .failed("Diese Quelle ist für Kinder gesperrt.")
        } catch {
            phase = .failed("Konnte nicht speichern: \(error.localizedDescription)")
        }
    }

    static func message(for error: Error) -> String {
        switch error {
        case YouTubeError.invalidURL: "Das ist kein YouTube-Link."
        case YouTubeError.notFound: "Nichts gefunden – ist der Inhalt öffentlich und der Link vollständig?"
        case YouTubeError.missingAPIKey: "Dafür wird der YouTube-API-Schlüssel gebraucht (Playlists ohne oEmbed, Ausweichweg für Videos). Er fehlt in Config/Secrets.xcconfig."
        case YouTubeError.http(let status) where status == 403: "YouTube hat die Anfrage abgelehnt (403) – Tageskontingent aufgebraucht oder Schlüssel nicht für diese App freigegeben."
        case YouTubeError.http(let status): "YouTube antwortet mit Fehler \(status)."
        case YouTubeError.decoding: "Antwort von YouTube war unlesbar."
        case YouTubeError.network(let text): "Keine Verbindung: \(text)"
        default: error.localizedDescription
        }
    }
}
