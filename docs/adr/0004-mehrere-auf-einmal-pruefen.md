# ADR 0004 – Mehrere Einträge auf einmal prüfen

Status: angenommen (Christian, 04.10.2026)

## Kontext

Wunsch der Eltern (04.10.2026): „Unter Prüfen möchte ich mehrere auf einmal prüfen können."
Die Prüfliste („Freigaben prüfen") kennt bisher nur den Einzelweg: Eintrag öffnen, Alter und Kategorie
setzen, freigeben oder ablehnen. Nach einem Startpaket oder einer Woche Wünsche stehen dort schnell
zwanzig Einträge, die oft dieselbe Antwort bekommen.

## Entscheidung

1. Die Prüfliste bekommt **„Auswählen"**: Häkchen je Eintrag, dazu „Alle auswählen". Für die Auswahl
   gibt es zwei Handlungen: **„Freigeben"** und **„Ablehnen"**.
2. **Sammelfreigabe** fragt einmal für alle:
   - **Mindestalter** und **Kategorie** – voreingestellt „wie vorgeschlagen" (jeder Eintrag behält seine
     eigene Vorgabe); wer einen Wert wählt, setzt ihn für alle. Hebt eine Kategorie das Alter an, gilt
     je Eintrag das höhere (wie im Einzelweg).
   - Sind **Kanäle** dabei: eine **Vertrauensstufe** für diese Kanäle (ADR 0003), voreingestellt „Nur
     einzeln geprüfte Videos". „Gesperrt" ist hier nicht wählbar – Sperren bleibt eine Einzelentscheidung.
3. **Nicht sammelbar** – bleiben in der Liste, mit Hinweis, wie viele es sind und warum:
   - Einträge mit **Risikotreffer** des Filters,
   - **Nachrichten**, die eine Elternprüfung verlangen (`newsStatus` „Eltern prüfen"),
   - Einträge aus **gesperrten** oder **„nur für Eltern"**-Quellen.
4. **Sammelablehnung** fragt einmal nach („N Einträge ablehnen?"), dann wird jeder abgelehnt.
5. Jeder Eintrag bekommt **seinen eigenen Verlaufseintrag** (Akteur „Eltern", Vermerk „Sammelprüfung").
   Wünsche, die durch eine Freigabe erfüllt sind, gelten wie im Einzelweg als erfüllt (ADR 0001).
6. Der Einzelweg bleibt unverändert und ist weiter der Normalfall für alles Unklare.

## Begründung

Viele Einträge bekommen dieselbe Antwort; einzeln ist das Klickarbeit ohne Gewinn an Sorgfalt. Der Preis:
Eine Sammelfreigabe sieht sich den einzelnen Eintrag nicht an. Deshalb bleiben gerade die Fälle, die
Aufmerksamkeit brauchen (Risikotreffer, Nachrichten, heikle Quellen), aus der Sammlung heraus, und die
Vorgaben je Eintrag gelten, solange niemand bewusst etwas anderes wählt.

## Folgen

- Android (`ReviewScreens.kt`, `ParentViewModel`) und iOS (`ReviewQueueView`) bekommen Auswahlmodus und
  Sammel-Maske; die Regel „sammelbar ja/nein" und die Berechnung je Eintrag stehen als reine Logik mit
  Tests auf beiden Plattformen gleich.

## Nachtrag zur Umsetzung (04.10.2026)

- Auch **„heikle" Nachrichten** (`sensitive`) sind nicht sammelbar, nicht nur „Eltern prüfen".
- **Filtertreffer** heißt: Themen am Eintrag, ein Risiko-Vermerk oder eine frische Vorprüfung des Titels
  beim Ausführen. Android schreibt in `editorialNotes` nur Risikobegriffe; iOS schreibt auch andere
  Hinweise in den Vermerk und erkennt Risikobegriffe am Präfix „Risikobegriffe:" – gleiche Bedeutung.
- Jeder Eintrag wird beim Ausführen frisch gelesen und neu geprüft; Nicht-Sammelbares bleibt bei
  Freigabe **und** Ablehnung unberührt. Im Auswahlmodus sind Wünsche ausgeblendet.
- Auf iOS erfüllte das **Einzel**-Freigeben in der Prüfliste keine Wünsche, auf Android schon.
  Angeglichen am 04.10.2026 (`CurationRepository.approveUndErfuelleWuensche`).
