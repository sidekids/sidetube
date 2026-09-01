# SidePlay / YouTubeWhitelist Vergleich

Stand: 13.08.2026. Verglichen wurden SidePlay Commit `4b95106` und
YouTubeWhitelist Commit `ffcf951` (Upstream `degipe/YouTubeWhitelist`, Version
1.1.0). Der Vergleich ist eine Quellcodeanalyse; reale YouTubeWhitelist-
Tastentests auf dem SP-01 stehen noch aus.

## Ausgangszustand

### SidePlay

- Kotlin, Compose Material 3, Navigation Compose
- zentraler `Tastenkanal` und `InputActionMapper`
- API 31 Mindestversion, Zielgeraet 480x640 px bei 320x427 dp
- dunkle feste Farbwelt mit gelbem Fokus
- Kinderoberflaeche mit Start, Bereichen, Suche und Player
- Player ueber Media3
- lokale Room-Daten und Offline-Downloads
- Elternbereich getrennt von der Kinderoberflaeche

### YouTubeWhitelist

- Kotlin, Compose Material 3, zehn Gradle-Module
- MVVM/Clean Architecture, Hilt, Room, Retrofit/WebView
- Kindstart mit Kanaelen, Videos, Playlists und Suche
- Kindersuche lokal gegen Room-Whitelist
- WebView-IFrame-Player mit automatischem Start
- Android LockTask im Kindmodus
- PIN mit PBKDF2WithHmacSHA256 und Brute-Force-Sperre
- Datenbank nutzt `fallbackToDestructiveMigration()`
- aktueller Kindstart verwendet vertikales Scrollen und horizontale Carousels
- zentrale Hardware-KeyEvent-Abstraktion fehlt

## Funktionsvergleich

| Funktion | SidePlay | YouTubeWhitelist | Ziel |
|---|---|---|---|
| Weiterhören | vorhanden | nur indirekt ueber Inhalte/Verlauf | sichtbarer Startbereich |
| Sammlung | Bereiche | Kanaele/Playlists | vier Ebenen |
| Suche | T9 und lokale Treffer | lokale Room-Suche, Touch-Feld | T9 ohne Touch |
| Player | Media3, zentrale Tasten | WebView-IFrame | gemeinsame Play/Pause-Logik |
| Offline | normaler Betriebsfall | Metadaten lokal, Wiedergabe netzabhaengig | Offlinezustand explizit anzeigen |
| Elternzugang | separater Bereich | PIN + LockTask-Aufhebung | Schutz erhalten, nicht per Langdruck |

## Sicherheitsvergleich

Erhalten werden muessen:

- Whitelist pro Kinderprofil
- lokale Room-Speicherung
- PBKDF2-Hash mit Salt und konstantem Vergleich
- Brute-Force-Sperren
- LockTask im Kindmodus
- Blockierung von Player-WebView-Navigation
- lokale Kindersuche ohne allgemeine YouTube-Suche

Vor einer Freigabe fuer Kinderbetrieb muessen zusaetzlich geprueft werden:

- automatische Player-Folgewechsel ausschliesslich innerhalb freigegebener
  Inhalte
- keine fremden Empfehlungen im IFrame
- keine externen Intents aus Kindansichten
- keine ungewollte WebView-Navigation durch JavaScript oder Fullscreen
- Export/Import nur im Elternmodus
- Datenbankmigration ohne Verlust bestehender Whitelist-Daten

## Navigations-Paritaet

| Aufgabe | SidePlay-Ziel | YouTubeWhitelist-Ziel | Identisch |
|---|---|---|---|
| App oeffnen | Fokus auf Weiterhoeren | Fokus auf Weitersehen | ja |
| Inhalt fortsetzen | Mitteltaste | Mitteltaste | ja |
| Sammlung oeffnen | Fokus + Mitteltaste | Fokus + Mitteltaste | ja |
| Inhalt starten | Fokus + Mitteltaste | Fokus + Mitteltaste | ja |
| pausieren | Mitteltaste | Mitteltaste | ja |
| zur Sammlung zurueck | Zurueck | Zurueck | ja |
| suchen | Suche-Aktion, T9 | Suche-Aktion, T9 | Ziel |
| zum Start | lange Zurueck-Taste | lange Zurueck-Taste | Ziel |

## Umgesetzter Stand des Forks (14.08.2026)

Im Branch `side-navigation-model` ist umgesetzt:

- zentrale Tastenabstraktion in `core:common/input` (Mapper, Kanal, Fokus, T9)
- Tastenbedienung auf Start, Suche, Kanal, Playlist, Player, PIN und
  Elternuebersicht; Zurueck und Start liegen bei der Navigation
- Weitersehen auf dem Kindstart, gegen Whitelist und Kanalcache aufgeloest
- T9 in der Kindersuche und in der Kanalsuche, Bildschirmtastatur nur bei Touch
- Player ohne Autostart und ohne automatischen Folgewechsel, Mitteltaste
  toggelt, Embed-Fehler wird sichtbar gemeldet, Wiedergabezeit wird fortlaufend
  verbucht
- lokales Elternkonto statt Google-Anmeldung, App-Name SideTube
- feste dunkle Farbwelt mit gelbem Fokus, kein Dynamic Color

Noch offen: Downloads, gespeicherte Abspielposition fuer Weitersehen, Ersatz von
`fallbackToDestructiveMigration()`, Geraetetests auf dem SP-01 nach dem
Testplan, sowie die Entscheidung ueber Paketnamen, Repository und Store-Auftritt
des Forks. BRD, FS, HLD und LLD sind nur an den Punkten angepasst, an denen sie
dem Code widersprochen haben.

## Bewusst erhaltene Unterschiede

- YouTubeWhitelist bleibt inhaltlich eine Whitelist-Video-App und wird nicht als
  Musik- oder Podcast-App modelliert.
- Eltern duerfen weiterhin Inhalte ueber den Elternbrowser suchen und freigeben.
- Die YouTube-Wiedergabe bleibt technisch WebView-basiert, bis ein sicherer
  Player ohne diese Abhaengigkeit vorhanden ist.
- Online-Metadatenaktualisierung bleibt moeglich; Offline darf nicht als
  vollstaendige Offline-YouTube-Wiedergabe behauptet werden.
