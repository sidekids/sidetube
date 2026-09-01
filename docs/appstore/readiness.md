# App-Store-Bereitschaft (Veröffentlichung als SideKids)

Stand 2026-08-31, Branch `feature/appstore-readiness`. Ziel: SideTube (und weitere Apps) erscheinen unter dem Anbieter
**SideKids**, perspektivisch im App Store.

## Jetzt festgelegt (später nicht oder nur schwer änderbar)
| Punkt | Entscheidung / Stand |
|---|---|
| Anbietername im Store | Kommt aus dem **Developer-Program-Account**, nicht aus der App. „SideKids" als Verkäufername ist nur mit einer **Organisation** möglich (Rechtsform + D-U-N-S-Nummer). Einzelpersonen/Einzelunternehmer müssen laut Apple als *Individual* einreichen → dann steht der bürgerliche Name als Anbieter. **Entscheidung:** Organisation gründen/nutzen (z. B. UG, GbR reicht nicht überall, e.V.) oder erst als Individual starten und die App später auf eine Organisation **übertragen** (App-Transfer ist möglich, Bundle-ID bleibt). |
| Bundle-ID | `xyz.steier.sidetube` bleibt. Sie ist für Nutzer unsichtbar, muss nicht zur Marke passen und ist nach dem ersten Store-Upload **permanent**. Ein Wechsel jetzt würde die installierte Dev-App zur „vierten App" machen (kostenloses Profil: max. 3). |
| App-Name | `sidetube` (Store-Name kann ergänzt werden: „sidetube – Kinder-Mediathek"), Untertitel siehe metadata-de.md |
| Version | `MARKETING_VERSION` 0.1.0 / `CURRENT_PROJECT_VERSION` 1 in project.yml (Build-Nummer bei jedem Upload erhöhen) |
| Icon | Platzhalter-Icon (dunkel, Bernstein-Ring, Play) in `Assets.xcassets/AppIcon` – **durch SideKids-Design ersetzen** |
| Kategorie | `LSApplicationCategoryType` = Bildung; Store: Primär Bildung, Sekundär Unterhaltung |
| Copyright | „© 2026 SideKids" |
| URL-Schema | `sidetube://` bleibt (Empfehlungslinks); Universal Links erst mit SideKids-Domain |
| Datenschutz | `docs/privacy-policy.md` → veröffentlichen unter `sidekids.github.io/about/privacy.html` (GitHub Pages, siehe Entscheidung 3) |

## Zielgruppe: Kinder unter 13 (Festlegung 2026-08-31)
Die App richtet sich ausdrücklich **auch an Kinder unter 13**. Folgen:
- **COPPA/DSGVO-Art. 8** gelten; die App erhebt keinerlei personenbezogene Daten von Kindern (lokal, keine Konten) – das
  muss so bleiben (keine Analytics-/Werbe-SDKs, kein Login, keine Kontaktdaten).
- YouTube: SideTube ist damit zwingend ein **Child-Directed API Client** → Meldung bei Google Pflicht, keine
  write-Aktionen, kein personalisiertes Advertising (siehe docs/content-safety.md).
- Apple: Altersfreigabe 4+; im Fragebogen „Made for Kids"/Zielgruppe ehrlich angeben. Guideline 5.1.4 (Kids) greift bei
  Apps, die sich an Kinder richten – erfüllt, weil keine Daten erhoben/weitergegeben werden. Die Kids-**Kategorie** ist
  davon getrennt zu entscheiden (siehe unten).

## Entscheidungen (2026-08-31)
1. **Developer Program als Individual** (Einzelperson). Anbietername im Store = bürgerlicher Name; SideKids erscheint als
   Marke in App-Name/Beschreibung/Icon. Späterer App-Transfer auf eine Organisation möglich.
2. **Kids-Kategorie: ja.** Konsequenzen umgesetzt: Elternschranke (Eltern-PIN) vor jeder Aktion, die die App verlässt
   (Empfehlen per Nachricht/Signal, Link kopieren, „Original auf YouTube" bei Empfehlungen); keine Drittanbieter-
   Analytics/-Werbung in der App; keine Datenerhebung. Altersband für die Kategorie: **6–8** vorschlagen (Profile decken
   3–12+ ab; Apple verlangt ein Band – 6–8 passt zur Seed-Bibliothek). **Restrisiko:** Werbung im eingebetteten YouTube-Player
   (siehe unten) kann zur Ablehnung führen; dann Ausweichplan: Mediathek-Provider (KiKA/ZDF/ARD) oder Einreichung außerhalb
   der Kids-Kategorie mit Kindersicherung.
3. **Web-Präsenz:** GitHub-Organisation **github.com/sidekids**; Website als **GitHub Pages** (`sidekids.github.io`),
   Entwurf liegt in Gitea `SideKids/website` (index.html, about/privacy.html, repos/). URLs in `Brand.swift`:
   Support `https://github.com/sidekids`, Datenschutz `https://sidekids.github.io/about/privacy.html`.
   **Erledigt 2026-08-31:** Pages veröffentlicht (`github.com/sidekids/sidekids.github.io`, kuratierte Teilmenge des
   Gitea-Entwurfs: nur SideTube als vorzeigbare App, Philosophie-/Datenschutzseiten); `about/privacy.html` enthält den
   App-spezifischen Abschnitt (EN + DE) → `https://sidekids.github.io/about/privacy.html` ist erreichbar (HTTP 200).
   Der vollständige Entwurf mit allen späteren Apps bleibt in Gitea `SideKids/website`; beim Veröffentlichen nur die
   freigegebenen Seiten kopieren.
   Universal Links (`sidekids.github.io/.well-known/apple-app-site-association`) sind mit GitHub Pages möglich.

## Kids-Kategorie: Achtung
Apple Review-Guideline 1.3 / 5.1.4: Apps in der **Kids-Kategorie** dürfen **keine Drittanbieter-Werbung und keine
Drittanbieter-Analytics** enthalten und Links aus der App heraus nur hinter einer **Elternschranke (Parental Gate)**.
Der eingebettete YouTube-Player kann **YouTube-Werbung** ausspielen – das ist mit der Kids-Kategorie voraussichtlich
**nicht vereinbar**. Realistischer Weg: **nicht** in der Kids-Kategorie einreichen, sondern als Bildungs-App mit
Altersfreigabe 4+/9+, Kindersicherung (PIN) und klarer Beschreibung. Wer die Kids-Kategorie will, braucht einen
werbefreien Provider (Mediatheken) statt YouTube-Embeds. Unabhängig davon empfehlenswert: Elternschranke vor Links,
die die App verlassen (Empfehlen per Nachricht/Signal, „Original auf YouTube") – als Profil-Einstellung; **offen**.

## Review-relevante Punkte (Checkliste)
- [ ] Developer Program (Individual oder Organisation) aktiv, Team in Xcode aktualisiert
- [ ] App in App Store Connect angelegt (Bundle-ID `xyz.steier.sidetube`, SKU `sidetube`)
- [ ] Icon final, Screenshots 6,7"/6,1" (Skript `ScreenshotUITests`), App-Vorschau optional
- [ ] Metadaten aus `metadata-de.md` eingetragen · [x] Datenschutz-URL und Support-URL erreichbar (sidekids.github.io, github.com/sidekids)
- [ ] „App Privacy": keine Datenerhebung (lokal; YouTube-Abrufe ohne Nutzerkennung) – Angabe „Daten werden nicht erfasst"
- [ ] Altersfreigabe-Fragebogen: keine Gewalt/Sex/Glücksspiel; „Unbeschränkter Webzugang: Nein" (nur eingebettete, freigegebene Videos)
- [ ] YouTube-API-Compliance: Child-Directed-Meldung bei Google (siehe docs/content-safety.md), API-Key iOS-beschränkt
- [ ] Review-Notizen: Test-PIN, Demo-Profil mit freigegebenen Inhalten, Erklärung Whitelist-Prinzip
- [ ] Export-Compliance: `ITSAppUsesNonExemptEncryption = false` (gesetzt)
- [ ] Keine Debug-Startparameter im Release (alle `#if DEBUG`) – erfüllt
- [ ] TestFlight-Betatest in der Familie vor Einreichung

## Weitere SideKids-Apps
Gleiche Struktur wiederverwenden: `Brand.swift`, Anbieter SideKids, Bundle-Präfix `xyz.steier.` (oder später
`de.sidekids.` für neue Apps, sobald die Domain existiert), Gitea-Org `SideKidsDev`.
