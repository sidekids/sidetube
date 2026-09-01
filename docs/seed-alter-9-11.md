# Startpaket 9–11 Jahre

Leitgedanke: **keine „Kinder-YouTube"-App, sondern eine Mediathek für die Altersstufe 9–11.**
Klassische Kinderformate funktionieren noch, Oberfläche und Auswahl sollen aber deutlich weniger kleinkindlich wirken
(FLIMMO trennt ähnlich zwischen 7–10 und 11–13). Import: Elternbereich → ⋯ → **Startpaket laden** → „Startpaket 9–11 Jahre".

## Profilvorgabe (wird beim Import gesetzt)
`ageBand = kids` (9–11) · Nachrichten **an**, aber belastende Meldungen nie auf Start · Manga zeichnen **an** ·
Anime & Manga (12+) **aus** · Shorts **aus** · Autoplay **aus** · offene Suche gibt es prinzipbedingt nicht.
Inhalte tragen `ageMin` 7 oder 9 – ein späteres Vorschulprofil sieht sie damit nicht.

## Auswahl: 40 Videos, alle als „Prüfung nötig"
| Quelle | Anzahl | Rolle |
|---|---:|---|
| Die Maus (WDR) | 8 | Kernbestand Wissen/Technik; darunter die von FLIMMO gelobten „Was ist Künstliche Intelligenz?" und „Wie arbeitet eine Suchmaschine?" |
| CHECKER WELT (BR) | 6 | Reportagen Wissen, Technik, Umwelt, Kreativ |
| PUR+ (ZDF) | 4 | ab 9: Umwelt, Tiere, Technikgeschichte |
| LfDI Baden-Württemberg (PeerTube) | 6 | „Datenschutz – leicht erklärt": Smartphone, Passwörter, Tracking, IT-Sicherheit, Soziale Netzwerke, Gaming |
| neuneinhalb (WDR) | 5 | Medienkompetenz und Gesellschaft: Datenschutz-Quiz, Geld mit YouTube/TikTok, Kinderrechte, Fast Fashion, Müll |
| ZDFtivi – Princess of Science | 3 | ab 9, MINT im Alltag – auf Start prominent statt unter „Lernen" |
| logo! (ZDF) | 4 | Nachrichten, ausschließlich als `NEWS_SAFE` eingestuft |
| KritzelPixel / Drawinglikeasir | 4 | Manga zeichnen und Perspektive (kein Anime-Feed) |

Schwerpunkte: Medienkompetenz 13 · Natur 6 · Umwelt 4 · Technik 4 · Manga/Zeichnen 4 · Wissen 3 · Gesellschaft 2 ·
Nachrichten 2 (+2 als Medienkompetenz/Umwelt geführt) · Kreativ 2.

## Bewusst **nicht** enthalten
Sesamstraße als Leitangebot (bleibt in der allgemeinen Bibliothek) · KiKA als Sammelkanal · Tanoshii (KiKA empfiehlt
ausdrücklich ab 12) · Shorts · Influencer-Vlogs, Pranks, Challenges · ungeprüfte Anime-Kanäle · offene Feeds.
Ebenfalls aussortiert: sehr junge Maus-Beiträge (Maßband, Kanaldeckel, Trudes Tier) und dünne Lifehack-/Ekel-Formate.

## Neue Quellen im Register
| Quelle | Stufe | Begründung |
|---|---|---|
| PUR+ `UCp3NGUmMHlYQdFuCNEM9W7A` | trustedSeries, ab 9 | ZDF-Wissensmagazin, FLIMMO ab 9 |
| ZDFtivi `UCrb6U1FuOP5EZ7n7LfOJMMQ` | perVideoReview | Sammelkanal (Löwenzahn, 1 2 oder 3, Dein Song); nur einzelne Reihen |
| WDR `UCn7wWR5KnpX_N6ZaBNuyVYw` | perVideoReview | **allgemeiner Erwachsenenkanal** (Kölner Treff, Feuer & Flamme, Kriminalthemen) – „neuneinhalb" nur als Einzelvideo, niemals stöbern |
| Wissen macht Ah! `UCI03DpfIrXGkmU5kJfojrfw` | perVideoReview, ab 7 | offizieller Kanal praktisch ohne Uploads (RSS leer); die „ganzen Folgen" auf YouTube stammen von Fremd-Uploadern → nicht verwenden |
| LfDI BaWü `pt:tube.xn--baw-joa.social` | trustedSeries, ab 9 | PeerTube-Instanz der Datenschutzaufsicht BW; Reihe für Schüler:innen. Instanz enthält überwiegend Fachvorträge für Erwachsene (IFG Days) → kein Stöbern |

## Anmerkungen zu den Quellen
- **Team Timster** hat keinen eigenen YouTube-Kanal; die Folgen liegen in der KiKA-/ZDF-Mediathek. Für die genannten
  Themen (KI, Google, Fake News/Deepfakes) sind ersatzweise Die Maus („Was ist Künstliche Intelligenz?",
  „Wie arbeitet eine Suchmaschine?"), neuneinhalb (Datenschutz, Geld mit YouTube/TikTok) und die LfDI-Reihe enthalten.
  Sobald ZDF/KiKA als Provider abspielbar sind, kommt Team Timster dazu.
- **ARTE Family DE** ließ sich nicht verifizieren: der Handle `@artefamilyde` antwortet mit 404, die bekannten
  „Eure Fragen"-Video-IDs sind über oEmbed nicht auflösbar. Keine IDs raten → nicht aufgenommen, erneut prüfen.
- **Sexting/Cybergrooming** aus der LfDI-Reihe sind bewusst nicht im 10er-Paket (für ältere Profile vorgesehen).
- Alle IDs am 2026-08-31 über RSS, oEmbed bzw. die PeerTube-API verifiziert – vor der Freigabe trotzdem jedes Video ansehen.
