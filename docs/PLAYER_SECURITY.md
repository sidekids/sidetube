# Player-Sicherheit: Videoende, Endscreen und Fremdvideos

> Historisches Dokument: Aussagen und Implementierungspfade können den früheren Stand beschreiben. Maßgeblich sind der [aktuelle Release-Audit](release/pre-release-audit.md) und seine offenen Gates; dieses Dokument ist keine Release-Freigabe.

Stand: 2026-09-08. Gilt für beide Apps; Belege zeigen auf die jeweiligen Dateien.

## 1. Ausgangslage: „Vorschauen am Videoende sind nicht anklickbar“

Der Befund war auf beiden Plattformen reproduzierbar, hatte aber je eine andere Ursache.

### iOS (WKWebView, `Player.html` + IFrame-API)

| Schritt | Was passierte | Beleg |
|---|---|---|
| Video endet | IFrame meldet Zustand 0 (`ended`) | `ios/Sources/Features/Player/PlayerModel.swift`, `handle(.state(0))` |
| sofort danach | App ruft `engine.stop()` → `player.stopVideo()`; IFrame fällt in Zustand −1/5 (cued) | dieselbe Stelle; Diagnose-Log im Simulator: `state 0 → -1 → 5` innerhalb derselben Sekunde |
| Anzeige | YouTube zeigt das Vorschaubild mit rotem Play-Knopf – der Endscreen ist weg oder inert | Screenshot Simulator iPhone 17, 2026-09-06 |
| Klick auf Kacheln | nichts – es gibt keine mehr; der rote Knopf startet nur dasselbe Video neu | – |
| Navigation aus dem IFrame | Hauptframe gesperrt, `target=_blank` → `nil` | `YouTubePlayerBridge.swift`, `decidePolicyFor`, `createWebViewWith` |

Das war **kein Bug, sondern eine bewusste Sicherheitsentscheidung** („Ende → `stopVideo()` + bewusste Wahl“, `docs/content-safety.md`). Die UX-Folge war allerdings eine Sackgasse: im Hochformat nur eine schmale Zeile „Fertig geschaut.“, im Vollbild gar kein sichtbarer Ausgang.

### Android (WebView, HTML aus `VideoPlayerScreen.kt`)

| Schritt | Was passierte | Beleg |
|---|---|---|
| Video endet | `onStateChange(0)` bucht nur Sehzeit | `VideoPlayerViewModel.onVideoEnded` (alte Fassung) |
| Anzeige | Player bleibt im Zustand `ended`; YouTube blendet seinen Endscreen (Vorschläge desselben Kanals, `rel=0`) ein | HTML in `VideoPlayerScreen.kt` |
| Klick auf Kacheln | `shouldOverrideUrlLoading` gibt pauschal `true` zurück und blockt jede Navigation, also auch Klicks, die eine Navigation im IFrame auslösen | `VideoPlayerScreen.kt`, `WebViewClient` |
| Restrisiko | Klicks, die YouTube **ohne Navigation** in-place abspielt, wären nicht geblockt gewesen – die App kannte die laufende Video-ID nie | Bridge meldete nur Zustand und Sekunden |

Auch hier kein Zufall, sondern die Sperre gegen „Auf YouTube ansehen“. Aber ohne eigenen Zustand nach dem Ende und ohne Kontrolle über das, was der IFrame tatsächlich spielt.

## 2. Sicherheitsfrage: Klick-Validierung im IFrame (Variante B)?

Bewertet und **verworfen**:

- Die IFrame-API liefert keine Klick-Ereignisse und keine Ziel-ID vor dem Start; erst `getVideoData().video_id` nach dem Wechsel verrät das neue Video.
- Endscreen-Elemente sind vom Kanalbetreiber frei belegbar (beliebige Videos, Playlists, Kanäle). `rel=0` beschränkt seit 2018 nur den Endscreen auf denselben Kanal, nicht die Endscreen-Elemente.
- Eine Validierung nach dem Start ist immer ein Wettlauf: Bild und Ton des fremden Videos wären für einen Moment sichtbar.
- Jede Änderung am Embed durch YouTube könnte die Erkennung still aushebeln.

