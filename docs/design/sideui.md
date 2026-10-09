# SideUI in SideTube

Stand 02.10.2026 · Verweis, keine eigene Spezifikation · Android-Abgleich siehe unten

SideTube folgt SideUI, der gemeinsamen Bedien- und Gestaltungssprache der
Sidephone-Apps. Die Quelle der Wahrheit liegt im SidePlay-Repositorium unter
`docs/design/sideui/` (SidePlay-Repository). SideUI ist eine
Spezifikation, keine geteilte Bibliothek (ADR 0018): SideTube setzt sie selbst
um und übernimmt keinen Code. Das passt zur Regel in
[architecture/README.md](../architecture/README.md), kein UI-Framework zu teilen.

## Was für SideTube gilt

| Thema | Regel | ADR |
|---|---|---|
| Farbe | ACTIVE hat genau einen Wert, `#FFB74D`. Das Markengelb `#FBBB1B` gehört Logo, App-Symbol und Store-Material. | 0001, 0016 |
| Fokus | eigene Fläche plus Rahmen, nie gelb; am Sidephone immer sichtbar, auf iOS nur bei offenem Rad | 0002, 0011 |
| Ring außerhalb des Players | hoch/runter = Reihe, links/rechts = Objekt, Mitte = öffnen, kein Umlauf | 0006, 0014 |
| Ring im Player | Mitte = Abspielen/Pause, links/rechts = Video, hoch/runter = 10 s spulen; Lautstärke nicht am Ring | 0015 |
| Zurück | kurz eine Ebene, lang zum Start | 0007 |
| Äußere Tasten | Abkürzungen: oben rechts Suche/lang Einstellungen, unten rechts Favoriten/lang Kontextmenü | 0013 |
| Tastenbelegung | nur aus der gemeinsamen Messtabelle; die Mitte (`ENTER` oder `KEY_PLAYPAUSE`) wird unter Firmware 2.0.0 neu gemessen | 0005, 0012 |
| Virtuelles Rad (iOS) | ab Werk aus, Elternschalter je Profil; 24° je Schritt; Blatt mit Freiraum | 0008–0010, 0017 |

## Offene Abweichungen

Die vollständige Liste mit Dateien führt `docs/design/sideui/konformitaet.md` im
SidePlay-Repositorium. Für SideTube sind es am 02.10.2026 zehn Punkte, darunter:

- ~~Android: gelber Fokusring in `kid/KidScreen.kt`.~~ erledigt 02.10.2026, siehe unten
- ~~Android: hoch/runter wechseln im Player das Video (`KidViewModel.onKey`).~~ erledigt 02.10.2026
- ~~iOS: hoch/runter regeln im Player die Lautstärke (`RemoteController`).~~ erledigt 02.10.2026, siehe unten
- ~~iOS: links/rechts springen in Listen zum ersten/letzten Eintrag.~~ erledigt 02.10.2026
- ~~iOS: Akzent ist das Markengelb (`KidComponents.swift`).~~ erledigt 02.10.2026
- ~~iOS: Griff „⌃ Fernbedienung“ ohne Elternschalter.~~ erledigt 02.10.2026

## iOS: Inventar und Umsetzung (02.10.2026)

Bestand vor der Umsetzung (Zeilen im Stand `video/szenen` 48f1b39), jeweils mit Erledigt-Vermerk.
Zweig `feature/ios-sideui`.

