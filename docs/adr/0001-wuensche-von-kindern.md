# ADR 0001 – Wünsche von Kindern: Thema, „Mehr davon“, „Neu bei deinen Kanälen“

Status: angenommen (Christian, 02.10.2026) – ergänzt durch ADR 0005 (freiwillige Benachrichtigung der Eltern)

## Kontext

SideTube zeigt Kindern nur, was Eltern freigegeben haben. Neues kommt bisher ausschließlich über die
Eltern in die App. Kinder sollen aber Neues entdecken und sich Inhalte wünschen können – ohne dabei
ungeprüfte Inhalte zu sehen. Schon Titel und Vorschaubilder aus dem offenen YouTube können ungeeignet
oder reißerisch sein. Eine offene YouTube-Suche für Kinder scheidet deshalb aus.

Geprüft wurden vier Wege: (1) Themenwunsch, (2) „Mehr davon“ bei einem freigegebenen Video,
(3) neue Folgen bekannter Kanäle, (4) ein von SideKids gepflegter Vorschlagskatalog.

## Entscheidung

Wege 1, 2 und 3 werden auf iOS und Android gebaut, mit derselben Bedeutung und denselben Grenzen.
Weg 4 nicht (siehe Begründung).

**Wunsch** (lokal gespeichert, je Profil): `art` ∈ {thema, mehrDavon, neueFolge}, Inhalt
(Stichwort bzw. Video-ID und Kanal), `status` ∈ {offen, erfuellt, abgelehnt, besprechen},
optional eine kurze Antwort der Eltern, Zeitpunkte.

1. **Themenwunsch.** In der Suche (und wenn sie nichts findet, dort zuerst) kann das Kind die
   Eingabe als Wunsch abschicken: „Wunsch an die Eltern: ‚Dinos‘“. Das Kind sieht dabei keine fremden
   Inhalte. Eingabe wie in der Suche (SidePhone: Rad-Suche; iPhone: Tastatur).
2. **„Mehr davon“.** Bei einem freigegebenen Video (Endkarte nach dem Video und Player-Menü) wünscht
   das Kind sich mehr davon. Der Wunsch trägt Video und Kanal als Anlass.
3. **„Neu bei deinen Kanälen“.** Für Kanäle mit der Stufe „Vertrauenswürdige Reihe“ (dort sind sonst
   nur einzeln freigegebene Videos sichtbar) zeigt die Startseite die neuesten Videos aus dem
   Kanal-Feed – mit Bild und Titel, aber **gesperrt** (nicht abspielbar) und mit „Wünschen“.
   Grenzen: höchstens 6 je Kanal, nicht älter als 60 Tage, keine Shorts/Livestreams, Risikofilter auf
   Titel, schon entschiedene Videos (abgelehnt/zurückgestellt) erscheinen nicht. Gesperrte Quellen nie.

**Für alle Wege:**
- Höchstens **3 Wünsche je Profil und Tag**; doppelte Wünsche (gleiches Thema, gleiches Video)
  entstehen nicht neu. Das Kind sieht, wie viele Wünsche heute noch gehen.
- Das Kind sieht unter „Meine Wünsche“ den Stand: offen · freigegeben (mit Weg zum Inhalt) ·
  „nicht jetzt“ (mit der Antwort der Eltern) · „sprechen wir drüber“.
- Eltern sehen Wünsche **hinter der PIN** zuerst in der Prüfliste, mit Herkunft. Aktionen:
  - neueFolge: Freigeben (legt einen freigegebenen Eintrag an), Ablehnen, Besprechen;
  - mehrDavon: Kanal prüfen (Kanal als Kandidat aufnehmen bzw. Stufe ändern), Video-Link
    hinzufügen, Ablehnen, Besprechen, Erledigt;
  - thema: Link hinzufügen (vorhandener Ablauf mit Vorschau), Erledigt, Ablehnen, Besprechen.
  Eine kurze Antwort an das Kind ist freiwillig.
- Keine Push-Benachrichtigungen, kein Ton; höchstens ein Zähler am Schloss/in der Prüfliste.
- Jeder Wunsch und jede Entscheidung erscheint im Verlauf der Freigaben.
- Kein Server, kein Konto: Wünsche bleiben auf dem Gerät. Netz nur für die Feeds bereits bekannter
  Kanäle (wie die Kanalansicht).

## Begründung

