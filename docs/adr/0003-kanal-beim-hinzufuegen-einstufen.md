# ADR 0003 – Einen Kanal beim Hinzufügen gleich einstufen

Status: angenommen (Christian, 04.10.2026)

## Kontext

Wunsch der Eltern (04.10.2026): „Wenn neue Kanäle eingefügt werden, möchte ich direkt entscheiden
können, ob die Quelle vertrauenswürdig ist, welches Alter und die Kategorie."

Bisher bekam ein neu hinzugefügter Kanal fest die Stufe „Nur einzeln geprüfte Videos"
(`perVideoReview`), ohne Mindestalter und ohne Kategorie. Stufe, Alter und Kategorie ließen sich erst
später unter „Quellen & Sicherheitsstufen" setzen – ein zweiter Weg, den man kennen muss. Auf Android
landete der Kanal zusätzlich erst ungeprüft in der Prüfliste, obwohl die Eltern ihn gerade selbst
ausgewählt hatten. Das Modell trägt die drei Angaben je Quelle längst (`CuratedSource.trust`,
`defaultAgeMin`, `defaultCategory`); sie gelten z. B. für neue Folgen aus Wünschen (ADR 0001).

## Entscheidung

1. Wird ein **Kanal** hinzugefügt (Link → Vorschau, Kanalsuche, „Kanal prüfen" aus einem Wunsch, Kanal-
   Kandidat in der Prüfliste), stehen in derselben Ansicht vor „Hinzufügen" drei Entscheidungen:
   - **Vertrauensstufe:** alle fünf Stufen mit je einem Satz Erklärung. Vorauswahl: die Stufe, die die
     Quelle schon hat; sonst „Nur einzeln geprüfte Videos" (die vorsichtigste, die etwas zeigt).
   - **Mindestalter:** 3 bis 16. Vorauswahl: das der Quelle; sonst 6.
   - **Kategorie:** eine der Kategorien oder „keine". Vorauswahl: die der Quelle.
2. Gespeichert wird in der **Quelle** (`trust`, `defaultAgeMin`, `defaultCategory`), angelegt oder
   aktualisiert, mit Eintrag im Verlauf (wer, wann, was). Hat die Kategorie ein höheres Mindestalter
   (`ContentCategory.minimumAge`), gilt das höhere; die Ansicht sagt das.
3. **„Gesperrt"** gewählt: Der Kanal kommt nicht in die Liste des Kindes, die Quelle wird als gesperrt
   gespeichert. Videos aus diesem Kanal werden künftig abgewiesen. Ein erneut eingefügter **Kanal**-Link
   öffnet die Vorschau mit „Gesperrt" vorausgewählt – Entsperren geht nur durch eine bewusste neue Wahl
   (so auf iOS und Android umgesetzt, 04.10.2026).
4. Sonst wird der **Kanal-Eintrag gleich freigegeben** (mit Alter und Kategorie) – die Entscheidung
   *ist* die Prüfung. Das gilt auch auf Android; dort entfällt der Umweg über die Prüfliste.
5. Unverändert: Videos und Playlists fragen wie bisher Alter und Kategorie; was eine Stufe erlaubt,
   regelt `ContentPolicy` (ADR 0002 gilt weiter). Einstufung bleibt Elternsache hinter PIN/Elternbereich.

## Begründung

Die Eltern wissen beim Hinzufügen am besten, was sie da gerade aufnehmen; später müssen sie den Kanal
erst wiederfinden. Der Preis: Eine zu großzügige Stufe ist schneller gewählt als bisher. Dagegen stehen
die Vorauswahl auf der vorsichtigen Stufe und die Erklärung je Stufe.

## Folgen

- Android: Vorschau-Dialog und Kanal-Freigabe in der Prüfliste bekommen die drei Wahlen; Kanäle aus
  dem Link-Weg sind sofort freigegeben.
- iOS: `AddWhitelistItemView` (und die Kanalsuche) bekommen die drei Wahlen für Kanäle.
- Die Regel (Stufe, Alter mit Kategorie-Mindestalter, Sperre) steht als reine Logik mit Tests auf beiden
  Plattformen gleich.
