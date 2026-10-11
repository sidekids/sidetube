# ADR 0010 – YouTube-Daten höchstens 30 Tage: auffrischen oder löschen

Status: angenommen (Christian, 11.10.2026 – „mach weiter“ auf den Vorschlag in provider-assessment.md, Punkt 5)

## Kontext

Die YouTube API Services Developer Policies (Stand 14.09.2026, III.E.4) erlauben, Daten ohne
Nutzer-Autorisierung höchstens 30 Kalendertage zu speichern; danach müssen sie gelöscht oder
aufgefrischt werden, und gespeicherte Daten sollen dem aktuellen Stand bei YouTube entsprechen.
SideTube speichert Titel, Kanalnamen und Vorschaubild-Adressen bisher unbegrenzt: in den Freigaben,
im Kanal-Zwischenspeicher, im Sehverlauf und in den Wünschen. Ob Daten aus oEmbed und RSS-Feeds
„API-Daten“ im Sinn der Regeln sind, ist nicht eindeutig; für die Data API ist es klar. SideTube
legt die Regel deshalb auf alle von YouTube bezogenen Titel, Namen und Bildadressen an.

## Entscheidung

Einmal am Tag, beim Öffnen der App und nur mit Netz für das Auffrischen:

1. **Freigaben** (Videos, Playlists, Kanäle) tragen den Zeitpunkt ihrer Metadaten. Ist er älter als
   30 Tage, werden Titel, Kanalname und Bild über denselben schlüsselfreien Weg wie beim Hinzufügen
   neu geholt (je Lauf höchstens 25 Einträge, damit kein Lauf das Netz belastet). Meldet YouTube den
   Inhalt als nicht mehr vorhanden oder privat, wird der Titel durch „Nicht mehr verfügbar“ ersetzt
   und das Bild entfernt; der Eintrag bleibt, damit die Eltern sehen, was fehlt, und ihn entfernen
   können. Bei Netz- oder Serverfehlern bleibt alles, bis der nächste Lauf es erneut versucht.
2. **Kanal-Zwischenspeicher:** Einträge älter als 30 Tage werden gelöscht; der Feed liefert sie neu.
3. **Sehverlauf:** Einträge älter als 30 Tage werden gelöscht. Die Nutzungsübersicht zeigt höchstens
   30 Tage, das Tageslimit braucht nur heute, „Weiterschauen“ nur die letzten Tage.
4. **Wünsche:** Entschiedene Wünsche älter als 30 Tage werden gelöscht. Offene Wünsche älter als
   30 Tage behalten das Thema (das eigene Wort des Kindes), verlieren aber Videotitel, Kanalname und
   Bild; sie erscheinen dann mit neutralem Titel („Mehr davon“, „Eine neue Folge“).
5. Prüfentscheidungen (Verlauf) enthalten keine YouTube-Titel und bleiben unberührt.

## Begründung

Auffrischen statt Löschen hält die Freigaben benutzbar, Löschen beim Rest ist zugleich
Datensparsamkeit: Ein Sehverlauf, der nach 30 Tagen verschwindet, ist auch für den Kinderschutz
die bessere Voreinstellung. Der Preis: ein täglicher Netzzugriff mehr (bis zu 25 oEmbed-Anfragen),
eine Datenbank-Migration (Android Room 3→4, iOS ein Feld mit Vorgabewert), Sehverlauf und
entschiedene Wünsche älter als 30 Tage sind weg – auch für Eltern, die länger zurückschauen wollten.

## Folgen

- Android: Spalte `metadataAt` in `whitelist_items` (Migration 3→4, nur Hinzufügen), reine Regel
  `YouTubeDatenFrist` mit Tests, Lauf beim App-Start im Hintergrund.
- iOS: Feld `metadataAt` an `WhitelistItem`, gleiche Regel und gleicher Ablauf.
- Datenschutzerklärung: Speicherdauer nennen (Sehverlauf und Wünsche 30 Tage).
