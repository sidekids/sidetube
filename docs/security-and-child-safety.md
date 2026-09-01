# Security and Child Safety

Stand: 13.08.2026. Dieses Dokument bewertet den analysierten Upstream und legt
Grenzen fuer den Sidephone-Fork fest.

## Schutzmodell

Im Kindermodus darf ein Inhalt nur abgespielt werden, wenn seine ID dem aktiven
Kinderprofil als Kanal, Video oder Playlist freigegeben ist. UI-Ausblendung
allein gilt nicht als Sicherheitskontrolle; Navigation und Datenzugriff muessen
dieselbe Grenze erzwingen.

## Bestehende Schutzfunktionen

- Room-Whitelist pro Kinderprofil
- lokale Suche gegen Whitelist-Daten
- PIN-Hash mit PBKDF2WithHmacSHA256, Salt und konstantem Vergleich
- Brute-Force-Sperre mit eskalierender Wartezeit
- Android LockTask im Kindmodus
- Player-WebView blockiert URL-Navigation
- Elternbrowser und Whitelist-Verwaltung liegen ausserhalb des Kindflusses

## Risiken und Restrisiken

- Der IFrame-Player verwendet aktuell `autoplay=1`.
- Folgeinhalte muessen gegen Whitelist-IDs abgesichert werden; `rel=0` ist keine
  Sicherheitskontrolle.
- WebView- und JavaScript-Verhalten kann sich serverseitig aendern.
- Der Elternbrowser ist bewusst ein freier YouTube-WebView und darf niemals im
  Kindmodus erreichbar sein.
- YouTube, Google OAuth und Invidious sind externe Netzwerke; lokale Daten
  bedeuten nicht vollstaendige Offline-Wiedergabe.
- `fallbackToDestructiveMigration()` kann bei unvorhergesehenen Schemawechseln
  lokale Daten loeschen und muss vor produktiver Nutzung ersetzt werden.
- `allowBackup=true` muss fuer Kinder-/Whitelistdaten bewusst bewertet werden.
- Der analysierte Upstream verwendet eine eingebaute API-Key-Konfiguration;
  API-Keys sind keine Geheimnisse, muessen aber in ihrer Nutzung begrenzt und
  nicht mit privaten Tokens verwechselt werden.

## Verbindliche Grenzen fuer den Fork

- Keine Google-Anmeldung im Kindermodus.
- Kein allgemeiner YouTube-Zugang im Kindermodus.
- Keine freie Suche, Kommentare, Shorts, Chats oder Empfehlungen.
- Keine externen Browser-/Share-Intents aus dem Kindermodus.
- Kein automatischer Sprung zu nicht freigegebenen Folgeinhalten.
- Keine Elternfunktion ueber Langdruck oder versteckte Geste.
- Bestehende PIN-Hash- und Lockout-Logik wird nicht entfernt oder abgeschwaecht.
- Whitelist-Daten werden vor jeder Datenbankmigration gesichert und getestet.

## Tests vor Freigabe

- Play-Versuch mit nicht freigegebener Video-ID
- Folgewechsel auf eine nicht freigegebene ID
- manipulierte Deep Links und YouTube-URLs
- WebView-Redirect, Fullscreen und JavaScript-Navigation
- Back- und Task-Switching im LockTask-Modus
- falsche PIN, wiederholte Fehlversuche und App-Neustart waehrend Lockout
- Export/Import ohne Eltern-PIN
- Datenbankmigration mit realen Whitelistdaten
- Offlinebetrieb und Wiederaufnahme nach Netzwechsel

## Massnahmen ausserhalb der App

- Android-Bildschirmsperre und getrenntes Elternkonto verwenden.
- Sidephone nicht mit einem Elternbrowser-Login im Kinderprofil betreiben.
- APKs nur aus nachvollziehbaren, signierten Quellen installieren.
- F-Droid-Status und Build-Signatur vor Installation pruefen; der analysierte
  Upstream enthaelt eine RFP-Dokumentation, ist aber nicht als offizielles
  F-Droid-Paket bestaetigt.
