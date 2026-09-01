// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftUI

/// Gestaltungskonstanten des Kindermodus: Akzent gezielt, sonst Systemfarben und -materialien.
enum KidTheme {
    static let accent = BrandColor.yellow   // kanonisches Brand-Gelb #FBBB1B
    static let outerPadding: CGFloat = 16
    static let sectionSpacing: CGFloat = 28
    static let cardSpacing: CGFloat = 12
    static let channelAvatarSize: CGFloat = 76
    static let minimumTouchTarget: CGFloat = 44
}

/// Bild mit eigenem Seitenverhältnis (füllen + beschneiden), rund für Kanäle; stabiler Platzhalter gleicher Größe.
struct KidThumbnail: View {
    let url: String?
    var style: KidRow.ThumbnailStyle = .video
    var systemImage: String?
    var size: CGSize

    var body: some View {
        Group {
            if let url, let imageURL = URL(string: url) {
                AsyncImage(url: imageURL, transaction: Transaction(animation: .easeIn(duration: 0.15))) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: size.width, height: size.height)
        .clipShape(RoundedRectangle(cornerRadius: style == .avatar ? size.width / 2 : 10, style: .continuous))
        .accessibilityHidden(true)
    }

    private var placeholder: some View {
        Color(.tertiarySystemFill)
            .overlay(Image(systemName: systemImage ?? (style == .avatar ? "person.crop.circle" : "play.rectangle"))
                .font(.title3).foregroundStyle(.secondary))
    }
}

/// Dezenter Auswahlzustand (nur bei geöffneter Fernbedienung): Hintergrund, leichte Skalierung, Haptik.
struct RemoteSelectionModifier: ViewModifier {
    let isSelected: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(isSelected ? KidTheme.accent.opacity(0.14) : .clear))
            .scaleEffect(isSelected && !reduceMotion ? 1.02 : 1)
            .animation(reduceMotion ? nil : .spring(duration: 0.25), value: isSelected)
            .sensoryFeedback(.selection, trigger: isSelected) { _, new in new }
            .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

extension View {
    func remoteSelected(_ isSelected: Bool) -> some View { modifier(RemoteSelectionModifier(isSelected: isSelected)) }
}

/// Kanal im Carousel: Avatar + Name.
struct ChannelAvatar: View {
    let row: KidRow
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                KidThumbnail(url: row.thumbnailUrl, style: .avatar, systemImage: row.systemImage,
                             size: CGSize(width: KidTheme.channelAvatarSize, height: KidTheme.channelAvatarSize))
                Text(row.title).font(.caption).lineLimit(2).multilineTextAlignment(.center).foregroundStyle(.primary)
                    .frame(width: KidTheme.channelAvatarSize + 16)
            }
            .padding(6)
        }
        .buttonStyle(.plain)
        .remoteSelected(isSelected)
        .accessibilityLabel(row.title)
        .accessibilityHint("Kanal öffnen")
    }
}

/// Zeile für Videos in Listen (Zuletzt geschaut, Kanal, Playlist, Suche): Bild, Titel (≤ 2 Zeilen), Quelle.
struct RecentVideoRow: View {
    let row: KidRow
    let isSelected: Bool
    var showsSubtitle = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: KidTheme.cardSpacing) {
                KidThumbnail(url: row.thumbnailUrl, style: row.thumbnailStyle, systemImage: row.systemImage,
                             size: row.thumbnailStyle == .avatar ? CGSize(width: 48, height: 48) : CGSize(width: 96, height: 54))
                VStack(alignment: .leading, spacing: 3) {
                    Text(row.title).font(.body).lineLimit(2).foregroundStyle(.primary).multilineTextAlignment(.leading)
                    if showsSubtitle, let subtitle = row.subtitle, !subtitle.isEmpty {
                        Text(subtitle).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.vertical, 6)
            .padding(.horizontal, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .remoteSelected(isSelected)
        .recommendContextMenu(for: row)
        .accessibilityLabel([row.title, row.subtitle].compactMap { $0 }.joined(separator: ", "))
    }
}

/// „Weiterschauen": eine Karte über die volle Breite mit Bild, Titel (≤ 2 Zeilen), Kanal und ▶.
struct ContinueWatchingCard: View {
    let row: KidRow
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: KidTheme.cardSpacing) {
                KidThumbnail(url: row.thumbnailUrl, systemImage: row.systemImage, size: CGSize(width: 112, height: 63))
                VStack(alignment: .leading, spacing: 3) {
                    Text(row.title).font(.headline).lineLimit(2).multilineTextAlignment(.leading).foregroundStyle(.primary)
                    if let subtitle = row.subtitle { Text(subtitle).font(.subheadline).foregroundStyle(.secondary).lineLimit(1) }
                }
                Spacer(minLength: 8)
                Image(systemName: "play.fill").font(.title3).foregroundStyle(KidTheme.accent)
                    .frame(width: KidTheme.minimumTouchTarget, height: KidTheme.minimumTouchTarget)
            }
            .padding(12)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color(.secondarySystemGroupedBackground)))
        }
        .buttonStyle(.plain)
        .remoteSelected(isSelected)
        .recommendContextMenu(for: row)
        .accessibilityLabel("Weiterschauen: \(row.title)\(row.subtitle.map { ", \($0)" } ?? "")")
        .accessibilityAddTraits(.startsMediaSession)
    }
}

/// Einfache, hilfreiche Leerzustände.
struct KidEmptyState: View {
    let systemImage: String
    let title: String
    let message: String

    var body: some View {
        ContentUnavailableView { Label(LocalizedStringKey(title), systemImage: systemImage) } description: { Text(LocalizedStringKey(message)) }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
    }
}

/// Schmale Leiste über der Tab-Leiste: öffnet die Fernbedienung (Tap oder Wischen nach oben).
struct RemoteHandle: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: "chevron.up").font(.caption.weight(.bold))
                Text("Fernbedienung").font(.subheadline.weight(.medium))
            }
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity)
            .frame(minHeight: KidTheme.minimumTouchTarget)
            .background(.bar)
            .overlay(alignment: .top) { Rectangle().fill(Color(.separator)).frame(height: 0.5) }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .gesture(DragGesture(minimumDistance: 20).onEnded { value in if value.translation.height < -20 { action() } })
        .accessibilityLabel("Fernbedienung")
        .accessibilityHint("Öffnet das Click-Wheel zur Steuerung")
        .accessibilityIdentifier("remote.handle")
    }
}

/// Schloss als Toolbar-Button (Elternbereich hinter PIN).
struct ParentControlButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "lock")
        }
        .accessibilityLabel("Elternbereich öffnen")
        .accessibilityIdentifier("parent.lock")
    }
}

/// Langes Drücken auf ein Video: Empfehlen (Original-Link).
struct RecommendContextMenuModifier: ViewModifier {
    let row: KidRow

    func body(content: Content) -> some View {
        if case .play(let videoId, let title) = row.action {
            content.contextMenu {
                RecommendMenu(title: title, videoId: videoId)
            }
        } else {
            content
        }
    }
}

extension View {
    func recommendContextMenu(for row: KidRow) -> some View { modifier(RecommendContextMenuModifier(row: row)) }
}
