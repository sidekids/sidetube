# Umsetzungsplan: Eltern über neue Wünsche benachrichtigen (ADR 0005)

Stand 04.10.2026. Grundlage: [ADR 0005](../adr/0005-eltern-ueber-wuensche-benachrichtigen.md).
Gebaut wird zuerst **Weg 1 (Nextcloud-Talk-Bot)**; ntfy (Weg 2) folgt nur bei Bedarf und nutzt
dieselbe Schnittstelle.

## Was es am Ende tut

1. Eltern richten auf ihrer Nextcloud einmal Talk, Bot und Gespräch ein (Skript, Phase 0).
2. In SideTube hinter der PIN: Einstellungen → „Eltern benachrichtigen" → Einrichtungscode einlesen →
   „Test senden".
3. Legt ein Kind einen Wunsch an (nicht bei Dublette, nicht über der Tagesgrenze), schreibt SideTube im
   Hintergrund `@eltern SideTube: neuer Wunsch (N offen)` ins Gespräch. Die Nextcloud-App der Eltern
   meldet es.

## Einrichtungscode

JSON, als QR-Code und als Text (zum Einfügen):

```json
{"v":1,"art":"talk","server":"https://wolke.example.org","gespraech":"abcd2345",
 "schluessel":"<64 Hex-Zeichen>","erwaehnen":["anna"]}
```

Geprüft wird beim Einlesen: `v` bekannt, `server` mit `https://`, Gesprächs-Token und Schlüssel im
erwarteten Format (Schlüssel 40–128 Zeichen), Nutzernamen ohne Leer- und Steuerzeichen. Der Code wird
nie geloggt; die Oberfläche zeigt danach nur Server und erwähnte Namen, nie den Schlüssel.

## Bausteine

| Baustein | iOS | Android |
|---|---|---|
| Einrichtung (Modell + Prüfung des Codes) | `ParentChannel` in `Sources/Data/` | `Elternkanal` in `core/…/benachrichtigung/` |
| Signieren | HMAC-SHA256 mit CryptoKit (`HMAC<SHA256>`) | `javax.crypto.Mac("HmacSHA256")` |
| Senden | eigener schmaler `HTTPPoster` (`URLSessionPoster`: ephemeral, ohne Cache/Cookies, Weiterleitungen gesperrt) – der YouTube-`HTTPClient` und seine Testersatzteile bleiben unberührt | eigener `HttpPoster` (`UrlConnectionPoster`, `instanceFollowRedirects = false`) |
| Melder | `protocol ParentNotifier` + `TalkBotNotifier` + `NoParentNotifier` | `interface Elternmelder` + `TalkBotMelder` + `KeinMelder` |
| Ablage | Schlüsselbund, eigener Eintrag (Service `xyz.steier.sidetube`, Account `parent_channel`, `AfterFirstUnlockThisDeviceOnly`) – kleiner allgemeiner Helfer neben `KeychainPINStore` | `EncryptedSharedPreferences` (Datei `sidetube-parent`, wie `PinStore`) |
| Auslöser | `WishRepository.submit` nach `context.save()` bei `.created` (`ios/Sources/Data/WishRepository.swift:103–131`) | `WunschRepository.wuensche` nach `dao.insert` bei `Geschickt` (`android/core/…/repo/WunschRepository.kt:60–95`) |
| Oberfläche | Eintrag im `⋯`-Menü von `ParentDashboardView`, Seite nach Vorlage `ChangePINView` | `Screen.Elternkanal` in `MainActivity`, Menüpunkt in `ProfileListScreen`, Seite nach Vorlage `ChangePinScreen` |

Signatur (am 04.10.2026 gegen Talk 22.0.18 erprobt, Antwort 201):
`X-Nextcloud-Talk-Bot-Random` = 32 Byte Zufall als Hex; `X-Nextcloud-Talk-Bot-Signature` =
HMAC-SHA256(Schlüssel, Random + Nachrichtentext) als Hex; Körper `{"message": "<Text>"}`;
`OCS-APIRequest: true`; `POST {server}/ocs/v2.php/apps/spreed/api/v1/bot/{gespraech}/message`.

Versand: einmal, im Hintergrund, Zeitlimit 15 s, kein Wiederholen, kein Warten in der Oberfläche.
Fehlschläge zeigt vorerst nur „Test senden" in den Einstellungen; ein Eintrag im Verlauf der Freigaben
(ohne Adresse, ohne Schlüssel) ist für später vorgemerkt.

## Phasen

