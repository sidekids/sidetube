# Kuratierung: Einstellungen und Redaktionsansicht

## Bedienung
- **Profil** (Einstellungen → Profil bearbeiten): Altersprofil, Nachrichten an/aus, Manga zeichnen an/aus,
  Anime & Manga (nur 12+) an/aus, Shorts erlauben, „Nächstes Video automatisch" (Standard aus), Tageslimit.
- **Link hinzufügen**: YouTube **oder PeerTube** (`…/w/<id>`, `…/c/<kanal>`); Vorschau → Filterhinweis → Alter/Kategorie →
  **Jetzt freigeben** oder **Erst zur Prüfung merken**. PeerTube-Links aus nicht eingetragenen Instanzen werden abgewiesen.
  Kanäle werden in derselben Vorschau eingestuft (Stufe, Mindestalter, Kategorie – siehe unten, ADR 0003).
- **Prüfen** (Whitelist → Häkchen-Siegel): Kandidaten mit Bild, Titel, Quelle, Länge, Altersempfehlung, Risiken,
  Made-for-Kids, Kategorie; Entscheidung **Freigeben / Ablehnen / Später**, Felder Alter, Höchstalter, Kategorie,
  Nachrichtenstatus, Anmerkung; Verlauf (Audit-Trail) je Video. „Auswählen“ entscheidet mehrere auf einmal (ADR 0004, unten).
- **Quellen & Sicherheitsstufen** (Dashboard-Menü): Stufe je Quelle ändern, Sperren, **PeerTube-Instanz hinzufügen**
  (Adresse eingeben → Stufe „nur einzeln geprüfte Videos"; eigene Familien-Instanz danach hochstufbar).
- **Startbibliothek laden** (Dashboard-Menü): importiert `seed-library.json` als Prüfkandidaten ins erste Profil (nie freigegeben).
- Eingehende Empfehlungen (`sidetube://add`) landen nach der PIN unter „Prüfen".

## Kanal beim Hinzufügen einstufen (ADR 0003) – Stand iOS

Stand 04.10.2026, Zweig `feature/kanal-einstufen-ios`. Android folgt getrennt.

- **Wo:** Link-Vorschau eines Kanals (`AddWhitelistItemView`), Kanalsuche „Abo finden" (`ChannelSearchView` →
  `KanalEinstufenSheet`), „Kanal prüfen" aus einem Wunsch „Mehr davon" (`WishDecisionView`) und Kanal-Kandidaten
  älterer Stände in der Prüfliste (`ReviewQueueView`). Videos und Playlists unverändert.
- **Wahlen:** alle fünf Stufen offen mit je einem Satz Erklärung (`SourceTrust.erklaerung`), Mindestalter 3–16,
  Kategorie inkl. „Keine". Vorauswahl aus der vorhandenen Quelle (bei PeerTube ggf. der Instanz), sonst
  „Nur einzeln geprüfte Videos" / 6 / keine; ein Register-Mindestalter 0 („nicht festgelegt") wird als 6 vorgeschlagen.
  Hebt die Kategorie das Alter an, sagt die Ansicht das; gespeichert wird das höhere Alter.
