# Konzept: SideTube für iOS und Android in einem Repository

Ziel: beide Apps unter `github.com/sidekids` gemeinsam pflegen, ohne die jeweiligen Toolchains zu vermischen.
Stand: August 2026. Die Entscheidung über den Umzug ist offen.

## 1. Ausgangslage

| | iOS | Android |
|---|---|---|
| Repository (heute) | Gitea `SideKidsDev/sidetube-ios` | Gitea `SideKidsDev/sidetube` |
| Stack | Swift 6 / SwiftUI / SwiftData, XcodeGen | Kotlin / Jetpack Compose, Gradle Multi-Modul |
| Umfang | 161 Dateien, 45 Commits, 12 MB (davon 5,7 MB Screenshots) | 255 Dateien, 31 Commits, 3,6 MB |
| Herkunft | Neuentwicklung | Fork von `degipe/YouTubeWhitelist` (GPL-3.0) |
| Lizenz | GPL-3.0 | GPL-3.0 (geerbt) |
| CI | keine | F-Droid-/Fastlane-Metadaten vorhanden |

Beide Apps lösen dieselbe Aufgabe, teilen aber **keinen Code**: Sprache, UI-Framework, Persistenz und Player sind
plattformspezifisch. Gemeinsam sind **Inhalte, Regeln, Marke und Dokumentation** – und genau die driften heute auseinander.

## 2. Was sich wirklich teilen lässt

| Gegenstand | Heute | Im Monorepo |
|---|---|---|
| Startpakete (`seed-library*.json`) | nur iOS | `content/libraries/*.json`, beide Apps lesen dasselbe Format |
| Quellenregister mit Sicherheitsstufen | iOS in Swift hartkodiert (`SourceRegistry`) | `content/sources.json`, je Plattform generiert oder geladen |
| Risikobegriffe (`RiskScreen`) | iOS in Swift | `content/risk-terms.json` |
| Kategorien, Altersstufen, Freigabestatus | iOS in Swift | `content/schema/*.json` als verbindliche Definition |
| Marke (S-Signet, Farben, Icons) | iOS `branding/` + Generator | `branding/` mit demselben Generator, Export für beide Plattformen |
| Inhaltsrichtlinien, Kuratierung, Manga-Regeln, Recherche | iOS `docs/` | `docs/` einmal für beide |
| Store-Metadaten, Datenschutz | iOS `docs/appstore` / Android `fastlane` | `docs/store/{ios,android}` mit gemeinsamem Textkern |

**Kein** geteilter Programmcode. Kotlin Multiplatform bleibt bewusst außen vor (Begründung in PROJEKT.md):
Der fachliche Kern ist klein, die Toolchain-Kosten wären hoch.

## 3. Vorgeschlagene Struktur

```
sidekids/sidetube            (ein Repository, github.com/sidekids)
├── README.md                Überblick, Statusmatrix beider Apps
├── LICENSE                  GPL-3.0
├── ios/                     bisheriges sidetube-ios (Sources, Tests, project.yml, Config)
├── android/                 bisheriges sidetube (app, core, feature, gradle, fastlane)
├── content/                 gemeinsame Kuratierungsdaten (JSON, plattformneutral)
│   ├── schema/              Kategorien, Altersstufen, Trust-Stufen, Freigabestatus
│   ├── sources.json         Quellenregister inkl. PeerTube-Instanzen
│   ├── risk-terms.json      Begriffe für die automatische Vorprüfung
│   └── libraries/           Startpakete (general, alter-9-11, …)
├── branding/                logo-spec.json, Generator, SVG/PNG-Exporte
├── docs/                    Inhaltsrichtlinien, Kuratierung, Recherche, Redesign, Store
├── scripts/                 Generatoren (Brand-Assets, Content-Sync)
└── .github/workflows/       getrennte CI je Plattform
```

Einbindung der geteilten Daten ohne Symlinks (die Xcode und Gradle beide nicht mögen):
- **iOS:** Build-Phase `scripts/sync-content.sh ios` kopiert `content/` nach `ios/Sources/Resources/content/`
  (gitignored); alternativ ein XcodeGen-`fileGroup` mit relativem Pfad `../content`.
- **Android:** Gradle-Task `syncContent` kopiert nach `android/app/src/main/assets/content/` (gitignored).
- Verbindlich bleibt `content/`; die Kopien sind Build-Artefakte. Ein Test je Plattform prüft, dass das Schema passt.

## 4. Migration ohne Historienverlust

```bash
# neues Repo anlegen, beide Historien als Unterverzeichnisse übernehmen
git init sidetube && cd sidetube
git remote add ios   <URL des iOS-Repos>
git remote add droid <URL des Android-Repos>
git fetch ios && git fetch droid
git subtree add --prefix=ios   ios/main
git subtree add --prefix=android droid/main
```
`git subtree` behält beide Historien und erlaubt später `git subtree push/pull` – wichtig für den Android-Fork,
der weiterhin Änderungen von `degipe/YouTubeWhitelist` übernehmen können soll:

