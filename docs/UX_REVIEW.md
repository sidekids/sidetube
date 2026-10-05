# UX-Review Kindermodus (Zielperson: 10-jähriges Kind)

> Historisches Dokument: Aussagen und Implementierungspfade können den früheren Stand beschreiben. Maßgeblich sind der [aktuelle Release-Audit](release/pre-release-audit.md) und seine offenen Gates; dieses Dokument ist keine Release-Freigabe.

Stand: 2026-09-08. Bewertungsraster je Ansicht: Verstehen · Fokus · Entscheidungslast · Orientierung · Touch-Ziele ·
Sprache. Grundsatz: **eine Ansicht – eine Hauptaufgabe.** Was in diesem Review umgesetzt wurde, ist als „✓ umgesetzt“
markiert; alles andere ist priorisierte Restarbeit.

## 1. Videoende (P0, beide Plattformen) – ✓ umgesetzt

| | vorher | jetzt |
|---|---|---|
| iOS Hochformat | „Fertig geschaut.“ + zwei Knöpfe in einer Zeile; „Zur Mediathek“ schloss nur den Player und log damit | **„Fertig 🎉“**, „Als Nächstes“ mit ≤ 3 Karten, **Nochmal**, **Zurück zu den Videos** |
| iOS Vollbild | Vorschaubild mit rotem YouTube-Knopf, kein Ausgang außer Doppeltipp/Drehen | dasselbe Panel über dem Video |
| Android | YouTube-Endscreen, Klicks ohne Wirkung („kaputt?“) | `stopVideo()`, Vollbild wird verlassen, dasselbe Panel |

Auswirkung: keine Sackgasse, keine Endlosliste, die drei Wege sind auf beiden Plattformen dieselben Wörter.
Hardware/Rad: ⏭ = erster Vorschlag, Mitte/Select = Nochmal, Zurück = Player schließen.

## 2. Ansichten im Einzelnen

### Start (iOS `HomeScreen`, Android `KidHomeScreen`)
- **Verstehen** ✓ Name des Kindes, Kanäle als Gesichter, Videos als Bilder.
- **Fokus** ✓ umgesetzt: „Weiterschauen“ steht auf beiden Seiten oben, danach Kanäle, dann Kategorien, dann „Zuletzt
  geschaut“. Kategorie-Reihen zeigen 6 statt 12 Kacheln. Auf iOS deckt sich die Reihenfolge damit auch mit der Radauswahl.
- **Entscheidungslast** ⚠️ Android zeigt vier Sektionen mit sehr ähnlichen Karten; iOS drei bis fünf. Vertretbar, aber mehr
  als nötig.
- **Sprache** ✓ umgesetzt: „Deine Eltern haben noch nichts ausgesucht“ statt „Whitelist/Elternbereich“; Android sagt
  „Meine Kanäle“ statt „Meine Abos“ und „Weiterschauen“ statt „Weitersehen“. ⚠️ Offen: „Restzeit: 45m“ (P2).
- **Touch** ✓ ≥ 44 pt / 48 dp; Android-Karten 200 dp breit.
- **Sicherheit hinter der UX** ✓ umgesetzt (iOS): „Zuletzt geschaut“ zeigt nur noch, was heute erlaubt ist.
- ✓ umgesetzt (Android-Ersatz, 02.10.2026, `KidRows.home`): Die Startseite ist eine Liste mit Abschnitten „Zuletzt
  geschaut“ (bis 3, nur heute Erlaubtes, auch aus Kanal- und Playlist-Zwischenspeicher), „Kanäle“, „Sendungen“, „Videos“
  (neueste 4, belastende Nachrichten nie) und zuletzt „Alle Videos“. Überschriften sind keine Fokusstellen. Ein
  „Weiterschauen“ mit Fortschritt gibt es auf Android nicht, weil keine Abspielposition gespeichert wird. Die Lupe im
  Kopf öffnet die Suche auch per Finger.

### Alle Videos / Kanal-Detail
- ✓ umgesetzt: Der Tab heißt „Alle Videos“ statt „Mediathek“, das Segment „Sendungen“ statt „Playlists“ — dieselben
  Wörter wie auf Android.
- ✓ umgesetzt (iOS): Kanäle mit Einzelfreigabe zeigen jetzt genau diese Videos statt einer leeren Liste mit
  Elternhinweis; Fehlertexte kindgerecht („Das hat nicht geklappt. Ist das Internet an?“ statt „API-Schlüssel fehlt“).
