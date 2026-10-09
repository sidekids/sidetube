// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Observation
import SwiftData
import SwiftUI

// Wünsche von Kindern im Kindermodus (ADR 0001): Themenwunsch in der Suche, „Mehr davon" im Player,
// „Neu bei deinen Kanälen" auf der Startseite und „Meine Wünsche". Keine Benachrichtigungen, kein Ton.

/// Kindgerechte Rückmeldung nach einem Wunsch.
enum WishFeedback {
    static func message(for result: Result<WishRepository.SubmitResult, Error>, remaining: Int) -> String {
        switch result {
        case .success(.created):
            return String(localized: "Dein Wunsch ist bei deinen Eltern. ") + remainingText(remaining)
        case .success(.duplicate):
            return String(localized: "Das hast du dir schon gewünscht. Deine Eltern schauen es sich an.")
        case .failure(WishRepository.WishError.dailyLimitReached):
            return String(localized: "Für heute sind alle \(WishRepository.dailyLimit) Wünsche verbraucht. Morgen geht es weiter.")
        case .failure(WishRepository.WishError.emptyTopic):
            return String(localized: "Schreib erst, was du dir wünschst.")
        case .failure(WishRepository.WishError.blockedSource):
            return String(localized: "Das geht leider nicht.")
        case .failure:
            return String(localized: "Das hat nicht geklappt. Sag deinen Eltern Bescheid.")
        }
    }

    static func remainingText(_ remaining: Int) -> String {
        switch remaining {
        case 0: String(localized: "Heute geht kein Wunsch mehr.")
        case 1: String(localized: "Heute geht noch 1 Wunsch.")
        default: String(localized: "Heute gehen noch \(remaining) Wünsche.")
        }
    }

    /// Wunsch abschicken und die passende Meldung liefern.
    static func submit(_ draft: WishDraft, profile: KidProfile, context: ModelContext, notifier: ParentNotifier) -> String {
        let repo = WishRepository(context: context)
        let result = Result { try repo.submit(draft, for: profile) }
        if case .success(let submitted) = result {
            _ = WishRepository.notifyParents(after: submitted, openCount: repo.openWishCount(), notifier: notifier)
        }
        return message(for: result, remaining: repo.remainingToday(for: profile.id))
    }
}

// MARK: Weg 1 – Themenwunsch aus der Suche

/// „Wunsch an die Eltern: ‚Dinos‘" – das Kind sieht dabei keine fremden Inhalte.
struct WishTopicCard: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(AppServices.self) private var services
    let topic: String
    let profile: KidProfile
    /// Steht ganz oben, wenn die Suche nichts gefunden hat.
    var nothingFound: Bool
    @State private var feedback: String?
    @State private var remaining = WishRepository.dailyLimit

    private var trimmed: String { topic.trimmingCharacters(in: .whitespacesAndNewlines) }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(nothingFound ? String(localized: "Nichts dabei? Wünsch es dir!") : String(localized: "Nicht das Richtige dabei?"), systemImage: "star.bubble")
                .font(.headline)
            Text("Wunsch an die Eltern: „\(String(trimmed.prefix(WishDraft.maxTopicLength)))“")
                .font(.body)
                .accessibilityIdentifier("wish.topic.text")
            if let feedback {
                Text(feedback).font(.subheadline).foregroundStyle(.secondary)
                    .accessibilityIdentifier("wish.feedback")
            } else {
                HStack {
                    Button("Wünschen", systemImage: "paperplane.fill", action: submit)
                        .buttonStyle(.borderedProminent)
                        .tint(KidTheme.accent)
                        .foregroundStyle(.black)
                        .disabled(remaining == 0)
                        .accessibilityIdentifier("wish.topic.submit")
                    Text(WishFeedback.remainingText(remaining)).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color(.secondarySystemGroupedBackground)))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("wish.topic")
        .onAppear { remaining = WishRepository(context: modelContext).remainingToday(for: profile.id) }
        .onChange(of: topic) { _, _ in feedback = nil }
    }

    private func submit() {
        feedback = WishFeedback.submit(.thema(trimmed), profile: profile, context: modelContext, notifier: services.parentNotifier)
        remaining = WishRepository(context: modelContext).remainingToday(for: profile.id)
    }
}