| # | Regel | Befund vorher (Datei:Zeile, unter `ios/Sources/`) | Stand |
|---|---|---|---|
| 1 | Farbe | Akzent ist das Markengelb: `Features/Kid/KidComponents.swift:9` (`KidTheme.accent = BrandColor.yellow`), `Resources/Assets.xcassets/AccentColor.colorset` = `#FBBB1B` (wirkt über `Color.accentColor` in `Parent/WhitelistManagerView.swift:128`, `WatchStatsView.swift:81`, `WishDecisionView.swift:15`, `PINEntryView.swift:48`) | erledigt: `SideUIColor.active = #FFB74D` (`App/Brand.swift`), `KidTheme.accent` und `AccentColor` darauf umgestellt. `BrandColor.yellow` bleibt nur in der Wortmarke und im App-Symbol. |
| 2 | Farbe | Gelb als Dekoration: ▶ auf „Weiterschauen" (`KidComponents.swift:138`) und „Als Nächstes" (`PlayerScreen.swift:305`), gelb getönte Tasten im Rad-Blatt (`RemoteSheet.swift:53`), gedrücktes Ringsegment gelb (`Wheel/ClickWheelView.swift:89`) | erledigt: neutral (`.primary`); gedrückt ist hell, nicht gelb. |
| 3 | Fokus | Fokus = Akzentgelb mit 14 % Deckkraft, kein Rahmen (`KidComponents.swift:57`) | erledigt: eigene Fläche `#262E38` plus Rahmen `#A8B1BD` 1,5 pt, nie gelb. Sichtbar weiterhin nur bei offenem Rad (`RemoteController.isSelected`). |
| 4 | Virtuelles Rad | Griff „⌃ Fernbedienung" (`KidComponents.swift:165`, eingebaut über `LibraryScreen.swift:255`) und Toolbar-Knopf (`KidComponents.swift:203`) immer sichtbar, kein Elternschalter | erledigt: `KidProfile.remoteWheelEnabled` (ab Werk `false`), Schalter „Fernbedienung mit Scrollrad" im Profil-Editor. Ohne ihn weder Griff noch Knopf; Abschalten schließt das Rad. |
| 5 | Virtuelles Rad | 24° je Schritt (`Wheel/WheelRotationTracker.swift:9`), Blatt mit Freiraum (`LibraryScreen.swift:263`) | war schon erfüllt, geprüft. |
| 6 | Ring außerhalb Player | links/rechts springen zum ersten/letzten Eintrag (`Kid/RemoteController.swift:63–67`); hoch/runter und Drehen bewegen flach durch alle Einträge (`:50–58`) | erledigt: `WheelMenuModel` kennt Reihen; hoch/runter = Reihe, links/rechts und Drehen = Objekt in der Reihe, Anschlag ohne Umlauf. |
| 7 | Ring im Player | hoch/runter regeln die Lautstärke (`RemoteController.swift:39–40`) | erledigt: hoch = 10 s zurück, runter = 10 s vor; Drehen spult weiter. `PlayerModel.adjustVolume` entfernt. Ringzeichen im Player: ⟲10/⟳10 und ⏮/⏭. |
| 8 | Zurück | nur kurz (`RemoteSheet.swift:46`); Start nur über eigene Taste | erledigt: Zurück lang (0,5 s) = Start, zusätzlich als Bedienhilfen-Aktion „Zum Start". Die sichtbare Start-Taste bleibt (ADR 0004: lang ist nur Kurzweg). |

### Auslegungen

Die ADR 0005–0018 lagen bei der Umsetzung nicht vor; gearbeitet wurde nach der Tabelle oben.

- **Reihe auf iOS:** Eine Reihe ist, was nebeneinander steht: die Kanäle der Startseite, eine Zeile
  im Raster „Alle Videos" (Spaltenzahl aus der Breite wie beim adaptiven Raster). Untereinander
  stehende Videos sind je eine Reihe. Hat die Reihe nur ein Objekt, bewegen links/rechts und Drehen
  zur nächsten Reihe, sonst wären sie tote Tasten; gibt es nur eine Reihe, bewegen hoch/runter das
  Objekt. Dieselbe Regel gilt in SidePlay (`Radsteuerung`).
- **Richtung beim Spulen:** hoch = 10 s zurück, runter = 10 s vor. Grund: hoch heißt außerhalb des
  Players „vorheriges", runter „nächstes" (wie `KEY_UP`/`KEY_DOWN` auf dem Sidephone, SidePlay
  Android `Aktion.Zurueck`/`Vorwaerts`); Drehen im Uhrzeigersinn spult vor.
- **Elternschalter je Profil:** liegt im Profil-Editor (Einstellungen, hinter der PIN). Für UI-Tests schaltet
  `-sidetube.devRemoteWheel 1` das Rad nur in DEBUG ein.
- **Bedienakzente:** Tab-Auswahl, Fortschrittsbalken des laufenden Videos, große Abspieltaste,
  „Gewünscht" tragen weiter `#FFB74D` (aktiver Zustand). Getönte Knöpfe im Elternbereich und bei
  den Wünschen (`Color.accentColor`, `Wishes.swift`) sind Bedienakzent, kein ACTIVE-Zustand; sie
  tragen jetzt `#FFB74D` statt des Markengelbs und sind als offener Punkt gegen ADR 0016 zu prüfen.

### Offen (iOS)

- Äußere Tasten (ADR 0013) hat das Rad-Blatt nur teilweise: Zurück unten links, Start unten rechts.
  Suche/Favoriten als Abkürzungen fehlen.
- Kategorien und „Neu bei deinen Kanälen" auf der Startseite sind mit dem Rad nicht erreichbar (wie vorher).
- Die UI-Tests `testCreateProfileWhitelistVideoAndRemove` (oEmbed im Simulator) und
  `testIncomingRecommendationNeedsParentPIN` (Startvideo fehlt) schlagen schon im Ausgangsstand fehl.
