# Inhaltssicherheit (Content Safety)

Stand 2026-08-31, Branch `feature/safe-content-curation`. Grundregel: **Neue Inhalte sind standardmäßig nicht
freigegeben.** Erst eine bewusste Freigabe im Elternbereich macht einen Inhalt im Kinderprofil sichtbar.

## Konzept
```
Redaktion (Eltern) kuratiert → freigegebene Quellen → freigegebene Videos → Altersprofil → Kinder-App
```
Im Kinderbereich gibt es keine freie YouTube-Suche, kein algorithmisches Discovery, keine Kommentare, keine
Shorts-Endlosschleife, keine Livestreams, kein automatisches „Nächstes Video" außerhalb der Whitelist, keine
Kanalzulassung wegen Popularität.

## IST → SOLL (Bestandsanalyse)
| | Vorher (`redesign/native-ui`) | Jetzt |
|---|---|---|
| Whitelist | `WhitelistItem` pro Profil = sichtbar | `WhitelistItem` mit `approvalStatus`, Alter, Kategorie, Risikoflags; sichtbar nur `approved` + `ContentPolicy` |
| Quellen | Kanal in Whitelist = Kanal komplett durchstöberbar (RSS/API) | `CuratedSource` mit Sicherheitsstufe; Stöbern nur bei `trustedChildSource`, sonst nur Einzelfreigaben |
| Alter | keins | `AgeBand` je Profil (3–5, 6–8, 9–11, 12+), `ageMin/ageMax` je Inhalt, Kategorien mit Mindestalter |
| Suche | lokal in Whitelist + Kanal-Cache | lokal, **nur freigegebene Inhalte** + Cache vertrauenswürdiger Kinderquellen; „Keine freigegebenen Videos gefunden." |
| Autoplay | nächstes Video automatisch | **aus** (Standard); Ende → `stopVideo()` + bewusste Wahl |
| Eltern | Link hinzufügen = freigeben | Link → Risikofilter → **Prüfen** (Freigeben/Ablehnen/Später) mit Alter/Kategorie, Audit-Trail |
| Empfehlungen (`sidetube://add`) | nach PIN direkt sichtbar | nach PIN **zur Prüfung** |

## Altersmodell
`AgeBand.preschool` (3), `.younger` (6), `.kids` (9), `.older` (12) → Prüfalter = Untergrenze der Gruppe. Ein Inhalt ist
sichtbar, wenn `ageMin ≤ Alter ≤ ageMax`. Kategorien mit Mindestalter: *Manga zeichnen* 8, *Anime & Manga* 12.
`sensitiveTopics` (war, violence, death, disaster, crime, fear, politics, sexual, coarseLanguage, horror, adultMedia,
advertising) werden vom Filter gesetzt und in der Redaktionsansicht angezeigt; `containsSexualContent` blendet immer aus.

## Trust-Modell (Quellen)
| Stufe | Bedeutung | Kanal stöbern | Beispiel |
|---|---|---|---|
| `trustedChildSource` | vollständig kindorientiert | ja (mit Risikofilter, ohne Shorts/Lives) | Die Maus, Sesamstraße |
| `trustedSeries` | seriöse Reihe, Einzelthemen prüfen | nein | Checker Welt, logo! (News-Prüfung), Tanoshii (12+) |
| `perVideoReview` | nur einzeln geprüfte Videos | nein | KiKA, KritzelPixel, Drawinglikeasir, TOKYOPOP, altraverse |
| `parentOnly` | nur Eltern sehen die Quelle | nein | ninotakutv |
| `blocked` | nie im Kinderprofil, Entdeckung wird abgewiesen | nein | Raafey, Kurono |