- **Speichern:** `CurationRepository.kanalAufnehmen` legt die Quelle des Kanals an oder aktualisiert sie
  (`trust`, `defaultAgeMin`, `defaultCategory`) mit Verlaufseintrag (`source:<id>`, Akteur „Eltern"). Geschrieben wird
  immer die Quelle genau dieses Kanals, nie die einer PeerTube-Instanz. „Gesperrt" → nichts in die Whitelist, Meldung
  bleibt stehen, ein Wunsch wird nicht als erfüllt gemeldet. Sonst Kanal-Eintrag sofort freigegeben (mit Alter und
  Kategorie); ein schon vorhandener Kandidat wird freigegeben statt verdoppelt. `WishRepository.proposeChannel`
  (ungeprüfter Kandidat) ist entfallen, ersetzt durch `kanalEntwurf`.
- **Regel:** `ios/Sources/Domain/Kanaleinstufung.swift`, Tests `ios/Tests/KanaleinstufungTests.swift`.
- **Prüfung:** Unit-Tests 257/257 grün (iOS-Simulator, eigens angelegt und danach gelöscht); App-Build für den
  Simulator grün; UI-Test `KanalEinstufenUITests` (netzfreie ESA-Vorschau: sperren → Vorauswahl „Gesperrt" →
  neu einstufen → Kanal in der Liste) grün. (Bildschirmfotos am 05.10.2026 aus dem Repo genommen – sie zeigten fremde Logos und Vorschaubilder; neue Aufnahmen mit frei lizenzierten Inhalten folgen.) Angepasst, aber nicht gelaufen (Live-Netz bzw. ESA steht schon im
  Startpaket): `VideoEinrichtungUITests`, `DemoRecordingUITests`.

## Mehrere auf einmal prüfen (ADR 0004) – Stand iOS

Stand 04.10.2026, Zweig `feature/sammelpruefung-ios`. Regeln gleich der Android-Fassung
(`feature/sammelpruefung-android`, `core/.../curation/Sammelpruefung.kt`).

- **Wo:** Prüfliste (`ReviewQueueView`) → „Auswählen“: Häkchen je Eintrag, „Alle auswählen“/„Keine auswählen“,
  unten „Ablehnen (N)“ und „Freigeben (N)“. Wünsche sind im Auswahlmodus ausgeblendet (keine Einträge der Liste).
- **Sammelfreigabe** (`SammelFreigabeSheet`): Mindestalter „Wie vorgeschlagen“ oder Stepper 3–16; Kategorie
  „Wie vorgeschlagen“, „Keine“ oder eine Kategorie; sind Kanäle dabei, eine Vertrauensstufe (alle außer „Gesperrt“,
  voreingestellt „Nur einzeln geprüfte Videos“). „Wie vorgeschlagen“ = unverändertes Einzel-Freigeben: Alter (auch 0)
  und Kategorie des Eintrags, Höchstalter und Anmerkung bleiben; bei Kanälen die Vorauswahl wie Android
  (erstes Alter ab 3 aus Eintrag, dann Quelle, sonst 6; Kategorie des Eintrags). Kategorie-Mindestalter hebt je
  Eintrag an; ein Höchstalter fällt nie darunter. Kanäle bekommen Stufe/Alter/Kategorie in ihrer Quelle (ADR 0003).
- **Nicht sammelbar** (bei Freigabe *und* Ablehnung unberührt, mit Hinweis „N brauchen eine Einzelprüfung: …“):
  nicht mehr offen · Quelle gesperrt · nur für Eltern · Filtertreffer (Themen am Eintrag, „Risikobegriffe:“ im
  Vermerk oder frische Vorprüfung des Titels mit Themen- oder hartem Treffer; Shorts/Live zählen nicht) ·
  Nachricht prüfen (`newsStatus` „Eltern prüfen“ oder „belastend“). Jeder Eintrag wird beim Ausführen neu geprüft.
- **Ablehnen:** Rückfrage „N Einträge ablehnen?“ (N = ablehnbare), dann jeder einzeln.
- **Verlauf:** je Eintrag ein eigener `ReviewEvent` (Akteur „Eltern“, Vermerk „Sammelprüfung“; eine vorhandene
  Anmerkung wird angehängt, die Anmerkung für die Familie bleibt unverändert). Freigaben erfüllen offene
  „Neue Folge“-Wünsche genau dieses Videos (`WishRepository.erfuelleDurchFreigabe`, wie Android).
- **Regel:** `ios/Sources/Domain/Sammelpruefung.swift`; Speichern: `CurationRepository.sammelFreigeben` /
  `sammelAblehnen`; Tests `ios/Tests/SammelpruefungTests.swift`.
- **Prüfung:** Unit-Tests 284/284 grün (davon 27 neu), UI-Tests `SammelpruefungUITests` (allgemeines Startpaket:
  alle auswählen, einen abwählen, Hinweis auf 3 Filtertreffer, Alter setzen, freigeben; dann Rest ablehnen – die
  Filtertreffer bleiben), `StarterPackUITests` und `KanalEinstufenUITests` grün – auf einem eigens angelegten und
  danach gelöschten Simulator. (Bildschirmfotos am 05.10.2026 aus dem Repo genommen – sie zeigten fremde Logos und Vorschaubilder; neue Aufnahmen mit frei lizenzierten Inhalten folgen.)
- **Offen:** Das Einzel-Freigeben in der Prüfliste erfüllt auf iOS weiterhin keine Wünsche (Android tut es) –
  bewusst nicht angefasst (ADR 0004, 6.).

## Seed-Bibliothek (38 Kandidaten, alle REVIEW_REQUIRED)
Verteilung: Wissen 6 · Natur 4 · Technik 4 · Umwelt 4 · Kreativ 2 · Geschichten 2 · Humor 2 · Gesellschaft 2 ·
Medienkompetenz 1 · Nachrichten 4 (1 davon `sensitive` als Beispiel) · Manga zeichnen 4 · Zeichnen 4 ·
Anime & Manga 2 (TOKYOPOP-Kinderbuch 10+, Tanoshii 12+ als ZDF-Quelle ohne Wiedergabe).
Alle YouTube-IDs am 2026-08-31 über RSS/oEmbed verifiziert; **vor der Freigabe jedes Video ansehen**.

## Kanal beim Hinzufügen einstufen (ADR 0003)

**Android – Stand 04.10.2026 (Zweig `feature/kanal-einstufen-android`):**
- Link → Vorschau: Ist es ein Kanal, fragt der Dialog „Kanal hinzufügen“ Vertrauensstufe (alle fünf mit
  je einem Satz), Mindestalter (3–16) und Kategorie (oder „keine“); hebt die Kategorie das Alter an, sagt
  er es. Vorauswahl aus der vorhandenen Quelle, sonst „Nur einzeln geprüfte Videos“ / 6 / keine.
- „Hinzufügen“ speichert die Quelle (`trust`, `defaultAgeMin` = effektives Mindestalter,
  `defaultCategory`, Verlauf unter `source:<channelId>`, Akteur „Eltern“) und gibt den Kanal-Eintrag
  gleich frei – kein Umweg über die Prüfliste. Ein Wunsch-Link wird verknüpft und mit der Freigabe erfüllt.
- „Sperren“: Quelle gesperrt, kein Eintrag, Meldung; künftige Links des Kanals werden abgewiesen.
- Kanal-Kandidaten in der Prüfliste (z. B. aus „Kanal prüfen“ bei einem Wunsch) zeigen in der
  Prüfmaske dieselben Felder; „Kanal sperren“ lehnt den Kandidaten ab.
- Ausnahmen: Ein schon abgelehnter Kanal wird per Link nicht still freigegeben, ein harter
  Filtertreffer auch nicht – beides meldet die App und verweist auf die Liste.
- Regel: `core/.../curation/Kanaleinstufung.kt`; Speichern: `CurationRepository.nimmKanalAuf` /
  `stufeKanalEin`; Oberfläche: `parent/KanalEinstufungFelder.kt`.
- Prüfung: `./gradlew testDebugUnitTest assembleDebug` grün (324 Tests, davon neu
  `KanaleinstufungTest`, 7 in `CurationRepositoryTest`, 3 in `ParentViewModelTest`). Nicht am Gerät
  geprüft – auf dem Emulator liegt der Video-Demostand.

**iOS:** offen (`AddWhitelistItemView`, Kanalsuche).

## Mehrere auf einmal prüfen (ADR 0004)

**Android – Stand 04.10.2026 (Zweig `feature/sammelpruefung-android`):**
- Prüfliste → **„Auswählen“**: Häkchen je Eintrag, darüber fest (scrollt nicht weg) „Alle auswählen (N)“ und
  der Hinweis „N brauchen eine Einzelprüfung: Filtertreffer (2), nur für Eltern (1)“. Unten eine Leiste mit
  **„Freigeben (N)“** und **„Ablehnen (N)“**; ✕ oben beendet die Auswahl. Wünsche stehen im Auswahlmodus nicht
  in der Liste – sie werden weiter einzeln beantwortet.
- **Nicht sammelbar** (Häkchen gesperrt, Zeile „Einzeln: <Grund>“, Tippen öffnet die Einzelprüfung): Eintrag
  nicht mehr offen; Quelle gesperrt; Quelle „nur für Eltern“; Filtertreffer (Themen am Eintrag, vermerkte
  Treffer oder eine frische Vorprüfung des Titels mit Themen- oder hartem Treffer – fängt auch einen
  zurückgestellten harten Treffer); Nachricht mit `newsStatus` „Eltern prüfen“ **oder „heikel“**. Gilt für
  Freigabe *und* Ablehnung; das Repository prüft beim Ausführen jeden Eintrag frisch und lässt Nicht-Sammelbares
  unberührt.
- **Sammel-Maske „N Einträge freigeben“**: Mindestalter (Schalter, aus = „wie vorgeschlagen“, an = Stepper 3–16),
  Kategorie (Aufklappliste „wie vorgeschlagen“ / „keine“ / Kategorien; Hinweis, wenn sie das Alter anhebt), bei
  Kanälen in der Auswahl „Stufe für K Kanäle“ (vier Stufen, **ohne „Gesperrt“**, Vorauswahl „Nur einzeln geprüfte
  Videos“, nur die gewählte Stufe erklärt). Passt auf das SidePhone ohne Scrollen
  (Bildschirmfotos am 05.10.2026 aus dem Repo genommen – sie zeigten fremde Logos und Vorschaubilder; neue Aufnahmen mit frei lizenzierten Inhalten folgen.).
- **Je Eintrag:** „wie vorgeschlagen“ = was ein unverändertes „Freigeben“ in der Einzelmaske speicherte (Alter und
  Kategorie des Eintrags, Höchstalter und Anmerkung bleiben). Gesetzte Werte gelten für alle; das
  Kategorie-Mindestalter hebt je Eintrag an, ein Höchstalter fällt nie darunter. Kanäle: Vorschlag wie
  `Kanaleinstufung.vorauswahl`, dazu die gewählte Stufe → `stufeKanalEin` (Quelle mit Verlauf, ADR 0003).
- **Ablehnen** fragt „N Einträge ablehnen?“. Jeder Eintrag bekommt seinen eigenen Verlaufseintrag (Akteur
  „Eltern“, Vermerk „Sammelprüfung“); erfüllte Wünsche wie im Einzelweg (`erfuelleDurchFreigabe`). Meldung danach
  z. B. „5 Einträge freigegeben – 1 Wunsch erfüllt. 1 braucht eine Einzelprüfung.“
- Regel: `core/.../curation/Sammelpruefung.kt` (`grund`, `freigabe`, `Sammelwahl`); Ausführen:
  `CurationRepository.sammelFreigeben` / `sammelAblehnen` / `sammelGrund`; Oberfläche:
  `parent/ReviewScreens.kt` (Auswahl), `parent/SammelpruefungScreens.kt` (Leiste, Maske, Rückfrage).
- Prüfung: `./gradlew testDebugUnitTest assembleDebug` grün (350 Tests, davon neu 17 in `SammelpruefungTest`,
  5 in `CurationRepositoryTest`, 4 in `ParentViewModelTest`). Am Emulator (480×640, 240 dpi) als eigene Variante
  `xyz.steier.sidetube.sammel` angesehen und danach deinstalliert: Startpaket „general“ geladen, Auswahl, Alle
  auswählen, Maske, Rückfrage, Freigabe von zwei Einträgen. Nicht am Gerät gesehen: die Stufenwahl (das Startpaket
  enthält keine Kanal-Kandidaten).

**iOS:** offen (`ReviewQueueView`).

## Re-Review
Fälligkeit 12 Monate (6 bei Nachrichten und Einzelprüfungs-Quellen) → Status `expiredReview` erscheint unter „Prüfen";
kein automatisches Ablehnen.

## Datenmodell (Auszug)
`WhitelistItem`: providerRaw, approvalStatusRaw, categoryRaw, subcategoriesRaw, ageMin, ageMax, language,
sensitiveTopicsRaw, containsAdvertising/ProductPlacement/Violence/Fear/SexualContent/CoarseLanguage, isShort, isLive,
isNews, newsStatusRaw, autoplayAllowed, madeForKidsRaw, educationalValue, durationSeconds, videoDescription,
parentNotes, editorialNotes, approvedBy, approvedAt, lastReviewedAt, sourceChannelId, sourceChannelHandle, sourceUrl.
`KidProfile`: ageBandRaw, allowNews, allowManga, allowMangaEntertainment, allowShorts, autoplayNext, disabledCategoriesRaw.
`CuratedSource`: channelId, handle, title, providerRaw, trustRaw, isNewsSource, defaultAgeMin, defaultCategoryRaw, notes, lastReviewedAt.
`ReviewEvent`: itemYoutubeId, profileId, decisionRaw, actor, at, itemVersion, note.
Migration: Einträge aus der Zeit vor der Kuratierung behalten den Status `approved` (von Eltern bewusst angelegt);
alle neuen Pfade setzen den Status explizit.