| Phase | Inhalt | Fertig, wenn |
|---|---|---|
| 0 Server-Skript | `scripts/talk-wunschkanal.sh`: prüft Talk, legt Bot (`--feature=response --no-setup`) und Gespräch an oder nutzt vorhandene, gibt Einrichtungscode als QR (`qrencode`) und Text aus; Schlüssel nur in die Ausgabe, nie in Dateien | Auf einer privaten Nextcloud läuft es durch (vorhandener Bot/Gesprächs-Token werden wiederverwendet) und der ausgegebene Code sendet eine Testnachricht – **erledigt 05.10.2026** (beide Wege erprobt, Testbot/-gespräch wieder entfernt) |
| 1 Kern beide | Modell + Codeprüfung, Signieren, Nachrichtentext, Melder mit POST; Tests mit festem Prüfvektor (gleicher Schlüssel/Random/Text → gleiche Signatur auf iOS und Android) | Unit-Tests grün auf beiden; Prüfvektor identisch – **erledigt 05.10.2026** (iOS 9, Android 8 Tests) |
| 2 Ablage | Speichern/Laden/Löschen der Einrichtung im sicheren Speicher | Tests mit In-Memory-Ersatz; nach App-Neustart ist die Einrichtung da – **erledigt 05.10.2026** (iOS Schlüsselbund `parent_channel`, Android eigene verschlüsselte Datei `sidetube-elternkanal`, damit ein PIN-Reset den Kanal nicht löscht) |
| 3 Auslöser | Melder in die Wunsch-Repositories; nur bei neuem Wunsch | Tests: neu → 1 Meldung, Dublette/Grenze/gesperrte Quelle → 0, ohne Einrichtung → 0, Fehler im Melder ändert das Ergebnis des Wunsches nicht – **erledigt 05.10.2026** (iOS über `WishFeedback.submit` → `WishRepository.notifyParents`, Android über `AppContainer.meldeNeuenWunsch` in eigenem Hintergrundbereich; iOS 298, Android 262 + 97 Tests grün) |
| 4 Oberfläche | Seite „Eltern benachrichtigen": Zustand, Code einlesen (QR/Text), Test senden, Entfernen | UI-Test iOS; am SidePhone mit Rad bedienbar; „Test senden" kommt auf dem Eltern-Telefon an – **gebaut 05.10.2026**: iOS-UI-Test `ElternkanalUITests` (Einfügen, Test senden gegen eine private Nextcloud, Entfernen) grün; Android-Echttest `ElternkanalEchtTest` grün, Seite und Kamerafrage im Emulator geprüft. **05.10.2026 am SidePhone SP-01 bestätigt** (Christian): QR-Scan vom Mac-Bildschirm, „Test senden“, Mitteilung in der Nextcloud-App auf dem iPhone. **Offen:** QR-Scan am echten iPhone; Bedienung nur mit dem Rad nicht gesondert geprüft |
| 5 Doku | `docs/privacy-policy.md` + Datenschutzseite der Website, `CROSS_PLATFORM_PARITY.md`, README (Einrichtung für Eltern), Befund ins Vorstellungsvideo-Repo | Doku nennt genau, was wohin geht; Website erst nach Freigabe – **erledigt 05.10.2026** im Repo: `docs/privacy-policy.md`, `docs/privacy/network-services.md`, `docs/privacy/data-storage.md`, `docs/release/feature-parity.md`, README (de/en). `CROSS_PLATFORM_PARITY.md` ist als historisch markiert und bleibt unverändert. **Offen:** Datenschutzseite der Website (sidekids.github.io/about/privacy.html#sidetube) – erst wenn die Funktion ausgeliefert wird und nach Freigabe |

## Offene Punkte

- **SidePhone ohne Kamera?** Ob das SP-01 eine Kamera hat, ist offen. Ohne Kamera liest Android den
  Code aus einer Datei im App-Ordner ein (wie die OPML-Datei in SidePlay) oder über die
  Zwischenablage; Abtippen von 64 Hex-Zeichen mit dem Rad ist keine Option.
- **iOS-Kamera:** Für den QR-Scan braucht iOS `NSCameraUsageDescription` (neu); Scan mit
  AVFoundation (`AVCaptureMetadataOutput`, läuft auf allen Geräten ab iOS 17) statt VisionKit.
  Einfügen als Text bleibt immer möglich.
- **Kids-Kategorie:** Die Kamera- bzw. Einrichtungsseite liegt hinter der PIN (Elterntor); das Kind
  löst nur den Versand aus und verlässt die App nicht.

## Befund nebenbei (05.10.2026)

Das Room-Schema Version 3 wurde am 02.10.2026 zweimal geändert, ohne die Versionsnummer zu erhöhen
(`b0f9690` Hash `8d7e05c6…` → `547da0a` Hash `4f372183…`). Ein Gerät mit einem Bau aus dieser Spanne
stürzt beim Update ab („Room cannot verify the data integrity"); so geschehen auf dem Emulator des
Videodrehs. `docs/privacy/data-storage.md` nennt die Erweiterung vom 03.10. als bewusst „vor dem ersten Release“;
für ausgelieferte Geräte also ohne Folgen. Vor einem Release trotzdem prüfen, ob irgendwo ein Testgerät
mit dem alten Stand weiterlaufen soll – sonst Version 4 mit Migration bzw. destruktivem Rückfall nur von genau diesem Hash.