## Freigabeworkflow
Quelle → Video gefunden (Link, Empfehlung, Seed) → Metadaten → **automatische Vorprüfung** (`RiskScreen`) →
`reviewRequired` (oder `rejected` bei harten Begriffen wie 18+/NSFW/Hentai/Ecchi) → **menschliche Freigabe** mit Alter,
Kategorie, Nachrichtenstatus, Anmerkung → `approved` → sichtbar. Jede Entscheidung erzeugt ein `ReviewEvent`
(wer, wann, Version des Inhalts, Entscheidung, Notiz). `expiredReview` markiert fällige Freigaben (12 Monate; 6 bei
Nachrichten/Einzelprüfung) – ohne automatisches Ablehnen.

## Nachrichten
`isNews` + `newsStatus` (`safe` / `sensitive` / `parentReview`). Der Filter setzt `sensitive` bei Krieg, Gewalt, Tod,
Katastrophe, Verbrechen, Angst, Horror; `parentReview` bei Politik. Nur `safe` ist im Kinderprofil sichtbar; belastende
Nachrichten werden auf Home nie hervorgehoben. Eltern können den Status bei der Freigabe ändern.

## YouTube-Einschränkungen (ehrlich)
- **Related Videos lassen sich im eingebetteten Player nicht abschalten.** `rel=0` bedeutet seit 2018 „Empfehlungen aus
  demselben Kanal" ([Player-Parameter](https://developers.google.com/youtube/player_parameters)). Beim Pausieren und am
  Ende zeigt YouTube Vorschläge. Gegenmaßnahmen: am Ende `stopVideo()` (zurück zum Vorschaubild, erlaubter API-Aufruf),
  Navigation aus dem Player gesperrt, Autoplay aus – und vor allem: **Stöbern nur in vollständig kindorientierten Quellen**.
  Bei gemischten Kanälen (Manga-Creator, Verlage) ist deshalb nur die Einzelfreigabe vorgesehen; ein Kanal-Empfehlungsraster
  aus einem solchen Kanal bleibt ein Restrisiko, das die App **nicht** kaschiert. Für ein geschlossenes System wären
  offizielle Kindermediatheken (KiKA/ZDF/ARD) als Provider nötig – Abstraktion `ContentProvider` ist angelegt,
  Wiedergabe für Nicht-YouTube-Quellen in v0.1 nicht implementiert (Einträge sind sichtbar, aber ohne Player).
- Keine Downloads, keine Werbeentfernung, kein Verdecken von YouTube-Branding.