- ✓ umgesetzt (Android): im Kindermodus steht ein Satz („Das hat nicht geklappt. Ist das Internet an?“) statt
  „Kanal-Daten konnten aus keiner Quelle geladen werden“; der Elternbereich behält die genaue Meldung.
- ✓ umgesetzt (Android): Kanäle ohne Kinderquellen-Stufe zeigen die einzeln freigegebenen Videos statt einer leeren Liste.
- ✓ umgesetzt (Android-Ersatz, 02.10.2026): „Alle Videos“ mit Umschalter Kanäle/Videos/Sendungen. Der Umschalter ist eine
  Fokusstelle, Mitte schaltet weiter, der Finger wählt direkt. Zurück führt an die Stelle, von der man kam.
- ✓ umgesetzt (Android-Ersatz, 02.10.2026): Sendungen (Playlists) öffnen eine Liste ihrer Videos statt ins Leere zu
  spielen. Inhalt aus dem Playlist-Feed ohne Schlüssel (erste 15 Einträge), zwischengespeichert; gezeigt wird nur, was
  `ContentPolicy.canPlayFromPlaylist` durchlässt (eigene Elternentscheidung, Risikofilter, Shorts/Live, Alter und
  Kategorie der Playlist, gesperrte Quellen).
- ✓ umgesetzt (Android-Ersatz, 02.10.2026): Endkarte „Fertig 🎉“ mit „Nochmal“ und „Zurück zu den Videos“ – hoch/runter
  wählt, Mitte bestätigt, Zurück schließt. Ohne „Als Nächstes“-Vorschläge.

### Suche
- ✓ Lokal, keine offene YouTube-Suche, Leerzustände vorhanden.
- ✓ umgesetzt (iOS): „Dazu gibt es kein Video.“ statt „Keine freigegebenen Videos gefunden.“
- ✓ umgesetzt (Android, 02.10.2026): T9 auf dem Sidephone. Zifferntasten sind eine T9-Folge (2 = abc … 9 = wxyz,
  ä → 2, ö → 6, ü → 8, ß → 7, aufgelöst „ae“ passt ebenfalls; 0 = Wortgrenze), gesucht wird an Wortanfängen in Titel
  und Kanalname der Freigaben – lokal, ohne Netz. Die Anzeige zeigt jede Ziffer als Taste mit ihren Buchstaben,
  leer steht „Tippe mit den Zahlen: 2 = abc …“. Zurück löscht die letzte Ziffer, erst die leere Suche wird verlassen.
  Buchstaben (Touch-Tastatur, Emulator) suchen weiter als Text. Belegung wie SidePlay (`T9.swift`); Code
  `core/input/T9.kt`, Tests `T9Test`, `KidT9SearchTest`.
- ✓ umgesetzt (Android, 02.10.2026): **Rad-Suche** ersetzt T9 als Hauptweg – das SP-01 hat gar keine Zifferntasten,
  nur den Ring. Eine Reihe Buchstabenfelder bietet nur Buchstaben an, mit denen ein freigegebener Titel oder Kanalname
  am Wortanfang weitergeht (Umlaute zählen als Grundbuchstabe, ä = a, ß = s; die Anzeige schreibt sie richtig), dazu
  „Lücke“ (nur nach einem fertigen Wort, wenn eines folgt) und „Löschen“. Ring links/rechts (`MEDIA_PREVIOUS/NEXT`)
  wählt, Mitte nimmt, runter führt in die Treffer, hoch vom ersten Treffer zurück; kein Umlauf. Ist der Name fertig,
  springt der Fokus auf den ersten Treffer, damit die nächste Mitte abspielt und nicht löscht. Zurück löscht wie
  bisher. Felder lassen sich antippen; Tastaturbuchstaben gehen weiter, Ziffern bleiben T9. Code
  `core/input/Radsuche.kt`, `Radtasten.kt`, `kid/KidRadsuche.kt`, `kid/KidRadView.kt`; Tests `RadsucheTest`,
  `KidRadSearchTest`. Offen: Die Trefferzeilen tragen noch den gelben Fokusrahmen (ADR 0016, `konformitaet.md`).

