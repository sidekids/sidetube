// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation

/// Eine Zeile/Karte in einer rad-bedienten Ansicht und was beim Auswählen passiert.
struct KidRow: Identifiable, Equatable {
    enum ThumbnailStyle: Equatable {
        case video   // 16:9-Kachel (Videovorschau)
        case avatar   // rundes Kanalbild
    }

    enum Action: Equatable {
        case push(KidScreenFactory)
        case play(videoId: String, title: String)
        case none
    }

    var id: String
    var title: String
    var subtitle: String?
    var thumbnailUrl: String?
    var thumbnailStyle: ThumbnailStyle = .video
    var systemImage: String?
    var action: Action
}

/// Erzeugt beim Auswählen den nächsten Bildschirm (spät, damit Listen nicht alle Unterbildschirme vorab bauen).
struct KidScreenFactory: Hashable {
    let id: String
    let make: () -> any KidScreenModel

    static func == (lhs: KidScreenFactory, rhs: KidScreenFactory) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

/// Kopfbereich (Sidephone-Hero): Cover, Titel, Untertitel.
struct KidHero: Equatable {
    var title: String
    var subtitle: String?
    var thumbnailUrl: String?
    var style: KidRow.ThumbnailStyle = .video
    var systemImage: String?

    init(title: String, subtitle: String? = nil, thumbnailUrl: String? = nil, style: KidRow.ThumbnailStyle = .video, systemImage: String? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.thumbnailUrl = thumbnailUrl
        self.style = style
        self.systemImage = systemImage
    }

    init(row: KidRow) {
        self.init(title: row.title, subtitle: row.subtitle, thumbnailUrl: row.thumbnailUrl, style: row.thumbnailStyle, systemImage: row.systemImage)
    }
}

/// Ein Bildschirm des Kindermodus: Karten (horizontal), Zeilen (vertikal), eine gemeinsame Radauswahl,
/// optional fester Hero, Suche und Nachladen.
protocol KidScreenModel: AnyObject {
    var id: String { get }
    var title: String { get }
   /// Kachel „Zuletzt gespielt" links (30 %) auf Bereichs-Startseiten; `nil` = keine.
    var lead: KidRow? { get }
   /// Startseiten-Layout 30/70 statt großem Hero.
    var usesSplitHeader: Bool { get }
    var cards: [KidRow] { get }
    var cardsTitle: String? { get }
    var rows: [KidRow] { get }
    var rowsTitle: String? { get }
   /// Fester Hero (z. B. Kanalbild in der Kanalansicht); `nil` → Hero zeigt die aktuelle Auswahl.
    var hero: KidHero? { get }
    var menu: WheelMenuModel { get }
    var isLoading: Bool { get }
    var footerHint: String? { get }
    var supportsSearch: Bool { get }
    var searchText: String { get set }
    func onAppear() async
    func onSelectionChanged(index: Int)
}

extension KidScreenModel {
    var lead: KidRow? { nil }
    var usesSplitHeader: Bool { false }
    var cards: [KidRow] { [] }
    var cardsTitle: String? { nil }
    var rowsTitle: String? { nil }
    var hero: KidHero? { nil }
    var isLoading: Bool { false }
    var footerHint: String? { nil }
    var supportsSearch: Bool { false }
    var searchText: String { get { "" } set {} }
    func onAppear() async {}
    func onSelectionChanged(index: Int) {}

   /// Auswahlreihe: erst die Kachel „Zuletzt gespielt", dann Karten, dann Zeilen.
    var allItems: [KidRow] { (lead.map { [$0] } ?? []) + cards + rows }
   /// Index der ersten Zeile innerhalb der Auswahlreihe.
    var rowsStartIndex: Int { (lead == nil ? 0 : 1) + cards.count }
    var selectedItem: KidRow? {
        let items = allItems
        let index = menu.selectedIndex
        return index < items.count ? items[index] : nil
    }
   /// Ist die Auswahl in der vertikalen Liste (Kopfbereich darf schrumpfen)?
    var selectionIsInRows: Bool { menu.selectedIndex >= rowsStartIndex && !rows.isEmpty }
   /// Aktualisiert die Radauswahl-Anzahl auf Kachel + Karten + Zeilen.
    func syncMenuCount() { menu.setCount(allItems.count) }
}