- UI-Tests brauchen eine Signatur (Schlüsselbund für die PIN): `CODE_SIGN_IDENTITY=-` statt
  `CODE_SIGNING_ALLOWED=NO`.

## Android: Inventar und Stand (02.10.2026)

Abgleich der Android-App gegen die Tabelle oben, Zweig `feature/android-sideui` (gemergt in
`video/szenen`). Zeilen beziehen sich auf den Stand **vor** der Änderung (Commit `48f1b39`).
Gemeinsame Messtabelle und ADR 0001–0004 aus SidePlay `docs/design/sideui/`; ADR 0005–0018 und
`konformitaet.md` lagen nicht vor – jede eigene Auslegung steht unten unter „Auslegungen".

| # | Regel | Abweichung vorher (Datei:Zeile) | Stand |
|---|---|---|---|
| 1 | Farbe | `SideTubeTheme.kt:16,24`: `primary` = Markengelb `#FBBB1B`, damit war jede Akzentfläche Markengelb | erledigt: `primary` = ACTIVE `#FFB74D` (`SideActive`); `#FBBB1B` nur noch im App-Symbol (`ic_launcher_foreground.xml`) |
| 2 | Fokus nie gelb | `kid/KidScreen.kt:241` (Zeilen), `:364` (Bereichsumschalter), `player/PlayerView.kt:226` (Endkarte): Fläche + Rahmen in Gelb | erledigt: überall `SideFokus` – Fläche `#262E38`, Rahmen `#A8B1BD` 3 dp, wie Rad, Wünsche und Player-Menü |
| 3 | Gelb nur ACTIVE | gelb ohne aktiven Zustand: `KidScreen.kt:216` (Restminuten), `:330,333` („Alle Videos"-Symbol), `:404,408` (Suchfeld), `:444` (Ziffern), `PlayerView.kt:107` (Hinweis „übersprungen") | erledigt: neutral (Textfarbe gedämpft) |
| 4 | Ring im Player | `kid/KidViewModel.kt:283-284`: hoch/runter wechselten das Video | erledigt: hoch/runter = ∓10 s (`PlaybackModel.seekBy`, `Command.SeekBy`), links/rechts = Video, Mitte = Pause; keine Lautstärke am Ring |
| 5 | Spulen | `player/PlayerBridge.kt:164`: `seek()` rief `seekTo()` auf, das es in `player.html` nicht gibt | erledigt: `seekBy()` ruft `seekBy()` aus `player.html` |
| 6 | Zurück lang | `MainActivity.kt:92`: nur `repeatCount == 0` ausgewertet; `isLongPress` (`:233`) kommt aber erst mit der Wiederholung. Langes Zurück löschte in der Suche ein Zeichen | erledigt: `core/input/HoldKey` entscheidet beim Loslassen (kurz) bzw. bei Wiederholung, Long-Press-Flag oder eigener Uhr nach 500 ms (lang) |
| 7 | Zurück lang im Player | `KidViewModel.kt:285, 745`: schloss nur den Player | erledigt: Player zu **und** Start |
| 8 | Äußere Tasten | `KidViewModel.kt:287`: oben rechts öffnete im Player das Menü; oben rechts lang und unten rechts lang fehlten (`core/input/KeyActions.kt:70,73`) | erledigt: oben rechts = Suche überall (auch aus Player, Menü, Endkarte), lang = Einstellungen (Elternbereich hinter der PIN); unten rechts lang (`ENTER` gehalten) = Kontextmenü = Player-Menü „Mehr davon" |
| 8a | Fokus nie gelb | Eingabefelder im Elternbereich (`parent/Components.kt:76`, `ProfileEditorScreen.kt:68,189`, `ReviewScreens.kt:215,311`, `WunschScreens.kt:118`) zeigten den Fokus in `primary` (gelb) | erledigt: `sideTextFieldColors()` (heller Rahmen, wie SidePlay) |
| 9 | Zurück = eine Ebene | `MainActivity.kt:273`: Zurück auf der PIN-Abfrage schloss die App (jetzt mit dem Ring erreichbar, Nr. 8) | erledigt: zurück zum Kindermodus |
| 10 | Kein Umlauf | – (`FocusModel`, Rad, Menü und Endkarte waren schon ohne Umlauf) | geprüft, Test vorhanden |
| 11 | Tastenbelegung | Mitte nur `ENTER`/`DPAD_CENTER`/`MEDIA_PLAY_PAUSE` kurz | alle drei öffnen weiterhin; siehe Auslegung A1 |

