# Sichere Videoempfehlungen

> Historisches Dokument: Aussagen und Implementierungspfade können den früheren Stand beschreiben. Maßgeblich sind der [aktuelle Release-Audit](../../docs/release/pre-release-audit.md) und seine offenen Gates; dieses Dokument ist keine Release-Freigabe.

SideTube zeigt unter dem Player nur SideTube-eigene Empfehlungen. Eine Empfehlung
ist ein Kandidat, keine Freigabe: `SafeRecommendationService` prüft jeden Eintrag
über die bestehende `ContentPolicy` für das aktive Kinderprofil.

## Sicherheitsmodell

Explizit freigegebene Videos müssen weiterhin `approved` sein und bestehen zusätzlich
Alters-, Kategorie-, Quellen-, Block-, Review-, Live-, Short- und Risikoregeln. Videos
aus einem dynamisch browsbaren Kanal dürfen nur bei `trustedChildSource` erscheinen;
das ist die bestehende Source-Trust-Regel und erzeugt keine gespeicherte Freigabe.
`reviewRequired`, `discovered`, `rejected` und `expiredReview` bleiben unsichtbar.

Ein Tap geht ausschließlich an `PlayerCoordinator`; vor `engine.load(videoId:)` wird
noch einmal geprüft. Es gibt keinen externen Link und keinen Wechsel in Safari oder die
YouTube-App. Watch History, Tageslimit, Ruhezeiten und Sleep Timer bleiben im selben
`PlayerModel` und werden beim Wechsel nicht zurückgesetzt.

## Candidate Sources und Sortierung

Die aktuelle Queue liefert den stärksten Kontext („Als Nächstes“), danach kommen
freigegebene Videos desselben Kanals, derselben Kategorie und weitere freigegebene
Inhalte. Gecachte Videos vertrauenswürdiger Kinderquellen können ebenfalls erscheinen.
Dubletten und das aktuelle Video werden entfernt. Die Sortierung ist deterministisch und
verwendet weder Views, Likes, Popularität, Watch Time noch Click-Through-Rate.

Provider-Related-Daten werden derzeit nicht direkt verwendet. Falls sie später als
`related`-Kandidaten hinzukommen, müssen sie Metadaten erhalten und dieselbe Policy
passieren; Screening allein kann niemals freigeben.

Der eingebettete Player startet nicht automatisch und blendet seine eigenen Provider-
Steuerelemente aus. Play/Pause/Weiter laufen über SideTube. Dadurch gibt es im
Kinder-UI keine YouTube-Related-Buttons; `rel=0` bleibt zusätzlich gesetzt.

## UI und Plattformen

Die SwiftUI-Ansicht nutzt die bestehende Player-Fläche als tappbare „Als Nächstes“-Rows
mit Thumbnail, Titel und optionalem Kanalnamen. Der bestehende Player wird wiederverwendet,
statt verschachtelte Player-Screens zu erzeugen.

Dieses Checkout enthält nur `Sources/` für iOS. Ein Android-Modul ist nicht vorhanden;
die Android-Fassung muss bei Verfügbarkeit denselben Domain-Vertrag nativ in Kotlin,
Room, Hilt und Compose umsetzen, ohne eine zweite Policy- oder Approval-Logik.

## Bekannte Grenzen

Der eingebettete YouTube-Player kann Related-Videos innerhalb seines eigenen Controls
nicht vollständig unterdrücken. Diese Oberfläche ist deshalb keine SideTube-Navigation;
Autoplay bleibt aus bzw. folgt den bestehenden Profileinstellungen. Für eine vollständig
geschlossene Wiedergabe bleibt ein kontrollierter Anbieter wie PeerTube geeigneter.
