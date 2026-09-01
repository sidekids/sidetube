# Ruhezeit — automatischer Schlafmodus

Status: Konzept, 14.08.2026. Ergaenzt SIDE_NAVIGATION_MODEL.md und
SECURITY_AND_CHILD_SAFETY.md.

## Problem

Der bestehende Schlafmodus ist ein manueller Timer, den jemand bewusst starten
muss. Kinder tun das von sich aus praktisch nie, und Eltern denken abends nicht
jeden Tag daran. Damit wirkt die Funktion genau dann nicht, wenn sie gebraucht
wird.

## Ziel

Eine Ruhezeit, die ohne taegliches Zutun greift, vorhersehbar ist und nicht
mitten im Bild abbricht.

## Paedagogische Grundlage

- Bildschirminhalte kurz vor dem Einschlafen verzoegern das Einschlafen. Als
  Faustregel gilt eine Stunde Abstand zwischen Bildschirm und Bett.
- Ein 12-jaehriges Kind braucht etwa neun bis elf Stunden Schlaf. Bei Schulbeginn
  um acht Uhr bedeutet das Bett gegen 21 Uhr, also Bildschirmende gegen 20 Uhr.
- Wirksam ist nicht die Haerte der Grenze, sondern ihre **Vorhersehbarkeit**:
  gleiche Zeit jeden Tag, sichtbare Vorwarnung, kein ueberraschender Abbruch.
- Ein abrupter Schnitt mitten im Video erzeugt Streit und das Gefuehl von
  Willkuer. Deshalb warnt die App zweimal vorher, statt kommentarlos zu stoppen.
- Die Ausnahme muss beim Elternteil liegen, nicht beim Kind: eine Verlaengerung
  ist moeglich, aber nur nach PIN.

## Regeln

| Punkt | Verhalten |
|---|---|
| Standard fuer neue Profile | Ruhezeit an, 20:00 bis 06:30 |
| Altersvorschlaege | 6-9 Jahre 19:00, 10-12 Jahre 20:00, ab 13 Jahren 21:00 |
| Wochenende | Freitag und Samstag standardmaessig 60 Minuten spaeter |
| Vorwarnung | 15 Minuten und 5 Minuten vorher, unaufdringlich, ohne Bestaetigung |
| Beginn | Wiedergabe pausiert, Gute-Nacht-Ansicht erscheint |
| Waehrend der Ruhezeit | Start, Suche und Player bleiben gesperrt |
| Ende | Ruhezeit endet zur Weckzeit automatisch, ohne Zutun |
| Ausnahme | Nur ueber Eltern-PIN, wahlweise 30 Minuten oder fuer heute aus |
| Zeitlimit | Bleibt unabhaengig bestehen; es gewinnt die Grenze, die zuerst greift |

Die Ruhezeit ist eine paedagogische Grenze, keine Sicherheitsfunktion. Sie
ersetzt weder PIN noch Whitelist und wird nie als Schutz vor fremden Inhalten
beschrieben.

## Bedienung fuer das Kind

1. **15 Minuten vorher**: ein schmaler Hinweisstreifen am unteren Rand,
   „Noch 15 Minuten". Er verschwindet nach wenigen Sekunden von selbst.
2. **5 Minuten vorher**: derselbe Streifen, „Noch 5 Minuten", etwas kraeftiger.
3. **Beginn**: das Video pausiert, die Gute-Nacht-Ansicht deckt den Bildschirm
   ab: Mond, „Gute Nacht, <Name>", darunter eine Zeile „Morgen frueh geht es
   weiter". Keine Schaltflaeche ausser dem Elternzugang.

Alle drei Zustaende sind ohne Lesen verstaendlich: Streifen heisst gleich Schluss,
dunkler Vollbildschirm heisst Schluss.

## Bedienung fuer die Eltern

Im Profil unter „Ruhezeit":

- Schalter „Ruhezeit"
- „Ab" und „Bis" als Zeitfelder
- Schalter „Freitag und Samstag spaeter" mit Minutenwert
- Hinweiszeile mit der aktuell wirksamen Regel im Klartext

