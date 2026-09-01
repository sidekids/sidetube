# Startpakete

Stand: 01.09.2026

Kuratierte Videolisten, die Eltern mit einem Griff in ein Kinderprofil übernehmen können.
Sie stammen aus demselben Bestand wie die iOS-Fassung, damit beide Apps dieselben geprüften
Inhalte anbieten und Kuratierungsarbeit nur einmal anfällt.

## Woher die Daten kommen

Verbindlich ist der Ordner `content/` im iOS-Repo (`sidetube-ios`): Taxonomie, Quellenregister,
Risikobegriffe und die Startpakete selbst. Von dort werden sie kopiert:

```bash
../sidetube-ios/scripts/sync-content.sh android .
```

Ziel ist `app/src/main/assets/content/`. Der Ordner ist **nicht versioniert** (`.gitignore`) —
er ist eine Kopie, keine zweite Quelle. Vor einem Release also einmal synchronisieren, sonst
enthält das APK einen alten Stand oder gar keine Pakete (die App zeigt dann „In dieser Fassung
sind keine Startpakete enthalten").

## Aufbau einer Paketdatei

`assets/content/libraries/*.json`, je Datei ein Paket:

| Feld | Bedeutung |
|---|---|
| `id`, `title`, `note` | Kennung und Anzeigetext; fehlt `id`, dient der Dateiname als Kennung |
| `profilePreset` | Vorschlag für das Profil, davon wird bisher nur die Ruhezeit ausgewertet |
| `videos[]` | `id`, `title`, optional `channelId`, `channelTitle`, `category`, `ageMin`, `note` |

Unbekannte Felder werden überlesen (`ignoreUnknownKeys`), das iOS-Modell darf also weiter
wachsen, ohne die Android-Fassung zu brechen.

## Was der Import tut

`StarterPackService` (in `core/export`, Paket `content`):

- legt jedes Video als `WhitelistItem` vom Typ `VIDEO` im **gewählten** Profil an,
- überspringt Videos, die dort schon liegen, und meldet die Zahl zurück,
- übernimmt auf Wunsch die Ruhezeit aus `profilePreset`.

Bewusst **nicht** übernommen werden ganze Kanäle: Die Kuratierung gibt einzelne Videos frei,
ein Kanalabo würde diese Prüfung aushebeln.

Der Import geht absichtlich **nicht** über `ExportImportService`. Dessen `importFromJson` legt
auch im Modus `MERGE` immer ein neues Profil an; ein Startpaket hätte damit ein zweites Profil
erzeugt statt das vorhandene zu ergänzen.

## Bedienung

Elternbereich → Whitelist eines Profils → Symbol „Startpakete" in der Titelleiste. Der Dialog
zeigt die enthaltenen Pakete mit Videoanzahl und ein Häkchen für die Ruhezeit; danach meldet
eine Einblendung, wie viele Videos übernommen wurden und wie viele schon vorhanden waren.

## Offen

- Quellenregister (`sources.json`) und Risikobegriffe (`risk-terms.json`) liegen zwar im APK,
  werden aber noch nicht ausgewertet — auf iOS warnen sie beim Freigeben.
- Der Freigabe-Workflow der iOS-Fassung (jedes neue Video zuerst `REVIEW_REQUIRED`) fehlt hier;
  die Startpakete kommen also direkt freigegeben ins Profil.
