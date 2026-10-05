# Cross-Platform-Parität iOS ↔ Android

> Historisches Dokument: Aussagen und Implementierungspfade können den früheren Stand beschreiben. Maßgeblich sind der [aktuelle Release-Audit](release/pre-release-audit.md) und seine offenen Gates; dieses Dokument ist keine Release-Freigabe.

Stand: 2026-09-08, nach dem Review und der Android-Nachrüstung. Ziel: **gleiche UX, native Implementierung.** Beide Apps sollen sich für ein Kind wie
dieselbe Anwendung verhalten; Animationen, Sheets, Systemdialoge, Back-Gesten und Typografie dürfen plattformspezifisch sein.

Korrigiert am 03.10.2026 nach dem Code-Stand von `video/szenen`: Zeilen *Playlist-Ansicht*, *Spulen*,
*YouTube-Datenquellen* und *Offline-/Netzwerkverhalten* sowie Punkt 3 in Abschnitt 2. Die übrigen Zeilen sind nicht neu geprüft.

Legende: ✅ vorhanden · ⚠️ teilweise · ❌ fehlt. „Identisch?“ bezieht sich auf das Verhalten aus Kindersicht.

## 1. Feature-Matrix

| Funktion | iOS | Android | Verhalten identisch? | Verbesserung nötig? | Beleg |
|---|---|---|---|---|---|
| Startseite | ✅ **neu** Weiterschauen · Kanäle · Kategorien · Zuletzt geschaut, Kategorie-Reihen auf 6 Kacheln | ✅ Weiterschauen · Meine Kanäle · Meine Videos · Meine Sendungen | ✅ **neu** gleiche Reihenfolge und gleiche Wörter | ja (P2): Kategorie-Sektionen fehlen auf Android | `HomeScreen.swift`, `KidHomeScreen.kt` |
| Navigation | ✅ Tabs Start · Mediathek · Suche, je NavigationStack | ✅ ein Stack (Home → Detail → Player), Suche als Screen | ⚠️ | nein – plattformtypisch, mentale Modelle vergleichbar | `KidRootView.swift`, `AppNavigation.kt` |
| Zurück-Navigation | ✅ „Fertig“-Chevron, Remote-Zurück | ✅ System-Back, Hardware-Back (Sidephone), Home blockt Back | ✅ | nein | `PlayerScreen.swift`, `KidHomeScreen.kt` |
| Videolisten | ✅ Zeilen/Kacheln, Kanäle als Avatare | ✅ Karten, Kanäle als Avatare | ✅ | nein | `KidComponents.swift`, `KidHomeScreen.kt` |
| Kanalansicht | ✅ dynamisch nur bei `trustedChildSource` **und** passendem Alter; sonst Einzelfreigaben des Kanals | ✅ **neu** dieselbe Regel: `observeChannelBrowsingAllowed`, sonst Einzelfreigaben, alter Cache wird geleert | ✅ **neu** | nein | `KidModels.swift` `ChannelModel`, `ChannelDetailViewModel.kt` |
| Playlist-Ansicht | ✅ Playlist-Feed (`feeds/videos.xml?playlist_id=`, erste 15 Einträge), mit Schlüssel weiter über die Data API; Zwischenspeicher für offline | ✅ Playlist-Feed (erste 15 Einträge), Zwischenspeicher für offline; keine Data API | ✅ gleiche Regel `ContentPolicy.canPlayFromPlaylist` (ADR 0002) | nein (P2): Android zeigt längere Playlists nur bis Eintrag 15 | `PlaylistModel`, `PlaylistPlayability.swift`; `KidViewModel.loadPlaylist`, `KidAbspielbar.kt` |
| Videoplayer | ✅ WKWebView + IFrame-API, `youtube-nocookie`, **eigene Steuerung (`controls: 0`)**, eine WebView je Sitzung | ✅ WebView + IFrame-API, `youtube-nocookie`, **eigene Steuerung (`controls: 0`)**, eine WebView je Screen | ✅ **neu** | nein | `YouTubePlayerBridge.swift`, `VideoPlayerScreen.kt` |
| Play/Pause | ✅ eigene Taste + Rad | ✅ **neu** eigene Taste + Select-Taste | ✅ | nein | – |
| Spulen | ✅ Fortschrittsbalken, ±10 s, Rad (hoch = zurück, runter = vor) | ✅ Ring hoch/runter spult ±10 s (SideUI ADR 0015, `KidViewModel.onKey` → `PlayerBridge.seekBy`); kein Fortschrittsbalken, Position als Text („1:23“) | ⚠️ gleiche Tasten, Android ohne Balken | nein | `PlayerScrubBar`; `KidViewModel.kt`, `PlayerView.kt` |
| Vor/Zurück in der Warteschlange | ✅ zyklisch über die Liste, aus der gestartet wurde | ✅ linear über Videos desselben Kanals | ⚠️ | ja (P2): Android-Warteschlange aus der Startliste bilden | `PlayerModel.swift`, `VideoPlayerViewModel.kt` |
| Vollbild | ✅ Querformat oder Taste, eigener Exit-Knopf | ✅ **neu** app-eigen: Taste, Querformat erzwungen, System-Zurück beendet es | ✅ **neu** | nein | – |
| **Videoende** | ✅ `stopVideo()` → „Fertig 🎉“, ≤ 3 Vorschläge, Nochmal, Zurück; auch im Vollbild | ✅ `stopVideo()` → „Fertig 🎉“, ≤ 3 Vorschläge, Nochmal, Zurück; Vollbild wird verlassen | ✅ **neu** | nein | `PlayerEndedView`, `VideoEndedPanel` |
| Empfehlungen/Vorschauen | ✅ nur `NextUpPolicy` (Warteschlange → Kanal → Freigaben) | ✅ nur `UpNextPolicy` (gleiche Regel) | ✅ **neu** | nein | `NextUpPolicy.swift`, `UpNextPolicy.kt` |
| Fremdvideo-Sperre im IFrame | ✅ `foreign`-Ereignis, Stop in der Seite | ✅ `onForeignVideo`, Stop in der Seite | ✅ **neu** | nein | `Player.html`, HTML in `VideoPlayerScreen.kt` |
| Autoplay nächstes Video | ✅ Elternoption, Standard aus | ❌ immer aus | ⚠️ | nein (Standard identisch) | `KidProfile.autoplayNext` |
| Whitelist-Verhalten | ✅ Freigabestatus (`reviewRequired` → `approved`), Prüfschleife | ⚠️ **neu** Freigabestatus je Eintrag und Sichtbarkeitsprüfung; neue Einträge gelten weiter sofort als freigegeben (keine Prüfschleife) | ⚠️ | ja (P1): Prüfschleife und Risikofilter nach Android | `ContentPolicy.swift`, `ContentPolicy.kt` |
| Profile | ✅ mehrere, Wechsel im Kindermodus per Menü | ✅ mehrere, Profilwahl-Screen | ✅ | nein | – |
| Altersfilter | ✅ `AgeBand`, `ageMin/ageMax`, Kategorie-Mindestalter | ✅ **neu** dieselben Regeln und Rohwerte | ✅ **neu** | ja (P2): Elternoberfläche zum Setzen von Alter und Kategorie je Video fehlt | `ContentPolicy.swift`, `ContentPolicy.kt` |
| Kategorien | ✅ Taxonomie aus `content/schema` | ✅ **neu** dieselbe Taxonomie (`Curation.kt`), Kategorie-Sektionen auf Home fehlen | ⚠️ | ja (P2) | `Curation.swift`, `Curation.kt` |
| Zeitlimit | ✅ Tageslimit, Live-Sekunden, Overlay | ✅ Tageslimit, Restzeit-Anzeige, Overlay | ✅ | ja (P2): iOS zeigt keine Restzeit im Kindermodus | `KidRootView.swift`, `TimeLimitChecker` |
| Ruhezeiten | ✅ Overlay, **Vorwarnung 15/5 min (neu)**, Uhrzeit im Overlay (neu), Wochenend-Offset, Eltern-Ausnahme | ✅ Overlay, Vorwarnung, Wochenend-Offset, Eltern-Ausnahme | ✅ | nein | `Bedtime.swift`, `BedtimeUi.kt` |
| Sleep Timer | ✅ Fade-out, Schlaf-Playlist, Overlay | ✅ Fade-out, Overlay | ✅ | nein | `SleepTimer.swift`, `SleepTimerManagerImpl.kt` |
| Parent Area | ✅ Profile, Prüfschleife, Quellen/Vertrauen, Statistik, Schlafmodus | ✅ Profile, Freigaben, **Quellen und Vertrauensstufen (neu)**, YouTube-Browser, Kanal-Suche, Statistik, Export/Import, Startpakete | ⚠️ | ja (P1): Android ohne Prüfschleife; iOS ohne Export/Import | `SourceTrustView.swift`, `SourceTrustScreen.kt` |
| PIN | ✅ PBKDF2, Keychain, Lockout persistent | ✅ PBKDF2, Room, Lockout persistent | ✅ | nein | `PINManager.swift`, `Pbkdf2PinHasher.kt` |
| Freigaben (Approve/Reject) | ✅ mit Alter, Kategorie, Audit-Trail | ❌ | ❌ | ja (P1) | `CurationRepository.swift` |
| Review Queue | ✅ | ❌ | ❌ | ja (P1) | `ReviewQueueView.swift` |
| Trust Levels | ✅ 5 Stufen aus `content/sources.json` | ✅ **neu** dieselben 5 Stufen, Register aus derselben Datei, Elternansicht *Quellen und Vertrauensstufen* | ✅ **neu** | nein | `Curation.swift`, `SourceTrustScreen.kt` |
| Risk Screen | ✅ `content/risk-terms.json` | ❌ (Datei liegt jetzt in der App, wird aber nicht ausgewertet) | ❌ | ja (P1) | `RiskScreen.swift` |
| Startpakete | ✅ aus `content/libraries` | ✅ **repariert**: die Dateien lagen nie im APK, jetzt kopiert sie der Build | ✅ | nein | `SeedLibraryImporter.swift`, `StarterPackServiceImpl.kt` |
| Lokale Speicherung | ✅ SwiftData | ✅ Room | ✅ | nein | – |
| Sehverlauf | ✅ Einträge je Wiedergabe, **nur noch erlaubte Videos sichtbar (neu)** | ✅ Heartbeat alle 15 s, Whitelist-gefiltert | ✅ | ja (P2): Android-Heartbeat auf Aufsummieren umstellen | `KidRows.playableVideoIds`, `KidHomeViewModel.kt` |
| YouTube-Datenquellen | ✅ oEmbed, RSS (Kanal und Playlist), Kanalseite, optional Data API | ✅ oEmbed, RSS (Kanal und Playlist), Kanalseite; keine Data API, **kein Invidious** | ⚠️ Android ohne Data API | nein | `YouTubeRepository.swift`; `core/provider/YouTubeSources.kt` |
| PeerTube | ✅ Instanz-Allowlist, eigener Player | ❌ | ❌ | ja (P2) | `PeerTubePlayerBridge.swift` |
| Ladezustände | ✅ Spinner, „Lädt …“ | ✅ Spinner | ✅ | nein | – |
| Fehlermeldungen | ✅ **kindgerecht (neu)**: „Das hat nicht geklappt. Ist das Internet an?“ | ✅ **neu** derselbe Satz im Kindermodus; Erwachsenentexte bleiben im Elternbereich | ✅ **neu** | nein | `KidModels.swift`, `kid_error_generic` |
| Offline-/Netzwerkverhalten | ⚠️ Whitelist offline, Kanal-Cache; Player ohne Netz meldet „lädt nicht“ | ✅ **neu** Zeitgrenze von 8 s im Player, danach eine Meldung mit „Nochmal“ und „Zurück“ | ✅ **neu** | nein: Playlist-Inhalte kommen auf beiden Seiten offline aus dem Zwischenspeicher | `VideoPlayerScreen.kt` |
| Sidephone-Steuerung | ✅ virtuelles Click-Wheel (Sheet) | ✅ Hardwaretasten via `SideInputChannel`, Fokusring | ⚠️ | ja – Belegung nach SideUI angleichen, siehe [design/sideui.md](design/sideui.md) | `RemoteController.swift`, `SideInputAction.kt` |
| Accessibility | ✅ Labels, Header, Reduce Motion, **Overlay modal (neu)** | ⚠️ contentDescriptions, **Headings + doppelte Ansagen behoben (neu, Home/Player)** | ⚠️ | ja (P2) | siehe `UX_REVIEW.md` |
| Empfehlen/Deep Link | ✅ `sidetube://add` hinter PIN | ❌ | ❌ | nein (Produktentscheidung offen) | `Recommend.swift` |
| Google-Login | ❌ | ❌ **entfernt** samt Token-Speicher und Client-Secret | ✅ | nein | – |
| Geteilte `content/`-Daten | ✅ Taxonomie, Quellen, Risikobegriffe, Bibliotheken | ✅ **neu** alle vier Dateien im APK; ausgewertet werden Quellen und Bibliotheken | ⚠️ | ja (P1): Risikobegriffe auswerten | `ContentBundle.swift`, `SourceRegistryImporter.kt` |

