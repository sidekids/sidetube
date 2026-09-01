# UI-Redesign Kindermodus (Branch `redesign/native-ui`, 2026-08-31)

## 1. Ausgangssituation (IST)

Stand `main` @ `c819021`. Kindermodus = ein einziger Screen (`KidRootView` + `KidShellView`):

| Bereich | Heute dargestellt | Datei |
|---|---|---|
| Obere ~55 % | „Zuletzt gespielt"-Kachel (30 %) + Kartenreihe „Kanäle (3)" (70 %) mit großen runden Kanalbildern, darunter Liste „Zuletzt geschaut" (jede Zeile trägt zusätzlich den Untertitel „Zuletzt geschaut"); schwebender runder Schloss-Button oben rechts | `KidSidephoneViews.swift`, `KidRootView.swift` |
| Bereichsleiste | Eigene Tab-Leiste Kanäle · Videos · Playlists · Suche (Bernstein) | `KidTabBar` |
| Untere ~45 % | Click-Wheel (Ring ↑ ↓ ⏮ ⏭, Mitte) **plus** vier Ecktasten (← → Zurück Enter), dauerhaft sichtbar | `KidShellView.swift`, `ClickWheelView.swift`, `WheelActions.swift` |
| Wiedergabe | Video ersetzt den Kopfbereich, Liste und Rad bleiben | `PlayerView` im Hero-Slot |
| Theme | erzwungenes Dark Mode, Hintergrund fast Schwarz, Bernstein als Rahmen um jede Auswahl | `KidTheme` |

Architektur: eigene Bildschirm-Stapel-Navigation (`KidNavigator` mit `[any KidScreenModel]`), Radereignisse → `WheelAction`,
Ecktasten mit `KeyMapping` (UserDefaults) und Elternseite „Tastenbelegung". Daten-/Player-Logik: `HomeModel`,
`ChannelModel`, `PlaylistModel`, `SearchModel`, `PlayerModel`, `YouTubePlayerBridge`, Repositories (SwiftData).

## 2. UX-Probleme (PROBLEM)

1. **Vier konkurrierende Navigationen gleichzeitig** (Inhalt, Medien, Tabs, Fernbedienung) – die Fernbedienung belegt
   dauerhaft die halbe Fläche, obwohl sie nur beim Abspielen oder bewusst „wie am Sidephone" gebraucht wird.
2. **Redundante Tasten**: Bereichswechsel per Ecken ← → *und* per Tab-Leiste; Öffnen per Mitte *und* Enter-Ecke;
   drei Zurück-Symbole (Ecke ↩, ZURÜCK-Label-Historie, Chevron im Kopf).
3. **Kein Platz für Inhalte**: Liste „Zuletzt geschaut" ist auf dem iPhone hinter der Bereichsleiste abgeschnitten.
4. **Visuelles Rauschen**: „Kanäle (3)"-Zähler, wiederholter Untertitel „Zuletzt geschaut", dicke Bernstein-Rahmen,
   schwebendes Schloss ohne Bezug, vollflächiges #000-Schwarz statt Systemmaterial.
5. **Eigene Tab-Leiste** statt nativer `TabView`: kein Systemverhalten (Badges, Accessibility, Größenklassen).
6. **Auswahlzustand immer sichtbar**, obwohl Touch direkt ist – der Fokus ist nur bei Radbedienung nötig.

## 3. Zielbild (SOLL)

