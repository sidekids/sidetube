# RECOMMENDATION IMPLEMENTATION REVIEW (Phase 1 – Audit)

*Repository:* local clone of https://github.com/sidekids/sidetube

---

## 1. Repository‑Struktur & Tech‑Stack

```
├─ android/               # Kotlin / Jetpack Compose (Android 8+)
│   ├─ app/                # Anwendung, Navigation, DI
│   ├─ core/               # Daten‑/Netz‑Schicht, gemeinsame Utilities
│   └─ feature/            # getrennte Feature‑Module (kid, parent …)
│
├─ ios/                   # Swift / SwiftUI (iOS 17+)
│   ├─ Sources/            # App‑Code (Domain, Data, Features, App)
│   │   ├─ Domain/          # Modell‑ und Policy‑Logik
│   │   ├─ Data/            # Repositories, SwiftData‑Modelle
│   │   ├─ Features/        # UI‑Screens (Kid, Parent …)
│   │   └─ App/             # App‑Entrypoint, DI, Konfiguration
│   └─ UITests/            # UI‑Tests (XCUITest)
│
├─ content/               # JSON‑Bibliotheken, Taxonomie, Quellen‑Listen
├─ docs/                  # Projekt‑Dokumentation (hier wird das Review‑Doc abgelegt)
└─ branding/              # Icons & Grafiken
```

**Tech‑Stack**
- **iOS**: Swift 5.9, SwiftUI, SwiftData (lokaler DB‑Layer), WKWebView (YouTube‑IFrame‑Player), Observation‑Framework für State‑Management.
- **Android**: Kotlin 1.9, Jetpack Compose, Hilt (DI), Room (oder alternativ SwiftData‑ähnliche Persistenz), Android WebView für YouTube‑IFrame‑Einbettung.
- **Gemeinsame Konzepte**: Content‑Policy‑Engine, Whitelist‑ bzw. Curation‑Repository, Trust‑Levels, Eltern‑Gate (PIN‑Schutz) – in beiden Plattformen exakt spiegelnd implementiert.

---

## 2. Bestehende Policy‑Engine

### Zentrale Entscheidungshilfe
**Datei**: `ios/Sources/Domain/ContentPolicy.swift`

```swift
enum ContentPolicy {
    struct Verdict { … }
    static func evaluate(_ item: WhitelistItem,
                         for profile: KidProfile,
                         source: CuratedSource?) -> Verdict { … }
    static func isVisible(_ item: WhitelistItem,
                          for profile: KidProfile,
                          source: CuratedSource?) -> Bool { … }
}
```

#### Signatur von `evaluate`
```swift
static func evaluate(_ item: WhitelistItem,
                     for profile: KidProfile,
                     source: CuratedSource?) -> Verdict
```
- **Parameter**
  - `item`: ein `WhitelistItem` (Video‑Eintrag aus der lokalen Datenbank).
  - `profile`: das aktive `KidProfile` (Alter, erlaubte Kategorien, …).
  - `source`: optionales `CuratedSource`‑Objekt, das die Vertrauens‑Stufe einer Quelle enthält.
- **Rückgabe**: `Verdict` – `visible` (Bool) + optional `reason` (String) für Debug/Logging.

#### Logik (Kurz‑Zusammenfassung)
1. **Provider‑Spezifisch** – PeerTube‑Instanzen benötigen explizite Freigabe.
2. **Source‑Trust** – Wird die Quelle als `blocked` oder `parentOnly` markiert, wird das Video verborgen.
3. **Freigabestatus** – Nur `approved` wird weiter betrachtet (`approvalStatus`).
4. **Alters‑Check** – `profile.ageBand.age` muss zwischen `ageMin`/`ageMax` liegen.
5. **Live / Shorts** – Live‑Streams nie zulässig, Shorts nur bei Erlaubnis (`allowShorts`).
6. **Kategorien** – Alters‑Grenzen der Kategorie, deaktivierte Kategorien im Profil, sowie explizite Manga/Anime‑Einschränkungen.
7. **News‑Spezial** – `allowNews` und `newsStatus` (sensibel / parent‑review) werden beachtet.
8. **Sexual‑/Violence‑Flags** – Sofortiger Ausschluss.
9. **Ergebnis** – `Verdict.ok` wenn **alle** Checks passen, sonst `Verdict.hidden(reason)`.

Die Hilfsfunktion `isVisible` ruft einfach `evaluate(...).visible` und wird an allen Stellen verwendet, wo ein Sichtbarkeits‑Check nötig ist (z. B. `WhitelistRepository.visibleItems`).

---

## 3. Aktuelle Video‑Navigation (iOS)