- Weg 1 zeigt dem Kind gar nichts Fremdes und führt zum Gespräch über Interessen; Aufwand trägt die
  Familie, nicht die App.
- Weg 2 und 3 nutzen Vorhandenes (Prüfliste, Vertrauensstufen, Kanal-Feeds) und zeigen nur Inhalte
  aus Quellen, die Eltern schon kennen und eingestuft haben. Das ist der kleinste Schritt zu echter
  Entdeckerfreude.
- Weg 4 bräuchte dauerhafte redaktionelle Pflege durch SideKids; das ist heute nicht zu leisten und
  würde SideKids zum Inhalteanbieter machen.
- **Preis:** Bei Weg 3 sieht das Kind Bild und Titel ungeprüfter Videos aus bekannten Kanälen. Das ist
  bewusst auf „Vertrauenswürdige Reihe“ begrenzt; Eltern, die das nicht wollen, stufen den Kanal auf
  „Nur einzeln geprüfte Videos“. Die Tagesgrenze kann Kinder frustrieren – gewollt, damit Wünschen
  kein Spiel ohne Ende wird.

## Folgen

- Neue Datenhaltung (iOS SwiftData-Modell, Android Room-Tabelle mit Migration), Unit-Tests für
  Tagesgrenze, Dubletten, Feed-Filter.
- Neue Szenen für die Vorstellungsvideos (Wunsch abschicken, Eltern entscheiden, Kind sieht Antwort).
- Website und Datenschutz: Wünsche bleiben lokal; die Datenschutzseite nennt die Feed-Abrufe
  bekannter Kanäle (schon für die Kanalansicht vorhanden).
- Die Tagesgrenze ist vorerst fest (3); ob Eltern sie einstellen können, wird nach dem Familientest
  entschieden.

## Umsetzung iOS (02.10.2026)

Stand und Auslegung, damit Android dieselben Regeln prüfen kann:

- **Datenmodell:** neue SwiftData-Entität `KidWish` (`profileID`, `kindRaw` = thema/mehrDavon/neueFolge,
  `statusRaw` = offen/erfuellt/abgelehnt/besprechen, `topic`, `videoId`/`videoTitle`/`thumbnailUrl`,
  `channelId`/`channelTitle`, `parentReply`, `fulfilledYoutubeId`, `createdAt`, `decidedAt`). Ohne Beziehung zu
  `KidProfile`, damit die Migration rein additiv ist (automatische Lightweight-Migration, Test vom alten Store);
  `ProfileRepository.delete` löscht die Wünsche mit.
- **Tagesgrenze:** 3 neu angelegte Wünsche je Profil und Kalendertag (Gerätezeitzone).
- **Dublette:** gleiche Art und gleicher Inhalt (Thema ohne Groß-/Kleinschreibung, Akzente und Mehrfach-Leerzeichen;
  bei Videos die Video-ID), solange der frühere Wunsch offen ist oder besprochen wird. Eine Dublette legt nichts an,
  zählt nicht zur Grenze und wird dem Kind als „Das hast du dir schon gewünscht“ gemeldet. Nach „erfüllt“ oder
  „abgelehnt“ darf derselbe Wunsch neu entstehen (die Tagesgrenze begrenzt Wiederholungen).
- **Übergänge:** offen → erfüllt/abgelehnt/besprechen; besprechen → erfüllt/abgelehnt/besprechen; erfüllt und
  abgelehnt sind endgültig. Eine leere Antwort lässt eine frühere Antwort stehen; Antworten höchstens 200 Zeichen.
- **neueFolge freigeben:** legt über den normalen Prüfweg (`discover` + `approve`) einen freigegebenen Eintrag an,
  Mindestalter und Kategorie aus der Quelle. Titel mit harten Filtertreffern lassen sich so nicht freigeben.
- **mehrDavon „Kanal prüfen“:** nimmt den Kanal als **Kandidaten** (Prüfung nötig, noch unsichtbar) in die Prüfliste
  und legt eine unbekannte Quelle mit „Nur einzeln geprüfte Videos“ an; die Stufe ist im selben Dialog änderbar.
  Der Wunsch bleibt offen, bis die Eltern „Erledigt“ wählen. „Video-Link hinzufügen“ bzw. „Link hinzufügen“
  (thema) nutzt den vorhandenen Ablauf mit Vorschau und erfüllt den Wunsch mit dem neuen Inhalt.