## 2. Muss identisch sein, ist es aber nicht (priorisiert)

1. **Prüfschleife und Risikofilter (P1).** Android kennt jetzt Freigabestatus, Vertrauensstufen, Alter und Kategorien,
   aber ein neu hinzugefügter Eintrag gilt sofort als freigegeben. Auf iOS geht er erst durch die Redaktionsliste, und
   der Risikofilter kann hart ablehnen. `content/risk-terms.json` liegt auf Android in der App, wird aber nicht gelesen.
2. **Redaktionsoberfläche (P1).** Alter, Kategorie und Nachrichtenstatus lassen sich auf Android je Eintrag speichern,
   aber nicht setzen — es fehlt die Eltern-Ansicht dafür. Die Vertrauensstufe je Quelle ist bereits einstellbar.
3. ~~**Datenquellen im Kindermodus (P2).**~~ Erledigt: Die neu aufgebaute Android-App nutzt keine Invidious-Instanzen
   mehr, nur oEmbed, die YouTube-Feeds und die Kanalseite (Stand 03.10.2026).
4. **Bezeichnungen (P1).** „Kanäle/Playlists“ (iOS) vs. „Abos/Sendungen“ (Android); „Mediathek“ (iOS) hat auf Android kein
   Gegenstück. Empfehlung: „Kanäle“ und „Sendungen“ auf beiden Seiten, „Mediathek“ → „Alle Videos“.