// MARK: Weg 2 – „Mehr davon"

enum MoreLikeThis {
    /// Wunsch zum laufenden Video; Kanal aus dem Player oder dem Whitelist-Eintrag.
    static func draft(for item: PlayerModel.Item, profile: KidProfile) -> WishDraft {
        let approved = profile.whitelistItems.first { $0.youtubeId == item.videoId && $0.type == .video }
        return WishDraft(kind: .mehrDavon, videoId: item.videoId, videoTitle: item.title,
                         thumbnailUrl: item.thumbnailURL ?? approved?.thumbnailUrl ?? YouTubeIDs.defaultThumbnail(videoId: item.videoId),
                         channelId: item.sourceChannelId ?? approved?.sourceChannelId,
                         channelTitle: item.channelTitle ?? approved?.channelTitle)
    }
}

// MARK: Weg 3 – „Neu bei deinen Kanälen"

/// Eine gesperrte neue Folge: Bild und Titel, nicht abspielbar.
struct NewEpisode: Identifiable, Equatable {
    var entry: FeedEntry
    var channelId: String
    var channelTitle: String
    var id: String { entry.video.videoId }
}

@Observable
final class NewEpisodesModel {
    private(set) var episodes: [NewEpisode] = []
    private(set) var wishedIds: Set<String> = []
    private(set) var remaining = WishRepository.dailyLimit
    private(set) var loaded = false
    let profile: KidProfile
    private let context: ModelContext
    private let youtube: YouTubeRepository
    private let notifier: ParentNotifier

    init(profile: KidProfile, context: ModelContext, youtube: YouTubeRepository, notifier: ParentNotifier = NoParentNotifier()) {
        self.profile = profile
        self.context = context
        self.youtube = youtube
        self.notifier = notifier
    }

    /// Kanäle des Profils mit Stufe „Vertrauenswürdige Reihe"; Netz nur für deren Feeds.
    func load() async {
        let curation = CurationRepository(context: context)
        let channels = WhitelistRepository(context: context).visibleItems(of: profile, type: .channel)
            .compactMap { item -> (String, String, CuratedSource)? in
                guard let source = curation.effectiveSource(channelId: item.youtubeId),
                      NewEpisodesPolicy.channelQualifies(source, profile: profile) else { return nil }
                return (item.youtubeId, item.title, source)
            }
        var result: [NewEpisode] = []
        for (channelId, title, source) in channels {
            guard let entries = try? await youtube.channelFeed(channelId: channelId) else { continue }
            let excluded = WishRepository(context: context).excludedEpisodeIds(for: profile)
            let allowed = NewEpisodesPolicy.filter(entries, channel: source, profile: profile, excludedVideoIds: excluded,
                                                   videoSource: { curation.effectiveSource(channelId: $0) })
            result += allowed.map { NewEpisode(entry: $0, channelId: channelId, channelTitle: title) }
        }
        episodes = result.sorted { ($0.entry.publishedAt ?? .distantPast) > ($1.entry.publishedAt ?? .distantPast) }
        refreshWishes()
        loaded = true
    }

    func refreshWishes() {
        let repo = WishRepository(context: context)
        wishedIds = Set(repo.pendingVideoWishes(of: profile.id).keys)
        remaining = repo.remainingToday(for: profile.id)
    }

    func wish(_ episode: NewEpisode) -> String {
        let video = episode.entry.video
        let message = WishFeedback.submit(
            WishDraft(kind: .neueFolge, videoId: video.videoId, videoTitle: video.title, thumbnailUrl: video.thumbnailUrl,
                      channelId: episode.channelId, channelTitle: episode.channelTitle),
            profile: profile, context: context, notifier: notifier)
        refreshWishes()
        return message
    }
}

