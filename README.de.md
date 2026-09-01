# SideTube

🇬🇧 English version: [README.md](README.md)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Plattformen](https://img.shields.io/badge/Plattformen-iOS%2017%2B%20%7C%20Android%208%2B-blue.svg)](#aufbau-des-repositorys)

SideTube ist eine Videoapp für Kinder, die nur zeigt, was Eltern vorher freigegeben haben.
Kein Konto, keine Empfehlungsspirale, keine offene Suche. Es gibt sie für iOS und Android;
beide Apps teilen dieselben Kuratierungsdaten und dieselben Regeln.

> **Reichweite.** SideTube begrenzt, was ein Kind innerhalb der App erreicht. Sie ersetzt
> keine Begleitung und erhebt keinen Anspruch, das offene Netz zu filtern. Die Wiedergabe
> nutzt den eingebetteten YouTube-Player; dessen Grenzen stehen offen unter
> [Bekannte Grenzen](#bekannte-grenzen).

## Überblick

Eltern nehmen Kanäle, Videos und Playlists einzeln auf. Nichts, was ein Kind sieht, gelangt
von selbst dorthin: Jeder neue Eintrag landet als *Prüfung nötig* in einer Redaktionsliste,
erst eine ausdrückliche Freigabe macht ihn sichtbar. Quellen tragen eine Sicherheitsstufe,
die entscheidet, ob ein ganzer Kanal durchstöbert werden darf oder nur einzeln freigegebene
Videos erscheinen. Eine automatische Risikoerkennung darf markieren und ablehnen, aber
niemals freigeben — diese Entscheidung bleibt bei den Eltern.

Die beiden Apps teilen **keinen Programmcode**; Sprache, UI-Framework, Persistenz und Player
sind plattformspezifisch. Gemeinsam sind **Inhalte, Regeln, Marke und Dokumentation** — sie
liegen unter `content/` als einzige verbindliche Quelle.

## Funktionen

- Freigabe statt Filter: Kanäle, Videos und Playlists werden je Kinderprofil aufgenommen;
  neue Einträge landen in der Prüfliste und erscheinen nie ungeprüft.
- Sicherheitsstufen je Quelle: Nur eine *vertrauenswürdige Kinderquelle* darf als ganzer
  Kanal durchstöbert werden; jede andere Stufe zeigt ausschließlich freigegebene Videos.
- Risikoerkennung, die nichts freigibt: Begriffserkennung für belastende Themen, Treffer nur
  am Wortanfang, damit „Attentäter“ nicht als „Täter“ gilt.
- Altersstufen und Inhaltskategorien, je Eintrag mit Mindest- und Höchstalter.
- Zeitgrenzen: Tagesbudget, Schlaf-Timer mit Ausblenden der Lautstärke und Ruhezeiten mit
  Vorwarnung und Wochenend-Zuschlag — Ausnahmen nur über die Eltern-PIN.
- Startpakete: kuratierte Bibliotheken unter `content/libraries/`, die Eltern in ein Profil
  laden; sie kommen zur Prüfung an, nicht freigegeben.
- Bedienung mit Rad: Der Kindermodus lässt sich vollständig über ein virtuelles Scrollrad
  steuern, passend zur Hardware des Sidephone.
- Zwei Anbieter: YouTube sowie PeerTube, beschränkt auf eingetragene Instanzen.
- Vollständig auf dem Gerät: kein Server, keine Auswertung, kein Tracking.

## Aufbau des Repositorys

```
├── ios/          SwiftUI-App (Swift 6, SwiftData, XcodeGen), ab iOS 17
├── android/      Jetpack-Compose-App (Kotlin, Room, Hilt), ab Android 8 — folgt
├── content/      gemeinsame Kuratierungsdaten, verbindlich für beide Apps
│   ├── schema/       Kategorien, Altersstufen, Sicherheitsstufen, Freigabestatus
│   ├── sources.json  Quellenregister einschließlich PeerTube-Instanzen
│   ├── risk-terms.json
│   └── libraries/    Startpakete
├── branding/     Logo-Spezifikation und Generator
├── docs/         Inhaltsrichtlinien, Oberflächenkonzept, Store-Vorbereitung
└── scripts/      Marken-Erzeugung, Inhalts-Abgleich, Bauhelfer
```

Verbindlich ist `content/`; jede Plattform erhält eine Kopie als Build-Artefakt
(`scripts/sync-content.sh ios|android`). Die Kopien sind nicht versioniert.

## Voraussetzungen

**iOS** — Xcode 26, Zielversion iOS 17, [XcodeGen](https://github.com/yonaskolb/XcodeGen).
**Android** — JDK 17, Android-SDK mit Plattform 35, Gradle-Wrapper enthalten.

Ein YouTube-Data-API-Schlüssel ist auf beiden Plattformen **optional**: Kanäle und Videos
werden über oEmbed und die Kanalseite aufgelöst. Ein Schlüssel erhöht das Kontingent bei
suchlastiger Nutzung.

## Einrichtung

```bash
# iOS
cd ios && xcodegen generate && open sidetube.xcodeproj

# Android
cd android && ./gradlew assembleDebug
```

Für einen API-Schlüssel unter iOS `ios/Config/Secrets.xcconfig` aus der mitgelieferten
Vorlage anlegen, unter Android `YOUTUBE_API_KEY` in `local.properties` eintragen. Beide
Dateien sind nicht versioniert.

## Bedienung

Beim ersten Start fragt die App nach einer Eltern-PIN. Dahinter:

| Schritt | Wo |
|---|---|
| Kinderprofil anlegen | Elternbereich → *Neues Profil* |
| Kanal oder Video aufnehmen | Profil → *Hinzufügen* → YouTube-Link einfügen |
| Kuratiertes Startpaket laden | Profil → ⋯ → *Startpaket laden* |
| Offene Einträge freigeben | Profil → *Freigaben prüfen* |
| Sicherheitsstufe einer Quelle | Elternbereich → ⋯ → *Quellen & Sicherheitsstufen* |
| Zeitgrenzen und Ruhezeiten | Profil → ⋯ → *Profil bearbeiten* / *Schlafmodus* |

Sperren führt zurück in den Kindermodus, in dem nur Freigegebenes erreichbar ist.

## Prüfen

```bash
cd ios && xcodebuild test -scheme sidetube -destination 'platform=iOS Simulator,name=iPhone 17'
cd android && ./gradlew testDebugUnitTest
```

Die iOS-Suite prüft Fachregeln, Kuratierung, Risikoerkennung sowie die Abläufe im Eltern- und
Kindermodus; die Android-Suite prüft dieselben Fachregeln sowie Repositories und Viewmodels.

## Bekannte Grenzen

- **Verwandte Videos lassen sich im eingebetteten YouTube-Player nicht vollständig
  abschalten.** Seit 2018 beschränkt `rel=0` sie nur noch auf denselben Kanal. SideTube
  begegnet dem mit abgeschaltetem Autoplay, sofortigem Stopp am Videoende und dem Stöbern
  ausschließlich in kindgerechten Quellen. Für eine wirklich geschlossene Wiedergabe eignet
  sich PeerTube besser.
- **PeerTube ist föderiert**, jede Instanz moderiert selbst; nur eingetragene Instanzen
  dürfen Inhalte liefern.
- **iOS bietet Fremd-Apps keinen Kiosk-Modus**; die App weist stattdessen auf den Geführten
  Zugriff hin.
- **Die Risikoerkennung ist ein Hinweis, kein Urteil.** Ein unauffälliger Titel kann trotzdem
  ungeeignet sein.

## Datenschutz

Alle Daten bleiben auf dem Gerät: Profile, Whitelists, Sehverlauf und Einstellungen liegen in
der lokalen Datenbank. Es gibt keinen Server, kein Konto, keine Auswertung und kein Tracking.
Netzverbindungen gehen ausschließlich zu den Videoanbietern, um freigegebene Inhalte
aufzulösen und abzuspielen. Die Eltern-PIN wird als PBKDF2-HMAC-SHA256-Ableitung im
Systemschlüsselbund abgelegt, nie im Klartext.

## Projektkontext

SideTube gehört zu **SideKids** ([sidekids.github.io](https://sidekids.github.io)), einer
kleinen Familie kindgerechter Anwendungen mit gemeinsamen Grundsätzen: keine
Bindungsmechanismen, keine Datensammlung über Kinder, Offline-Fähigkeit wo möglich, und
Entscheidungen, die bei den Eltern bleiben statt bei einem Algorithmus.

Die Kuratierung folgt etablierten medienpädagogischen Empfehlungen für die jeweilige
Altersstufe (unter anderem den Einschätzungen von [FLIMMO](https://www.flimmo.de)).
Altersstufen, Inhaltskategorien und Sicherheitsstufen sind in `docs/` beschrieben und in
`content/schema/` maschinenlesbar definiert, damit beide Apps und spätere Auswertungen von
denselben Festlegungen ausgehen.

Idee, Kuratierung und pädagogisches Konzept: Christian-Maximilian Steier.

## Lizenz

Veröffentlicht unter der [GNU General Public License v3.0 oder später](LICENSE). Als
Copyleft-Lizenz verlangt die GPL, dass Weitergaben und veränderte Fassungen ebenfalls unter
der GPL stehen. Die Android-App entstand als Fork von
[degipe/YouTubeWhitelist](https://github.com/degipe/YouTubeWhitelist) und erbt diese Lizenz;
die iOS-App ist eine eigenständige Neuschreibung unter denselben Bedingungen.