### Wünsche (ADR 0001) – Android, 02.10.2026
- ✓ **Themenwunsch:** In der Suche steht immer „Wunsch an die Eltern“ – ohne Treffer als einzige Zeile (▼ aus dem
  Rad), sonst nach den Treffern. Mitte öffnet ein **freies Rad** (alle Buchstaben, Ä Ö Ü ß, Lücke, Löschen), die
  Eingabe der Suche wird übernommen; ▼ „Wunsch schicken: „Dinos““, Mitte. Das Kind sieht nichts Fremdes.
- ✓ **„Mehr davon“:** Endkarte jetzt Nochmal · Mehr davon wünschen · Zurück zu den Videos (geht der Wunsch nicht,
  überspringt der Fokus ihn); im laufenden Video öffnet die Taste außen oben rechts (oder der Daumen-Knopf) ein
  Menü „Mehr davon wünschen“ / „Weiterschauen“, das Video hält solange an.
- ✓ **„Neu bei deinen Kanälen“:** Abschnitt am Ende der Startseite mit Bild und Titel, Schloss im Bild, Untertitel
  „🔒 Wünschen“ bzw. „✓ Gewünscht“. Mitte öffnet die Folge mit „Wünschen“ und „Zurück“ – abspielen geht nicht.
- ✓ **„Meine Wünsche“** (letzte Zeile der Startseite): Überschrift „Heute noch N Wünsche“, je Wunsch der Stand
  (Wartet auf die Eltern · Freigegeben ▶ · Nicht jetzt · Sprechen wir drüber) und die Antwort der Eltern; ein
  erfüllter Wunsch spielt mit Mitte sein Video. Hinweis nach dem Abschicken „Dein Wunsch ist bei den Eltern. Heute
  noch 2 Wünsche.“ Keine Benachrichtigung, kein Ton; Eltern sehen einen Zähler am „Prüfen“-Knopf.
- ✓ Neue Elemente tragen den SideUI-Fokus (eigene Fläche, heller Rahmen, nie gelb); die älteren Zeilen und
  Endkarten-Knöpfe bleiben vorerst gelb (`konformitaet.md`).
- Offen: Gerätetest am SidePhone; ob die Startseite mit sechs gesperrten Folgen je Kanal zu lang wird.

### Player (Hochformat)
- ✓ Titel, Zustand, große Play-Taste (64 pt) mittig, Warteschlange darunter.
- ✓ umgesetzt (iOS): Zustandstexte kindgerecht („Fertig“, „Diese Videos gehen gerade nicht.“, „Video 1 von 3“).
- ✓ umgesetzt (Android): Inhalt unter dem Video scrollt (auf 320×427 dp war die Warteschlange vorher unsichtbar).
- ✓ umgesetzt (Android): Meldet sich der Player nicht innerhalb von 8 Sekunden — ohne Netz meldet die IFrame-API gar
  nichts —, sagt die App es und bietet „Nochmal“ und „Zurück“ an, statt schwarz zu bleiben.
- ✓ umgesetzt (beide): eigene Steuerung statt YouTubes Leiste — Fortschrittsbalken zum Spulen, ⏮, −10 s, ⏯, +10 s, ⏭,
  Vollbild, alle Ziele groß genug. YouTubes Leiste war auf dem Sidephone kaum zu treffen und blendete beim Pausieren
  fremde Vorschläge ein.
- ⚠️ Offen (P2): iOS-Fehler nur als kleine Zeile unter dem schwarzen Video statt als Karte im 16:9-Bereich.
- ⚠️ Offen (Produktentscheidung): „Empfehlen“-Symbol in der Player-Toolbar führt für das Kind nur in die PIN-Abfrage.

### Player (Vollbild)
- ✓ umgesetzt (iOS): „Fertig“-Panel auch im Vollbild – vorher Sackgasse.
- ⚠️ Offen (P2): Exit-Knopf im echten Querformat fehlt (nur Drehen), Doppeltipp ist unentdeckbar.
- ✓ umgesetzt (Android): Das Vollbild gehört jetzt der App; System-Zurück beendet es, statt den Player zu verlassen.
  Die Sidephone-Zurücktaste läuft weiter über die Navigation (P2).

### Overlays Zeit-um / Ruhezeit / Gute Nacht
- ✓ umgesetzt (iOS): Vorwarnung „Noch 15/5 Minuten“ wie auf Android, Ruhezeit-Overlay nennt die Uhrzeit („Ab 6:30 Uhr
  geht es weiter“), Knopf „Für Eltern“ statt „Eltern-PIN“, 44 pt, VoiceOver-modal.