/// Abschnitt auf der Startseite. Erscheint nur, wenn es neue Folgen gibt.
struct NewEpisodesSection: View {
    let model: NewEpisodesModel
    @State private var feedback: String?

    var body: some View {
        if !model.episodes.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Neu bei deinen Kanälen").font(.title3.weight(.semibold)).accessibilityAddTraits(.isHeader)
                    Text("Noch nicht freigegeben. Wünsch dir, was du sehen möchtest. " + WishFeedback.remainingText(model.remaining))
                        .font(.subheadline).foregroundStyle(.secondary)
                        .accessibilityIdentifier("newEpisodes.remaining")
                }
                .padding(.horizontal, KidTheme.outerPadding)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: KidTheme.cardSpacing) {
                        ForEach(model.episodes) { episode in
                            LockedEpisodeTile(episode: episode, wished: model.wishedIds.contains(episode.id),
                                              canWish: model.remaining > 0) {
                                feedback = model.wish(episode)
                            }
                            .frame(width: 190)
                        }
                    }
                    .padding(.horizontal, KidTheme.outerPadding - 6)
                }
                .accessibilityIdentifier("newEpisodes.list")
            }
            .alert("Wunsch", isPresented: Binding(get: { feedback != nil }, set: { if !$0 { feedback = nil } })) {
                Button("OK", role: .cancel) {}
            } message: { Text(feedback ?? "") }
        }
    }
}

/// Gesperrte Kachel: Bild mit Schloss, Titel, Kanal, „Wünschen".
struct LockedEpisodeTile: View {
    let episode: NewEpisode
    let wished: Bool
    let canWish: Bool
    let onWish: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            GeometryReader { proxy in
                KidThumbnail(url: episode.entry.video.thumbnailUrl, size: CGSize(width: proxy.size.width, height: proxy.size.width * 9 / 16))
                    .saturation(0.6)
                    .overlay(alignment: .topTrailing) {
                        Image(systemName: "lock.fill").font(.caption.weight(.bold)).foregroundStyle(.white)
                            .padding(6).background(Circle().fill(.black.opacity(0.6))).padding(6)
                    }
            }
            .aspectRatio(16 / 9, contentMode: .fit)
            Text(episode.entry.video.title).font(.subheadline).lineLimit(2).multilineTextAlignment(.leading)
            Text(episode.channelTitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            if wished {
                Label("Gewünscht", systemImage: "checkmark").font(.subheadline.weight(.semibold)).foregroundStyle(KidTheme.accent)
                    .frame(minHeight: KidTheme.minimumTouchTarget)
                    .accessibilityIdentifier("wish.newEpisode.done.\(episode.id)")
            } else {
                Button("Wünschen", systemImage: "star", action: onWish)
                    .buttonStyle(.bordered)
                    .tint(KidTheme.accent)
                    .disabled(!canWish)
                    .frame(minHeight: KidTheme.minimumTouchTarget)
                    .accessibilityLabel("Wünschen: \(episode.entry.video.title)")
                    .accessibilityIdentifier("wish.newEpisode.\(episode.id)")
            }
        }
        .padding(6)
        .accessibilityElement(children: .contain)
    }
}

// MARK: „Meine Wünsche"

struct MyWishesButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) { Image(systemName: "star.bubble") }
            .accessibilityLabel("Meine Wünsche")
            .accessibilityIdentifier("wishes.open")
    }
}

