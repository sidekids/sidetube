# Sidephone UX Testplan

Status: Plan, 13.08.2026

## Zielgeraet

- Sidephone SP-01
- Android API 31
- Display: 480x640 px, Dichte 240, 320x427 dp
- Hardware: `gxa535_keyboard`, DPAD, Media Previous/Next, Play/Pause, Enter,
  Back/Tab/Escape und Zifferntasten

## Testbedingungen

- Hochformat, sichtbare Systemleisten
- keine Touchbedienung bei Tastentests
- reale Tasten und anschliessend Touch als Paritaetstest
- Netzwerk: online, langsam, getrennt
- leere Whitelist, ein Kanal, viele Kanaele
- lange deutsche Titel und Kanalnamen
- grosse Android-Schrift
- App-Neustart und Prozessneustart

## Akzeptanzkriterien

- Jede Kinderaktion ist ohne Touch abschliessbar.
- Der Fokus ist jederzeit sichtbar und nie unter einer Systemleiste.
- Mitteltaste und kurze Zurueck-Taste entsprechen SidePlay.
- Lange Zurueck-Taste fuehrt ohne Adminfunktion zum Start.
- Suche liefert nur lokale Whitelist-Treffer.
- Fremde Links, Empfehlungen, Kommentare und Shorts sind nicht erreichbar.
- Player startet nicht ungefragt in ungepruefte Inhalte.
- PIN-Fehler erzeugen Sperrverzoegerung.
- Bestehende Whitelist-Daten bleiben nach Neustart und Migration erhalten.

## Aufgabenmatrix

| Aufgabe | Messung |
|---|---|
| Startbildschirm | Tastendruecke, Startfokus |
| Kanal oeffnen | Tastendruecke, Ebenen |
| Video starten | Tastendruecke, Zeit bis Wiedergabe |
| Pause/Fortsetzen | Mitteltasten, Zustand sichtbar |
| Zurueck zur Sammlung | Ebenen, Fokusrestaurierung |
| T9-Suche | Eingaben, Treffergrenze, Touch-Rueckgriff |
| Elternbereich | benoetigte Schritte, PIN-Schutz |
| Offline | Fehlermeldung, lokale Inhalte, Sackgassen |

## Automatisierte Regressionen

- Input-Mapping fuer alle SP-01-Keycodes
- Fokusreihenfolge und Fokusrestaurierung
- Navigationsebenen und Back-Stack
- T9-Suche gegen Whitelist-Daten
- Touch-/Tasten-Paritaet
- externe URL-/Intent-Blockierung
- Player Play/Pause und Folgebegrenzung
- PIN, falsche PIN und Lockout
- Room-Migration ohne Whitelistverlust
- leere, Lade-, Fehler- und Offlinezustaende

## Beobachtungstest

Fuenf kurze Aufgaben, maximal 15 bis 20 Minuten, ohne Anleitung vorweg:

1. Einen begonnenen Inhalt fortsetzen.
2. Einen Kanal und darin ein Video oeffnen.
3. Ein Video pausieren und fortsetzen.
4. Einen freigegebenen Titel suchen.
5. Zur Startseite zurueckkehren.

Beobachtet werden Fehlwege, Touch-Rueckgriff, unklare Symbole, Zurueck-
Verhalten und subjektive Sicherheit. Es werden keine Nutzungsprofile oder
Empfehlungen aus dem Testverhalten erzeugt.
