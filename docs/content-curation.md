# Kuratierung: Elternbereich und Redaktionsansicht

## Bedienung
- **Profil** (Elternbereich → Profil bearbeiten): Altersprofil, Nachrichten an/aus, Manga zeichnen an/aus,
  Anime & Manga (nur 12+) an/aus, Shorts erlauben, „Nächstes Video automatisch" (Standard aus), Tageslimit.
- **Link hinzufügen**: YouTube **oder PeerTube** (`…/w/<id>`, `…/c/<kanal>`); Vorschau → Filterhinweis → Alter/Kategorie →
  **Jetzt freigeben** oder **Erst zur Prüfung merken**. PeerTube-Links aus nicht eingetragenen Instanzen werden abgewiesen.
  Kanäle werden als Quelle mit Stufe „nur einzeln geprüfte Videos" angelegt, falls unbekannt; gesperrte Quellen werden abgewiesen.
- **Prüfen** (Whitelist → Häkchen-Siegel): Kandidaten mit Bild, Titel, Quelle, Länge, Altersempfehlung, Risiken,
  Made-for-Kids, Kategorie; Entscheidung **Freigeben / Ablehnen / Später**, Felder Alter, Höchstalter, Kategorie,
  Nachrichtenstatus, Anmerkung; Verlauf (Audit-Trail) je Video.
- **Quellen & Sicherheitsstufen** (Dashboard-Menü): Stufe je Quelle ändern, Sperren, **PeerTube-Instanz hinzufügen**
  (Adresse eingeben → Stufe „nur einzeln geprüfte Videos"; eigene Familien-Instanz danach hochstufbar).
- **Startbibliothek laden** (Dashboard-Menü): importiert `seed-library.json` als Prüfkandidaten ins erste Profil (nie freigegeben).
- Eingehende Empfehlungen (`sidetube://add`) landen nach Eltern-PIN unter „Prüfen".

## Seed-Bibliothek (38 Kandidaten, alle REVIEW_REQUIRED)
Verteilung: Wissen 6 · Natur 4 · Technik 4 · Umwelt 4 · Kreativ 2 · Geschichten 2 · Humor 2 · Gesellschaft 2 ·
Medienkompetenz 1 · Nachrichten 4 (1 davon `sensitive` als Beispiel) · Manga zeichnen 4 · Zeichnen 4 ·
Anime & Manga 2 (TOKYOPOP-Kinderbuch 10+, Tanoshii 12+ als ZDF-Quelle ohne Wiedergabe).
Alle YouTube-IDs am 2026-08-31 über RSS/oEmbed verifiziert; **vor der Freigabe jedes Video ansehen**.

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