/// Stand der eigenen Wünsche: wartet · freigegeben (mit Weg zum Inhalt) · „nicht jetzt" (mit Antwort) · „sprechen wir drüber".
struct MyWishesView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    let profile: KidProfile
    let context: KidContext
    /// Merkt freigegebene Inhalte zum Öffnen vor; der Aufrufer öffnet sie, sobald das Blatt geschlossen ist.
    let onOpen: (KidRow) -> Void
    @State private var wishes: [KidWish] = []
    @State private var remaining = WishRepository.dailyLimit

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(WishFeedback.remainingText(remaining)).foregroundStyle(.secondary)
                        .accessibilityIdentifier("wishes.remaining")
                }
                if wishes.isEmpty {
                    KidEmptyState(systemImage: "star.bubble", title: String(localized: "Noch keine Wünsche"),
                                  message: String(localized: "In der Suche, am Ende eines Videos und bei neuen Folgen kannst du dir etwas wünschen."))
                        .listRowBackground(Color.clear)
                }
                ForEach(wishes, id: \.id) { wish in
                    row(wish)
                }
            }
            .navigationTitle("Meine Wünsche")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Fertig") { dismiss() }.accessibilityIdentifier("wishes.close")
                }
            }
            .onAppear(perform: load)
        }
        .preferredColorScheme(.dark)
        .tint(KidTheme.accent)
    }

    private func row(_ wish: KidWish) -> some View {
        let showsVideo = WishDisplay.showsForeignVideo(wish, context: modelContext)
        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 12) {
                if wish.kind != .thema, showsVideo {
                    KidThumbnail(url: wish.thumbnailUrl, size: CGSize(width: 80, height: 45))
                } else {
                    Image(systemName: wish.kind == .thema ? "star.bubble" : wish.kind.systemImage)
                        .font(.title2).foregroundStyle(KidTheme.accent)
                        .frame(width: 80, height: 45)
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(showsVideo ? wish.headline : WishDisplay.neutralHeadline(wish)).font(.body).lineLimit(2)
                        .accessibilityIdentifier("wish.headline")
                    Label(wish.status.kidTitle, systemImage: wish.status.systemImage)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(wish.status == .erfuellt ? .green : (wish.status == .offen ? .secondary : KidTheme.accent))
                        .accessibilityIdentifier("wish.status")
                }
            }
            if let reply = wish.parentReply {
                Text("Deine Eltern: „\(reply)“").font(.subheadline).foregroundStyle(.primary)
                    .accessibilityIdentifier("wish.reply")
            }
            if wish.status == .erfuellt, let target = contentRow(for: wish) {
                Button(target.action.isPlay ? String(localized: "Anschauen") : String(localized: "Öffnen"), systemImage: target.action.isPlay ? "play.fill" : "chevron.right") {
                    onOpen(target)
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
                .foregroundStyle(.black)
                .accessibilityIdentifier("wish.open")
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("wish.row")
    }

    /// Weg zum Inhalt – nur, wenn er im Kinderprofil heute sichtbar ist.
    private func contentRow(for wish: KidWish) -> KidRow? {
        guard let id = wish.fulfilledYoutubeId,
              let item = WhitelistRepository(context: modelContext).visibleItems(of: profile).first(where: { $0.youtubeId == id }) else { return nil }
        let row = KidRows.row(for: item, context: context)
        if case .none = row.action { return nil }
        return row
    }

    private func load() {
        let repo = WishRepository(context: modelContext)
        wishes = repo.wishes(of: profile.id)
        remaining = repo.remainingToday(for: profile.id)
    }
}

private extension KidRow.Action {
    var isPlay: Bool { if case .play = self { true } else { false } }
}

/// Was „Meine Wünsche" vom gewünschten Video zeigen darf. Bild und Titel eines fremden Videos nur, solange
/// die Eltern nicht abgelehnt haben und seine Quelle nicht gesperrt oder „nur für Eltern" ist – danach
/// bleiben nur Stand und Antwort. Das Thema ist das eigene Wort des Kindes und bleibt stehen.
enum WishDisplay {
    static func showsForeignVideo(_ wish: KidWish, context: ModelContext) -> Bool {
        guard wish.kind != .thema else { return true }
        if wish.status == .abgelehnt { return false }
        guard let channelId = wish.channelId,
              let source = CurationRepository(context: context).effectiveSource(channelId: channelId) else { return true }
        return source.trust != .blocked && source.trust != .parentOnly
    }

    static func neutralHeadline(_ wish: KidWish) -> String {
        switch wish.kind {
        case .thema: wish.headline
        case .mehrDavon: String(localized: "Mehr davon")
        case .neueFolge: String(localized: "Eine neue Folge")
        }
    }
}