Deshalb gilt Variante **A + C**: eigene Vorschläge, YouTube-Endscreen unterdrückt, und – als Verteidigung in der Tiefe – eine Fremdvideo-Sperre, falls der IFrame trotzdem etwas anderes startet.

## 3. Umgesetzte Architektur (beide Plattformen identisch)

```
Video endet (Zustand 0)
   │
   ├─ Seite/App ruft stopVideo()          → kein YouTube-Endscreen, Vorschaubild
   ├─ Sehzeit wird gebucht
   └─ App zeigt „Fertig 🎉“
        ├─ Als Nächstes: ≤ 3 Karten aus NextUpPolicy / UpNextPolicy
        │     1. nächste Einträge der Warteschlange (zyklisch)
        │     2. freigegebene Videos desselben Kanals
        │     3. weitere freigegebene Videos (neueste zuerst)
        ├─ Nochmal   → dasselbe Video von vorn
        └─ Zurück zu den Videos → Player schließen
```

Nur `visibleItems` (iOS, `ContentPolicy`) bzw. die Whitelist des Profils (Android) liefern Kandidaten. Die Regel kennt keine Datenbank und ist auf beiden Seiten mit denselben Fällen getestet (`ios/Tests/NextUpPolicyTests.swift`, `android/.../domain/UpNextPolicyTest.kt`).

### Fremdvideo-Sperre (Verteidigung in der Tiefe)

Die Player-Seite merkt sich die zuletzt angeforderte Video-ID (`expectedVideoId`). Bei jedem Zustandswechsel in *playing/buffering* und bei jedem Heartbeat vergleicht sie `player.getVideoData().video_id` damit. Weicht die ID ab:

1. `player.stopVideo()` sofort in der Seite (kein Rundlauf über die App),
2. Ereignis `foreign` an die App,
3. App: nichts wird gebucht, die fremde ID landet nie in der Warteschlange, Zustand „Fertig“ mit eigenen Vorschlägen.

| | iOS | Android |
|---|---|---|
| Seite | `ios/Sources/Resources/Player.html`, `guardForeignVideo()` | HTML in `VideoPlayerScreen.kt`, `guardForeignVideo()` |
| Bridge | `PlayerEngineEvent.foreignVideo(String)` | `VideoEndedBridge.onForeignVideo(String)` |
| Logik | `PlayerModel.handle(.foreignVideo)` | `VideoPlayerViewModel.onForeignVideo` |
| Test | `foreignVideoFromIframeIsStoppedAndNeverBecomesCurrent` | `foreign video from the iframe ends in the done state …` |

Nach `stopVideo()` meldet `getVideoData()` weiter das alte Video, deshalb wird nur in den Zuständen 1/3 geprüft – sonst würde die Sperre sich selbst auslösen.

### Eigene Steuerung statt YouTubes Leiste

Beide Player laufen jetzt mit `controls: 0`. Das ist zuerst eine Bedienfrage — YouTubes Leiste ist auf einem kleinen
Display kaum zu treffen —, aber es nimmt dem Embed auch drei Wege nach draußen: den Titel- und Kanalverweis, den
YouTube-Vollbildknopf und das Vorschlagsraster, das beim Pausieren über dem Bild erscheint.

Ersetzt wird sie durch eine App-Leiste mit Fortschrittsbalken (Ziel ≥ 44 pt / 48 dp), ⏮, −10 s, ⏯, +10 s, ⏭ und
Vollbild. Die Stelle liefert die IFrame-API zweimal pro Sekunde (`getCurrentTime`, `getDuration`); gesprungen wird mit
`seekTo`. Diese Meldung dient **nur der Anzeige** — die Sehzeit buchen weiterhin die Zustandswechsel und der grobe
Takt, damit das Tageslimit nicht an einem 500-ms-Ticker hängt.

Auf Android schaltet damit die App das Vollbild selbst (Querformat erzwingen, Systemleisten ausblenden), statt auf
`WebChromeClient.onShowCustomView` zu warten. Die WebView bleibt dabei derselbe Knoten im Layout und wird nur
umgehängt — sonst begänne das Video von vorn. Am Sidephone spulen Rad hoch und runter um zehn Sekunden.

