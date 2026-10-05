# ADR 0002 – Playlist-Videos und Einzelprüfung

Status: angenommen (Christian, 02.10.2026)

## Kontext

Eltern können eine YouTube-Playlist freigeben. Bisher trug diese Freigabe alle Videos der Playlist
(`ContentPolicy.canPlayFromPlaylist` auf iOS und Android): Eine eigene Elternentscheidung zum Video ging
vor, Risikotreffer, Shorts und Livestreams blieben draußen, eine gesperrte oder „nur für Eltern“
eingestufte Quelle sperrte. Die Stufe „Nur einzeln geprüfte Videos“ (`perVideoReview`) der Quelle eines
Videos spielte dagegen keine Rolle.

Eine Playlist darf Videos fremder Kanäle enthalten, und ihr Besitzer kann sie jederzeit ändern. Ein Kanal
mit „Nur einzeln geprüfte Videos“ ist genau einer, bei dem die Eltern gesagt haben: von hier nur, was wir
uns einzeln angesehen haben. Über eine Playlist ließe sich diese Entscheidung umgehen – auch
unbeabsichtigt, etwa mit einer Playlist eines anderen Kanals, die Videos dieses Kanals einsammelt.

Die Code-Durchsicht vor dem Merge von `video/szenen` (02.10.2026) fand außerdem, dass die Quelle eines
Videos aus dem Zwischenspeicher nur über den Kanalnamen gesucht wurde; ohne Treffer wurde zugelassen.

## Entscheidung

1. **Kanal mit „Nur einzeln geprüfte Videos“:** Ein Video aus so einem Kanal braucht auch in einer
   freigegebenen Playlist eine **eigene Freigabe**. Ohne sie erscheint es in der Playlist nicht und ist
   über den Verlauf nicht erreichbar.
2. **Unbekannte Kanäle** (keine Quelle angelegt, also nie eingestuft) trägt die Playlist-Freigabe
   weiterhin. Ebenso Kanäle mit „Vertrauenswürdige Kinderquelle“ und „Vertrauenswürdige Reihe“.
3. **Kanal nicht feststellbar** (kein `channelId`, etwa ein Zwischenspeicher-Eintrag von vor dieser
   Änderung): wie 1. – nur mit eigener Freigabe. Ohne Kanal lassen sich weder Sperre noch Stufe prüfen.
4. Die Quelle eines Videos wird **nur über die Kanal-ID** bestimmt, nie über den Namen. Der
   Zwischenspeicher führt dafür die Kanal-ID jedes Videos (Android Spalte `videoChannelId`, iOS Attribut
   `videoChannelId`).
5. Unverändert: Eigene Elternentscheidung geht vor; gesperrte und „nur für Eltern“-Quellen sperren;
   Risikotreffer, Shorts (Titel **oder** Feed-Verweis `/shorts/`) und Livestreams bzw. angekündigte
   Premieren (Titel **oder** Feed `views="0"`) bleiben draußen; Alter, Kategorie und Nachrichtenregeln der
   Playlist gelten für jedes Video. Aus einer Playlist entsteht nie eine gespeicherte Freigabe.

Gleiche Regel auf iOS (`ContentPolicy.canPlayFromPlaylist` in `ios/Sources/Domain/ContentPolicy.swift`)
und Android (`ContentPolicy.canPlayFromPlaylist` in `android/core/.../curation/ContentPolicy.kt`).

## Begründung

- Die Stufe einer Quelle ist eine Elternentscheidung über den ganzen Kanal. Sie muss gelten, egal auf
  welchem Weg ein Video des Kanals hereinkommt; sonst ist „Nur einzeln geprüfte Videos“ ein Versprechen,
  das die App nicht hält.
- Unbekannte Kanäle zu sperren, würde die meisten Playlists leeren: Typische Playlists (Sendungsreihen,
  Lieder, Erklärvideos) bestehen aus Kanälen, die die Eltern nie eingestuft haben. Die Freigabe der
  Playlist ist dann die Prüfung – die Eltern haben die Liste als Ganzes angesehen.
- „Kanal nicht feststellbar“ ist selten (nur alte Zwischenspeicher) und behebt sich mit dem nächsten
  Laden der Playlist. Hier lieber ein Video zu wenig als eines, dessen Quelle niemand prüfen kann.
- Der Kanalname ist kein Schlüssel: Er ist nicht eindeutig, ändert sich, und der Name im Feed weicht oft
  vom Namen der angelegten Quelle ab („Blender“ vs. „Blender Foundation“).
- **Preis:** Die Regel ist asymmetrisch – ein nie eingestufter Kanal ist in einer Playlist freier als
  einer mit „Nur einzeln geprüfte Videos“. Wer einem Kanal misstraut, muss ihn einstufen; wer ihn gar
  nicht will, sperrt ihn. Eltern, die eine Playlist mit Videos eines einzeln geprüften Kanals freigeben,
  sehen weniger Videos als in der Playlist stehen und müssen die fehlenden einzeln freigeben. Die
  Elternansicht erklärt das noch nicht.

## Folgen

- Videos aus einzeln geprüften Kanälen verschwinden nach dem Update aus freigegebenen Playlists, bis sie
  einzeln freigegeben sind (Hinweis für die Release-Notes, `docs/release/ios-compatibility.md`).
- Zwischenspeicher: Android ergänzt die noch unveröffentlichte Migration 2→3 um `videoChannelId` und
  `isUpcoming` (Schema `3.json` neu exportiert, `MigrationTest`); iOS ergänzt `CachedChannelVideo` um
  `videoChannelId`, `isShort`, `isUpcoming` (leichtgewichtige Migration, Test vom alten Store). Die iOS-
  Data-API-Seiten tragen jetzt `videoOwnerChannelId`.
- Tests: `PlaylistPolicyTest` (Android), `PlaylistPolicyTests`/`PlaylistModelTests` (iOS) für Stufe,
  fehlende Kanal-ID, Feed-Shorts/-Premieren und Quellentitel ≠ Autorname.
- Offen: In der Playlist-Ansicht der Eltern anzeigen, welche Videos wegen der Einzelprüfung fehlen, mit
  direktem Weg zur Einzelfreigabe.
