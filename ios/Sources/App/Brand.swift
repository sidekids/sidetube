// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftUI

/// Markendaten für Store und Über-Screen. Anbieter ist SideKids; die App heißt weiterhin sidetube.
/// Web-Präsenz: GitHub-Organisation `sidekids` ; Website als GitHub Pages
/// (Entwurf: Gitea SideKids/website → `about/privacy.html`). Bis Pages live ist, liefert die Support-URL die Org-Seite.
enum Brand {
    static let appName = "SideTube"
    static let publisher = "SideKids"
    static let copyrightHolder = "SideKids"
   /// Ideengeber und Autor der App (Über-Screen).
    static let author = "Christian-Maximilian Steier"
    static let authorRole = "Idee, Konzept und Entwicklung"
    static let websiteURL = URL(string: "https://sidekids.github.io")!
    static let supportURL = URL(string: "https://github.com/sidekids")!
    static let privacyPolicyURL = URL(string: "https://sidekids.github.io/about/privacy.html")!
    static let sourceURL = URL(string: "https://github.com/sidekids/sidetube")!
}

/// Kanonische Markenfarben der Side-Familie (branding/logo-spec.json). Das Markengelb gehört nur Logo,
/// Wortmarke, App-Symbol und Store-Material (SideUI ADR 0001/0016) – in der Oberfläche gilt `SideUIColor`.
enum BrandColor {
    static let yellow = Color(red: 251 / 255, green: 187 / 255, blue: 27 / 255)   // #FBBB1B
    static let dark = Color(red: 9 / 255, green: 10 / 255, blue: 12 / 255)   // #090A0C
    static let light = Color(red: 246 / 255, green: 244 / 255, blue: 239 / 255)   // #F6F4EF
}

/// SideUI-Farben der Oberfläche (Quelle: SidePlay `docs/design/sideui/colors.md`, `SideColors.kt`).
enum SideUIColor {
    /// Das einzige Gelb der Oberfläche; es bedeutet ausschließlich ACTIVE.
    static let active = Color(red: 255 / 255, green: 183 / 255, blue: 77 / 255)   // #FFB74D
    /// Fokusfläche (FOCUSED) – eigene Fläche plus Rahmen, nie gelb.
    static let focusSurface = Color(red: 38 / 255, green: 46 / 255, blue: 56 / 255)   // #262E38
    static let focusBorder = Color(red: 168 / 255, green: 177 / 255, blue: 189 / 255)   // #A8B1BD
    static let focusBorderWidth: CGFloat = 1.5
}

/// Wortmarke: "Side" in Primaerfarbe, "Tube"/"Play" in Brand-Gelb (Schreibweise immer SideTube / SidePlay), Systemschrift Regular/Medium (kein Bitmap).
struct SideWordmark: View {
    enum Product: String { case tube, play }
    let product: Product
    var font: Font = .title2

    var body: some View {
        HStack(spacing: 0) {
            Text("Side").foregroundStyle(.primary)
            Text(product.rawValue.capitalized).foregroundStyle(BrandColor.yellow)
        }
        .font(font.weight(.medium))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(product == .tube ? "SideTube" : "SidePlay")
    }
}
