# Side Navigation Model

Status: Entwurf nach Quellcodeanalyse, 13.08.2026

Dieses Modell beschreibt die Bedienlogik für SidePlay und den YouTubeWhitelist-
Fork auf dem Sidephone SP-01. SidePlay ist die Referenz. Die Implementierung
darf Whitelist- und Elternschutz nicht zugunsten der Bedienparitaet abschwaechen.

## Ebenen

Der Kindermodus verwendet hoechstens vier Ebenen:

1. Start
2. Sammlung oder Liste
3. Inhalt oder Detail
4. Player

Der Elternmodus ist kein Kindermenue. Er wird ausschliesslich nach erfolgreicher
PIN-Pruefung betreten und besitzt einen eigenen Navigationsstapel.

## Tasten

| Taste am SP-01 | Aktion | Regel |
|---|---|---|
| DPAD_UP | Fokus vorher | Visuelle Reihenfolge, kein zyklischer Sprung |
| DPAD_DOWN | Fokus naechstes | Am Ende bleibt der Fokus sichtbar |
| MEDIA_PREVIOUS | Fokus vorher / Player zurueck | Kontext wird sichtbar angezeigt |
| MEDIA_NEXT | Fokus naechstes / Player vor | Kontext wird sichtbar angezeigt |
| MEDIA_PLAY_PAUSE oder DPAD_CENTER | Auswaehlen, im Player Play/Pause | Kurzer Druck |
| DPAD_LEFT, BACK, TAB, ESCAPE, STAR | Eine Ebene zurueck | Lange Variante geht zum Start |
| DPAD_RIGHT | Suche oeffnen | Abkuerzung, ersetzt keine Auswahl |
| Ziffern 0-9 | T9-Eingabe in der Suche | Keine Doppelbelegung waehrend Texteingabe |
| Touch | Entspricht Fokus und Mitteltaste | Keine exklusiven Touch-Funktionen |

Die Hardware-Keycodes werden in einer zentralen Abstraktion behandelt. Screens
duerfen keine eigenen Android-Keycodes interpretieren.

Am Geraet ausgelesen (`getevent -pl`, `/system/usr/keylayout/Generic.kl`):

- `gxa535_keyboard` liefert KEY_0 bis KEY_9, KEY_BACKSPACE, KEY_ENTER,
  KEY_UP/DOWN/LEFT/RIGHT, KEY_BACK, KEY_ESC, KEY_TAB, die Medientasten sowie
  KEY_NUMERIC_STAR und KEY_NUMERIC_POUND.
- Der runde Bedienbereich ist **keine Drehscheibe**. Kein Eingabegeraet meldet
  REL- oder Drehachsen; es gibt nur die ABS-Koordinaten des Touchpanels. Der
  Kreis liefert Richtungstasten und ueber `sitronix_ts_i2c` zusaetzlich HOME,
  MENU und BACK.
- Die Mitteltaste meldet **KEY_ENTER**, nicht DPAD_CENTER. Wer nur auf
  DPAD_CENTER hoert, bekommt den zentralen Druck nie.

Zurueck und Start werden immer von der Navigation verarbeitet. Alle uebrigen
Aktionen erhaelt nur der Screen, der gerade im Vordergrund ist; hat kein Screen
eine Tastenbehandlung, bleibt das normale Android-Verhalten erhalten. Damit
bleiben Texteingaben im Elternbereich bedienbar.

## Fokus

- Beim Oeffnen steht der Fokus auf der wichtigsten sicheren Aktion.
- Der Fokus ist immer sichtbar, auch nach Touchbedienung.
- Gelb markiert den Fokus und wird zusaetzlich durch Rahmen oder Anhebung
  kenntlich gemacht.
- Loeschen, Freigeben und Elternzugang erhalten nie den Startfokus.
- Nach Zurueckkehren wird der vorherige sinnvolle Fokus restauriert.
- Listen scrollen nur so weit, dass der Fokus vollstaendig sichtbar bleibt.

## Kindstart

Die Kinderansicht zeigt nur stabile, freigegebene Bereiche:

- Weitersehen
- Meine Abos
- Meine Videos
- Meine Sendungen
- Suchen
- Auf dem Telefon, falls genehmigte Downloads vorhanden sind

Weitersehen enthaelt die zuletzt gesehenen Inhalte. Ein Eintrag erscheint nur,
solange er selbst freigegeben ist oder zu einem freigegebenen Abo gehoert.
Die genaue Abspielposition wird noch nicht gespeichert; ein Eintrag startet das
Video von vorne. Downloads gibt es in dieser Version nicht.