| Screen / View | Verantwortlich | Wichtigste State‑Variablen | Navigation‑Mechanik |
|---|---|---|---|
| **HomeScreen** (`HomeScreen.swift`) | `HomeScreen` (View) + `HomeModel` (View‑Model) | `@State path: NavigationPath` – SwiftUI‑Navigation‑Stack; `model.allItems` (komplette Queue) | `path.append(factory)` für Detail‑Screens, `playerCoordinator.play(row, …)` für Playback. |
| **PlayerCoordinator** (`PlayerCoordinator.swift`) | `PlayerCoordinator` (Observable) | `player: PlayerModel?`, `bridge` / `peerTubeBridge`, `usesPeerTube`, `fullscreenRequested` | Erstellt je nach ID (`PeerTubeIDs.isPeerTube`) eine passende `PlayerEngine`. Das `PlayerModel` enthält die eigentliche Queue (`[PlayerModel.Item]`) und steuert `autoAdvance`. |
| **PlayerView / FullscreenPlayerView** (`PlayerView.swift`) | SwiftUI‑View, zeigt `WKWebView` aus dem Bridge‑Objekt. | `model.current.title`, `model.status`, `model.positionText` etc. | Keine eigene Navigation – wird über `NavigationStack` von `HomeScreen` eingebettet. |
| **DetailScreen** (generisch über `KidScreenFactory`) | Jeder Detail‑Screen (z. B. `VideoDetailView` über `KidScreenFactory`) nutzt ebenfalls `playerCoordinator.play` für das eigentliche Abspielen. | `selectedVideo`, `row.action` (enum `KidRow.Action`) | `switch row.action { case .play(let videoId, _) … }`.

**Wichtige Code‑Stellen**
- `HomeScreen.activate(_:)` (Zeile 163‑166) entscheidet, ob ein `push` (Detail‑Screen) oder `play` (Video) erfolgt.
- `PlayerCoordinator.play(queue:startIndex:…)` (Zeile 40‑60) baut die Queue, wählt den passenden Bridge‑Typ und startet das `PlayerModel`.
- `PlayerModel` (nicht im Audit‑Auszug, aber in `PlayerModel.swift` vorhanden) verwaltet Wiedergabe‑Status, Auto‑Advance und Watch‑Time‑Tracking.

---

## 4. Aktuelle Recommendation‑Datenquelle

- **Quellcode**: `ios/Sources/Data/WhitelistRepository.swift` (Methode `visibleItems`).
- **Logik**: Alle Whitelist‑Einträge eines Profils werden gefiltert über `ContentPolicy.isVisible`. Es gibt *keine* eigene Empfehlungs‑Engine, die YouTube‑„Related‑Videos“‑APIs nutzt.
- **Resultat**: Die im Player‑UI angezeigten Empfehlungen (z. B. in `KidComponents` via `recommendContextMenu`) stammen ausschließlich aus der **Whitelist**‑Datenbank, die von Eltern manuell gepflegt bzw. über das Curation‑Backend importiert wird.
- **YouTube‑Related‑Videos**: Nicht eingebettet. Es gibt keinen Aufruf einer YouTube‑Related‑API; das UI‑Element `RecommendContextMenu` bietet nur das Teilen/Weiterempfehlen des aktuellen Videos, nicht das Anzeigen von Vorschlägen.
- **Eigen‑Empfehlungs‑Logik**: Nicht vorhanden – das System zeigt lediglich die **Whitelist‑Einträge** (nach Policy‑Filter) in den jeweiligen Kategorien/Carousels.

---

## 5. Trust‑Levels (SourceTrust)

**Definition** (`ios/Sources/Domain/Curation.swift` → `enum SourceTrust`):
```swift
enum SourceTrust: String, Codable, CaseIterable, Identifiable, Sendable {
    case trustedChildSource, trustedSeries, perVideoReview, parentOnly, blocked
    var allowsChannelBrowsing: Bool { self == .trustedChildSource }
}
```
- **`trustedChildSource`** – erlaubt dynamisches Browsen von Kanälen im Kindermodus (die einzige Stufe, bei der `allowsChannelBrowsing` == `true`).
- **`trustedSeries`** – vertrauenswürdige Serie, aber kein generelles Kanal‑Browsing.
- **`perVideoReview`** – jedes Video muss einzeln geprüft werden (Standard‑Stufe für neu importierte Quellen).
- **`parentOnly`** – Inhalt nur im Eltern‑Modus sichtbar.
- **`blocked`** – komplett gesperrt.