```bash
git remote add upstream https://github.com/degipe/YouTubeWhitelist.git
git subtree pull --prefix=android upstream main --squash   # Upstream-Stand nachziehen
```

Vor der Migration offene Feature-Branches zusammenführen; danach arbeiten alle Branches im Monorepo.

## 5. Gitea und GitHub parallel

Gitea bleibt die Arbeitsumgebung, GitHub die öffentliche Seite (Website, Issues, Releases):
- `origin` = Gitea, `github` = `git@github.com:sidekids/sidetube.git`
- Push-Spiegelung: entweder in Gitea unter *Einstellungen → Spiegel* (Push-Mirror, automatisch),
  oder lokal `git remote set-url --add --push origin` auf beide Ziele.
- Die Website `sidekids.github.io` verlinkt künftig auf `github.com/sidekids/sidetube` statt auf Gitea.

## 6. CI mit Pfadfiltern

`.github/workflows/ios.yml` (macOS-Runner, nur bei Änderungen unter `ios/**`, `content/**`, `brand/**`):
`xcodegen generate` → `xcodebuild test` (Unit-Tests; UI-Tests optional nächtlich, weil sie Netz brauchen).
`.github/workflows/android.yml` (Ubuntu, nur bei `android/**`, `content/**`): `./gradlew test`.
`.github/workflows/content.yml` (Ubuntu, bei `content/**`): JSON-Schema prüfen, IDs auf Dubletten prüfen,
optional wöchentlich per oEmbed/RSS/PeerTube-API testen, ob freigegebene Videos noch existieren – Ergebnis als Issue.
Für öffentliche Repositories sind macOS-Runner kostenlos; bei privatem Repo kostet macOS ein Vielfaches der Linux-Minuten.

## 7. Versionen und Releases

Getrennte Tags mit Präfix: `ios/v0.1.0`, `android/v1.1.0`; Release-Notes je Plattform, gemeinsamer `CHANGELOG.md`
mit zwei Spalten. Die Versionsnummern laufen bewusst unabhängig – die Apps erscheinen in verschiedenen Stores.

## 8. Was dagegen spricht

- **Fork-Bezug:** Der Android-Teil verliert die direkte GitHub-Fork-Beziehung (Pull Requests an Upstream werden
  umständlicher). `git subtree` mildert das, ersetzt es aber nicht.
- **F-Droid:** Das Android-Rezept zeigt heute auf das Repo-Root (`build.gradle`, Fastlane-Metadaten). Nach dem Umzug
  müssen `subdir`/`gradle`-Pfade im F-Droid-Rezept angepasst werden – vor der Migration klären, sonst bricht der Build.
- **Werkzeuge:** Xcode und Android Studio öffnen jeweils nur ihr Unterverzeichnis; das ist Gewöhnung, aber kein Problem.
- **Repo-Größe:** Screenshots (5,7 MB) besser nach `docs/screenshots` mit Kompression oder in die Website auslagern.

## 9. Umsetzung

**Stufe 1 ist umgesetzt.** Die gemeinsamen Daten liegen plattformneutral im iOS-Repository:

```
content/
├── schema/taxonomy.json    Altersstufen, Kategorien (mit Mindestalter), Sicherheitsstufen,
│                           Freigabestatus, Nachrichtenstatus, sensible Themen, Anbieter
├── sources.json            20 Quellen mit Sicherheitsstufe, Standardalter, Kategorie und Begründung
├── risk-terms.json         Begriffe der automatischen Vorprüfung (hart / thematisch)
└── libraries/              Startpakete: general.json, alter-9-11.json
```

- **iOS** bindet `content/` als Ordnerreferenz in die App ein; `ContentBundle` lädt daraus. `SourceRegistry`,
  `RiskScreen` und `SeedLibraryImporter` enthalten keine hartkodierten Listen mehr.
- **Android** übernimmt die Dateien mit `scripts/sync-content.sh android [Repo]` nach
  `app/src/main/assets/content/` (dort in die `.gitignore` aufnehmen und beim Build erzeugen).
- Vier Konsistenztests laufen bei jedem Build: Taxonomie ↔ Swift-Typen, Register vollständig und ohne Dubletten,
  Risikobegriffe greifen, jedes Video einer Bibliothek zeigt auf eine bekannte, nicht gesperrte Quelle und liegt
  nicht unter dem Mindestalter seiner Kategorie.

Die Zusammenführung machte drei Datenfehler sichtbar, die vorher über den Code verstreut waren: „Ebbe und **Flut**"
wurde als Katastrophe markiert, „das **war**" als Krieg, „**Feuer**wehrmann" als Brand; und ein Verlagsvideo stand mit
Alter 10 in der Kategorie Anime & Manga (ab 12). Alle drei sind korrigiert.

**Stufe 2, sobald beide Apps in TestFlight bzw. F-Droid laufen:** Monorepo `sidekids/sidetube` wie oben anlegen,
F-Droid-Rezept anpassen, Gitea als Push-Mirror behalten. Bis dahin bleibt der Umzug eine Verwaltungsentscheidung
und blockiert die laufende Entwicklung nicht.