Der Startfokus liegt auf dem ersten Eintrag von Weitersehen, sonst auf der
ersten Sammlung. Die Suche ist ueber die Fokusreihenfolge, DPAD_RIGHT und Touch
erreichbar.

Es gibt keinen allgemeinen YouTube-Feed, keine Trends, Shorts, Kommentare,
Livestream-Chats, fremde Abos oder technischen Einstellungen.

## Suche

Die Suche arbeitet ausschliesslich gegen lokale, dem aktiven Kinderprofil
zugeordnete Whitelist-Daten. T9-Eingabe beginnt nach dem Oeffnen unmittelbar.
Ein Suchtreffer darf nur zu einem bereits freigegebenen Abo, Video oder einer
freigegebenen Playlist fuehren.

Auch die Suche innerhalb eines Abos arbeitet per T9 gegen den lokalen
Videocache dieses Abos. Die Bildschirmtastatur erscheint nur, wenn die Suche
per Touch geoeffnet wurde; bei Tastenbedienung bleibt T9 aktiv.

## Player

- Mitteltaste toggelt Play/Pause.
- Ein nicht einbettbares Video zeigt eine sichtbare Meldung mit Erneut-Versuchen
  und Zurueck; es wird nie stumm auf einen anderen Inhalt gewechselt.
- Die gesehene Zeit wird bei Pause, Ende und alle 15 Sekunden fortgeschrieben,
  damit Zeitlimits auch bei abgebrochenen Videos stimmen.
- Zurueck verlaesst zuerst den Player und loescht nichts.
- Folgeinhalte werden nur aus der aktuell freigegebenen Sammlung erzeugt.
- Kein automatischer Sprung zu Empfehlungen oder fremden Abos.
- Autoplay ist standardmaessig aus. Eine spaetere Fortsetzung ist nur innerhalb
  einer explizit freigegebenen Playlist und nach Elternkonfiguration zulaessig.
- WebView-Navigation und externe Intents bleiben im Kindmodus blockiert.

## Elternbereich

Der Elternbereich ist nur ueber einen expliziten, sicheren Zugang mit PIN
erreichbar. Ein Langdruck allein ist niemals ein Administrationsweg. Falsche
PINs loesen die bestehende PBKDF2- und Brute-Force-Logik aus.

## Visuelle Tokens

Der Fork uebernimmt die SidePlay-Semantik, nicht YouTube-Markenfarben:

- dunkler Hintergrund
- helle Hauptschrift
- Gelb nur fuer Fokus und zentrale Aktion
- Rot nur fuer echte Warnungen oder destruktive Elternaktionen
- grosse Cover, zwei Spalten maximal auf 320 dp
- Titel maximal zwei Zeilen
- ruhige, kurze Animationen

## Beispiele

### Video starten

`Start -> Meine Abos -> Abo -> Video -> Mitteltaste`

### Pause und zurueck

`Mitteltaste -> Zurueck`

### Suche

`Start -> Suchen -> T9-Eingabe -> Fokus auf Treffer -> Mitteltaste`

### Zum Start

Kurze Zurueck-Taste geht eine Ebene zurueck. Lange Zurueck-Taste geht direkt
zum Start und fuehrt keine administrative Aktion aus.

## Wording

Die Oberflaeche ist durchgaengig deutsch. Verbindliche Begriffe:

| Begriff | Bedeutung | Nicht verwenden |
|---|---|---|
| Abo | freigegebener YouTube-Kanal | Kanal, Channel |
| Meine Abos, Meine Videos, Meine Sendungen | Bereiche im Kindstart | Channels, Playlists |
| Weitersehen | zuletzt gesehene Inhalte | Continue watching |
| Freigabe, freigeben, freigegeben | Whitelist-Eintrag und -Vorgang | Whitelist, whitelisted |
| Elternbereich | PIN-geschuetzter Bereich | Parent Mode, Dashboard |
| Sichern und Wiederherstellen | Export und Import der Daten | Export/Import |
| Restzeit, Tageslimit, Sehzeit | Zeitbegriffe | Time remaining, Watch time |
| Einschlaf-Timer, Schlafmodus | Schlaffunktion | Sleep timer |

Technische Bezeichner im Code (channelId, WhitelistItem, playlist) bleiben
englisch; die Regel gilt fuer sichtbaren Text, auch fuer Fehlermeldungen.
