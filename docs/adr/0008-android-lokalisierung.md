# ADR 0008 – Android: Texte als Ressourcen, ViewModels ohne Context

Status: angenommen (Christian, 09.10.2026)

## Kontext

Die Android-App trug rund 420 deutsche Literale in 23 Dateien; `strings.xml` kannte nur den App-Namen.
Das Audit führte „Android UI strings are hard-coded German“ als Sperre für jede Fassung außerhalb des
deutschen Sprachraums (`docs/release/pre-release-audit.md`, `feature-parity.md`). Etwa ein Viertel der
Texte entsteht nicht in Composables, sondern in ViewModels und Zeilenbauern (Rückmeldungen, Hinweise,
Abschnittsüberschriften, Status der Wünsche) – dort gibt es keinen `stringResource`, und ein `Context`
im ViewModel wäre der falsche Weg. Die Unit-Tests prüfen viele dieser Sätze wörtlich.

## Entscheidung

1. **Composables** nehmen `stringResource` und `pluralStringResource` direkt; alle Texte stehen in
   `app/src/main/res/values/strings.xml` (Deutsch als Quellsprache, rund 400 Einträge).
2. **Code außerhalb von Compose** fragt die Schnittstelle **`Texte`** (`get(id, args)`, `plural(id, menge,
   args)`). `AndroidTexte` löst sie über den `Context`; `AppContainer` reicht sie an `KidViewModel`,
   `ParentViewModel`, `KidRows`, `KidWunschRows`, `PinFlow`, `PinChangeFlow`, `ElternkanalEinrichtung`.
   Helfer, die aus Composables heraus Text bauen, bekommen sie über `LocalTexte` (an der Wurzel der
   Activity bereitgestellt).
3. Bezeichnungen für Kennungen (`ParentLabels`, `KidSperreText`) liefern **Ressourcen-IDs** (`*Res`);
   die Composable-Fassung löst sie, der Rest nimmt `Texte`.
4. **Tests** laufen weiter gegen die deutschen Sätze: `TestTexte` liest `strings.xml` aus dem Quellbaum
   und ordnet die IDs per Reflexion über `R.string`/`R.plurals` zu. Kein Robolectric, keine Dubletten
   der Texte im Test.
5. **Nicht übersetzt** bleiben Daten, die gespeichert oder versendet werden: der Akteur „Eltern“ in
   Verlaufseinträgen, Vermerke wie „Sammelprüfung“, die Titel der Wünsche im Verlauf, Gründe der
   `ContentPolicy` (nur intern) und die Gründe eines ungültigen Einrichtungscodes aus `core`.
   Zahlenformen mit echter Einzahl sind `<plurals>`; reine Zählangaben („3 ausgewählt“) bleiben Strings.

## Begründung

`Texte` hält die ViewModels frei von Android-Abhängigkeiten und bleibt eine Zeile pro Aufruf. Die Tests
gegen `strings.xml` kosten nichts an Lesbarkeit und entdecken fehlende Einträge sofort. Der Preis:
Abschnittsüberschriften im Kindermodus sind zugleich der Schlüssel, unter dem Zeilen zusammengehören –
sie werden einmal je Instanz aus den Ressourcen gelesen; ein Sprachwechsel zur Laufzeit baut die Zeilen neu
(die Activity wird ohnehin neu erzeugt). Führende und abschließende Leerzeichen stehen in `strings.xml`
in Anführungszeichen, sonst schneidet `aapt` sie ab.

## Folgen

- Eine zweite Sprache ist eine Datei `values-en/strings.xml`; der Code ändert sich nicht. Englisch liegt seit 09.10.2026 vor (alle 378 Strings und 19 Mengenformen).
- `core` bleibt ohne Ressourcen. Soll ein Text aus `core` übersetzt werden, bekommt er eine Kennung und
  die App ordnet ihr einen Eintrag zu (wie `ParentLabels.decisionRes`).
- Lint meldet weiter einige `PluralsCandidate` für Zählangaben ohne Einzahl; bewusst belassen.

## Nachtrag iOS (09.10.2026)

iOS braucht keine eigene Schnittstelle: SwiftUI-Texte (`Text`, `Label`, `Button` …) landen über
`SWIFT_EMIT_LOC_STRINGS` von selbst im Katalog `Localizable.xcstrings` (Quellsprache Deutsch, Ziel
Englisch). Texte, die als normale `String`-Werte entstehen (Domänen-Titel in `Curation.swift`, `Wish.swift`,
Modelle des Kindermodus, Rückmeldungen, Overlay-Texte), stehen jetzt in `String(localized:)`, damit der
Compiler sie ebenfalls extrahiert. `xcodebuild` schreibt den Katalog nicht zurück – das tut nur die IDE;
die Schlüssel kommen aus den `.stringsdata`-Dateien des Builds in DerivedData und wurden mit englischen
Übersetzungen in den Katalog geschrieben (575 Schlüssel). Wie auf Android bleiben gespeicherte Daten
(Akteur, Vermerke, Wunschtitel im Verlauf), Protokollzeilen und Testfixtures deutsch. Die Unit-Tests
laufen mit `-testLanguage de`, weil sie deutsche Sätze vergleichen.
