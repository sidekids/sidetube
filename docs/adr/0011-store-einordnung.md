# ADR 0011 – Store-Einordnung: Eltern als Zielgruppe, gemischtes Publikum

Status: angenommen (Christian, 11.10.2026)

## Kontext

Die Bewertung in `docs/release/provider-assessment.md` (Punkte 3, 7, 9) zeigte drei Fragen, die vor
den Stores entschieden sein müssen. Apples Kinder-Kategorie verbietet jede Weitergabe von Geräte-
oder Personendaten an Dritte; der eingebettete YouTube-Player sendet solche Daten zwangsläufig an
Google. Google Play verlangt für reine Kinder-Apps nur freigegebene SDKs und Dienste; ob ein
YouTube-Player im WebView dazugehört, ist offen. Und SideTube fängt Tipps auf Logo, Titel und
YouTubes eigene Vorschläge ab, was die YouTube-Regeln (Developer Policies I.6) eigentlich verbieten.

## Entscheidung

1. **App Store:** eine normale Kategorie (etwa „Familie“ oder „Bildung“) mit Eltern als Zielgruppe
   und der ehrlichen Beschreibung, dass Kinder die App unter Aufsicht nutzen. **Nicht** die
   Kinder-Kategorie. Das ersetzt die Entscheidung vom 31.08.2026 („Kids-Kategorie ja“).
2. **Google Play:** Zielgruppe „Eltern und Kinder“ (gemischtes Publikum). Ohne Werbe-SDKs entfällt
   ein Altersfilter für Werbung; die Familienrichtlinien gelten für den Kinderteil weiter.
3. **Abfangen von Player-Tipps:** zurückgestellt bis nach den Gerätebelegen. Bis dahin bleibt das
   Verhalten wie es ist, und das Risiko steht offen in der Bewertung.

## Begründung

Die Kinder-Kategorie wäre mit dem YouTube-Player sehr wahrscheinlich nicht zu erreichen; ein
vorhersehbar abgelehnter Antrag kostet Zeit und eine Angabe, die nicht stimmt, wäre schlimmer.
Das gemischte Publikum bei Google ist die ehrlichere und risikoärmere Angabe. Der Preis: SideTube
erscheint nicht dort, wo Eltern gezielt nach Kinder-Apps suchen, und muss Familien über Website
und Beschreibung erreichen. Die Frage der Player-Tipps bleibt ein bekanntes Risiko für beide Stores
(Apple 5.2.2 verlangt, dass die Nutzung den Bedingungen des Dienstes entspricht).

## Folgen

- Store-Checklisten (`store-ios.md`, `store-android.md`) und die Bewertung sind angepasst.
- Vor einer Store-Einreichung muss Punkt 3 entschieden sein.