Tests: `core/input/InputTest.kt` (`KeyMapTest`, `HoldKeyTest`), `core/player/PlaybackModelTest.kt`
(Spulen), `kid/KidPlayerRingTest.kt` (Player-Ring, äußere Tasten, Zurück lang), `KidRadSearchTest`
(langes Zurück in der Suche führt zum Start), `KidWuenscheTest` (Menü auf unten rechts lang).
Am Emulator SP-01 geprüft (Belege im Repo sidekids-video unter `befunde/sideui/`): Fokusfläche,
kein Umlauf am Listenende, Spulen +10/+10/−10 s (Abspielzeit über die WebView-Devtools gelesen),
links/rechts wechselt das Video, unten rechts lang öffnet das Menü, Zurück lang aus Player und
Suche zum Start, Zurück kurz löscht in der Suche ein Zeichen, oben rechts lang öffnet die PIN.

### Auslegungen (eigene, bis ADR 0005–0018 wieder vorliegen)

- **A1 Mitte und `ENTER`.** Die Messtabelle legt `ENTER` auf außen unten rechts, die Mitte auf
  `KEY_PLAYPAUSE`; SideTube hatte die Mitte als `ENTER` gemessen, und die Mitte ist bis zur
  Neumessung unter Firmware 2.0.0 offen. Eine Taste kann nicht beides sein. SideTube nimmt `ENTER`
  **kurz als Mitte** (Grundbedienung vor Abkürzung, ADR 0004) und **lang als Kontextmenü** von
  außen unten rechts. `DPAD_CENTER` und `MEDIA_PLAY_PAUSE` öffnen ebenfalls. Ergibt die Neumessung
  die Tabelle (Mitte = `KEY_PLAYPAUSE`), wird `ENTER` kurz zur Abkürzung – eine Zeile in `KeyMap`.
- **A2 Favoriten.** SideTube hat keine Favoriten. Die kurze Abkürzung unten rechts entfällt
  (siehe A1); „Meine Wünsche" ist das Nächstliegende, liegt aber als letzte Zeile der Startseite und
  braucht keine Taste. Unten rechts **lang** ist das Kontextmenü: im Player das Menü „Mehr davon
  wünschen"; außerhalb des Players hat SideTube kein Kontextmenü, die Taste tut dort nichts.
- **A3 Einstellungen.** Oben rechts lang öffnet die Einstellungen hinter der PIN – dieselbe
  Stelle wie das Schloss im Kopf. Ein laufendes Video wird dabei ordentlich geschlossen.
- **A4 Suche aus dem Player.** Oben rechts schließt den Player ordentlich (Sehzeit gebucht) und
  öffnet die Suche – die Abkürzung gilt überall.
- **A5 Links/rechts außerhalb des Players.** Der Kindermodus ist eine einzige Spalte; jede Zeile
  ist ein Objekt. Links/rechts bewegen deshalb wie hoch/runter um ein Objekt (wie die Listen in
  SidePlay). Im Rad der Suche wählen sie den Buchstaben.
- **A6 Fokusrahmen 3 dp.** SideTube behält 3 dp (Zeilen sind höher als in SidePlay, das 2 dp nutzt);
  Fläche und Farbe sind in beiden Apps gleich.

### Offen

- Der Elternbereich (Touch) nutzt `primary` weiter für Abschnittsköpfe und Knöpfe – jetzt im
  ACTIVE-Wert, semantisch aber kein ACTIVE. Nicht Teil dieser Runde (nur die Eingabefelder, 8a).
- Der große Abspielknopf im Player ist gelb (Zustand „spielt"/„Pause" der Wiedergabe) – als ACTIVE
  gelesen, nicht geändert.
- Positionsanzeige im Player: Am Emulator blieb sie bei laufendem Video stehen (0:21 über mehr als
  12 s, die WebView meldete zugleich steigende Zeiten). **Ursache gefunden und behoben (09.10.2026):**
  `player.html` startete den Fünf-Sekunden-Takt bei jedem Zustandswechsel neu, und auf dem Emulator
  wechselt YouTube beim Abspielen häufig zwischen Puffern (3) und Spielen (1) – der Takt kam so
  minutenlang nicht zum Zug. Jetzt läuft er durch Puffern hindurch, startet nur einmal und meldet die
  Stelle beim Start sofort.
- Messung der Mitte unter Firmware 2.0.0 (A1).

Ältere Dokumente ([ui-redesign.md](../ui-redesign.md), [UX_REVIEW.md](../UX_REVIEW.md))
führen Bernstein als Fokusfarbe. Sie bleiben als Stand ihres Datums stehen; es
gilt diese Seite.