| Element | Wohin |
|---|---|
| Fernbedienung (Click-Wheel) | **eigener Modus**: `RemoteHandle` („⌃ Fernbedienung") oberhalb der Tab-Leiste öffnet ein **Bottom Sheet** (`RemoteSheet`, Detents 55 % / groß, Inhalt dahinter bleibt bedienbar). Wheel wird dort groß und zentral; darunter nur **Zurück** und **Home**. |
| Home | kuratiert: **Weiterschauen**-Karte (Bild, Titel ≤ 2 Zeilen, Kanal, ▶), **Kanäle** (Carousel 76 pt, „Alle" → Mediathek), **Zuletzt geschaut** (Bild, Titel, Kanal – ohne wiederholte Beschriftung), Empty States |
| Kanäle · Videos · Playlists | in die **Mediathek** als Segmented Control; Raster; Detailansichten für Kanal (mit `searchable`) und Playlist |
| Suche | eigener Tab mit `searchable`, letzte Suchen, Empty States |
| Bottom-Navigation | native `TabView`: **Home · Mediathek · Suche** |
| Schloss | Toolbar-Button (`lock`) in der Navigation Bar jedes Tabs; PIN-Logik unverändert |
| Wiedergabe | eigener `PlayerScreen` (Full-Screen Cover): Video, Titel, native ⏮ ⏯ ⏭, Warteschlange, Remote-Handle; Querformat = Vollbild |
| Auswahlzustand | nur bei geöffneter Fernbedienung: dezenter Hintergrund + leichte Skalierung + Haptik; VoiceOver `isSelected` |
| Theme | Systemfarben/-materialien, Dark Mode folgt dem System; Bernstein nur für aktive Tabs, Fortschritt, Fokus, Primäraktion |

### Mapping-Tabelle Bedienelemente

| UI-Element (alt) | Aktion | Häufigkeit | Nötig? | Zielzustand |
|---|---|---|---|---|
| Rad drehen | Auswahl bewegen / spulen | hoch | ja | bleibt (Remote-Sheet) |
| Mitte | Öffnen / Play-Pause | hoch | ja | bleibt |
| Ring ↑ ↓ | Auswahl ±1 / Lautstärke | mittel | ja (Lautstärke) | bleibt |
| Ring ⏮ ⏭ | Anfang/Ende / Video wechseln | mittel | ja (Video) | bleibt |
| Ecke ← → | Bereich wechseln | niedrig | **nein** – native Tab-Leiste | entfällt |
| Ecke Enter | Öffnen | niedrig | **nein** – identisch mit Mitte | entfällt |
| Ecke Zurück | eine Ebene zurück | mittel | ja | Button **Zurück** im Remote-Sheet (+ native Back-Navigation) |
| — | zum Start | mittel | ja | Button **Home** im Remote-Sheet |
| Tastenbelegung (Eltern) | Ecken umbelegen | — | entfällt mit den Ecken | entfernt |
| Schwebendes Schloss | Eltern-PIN | niedrig | ja | Toolbar-Button |

## 4. Designprinzipien
Progressive Disclosure · eine Aufgabe pro Screen · native Bausteine vor Eigenbau · Auswahl nur wenn per Rad bedient ·
Akzentfarbe sparsam · Empty States statt leerer Überschriften · Touch-Ziele ≥ 44 pt · Dynamic Type · Reduce Motion.

## 5. Neue Informationsarchitektur
```
KidRootView (TabView)
├── Home        NavigationStack → Kanal-/Playlist-Detail
├── Mediathek   NavigationStack → Segment Kanäle | Videos | Playlists → Detail
└── Suche       NavigationStack (searchable) → Detail
RemoteHandle (safeAreaInset unten in jedem Tab) → RemoteSheet (Wheel, Zurück, Home)
PlayerScreen (fullScreenCover) ← PlayerCoordinator; eigenes Remote-Handle
Overlays „Gute Nacht" / „Zeit ist um" über allem (nur Eltern-PIN führt heraus)
```
Koordinatoren (Environment): `KidSession` (aktives Profil), `RemoteController` (Rad-Ziel = sichtbarer Screen oder Player),
`PlayerCoordinator` (Warteschlange, WebView-Bridge, Vollbild). Fachlogik (`*Model`, Repositories, `PlayerModel`) unverändert.

## 6. Home
`HomeScreen`: Large Title „Home", Toolbar links Profil-Menü (nur bei mehreren Profilen), rechts Schloss. Sektionen:
**Weiterschauen** (`ContinueWatchingCard`, nur wenn Verlauf vorhanden; ganze Karte antippbar, ▶ als Akzent),
**Kanäle** (`ChannelAvatar` 76 pt im horizontalen Carousel, „Alle" springt in die Mediathek) und **Zuletzt geschaut**
(`RecentVideoRow`: Bild 96×54, Titel ≤ 2 Zeilen, keine wiederholte Beschriftung). Leere Sektionen zeigen `KidEmptyState`
statt leerer Überschriften. Daten: `HomeModel(tab: .channels)` unverändert (lead/cards/rows), Verlauf aus `WatchHistoryEntry`.

## 7. Mediathek
`LibraryScreen`: Segmented Control Kanäle | Videos | Playlists (`@Binding` aus dem Root, damit „Alle" auf Home dorthin
springen kann), `LazyVGrid` adaptiv (Kanäle als Avatare, Videos/Playlists als `LibraryTile` 16:9). `LibraryModel` liefert
nur Karten und ist Radziel. Details: `DetailScreen` (Kanal mit `searchable` „Im Kanal suchen", Nachladen beim Erreichen der
letzten drei Zeilen, Playlist ohne Suche). Zustand pro Tab bleibt beim Wechsel erhalten (eigener `NavigationStack`).

## 8. Suche
`SearchScreen`: natives `searchable` mit Vorschlägen aus den letzten sechs Suchbegriffen (`RecentSearches`, UserDefaults),
300-ms-Debounce, Leerzustände „Wonach suchst du?" / „Nichts gefunden", Treffer aus Whitelist + Kanalvideo-Cache (0 Quota).

## 9. Fernbedienung (Remote-Modus)
`RemoteHandle` („⌃ Fernbedienung", 44 pt, `.bar`-Material) sitzt als `safeAreaInset` über der Tab-Leiste in jedem Tab
und im Player; Tap oder Wischen nach oben öffnet `RemoteSheet` (Detents 55 % und groß, Drag-Indicator, Hintergrund bei 55 %
bedienbar, `regularMaterial`). Inhalt: Titel + Schließen, Kontextzeile (laufendes Video oder ausgewählter Eintrag), Wheel
(max. 320 pt), darunter **Zurück** und **Home**. `RemoteController` kennt das aktuelle Ziel (`RemoteTargetBinding`:
sichtbarer Screen registriert sich in `onAppear`) oder den Player. Bei offenem Sheet bekommen die Listen unten einen Freiraum in Sheet-Höhe und scrollen die Radauswahl automatisch
ins obere Drittel (`remoteAutoScroll`, Reduce Motion ohne Animation). Auswahlzustand (`remoteSelected`) erscheint **nur bei
geöffneter Fernbedienung**: dezenter Akzenthintergrund, 2 % Skalierung, Haptik `.selection`, VoiceOver `isSelected`.

## 10. Click-Wheel
`ClickWheelView` unverändert in der Mechanik (Rastschritt 24°, Überlauf, Tipp vs. Drehen, Haptik pro Schritt). Belegung:
Mitte = Öffnen / Play-Pause, Ring ↑↓ = Auswahl bzw. Lautstärke, Ring ⏮⏭ = Anfang/Ende bzw. Video wechseln, Drehen =
Auswahl bzw. Spulen. Entfernt: vier Ecktasten (← → Enter Zurück) und die Elternseite „Tastenbelegung" – Bereichswechsel und
Öffnen sind redundant zur nativen Tab-Leiste bzw. zur Mitte, Zurück/Home sind jetzt Tasten im Sheet. VoiceOver: Auf/Ab-Aktion
plus benannte Aktionen „Auswählen", „Zurück", „Play/Pause".

## 11. Player
`PlayerScreen` als Full-Screen Cover (`PlayerCoordinator`): Video (16:9), Titel/Position/Status (`PlayerView`), native
⏮ ⏯ ⏭ (44/64 pt), Liste „Als Nächstes" (aktueller Eintrag markiert und automatisch ins Bild gescrollt, Tipp springt), Remote-Handle, Toolbar „Fertig" und
Schloss. Querformat oder Doppeltipp → `FullscreenPlayerView`. Eine WebView-Bridge pro Sitzung; Sehzeit wird beim Ende und
beim Schließen gebucht (unverändert).

## 12. Elternbereich
Schloss ist jetzt Toolbar-Button (`ParentControlButton`, Identifier `parent.lock`) in jedem Tab und im Player. PIN-Logik,
Sperrstufen, Overlays „Gute Nacht"/„Zeit ist um" (nur Eltern-PIN führt heraus) unverändert; bei Overlay werden Player und
Fernbedienung geschlossen (statt Pause – Sehzeit wird gebucht). Elternbereich ohne „Tastenbelegung".

## 12a. Empfehlen (Produktentscheidung 2026-08-31)
`RecommendMenu` im Player (Toolbar) und per langem Drücken auf Video-Zeilen/-Kacheln/-Karte: **Per Nachricht (iMessage)**
(`MFMessageComposeViewController`, Fallback `sms:`), **Per Signal** (Signal hat keine Compose-URL → Text in die
Zwischenablage, `sgnl://` öffnen, Hinweis „einfügen"), **Link kopieren**. Bewusst **kein** System-Teilen-Blatt, damit WhatsApp
nie erscheint. Der Text enthält immer den **Original-Link** `youtube.com/watch?v=…` und darunter `sidetube://add?v=…`:
Ist SideTube installiert, öffnet der zweite Link `IncomingRecommendationView` (Vorschau per oEmbed → **Eltern-PIN** →
Profilwahl → landet unter *Videos*). Die PIN-Pflicht schützt die Whitelist vor fremden Links. Ohne App ist der
`sidetube://`-Link nicht tippbar; ein einziger Link für beides bräuchte Universal Links über eine eigene Domain
(z. B. `<eigene-domain>/v/<id>` mit Weiterleitung zu YouTube) – offen, braucht Associated Domains.

## 13. Accessibility
Semantische Text Styles (Dynamic Type), Touch-Ziele ≥ 44 pt (Handle, Schloss, Play, Avatare, Remote-Tasten), Labels
(„Weiterschauen: …", „Kanal öffnen", „Profil wechseln, aktuell …"), `isHeader` für Sektionen, `startsMediaSession`,
Reduce Motion (Skalierung/Animation des Auswahlzustands aus), Information nie nur über Farbe (Auswahl = Hintergrund +
Skalierung + Haptik + `isSelected`). Systemfarben/-materialien statt Hardcode-Schwarz; Kindermodus **immer dunkel** (`preferredColorScheme(.dark)` auf Root, Remote-Sheet und Player-Cover – der Bernstein-Akzent trägt auf Hell nicht; Produktentscheidung 2026-08-31).

## 14. Technische Änderungen
Neu: `KidSession`, `RemoteController`, `PlayerCoordinator`, `HomeScreen`, `LibraryScreen` (+`LibraryModel`, `DetailScreen`,
`LibraryTile`), `SearchScreen` (+`RecentSearches`), `PlayerScreen`, `RemoteSheet`, `KidComponents` (Thumbnail, Avatar, Zeilen,
Karte, EmptyState, Handle, Schloss), `KidRootView` neu (TabView). Entfernt: `KidNavigator`, `KidSidephoneViews`,
`KidShellView`, `WheelActions`/`KeyMapping`, `KeyMappingView`, `ProfilePickerModel`. Angepasst: `KidScreenFactory` Hashable
(NavigationPath), `HomeModel` (Titel ohne Zähler, Weiterschauen nur mit Verlauf), `PlayerModel.jump(to:)`, `SessionState`
ohne KeyMapping. Fachlogik (`*Model`, Repositories, YouTube, Player-Bridge, PIN, Timer/Limit) unverändert.

## 15. Tests
Unit: 65 Tests / 18 Suiten grün (neu `RemoteController`, `PlayerCoordinator`, `KidSession`; entfernt: KeyMapping-Tests mit
dem Feature). UI (XCUITest, iPhone 17 Pro): Eltern-Ablauf, Kindermodus (Home ohne Wheel, Mediathek → Player, Remote-Sheet
Zurück/Home, Rotation → Vollbild, Weiterschauen), Schlafmodus-Overlay – grün. Screenshots (`ScreenshotUITests`) auf iPhone 17e
(klein), 17 Pro, 17 Pro Max in `docs/screenshots/`.

## 16. Offene Punkte
- Verlauf speichert keinen Kanalnamen → „Zuletzt geschaut"/„Weiterschauen" ohne Quellzeile (Modellerweiterung in v0.2).
- Wiedergabe im Simulator bleibt bei „Puffern" (bekannt) – Bild/Ton nur auf dem Gerät prüfbar.
- Dynamic Type sehr groß: Carousel-Namen brechen auf zwei Zeilen um (gewollt), Remote-Sheet bei „groß" nötig.
