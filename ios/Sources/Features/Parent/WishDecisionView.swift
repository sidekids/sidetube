// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftData
import SwiftUI

/// Zeile eines Wunsches in der Prüfliste: Herkunft, Inhalt, Stand.
struct WishRow: View {
    let wish: KidWish

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if wish.kind == .thema {
                Image(systemName: "star.bubble").font(.title2).foregroundStyle(Color.accentColor)
                    .frame(width: 80, height: 48)
            } else {
                Thumbnail(url: wish.thumbnailUrl ?? "")
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(wish.headline).font(.subheadline.weight(.semibold)).lineLimit(2)
                Text([wish.kind.origin, wish.channelTitle, wish.createdAt.formatted(.relative(presentation: .named))]
                    .compactMap { $0 }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(.secondary)
                if wish.status != .offen {
                    Text(wish.status.parentTitle).font(.caption2.weight(.semibold)).padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Capsule().fill(Color.orange.opacity(0.15))).foregroundStyle(.orange)
                }
                if let reply = wish.parentReply { Text("Antwort: \(reply)").font(.caption2).foregroundStyle(.secondary) }
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}

/// Entscheidung über einen Wunsch – Aktionen je Herkunft wie in ADR 0001, Antwort an das Kind freiwillig.
struct WishDecisionView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    let wish: KidWish
    let profile: KidProfile
    @State private var reply: String
    @State private var showAddLink = false
    @State private var error: String?
    @State private var channelRevision = 0
    @State private var addedYoutubeId: String?
    @State private var kanalEinstufen: KanalKandidat?