Waehrend der Ruhezeit bietet die Gute-Nacht-Ansicht ueber den Elternzugang zwei
Aktionen: „30 Minuten laenger" und „Heute aus".

## Gestaltung

Ruhig, dunkel, wenig Elemente:

- Vollflaechiger dunkler Hintergrund, ein weicher Verlauf, keine harten Kanten.
- Genau ein Symbol, ein Titel, eine Nebenzeile.
- Typografie traegt die Hierarchie, nicht Farbe. Gelb bleibt dem Fokus
  vorbehalten, Rot bleibt Warnungen vorbehalten.
- Der Hinweisstreifen ist eine abgerundete Flaeche mit Innenabstand, mittig
  unten, kurzer Ein- und Ausblendung, ohne Schatten und ohne Rahmen.
- Bewegung nur als kurzes Ein- und Ausblenden; nichts springt, nichts blinkt.

## Technischer Entwurf

- Profilfelder: `bedtimeEnabled`, `bedtimeStartMinutes`, `bedtimeEndMinutes`,
  `bedtimeWeekendOffsetMinutes`. Minuten seit Mitternacht, damit Zeitzonen und
  Sommerzeit keine Rolle spielen.
- Schemawechsel per echter Room-Migration, nicht ueber
  `fallbackToDestructiveMigration()`: bestehende Profile und Freigaben bleiben
  erhalten.
- `BedtimeEvaluator`: reine Funktion aus Einstellungen und Zeitpunkt auf einen
  Zustand (`Off`, `Warning(minutesLeft)`, `Active`). Ueber Mitternacht laufende
  Zeitraeume werden korrekt behandelt.
- `BedtimeStateProvider`: Flow, der den Zustand bis zur naechsten Grenze schlafen
  legt statt im Sekundentakt zu rechnen.
- Kindstart und Player lesen denselben Zustand wie das bestehende Zeitlimit; die
  Gute-Nacht-Ansicht wird von beiden Quellen genutzt.

## Tests

- Zeitraum ueber Mitternacht, Start vor und nach Mitternacht
- Wochenendversatz an Freitag, Samstag und Sonntag
- Vorwarnstufen genau auf der Minutengrenze
- Ruhezeit aus, Ruhezeit an ohne Wochenendversatz
- Zusammenspiel mit erreichtem Tageslimit
- Verlaengerung um 30 Minuten und „Heute aus" inklusive Wirkung am Folgetag
- Migration von Version 3 auf 4 mit vorhandenen Profilen

## Umsetzungsstand (14.08.2026)

Umgesetzt:

- Profilfelder und echte Room-Migration 3 auf 4;
  `fallbackToDestructiveMigration()` ist entfernt
- `BedtimeEvaluator` mit Mitternachtsuebergang, Wochenendversatz, Vorwarnstufen
  und Eltern-Ausnahme, 11 Tests
- `BedtimeStateProvider`, der bis zur naechsten Grenze schlaeft
- Kindstart und Player: Hinweisstreifen, Gute-Nacht-Ansicht, Wiedergabe pausiert,
  Mitteltaste bleibt waehrend der Ruhezeit wirkungslos
- Elterneinstellung im Profil: Schalter, Ab- und Bis-Zeit, Wochenendregel,
  Klartextzusammenfassung sowie „Heute 30 Minuten laenger" und
  „Heute keine Ruhezeit"
- Migrationstest mit bestehendem Profil

Offen:

- Der Elternzugang aus der Gute-Nacht-Ansicht fuehrt in den Elternbereich; die
  Ausnahme wird dort im Profil gesetzt, nicht direkt im Overlay.
- Altersvorschlaege sind als Werte hinterlegt, aber noch nicht als Auswahl in der
  Profilanlage sichtbar.
- Geraetetest auf dem SP-01 steht aus (Zeitumstellung, Neustart waehrend der
  Ruhezeit, Wochenende).