| | iOS | Android |
|---|---|---|
| Seite | `Player.html`, `controls: 0`, `seekTo`, 500-ms-Takt | HTML in `VideoPlayerScreen.kt`, identisch |
| Ereignis | `PlayerEngineEvent.position(seconds:duration:isPlaying:)` | `AndroidBridge.onTimeUpdate` |
| Zustand | `PlayerModel.currentSeconds/durationSeconds/isPlaying` | `VideoPlayerViewModel.position` (eigener Fluss, damit nur der Balken neu zeichnet) |
| Bedienung | `PlayerScrubBar` + sechs Tasten | `PlaybackControls` + sechs Tasten |
| Vollbild | Querformat, Doppeltipp oder Taste | App-eigen, Taste oder System-Zurück |

### Weitere Härtungen

- **`youtube-nocookie.com`** als Player-Host (Privacy-Enhanced Mode) auf beiden Plattformen und auf iOS ein nicht-persistenter `WKWebsiteDataStore`. Auf Android sind Drittanbieter-Cookies **weiterhin zugelassen**; der prozessweite Cookie-Bestand wird lediglich zu Beginn und zum Ende jeder Player-Sitzung geleert (`PlayerCookiePolicy`). Eine Verknüpfung über Sitzungen hinweg ist damit erschwert, aber nicht nachgewiesen ausgeschlossen — die Messung am Gerät steht aus.
- **Schließen stoppt**: `PlayerModel.close()` ruft `engine.stop()`, damit nach „Fertig“ kein Ton weiterläuft (die WebView lebt für die nächste Sitzung weiter).
- **Video-IDs** erreichen die Android-Seite nur noch gefiltert (`safeVideoId`, auch beim Erstaufbau).
- **Logging** der JS-Ereignisse auf iOS mit `privacy: .private`.

## 4. Verwandte Lücken, die im selben Zug geschlossen wurden

| Lücke | Plattform | Behebung |
|---|---|---|
| Sehverlauf umging die Freigabe: einmal gesehene, später entzogene Videos blieben unter „Zuletzt geschaut“ abspielbar | iOS | `KidRows.playableVideoIds` + `recentlyWatched(allowed:)`; Test `recentlyWatchedHidesRevokedVideos`. Android filterte bereits. |
| Alter Kanal-Cache nach Abstufung einer Quelle blieb sichtbar | iOS | `ChannelModel.onAppear` leert den Cache, wenn Stöbern nicht erlaubt ist; zeigt stattdessen die Einzelfreigaben des Kanals. Test `perVideoSourceListsOnlyApprovedVideosAndClearsStaleCache`. |
| Kanal-Stöbern ignorierte das Profilalter (Vertrauensstufe ist geräteweit) | iOS | `browsingAllowed` prüft `defaultAgeMin ≤ Profilalter`. |
| Overlays „Zeit ist um“/„Gute Nacht“ ließen Touch und Tasten durch | Android | `Modifier.blockInput()` auf allen Overlays; Tasten werden bei Sperre ignoriert; Warteschlangenwechsel unter Sperre pausiert sofort. |
| Auto-Backup exportierte die Datenbank (PIN-Hash, Namen, Verlauf) | Android | `android:allowBackup="false"`. |

## 5. Was bewusst offen bleibt

- **Endscreen-Elemente in den letzten 20 Sekunden** eines Videos zeigt YouTube weiterhin im IFrame (nicht abschaltbar). Ein Tipp darauf wird jetzt erkannt und gestoppt; ein kurzes Aufblitzen ist möglich. Verifiziert ist die Sperre gegen einen simulierten IFrame-Wechsel (Unit-Tests); ein Gerätetest mit einem Video, das Endscreen-Elemente trägt, steht aus.
- **Playlist-Inhalte** kommen live von YouTube; der Besitzer kann später Videos ergänzen (siehe `content-safety.md`). Ein Snapshot bei der Freigabe ist der nächste Schritt.
- **Android hat keine `ContentPolicy`** (Vertrauensstufen, Alter, Prüfschleife). Alles, was in der Whitelist steht, ist dort sichtbar. Siehe `CROSS_PLATFORM_PARITY.md`.