- **Feed-Filter „Neu bei deinen Kanälen“:** nur Kanäle in der Whitelist des Profils mit Stufe „Vertrauenswürdige
  Reihe“ (YouTube), Mindestalter der Quelle passt, keine Nachrichtenquellen. Je Kanal die 6 neuesten Einträge der
  letzten 60 Tage; es fallen weg: Einträge ohne Datum, Shorts (Link `/shorts/`, „#shorts“), Livestreams
  (Titelbegriffe) und angekündigte Premieren/Streams (`views="0"`). Risikofilter strenger als beim Stöbern: auch Themenhinweise
  schließen aus. Ausgeschlossen ist alles, was im Profil schon einen Whitelist-Eintrag hat (freigegeben,
  zurückgestellt, abgelehnt), und abgelehnte Wünsche. Laufende Livestreams sind im Feed nicht sicher erkennbar.
- **Verlauf:** jeder Wunsch und jede Entscheidung als `ReviewEvent` (`wished`, `wishFulfilled`, `wishRejected`,
  `wishDiscuss`); bei Videos unter der Video-ID, sodass sie im Verlauf des Videos stehen, bei Themen unter
  `thema:<stichwort>`; `itemVersion` = `wish:<UUID>` bündelt die Ereignisse eines Wunsches.
- **Zähler:** in der Prüfliste („Prüfen (n)“, Abschnitt „Wünsche“ zuerst) und in der Profilzeile des
  Elternbereichs; kein Zähler am Schloss im Kindermodus.
- **Offen:** Bedienung der gesperrten Kacheln und der Wunschkarte über das Click-Wheel; Gerätetest.

## Umsetzung Android (02.10.2026)

Gleiche Regeln wie unter „Umsetzung iOS“ (Tagesgrenze, Dublette, Übergänge, Antwort ≤ 200 Zeichen,
Feed-Filter mit `views="0"` und ohne Nachrichtenquellen, Verlauf unter Video-ID bzw. `thema:<stichwort>`).
Abweichend oder plattformeigen:

- **Datenmodell:** Room-Tabelle `wishes` (Schema 3, Migration 2→3 mit Test), Fremdschlüssel auf das Profil mit
  Kaskade; der Kanal-Cache bekommt `publishedAt` und `isShort` für den Feed-Filter. Regeln rein und geprüft in
  `core/curation/Wunsch.kt`, `NeueFolgen.kt`, `ContentPolicy.canShowLockedNewEpisode`; Speicher
  `core/repo/WunschRepository.kt`.
- **SidePhone-Bedienung:** Themenwunsch über die Zeile „Wunsch an die Eltern“ in der Rad-Suche (ohne Treffer die
  einzige Zeile, ▼ aus dem Rad) und ein **freies Rad** mit allen Buchstaben (die Suche bietet nur Buchstaben mit
  Treffern an, ein neues Thema ginge dort nicht). „Mehr davon“: Endkarte (Nochmal · Mehr davon wünschen · Zurück)
  und **Player-Menü** über die Taste außen oben rechts bzw. den Daumen-Knopf; das Video hält solange an.
  Gesperrte Folgen stehen als Abschnitt am Ende der Startseite; Mitte öffnet sie mit „Wünschen“ und „Zurück“.
  „Meine Wünsche“ ist die letzte Zeile der Startseite. Neue Elemente tragen den SideUI-Fokus.
- **Feed:** nur beim Aufbau der Startseite, je Kanal höchstens alle 30 Minuten; sofort aus dem Cache.
- **„Kanal prüfen“:** fehlt der Kanal in der Liste, kommt er als Kandidat in die Prüfliste (ohne bekannte
  Kanal-ID wird er über oEmbed/Kanalseite gesucht); steht er schon darin, öffnet sich „Quellen &
  Sicherheitsstufen“ (unbekannte Quelle mit „Nur einzeln geprüfte Videos“ angelegt). Der Wunsch bleibt offen bis
  „Erledigt“.
- **Link zum Wunsch:** Vorschau wie „Link hinzufügen“; der Eintrag kommt in die Prüfliste, der Wunsch zeigt
  darauf und ist **mit der Freigabe** erfüllt (jede Freigabe erfüllt passende offene Wünsche).
- **Zähler:** nur am „Prüfen“-Knopf des Profils und in der Prüfliste.
- **Offen:** Gerätetest am SidePhone.