5. ~~**Startseiten-Reihenfolge.**~~ Erledigt: „Weiterschauen“ steht auf beiden Seiten oben, und auf iOS deckt sich die
   Reihenfolge jetzt mit der Radauswahl.
6. **Warteschlange (P2).** iOS spielt zyklisch die Liste, aus der gestartet wurde; Android nur Videos desselben Kanals.

Neu **identisch** seit diesem Review: Verhalten am Videoende, Vorschlagsregel, Fremdvideo-Sperre, Privacy-Host des Players,
Ruhezeit-Vorwarnung, Overlays als echte Sperren, Sehverlauf nur mit erlaubten Videos, Freigabeprüfung samt Vertrauensstufen
und Altersfilter, kindgerechte Fehlertexte, Verhalten ohne Netz im Player, Bezeichnungen und Startseiten-Reihenfolge.

## 3. Plattformspezifisch – in Ordnung

- iOS: Tabs, Full-Screen-Cover, Bottom-Sheet-Fernbedienung, Doppeltipp/Querformat für Vollbild, Dynamic Type, VoiceOver-Traits.
- Android: ein Navigationsstack mit System-Back, YouTube-Vollbildknopf mit erzwungenem Querformat, Hardwaretasten mit
  Fokusring, TalkBack-Semantik, Material-3-Komponenten.
- Der iOS-„Fertig“-Zustand liegt im Vollbild als Panel über dem Video; Android verlässt das YouTube-Vollbild und zeigt das
  Panel unter dem Video. Beides führt zum gleichen Ergebnis: ein klarer Zustand mit denselben drei Wegen.