- ✓ umgesetzt (Android): Overlays blockieren jetzt Touch und Tasten (vorher konnte das Kind darunter weitertippen
  und der Ton lief weiter).
- ⚠️ Offen (P2): Tageslimit ohne sichtbaren Countdown auf iOS; Overlays auf Android fehlen in Kanal/Suche/Sendung.

### Fernbedienung (iOS Sheet) / Hardwaretasten (Android)
- ⚠️ Offen (iOS): Handle „⌃ Fernbedienung“ in jedem Tab; Ring doppelt belegt (↑↓ Auswahl oder Lautstärke).
  Der Vorschlag, das Handle nur im Player zu zeigen, ist **bewusst nicht umgesetzt**: das Rad bedient auch die Listen,
  und ohne Handle wäre die Sidephone-Bedienung außerhalb der Wiedergabe nicht mehr erreichbar. Sinnvoll bleibt die
  Kontextzeile „Drehen = Spulen, Mitte = Pause“ (P2).
- ✓ Android: gelber Fokusring, Fokus bleibt über Rotation erhalten.

## 3. Cognitive Load – Prinzipien angewandt

| Prinzip | Umsetzung |
|---|---|
| Eine Ansicht, eine Aufgabe | „Fertig“ ersetzt Steuerung + Warteschlange, statt sich dazuzulegen |
| Wenige dominante Aktionen | ≤ 3 Vorschläge, zwei Knöpfe; kein Raster, kein Scrollen nötig |
| Kein Engagement-Design | keine Autoplay-Kette (Elternoption, Standard aus), kein Countdown, keine „Noch eins“-Endlosliste |
| Vorhersehbarkeit | dieselben drei Wörter auf beiden Plattformen: Als Nächstes · Nochmal · Zurück zu den Videos |
| Klarheit statt Technik | Kindertexte ohne Whitelist, API, Provider, Elternbereich |

## 4. Accessibility – Stand

**iOS** ✓ Labels auf allen Bedienelementen, Sektions-Header, `isSelected`/`startsMediaSession`, Reduce Motion beim
Auswahlzustand und Auto-Scroll, Overlay `isModal` (neu), Panel-Karten mit „Abspielen: Titel, Kanal“ (neu), Alle-Kanäle-Label
(neu). ⚠️ Offen: feste Breiten bei sehr großem Dynamic Type (Kanalname 92 pt, Kategorie-Kachel 180 pt, Remote-Tasten),
„Suche löschen“ < 44 pt, Rad-Hint mit „Oeffnen“-Umschrift.

**Android** ✓ contentDescription auf Funktionssymbolen, IconButtons 48 dp. Neu: Sektionsüberschriften als `heading()`,
Thumbnails ohne doppelte Ansage (Home, Player), Overlays blockieren auch die Tastenbedienung. ⚠️ Offen: Fokusring nur
visuell (kein `selected`-Semantik), feste Höhen in der PIN-Eingabe bei Schriftskalierung, Zähler/Restzeit werden
buchstäblich vorgelesen.

## 5. Antwort auf die Leitfragen

- **Kann ein 10-jähriges Kind SideTube ohne Erklärung bedienen?** Den Hauptpfad ja, auf beiden Plattformen: Start →
  Weiterschauen oder Kanal → schauen → „Fertig“ → weiter oder zurück. Die Wörter sind jetzt auf beiden Seiten dieselben
  und kindgerecht. Was bleibt: die Fernbedienungs-Leiste in jedem iOS-Tab. Die Sidephone-Suche mit dem Rad erklärt sich seit
  02.10.2026 selbst – ob ein Kind sie ohne Hilfe findet (Rad rechts), ist am Gerät noch zu prüfen.
- **Gibt es unnötige Entscheidungen?** Weniger: Kategorie-Reihen sind auf 6 Kacheln gekürzt und „Weiterschauen“ steht
  oben. Android zeigt weiterhin vier sehr ähnliche Sektionen (P2).
- **Gibt es Sackgassen?** Die gravierendste (Videoende, Vollbild) ist geschlossen, ebenso das schwarze Bild ohne Netz auf
  Android und die leere Kanalseite bei Einzelfreigabe. Offen: Hardware-Zurück im Android-Vollbild verlässt den Player (P1).
- **Sind Hauptaktionen eindeutig?** Ja: im Player „Als Nächstes“ mit drei Karten, auf der Startseite „Weiterschauen“ als
  erste Sektion.