**Verwendung**
- In `ContentPolicy.evaluate` wird die Trust‑Stufe geprüft (Zeile 19‑20):
  ```swift
  if let source, source.trust == .blocked { return .hidden("Quelle gesperrt") }
  if let source, source.trust == .parentOnly { return .hidden("Quelle nur für Eltern") }
  ```
- Für das Kanal‑Browsing (z. B. im **Kid‑Home‑Screen**) wird `SourceTrust.allowsChannelBrowsing` geprüft, um zu entscheiden, ob ein Kind die Kanalliste selbständig durchstöbern darf. Nur `trustedChildSource` gibt vollständiges Browsing frei.

---

## 6. Player‑Integration (YouTube IFrame)

- **Implementation**: `YouTubePlayerBridge.swift` (iOS) und analog `VideoPlayerScreen.kt` (Android) nutzen eine **WKWebView** / **WebView** mit einer lokal eingebetteten HTML‑Datei (`Player.html`).
- **Key‑Features**
  - **IFrame‑API** wird über das HTML‑File geladen; die Origin‑URL wird auf die Bundle‑ID gesetzt (`defaultOrigin`).
  - **Navigation‑Blocking** – `webView(_:decidePolicyFor:decisionHandler:)` lässt nur Unter‑Frames (die eigentliche IFrame‑Instanz) zu, verhindert aber jede Haupt‑Navigation (z. B. „Auf YouTube ansehen“-Links). Das verhindert, dass ein Kind aus dem Player heraus zu YouTube navigiert.
  - **JS‑→‑Native‑Bridge** – über `WeakMessageHandler` und `userContentController(_:didReceive:)` werden Events (`ready`, `state`, `error`, `time`, `apiFailed`) als `PlayerEngineEvent` zurück in Swift gemeldet.
  - **Callbacks**: `onEvent` wird vom `PlayerModel` gesetzt und erhält Status‑Updates, sodass UI (Play/Pause‑Button, Fortschritt, Fehlermeldungen) korrekt aktualisiert wird.
  - **Kein Erfassen von Related‑Video‑Klicks** – Da jede Navigation aus dem Haupt‑Dokument abgelehnt wird (siehe `decisionHandler(.cancel)`), erreichen Klicks auf vorgeschlagene „Related‑Videos“ (falls UI‑Elemente im IFrame existieren) das native Layer nicht. Der Player ist also strikt auf das **einzige** Video begrenzt.

- **Android‑Äquivalent** (`VideoPlayerScreen.kt`):
  - Nutzt `android.webkit.WebView` mit ähnlicher Konfiguration (JavaScript aktiviert, keine Scroll‑/Zoom‑Gesten), lädt `Player.html` aus den Assets.
  - `WebViewClient` überschreibt `shouldOverrideUrlLoading` und gibt `false` zurück, um externe Links zu blockieren – gleichwertiges Verhalten wie iOS.

**Zusammenfassung**: Der Player ist ein isoliertes IFrame‑Widget, das ausschließlich das aktuell ausgewählte Video rendert. Es gibt keine Mechanik, um **Related‑Videos** abzufangen oder anzuzeigen – solche Inhalte müssten über eine eigene Empfehlungs‑Logik bzw. UI‑Komponente implementiert werden.

---

### Fazit & erste Handlungsfelder für Phase 2
1. **Policy‑Engine** ist klar zentralisiert (`ContentPolicy.evaluate`). Änderungen am Sichtbarkeits‑Algorithmus können hier vorgenommen werden, ohne UI‑Code anzupassen.
2. **Navigation** nutzt SwiftUI‑NavigationStack; das Player‑Modul ist komplett entkoppelt von der Rest‑Navigation – gut für Refactoring.
3. **Empfehlungen** stammen ausschließlich aus der Whitelist; es gibt keine YouTube‑Related‑Feed‑Integration. Für kind‑sichere automatische Empfehlungen muss ein neuer Service/Repository gebaut werden, das YouTube‑Related‑Daten abruft, aber anschließend über die vorhandene `ContentPolicy` filtert.
4. **Trust‑Levels** definieren klar, welche Quellen komplett durchsucht werden dürfen (`trustedChildSource`). Das ist der einzige Punkt, an dem ein „Freies Browsing“ erlaubt wird.
5. **Player‑Integration** ist sicher, blockiert externe Navigation und liefert Events über eine saubere Bridge. Für weitere Interaktionen (z. B. Klick‑Tracking auf Related‑Videos) müsste das HTML‑Widget erweitert und die Bridge‑API angepasst werden.

*Dieses Dokument bildet die Basis für das weitere Design‑Review und die Implementierung einer kontrollierten Empfehlungs‑Engine.*