    init(wish: KidWish, profile: KidProfile) {
        self.wish = wish
        self.profile = profile
        _reply = State(initialValue: wish.parentReply ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    WishRow(wish: wish)
                    if let videoId = wish.videoId, let url = URL(string: "https://www.youtube.com/watch?v=\(videoId)") {
                        Link(wish.kind == .neueFolge ? "Video im Original ansehen (Eltern)" : "Anlass im Original ansehen (Eltern)",
                             destination: url).font(.footnote)
                    }
                } header: { Text(wish.kind.origin) } footer: { Text(footer) }

                if wish.kind == .mehrDavon, wish.channelId != nil { channelSection }

                Section("Antwort an \(profile.name) (freiwillig)") {
                    TextField("z. B. „Schauen wir am Wochenende zusammen.“", text: $reply, axis: .vertical)
                        .accessibilityIdentifier("wish.reply.field")
                }

                Section { actions }

                Section("Verlauf") {
                    let events = WishRepository(context: modelContext).history(of: wish)
                    if events.isEmpty { Text("Noch keine Einträge").foregroundStyle(.secondary) }
                    ForEach(Array(events.enumerated()), id: \.offset) { _, event in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(event.headline).font(.caption.weight(.semibold))
                            Text(event.at.formatted(date: .abbreviated, time: .shortened)).font(.caption2).foregroundStyle(.secondary)
                            if let note = event.note { Text(note).font(.caption2).foregroundStyle(.secondary) }
                        }
                    }
                }
                if let error { Text(error).foregroundStyle(.red) }
            }
            .navigationTitle("Wunsch")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Schließen") { dismiss() } } }
            // Erst nach dem Schließen der Link-Maske entscheiden – zwei Blätter gleichzeitig zu schließen ist unzuverlässig.
            .sheet(isPresented: $showAddLink, onDismiss: {
                if let added = addedYoutubeId { addedYoutubeId = nil; decide(.erfuellt, fulfilled: added) }
            }) {
                AddWhitelistItemView(profile: profile) { youtubeId in addedYoutubeId = youtubeId }
            }
            // Der Wunsch bleibt offen: ob er damit erfüllt ist, entscheiden die Eltern danach selbst („Erledigt").
            .sheet(item: $kanalEinstufen, onDismiss: { channelRevision += 1 }) { kandidat in
                KanalEinstufenSheet(kandidat: kandidat, profile: profile)
            }
        }
    }

    private var footer: String {
        switch wish.kind {
        case .thema: "Das Kind hat nur ein Stichwort geschickt und dabei nichts Fremdes gesehen."
        case .mehrDavon: "Das Kind möchte mehr wie dieses freigegebene Video."
        case .neueFolge: "Neue Folge eines Kanals mit Stufe „Vertrauenswürdige Reihe“. Das Kind hat Bild und Titel gesehen, nicht das Video."
        }
    }

    @ViewBuilder
    private var actions: some View {
        switch wish.kind {
        case .neueFolge:
            Button("Freigeben", systemImage: "checkmark.circle.fill", action: approveEpisode)
                .buttonStyle(.borderedProminent).accessibilityIdentifier("wish.approve")
            rejectButton
            discussButton
        case .mehrDavon:
            Button("Video-Link hinzufügen", systemImage: "link.badge.plus") { showAddLink = true }
                .accessibilityIdentifier("wish.addLink")
            doneButton
            rejectButton
            discussButton
        case .thema:
            Button("Link hinzufügen", systemImage: "link.badge.plus") { showAddLink = true }
                .buttonStyle(.borderedProminent).accessibilityIdentifier("wish.addLink")
            doneButton
            rejectButton
            discussButton
        }
    }

    private var doneButton: some View {
        Button("Erledigt", systemImage: "checkmark") { decide(.erfuellt) }.accessibilityIdentifier("wish.done")
    }
    private var rejectButton: some View {
        Button("Ablehnen („nicht jetzt“)", systemImage: "hand.raised", role: .destructive) { decide(.abgelehnt) }
            .accessibilityIdentifier("wish.reject")
    }
    private var discussButton: some View {
        Button("Besprechen", systemImage: "bubble.left.and.bubble.right") { decide(.besprechen) }
            .accessibilityIdentifier("wish.discuss")
    }

    /// „Kanal prüfen": neu aufnehmen (mit Stufe, Alter, Kategorie – ADR 0003) oder die Stufe ändern.
    @ViewBuilder
    private var channelSection: some View {
        let curation = CurationRepository(context: modelContext)
        let source = curation.effectiveSource(channelId: wish.channelId)
        let inWhitelist = profile.whitelistItems.contains { $0.youtubeId == wish.channelId }
        Section {
            if let source {
                Picker("Stufe", selection: Binding(get: { source.trust }, set: { setTrust($0, for: source) })) {
                    ForEach(SourceTrust.allCases) { Text($0.title).tag($0) }
                }
            } else {
                Text("Quelle noch nicht eingestuft.").foregroundStyle(.secondary)
            }
            if inWhitelist {
                Label("Kanal steht in der Whitelist von \(profile.name).", systemImage: "checkmark.seal").font(.footnote)
            } else {
                Button("Kanal einstufen und aufnehmen", systemImage: "person.crop.rectangle.badge.plus") {
                    kanalEinstufen = WishRepository(context: modelContext).kanalEntwurf(of: wish).map(KanalKandidat.init(draft:))
                }
                .accessibilityIdentifier("wish.proposeChannel")
            }
        } header: {
            Text("Kanal prüfen: \(wish.channelTitle ?? "")")
        } footer: {
            Text("Beim Aufnehmen entscheidet ihr Stufe, Alter und Kategorie; „Gesperrt“ nimmt den Kanal nicht auf. „Vertrauenswürdige Reihe“ zeigt neue Folgen gesperrt zum Wünschen.")
        }
        .id(channelRevision)
    }

    private func approveEpisode() {
        do {
            try WishRepository(context: modelContext).approveEpisode(wish, in: profile, reply: reply)
            dismiss()
        } catch WishRepository.WishError.notApprovable {
            error = "Der Filter hat eindeutig nicht kindgerechte Begriffe im Titel erkannt – keine Freigabe möglich."
        } catch WishRepository.WishError.blockedSource {
            error = "Diese Quelle ist für Kinder gesperrt."
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func decide(_ status: WishStatus, fulfilled: String? = nil) {
        do {
            try WishRepository(context: modelContext).decide(wish, status: status, reply: reply, fulfilledYoutubeId: fulfilled)
            dismiss()
        } catch { self.error = "Das ging nicht: Der Wunsch ist schon entschieden." }
    }

    private func setTrust(_ trust: SourceTrust, for source: CuratedSource) {
        do {
            try CurationRepository(context: modelContext).setTrust(trust, for: source, actor: "Eltern")
            channelRevision += 1
        } catch { self.error = error.localizedDescription }
    }
}