## YouTube-API-Compliance (Prüfung 2026-08-31)
Quelle: [YouTube API Services – Developer Policies](https://developers.google.com/youtube/terms/developer-policies),
Abschnitt III.J („Child-Directed API Clients") sowie III.E.4.9/4.10 und III.F.3.
- SideTube ist ein **Child-Directed API Client** – die App richtet sich ausdrücklich auch an Kinder unter 13 (Festlegung 2026-08-31). Pflichten: COPPA/DSGVO einhalten und Google
  über die Kindzielgruppe informieren („notify Google … using the tools provided"). **Offen:** die Meldung über das von
  Google bereitgestellte Formular/Tool im Google-Cloud-Projekt des API-Keys – muss der Betreiber erledigen.
- Keine write-basierten Aktionen (Upload, Kommentare, Playlists) – SideTube nutzt ausschließlich lesende Endpunkte
  (`videos/channels/playlists/playlistItems.list`), keine Kommentare, keine Uploads, keine Playlist-Manipulation.
- Keine personalisierte Werbung, kein Tracking (keine SDKs, keine Analytics). Für *Made-for-Kids*-Inhalte gilt zusätzlich
  „turn off tracking" – erfüllt, da nichts getrackt wird. `madeForKids`-Status wird gespeichert (Data API `status.madeForKids`),
  ohne Key `unknown`.
- Branding/Attribution des Players wird nicht verdeckt (III.F.3).
- EU User Consent Policy: die App zeigt keine Werbung und setzt keine Cookies; der einzige Cookie (`SOCS=CAI`) dient dem
  schlüssellosen Lesen der Kanalseite in einer ephemeren Session und wird nicht gespeichert.

## PeerTube (umgesetzt 2026-08-31)
Zweiter abspielbarer Anbieter neben YouTube – und der einzige, der eine **werbefreie, geschlossene** Wiedergabe erlaubt.
- **Kennungen:** Videos `pt:<ursprungs-host>:<shortUUID>`, Kanäle `pt:<host>:<name>`, Instanzen `pt:<host>`. Der Ursprungs-Host
  wird aus `channel.host` übernommen (föderierte Videos behalten ihre Heimat-Instanz).
- **Daten:** offene REST-API ohne Schlüssel (`/api/v1/videos/{id}`, `/api/v1/video-channels/{name}@{host}[/videos]`),
  Kanalvideos serverseitig mit `nsfw=false`; Livestreams werden zusätzlich clientseitig verworfen.
- **Player:** `PeerTubePlayerBridge` lädt die Embed-Seite mit `p2p=0&peertubeLink=0&warningTitle=0&title=0` – kein P2P,
  kein Instanz-Link, kein Warnhinweis, **keine Werbung, keine fremden Empfehlungen**. Navigation aus dem Player gesperrt
  (nur `/videos/embed/`-Pfade), Steuerung über das `<video>`-Element, Zustand/Position im Sekundentakt.
- **Instanz-Allowlist:** Föderation heißt sehr unterschiedliche Moderation. Deshalb liefern nur Instanzen Inhalte, die im
  Quellenregister stehen (`SourceRegistry.peerTubeInstances`, Standard `perVideoReview`); unbekannte Instanzen werden beim
  Hinzufügen **und** beim Entdecken abgewiesen (`PeerTubePolicy`), und ein Video ohne passende Instanz-Quelle ist nie sichtbar.
  Eltern können Instanzen unter „Quellen & Sicherheitsstufen" ergänzen – eine **eigene Familien-Instanz** kann dort auf
  „vertrauenswürdige Kinderquelle" gesetzt werden und ist dann durchstöberbar.
- **NSFW:** das `nsfw`-Flag der Instanz führt zur automatischen Ablehnung (wie ein harter Filtertreffer).
- **Empfehlen:** verschickt den Original-Link `https://<host>/w/<id>` (kein `sidetube://`-Importlink für Fremdanbieter).
- **Grenzen:** eine eigene Instanz darf **keine** fremden YouTube-/Sender-Inhalte spiegeln (Urheberrecht). Ohne eigene
  Instanz bleibt PeerTube inhaltlich dünn für deutsche Kinderinhalte – der Wert liegt in der geschlossenen Wiedergabe.
- **Vimeo (nicht umgesetzt):** Embed sauber (keine Werbung, `dnt=1` schaltet Tracking ab, Endscreen bestimmt der Uploader), oEmbed ohne
  Schlüssel; Metadaten-API braucht OAuth-Token; Uploader können Einbettung auf Domains beschränken → Videos können im
  App-Player scheitern. Inhaltlich für deutsche Kinderinhalte dünn → geringer Nutzen.
Beide passen in die `ContentProvider`-Abstraktion (URL-Parser + Metadaten-Client + Embed-Player je Provider; Trust je
Kanal/Instanz; `nsfw` → automatische Ablehnung).

## Datenschutz
Alle Daten lokal (SwiftData). Sehverlauf nur pro Profil auf dem Gerät (Tageslimit, Weiterschauen). Keine Analytics,
keine externen SDKs, keine Daten an Dritte außer den notwendigen YouTube-Abrufen (oEmbed, RSS, Kanalseite, Data API).

## Bekannte Grenzen
Related Videos im Embed (s. o.); Nicht-YouTube-Provider ohne Wiedergabe; Risikofilter ist wortbasiert (unauffällige
Titel können problematisch sein – deshalb menschliche Prüfung Pflicht); `madeForKids` nur mit API-Key; Hintergrund-Import
neuer Videos nicht umgesetzt – Kandidaten kommen über Links, Empfehlungen und die Seed-Bibliothek.
