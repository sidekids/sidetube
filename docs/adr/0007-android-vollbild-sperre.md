# ADR 0007 – Android: Vollbild-Sperre statt wegtippbarem Hinweis

Status: angenommen (Christian, 08.10.2026)

## Kontext

Auf iOS endet jede Zeitregel im Kindermodus mit einem dunklen Vollbild (`KidOverlayView`): Ruhezeit,
abgelaufener Schlaf-Timer, aufgebrauchte Sehzeit, Speicherfehler, unterbrochene Wiedergabe. Heraus führt
nur die PIN der Eltern. Android zeigte stattdessen einen Hinweis in der Snackbar, den das Kind wegtippen
konnte; die Regel selbst griff zwar (kein Start mehr), aber Liste und Suche blieben benutzbar, und die
Ruhezeit galt nur für laufende Wiedergaben. Das war seit dem Audit vom September der älteste offene
Kinderschutz-Punkt (`docs/release/pre-release-audit.md`, `docs/release/feature-parity.md`).

## Entscheidung

1. Der Kindermodus bekommt einen Zustand **`sperre`** (`KidSperre`: Gute Nacht, Zeit um, Ruhezeit,
   Speicherfehler, Unterbrochen). Steht er, liegt **`KidSperreView`** über Liste und Player: dunkel,
   Symbol, Titel, Satz und ein Knopf „Für Eltern". Texte wörtlich wie auf iOS.
2. **Unter der Sperre wirkt nichts**: Berührungen enden auf der Sperre, der Ring tut nichts. Zwei
   Ausnahmen: **Mitte** öffnet die PIN wie der Knopf; **Einstellungen (oben rechts lang)** führt wie immer
   in den Elternbereich – die Sperre bleibt dabei stehen und gilt beim Rückweg weiter.
3. **Nur die PIN von der Sperre aus hebt sie auf.** Was dann passiert, hängt von der Art ab, wie auf iOS:
   - Gute Nacht: der Schlaf-Timer wird beendet, zurück zum Kind.
   - Ruhezeit: **Ausnahme bis zum Ende des laufenden Fensters** (`bedtimeSkipUntil`), zurück zum Kind.
   - Zeit um, Speicherfehler, Unterbrochen: Sperre weg, weiter in den Elternbereich, wo etwas zu tun ist
     (Limit anpassen, Wiedergabe freigeben). Greift die Regel beim Rückweg noch, steht die Sperre wieder.
4. **Die Ruhezeit sperrt auch ohne Wiedergabe.** Eine einzelne Frist (keine wiederkehrende Uhr) wartet
   auf die nächste Grenze – Beginn oder Ende der Ruhezeit, Mitternacht bei aufgebrauchter Sehzeit –
   und prüft dann neu. Sperren aus Regeln (Gute Nacht, Zeit um, Ruhezeit) heben sich von selbst auf,
   sobald die Regel nicht mehr greift: Fensterende, Ausnahme oder höheres Limit aus dem Elternbereich,
   Mitternacht, beendeter Timer. Speicherfehler und Unterbrochen bleiben bis zur Freigabe der Eltern.
5. Der wegtippbare Hinweis bleibt für alles, was keine Sperre ist (Vorwarnungen, Wünsche, Freigaben
   aktualisiert).

## Begründung

Ein Hinweis, den das Kind selbst wegdrücken kann, ist keine Schutzmaßnahme; die Sperre ist die sichtbare
Seite der Regel. Dass die Ruhezeit auch das Stöbern sperrt, entspricht iOS und der Absicht der Eltern.
Der Preis: eine weitere Frist außerhalb der Wiedergabe (bisher gab es Fristen nur, solange ein Player
existierte), und der Einstellungen-Weg per Taste hebt die Ruhezeit bewusst **nicht** auf – wer die
Ausnahme will, nimmt den Knopf auf der Sperre. Das vermeidet, dass ein beiläufiger Gang in die
Einstellungen die Ruhezeit für den Abend aushebelt.

## Folgen

- Android: `kid/KidSperre.kt` (Arten, Texte), `kid/KidSperreView.kt`, `KidViewModel` (`sperre`,
  `pruefeSperre`, `elternHebenSperreAuf`), `MainActivity` (PIN von der Sperre, Tasten). Tests in
  `KidSperreTest` (13 Fälle: Timer, Ruhezeit mit und ohne Wiedergabe, Fensterende, Ausnahme über Sperre
  und Elternbereich, Limit samt Mitternacht und Erhöhung, Marker, Lesefehler).
- Nicht am Gerät geprüft: Darstellung auf dem SidePhone-Display, Verhalten über Doze/Prozessende.
  Die Sperre lebt im ViewModel; nach einem Prozessende prüft der Start sie neu aus den Regeln.
- iOS unverändert.
