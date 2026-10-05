# ADR 0005 – Eltern über neue Wünsche benachrichtigen

Status: angenommen (Christian, 04.10.2026) – Weg 1 Nextcloud Talk, Weg 2 ntfy, beide nur mit eigenem Server

## Kontext

Wunsch der Eltern (04.10.2026): Eltern sollen erfahren, dass das Kind sich etwas gewünscht hat – auf
iPhone und SidePhone. Bisher gilt ADR 0001: keine Push-Benachrichtigungen, kein Server, Wünsche bleiben
auf dem Gerät; Eltern sehen nur einen Zähler hinter der PIN. Das heißt in der Praxis: Ein Wunsch liegt
tagelang unbemerkt, und das Kind lernt, dass Wünschen nichts bringt.

Geprüft am 04.10.2026 (Quellen unten):

- **Familienfreigabe (Apple):** Es gibt keine Schnittstelle, über die eine App eigene Anfragen an die
  Eltern stellen kann. „Kaufanfrage" und Bildschirmzeit-Anfragen sind dem System vorbehalten. Die
  Screen-Time-API (Family Controls) dient dem Beschränken und Beobachten, nicht dem Fragen.
- **PermissionKit (Apple, ab iOS 26):** Kinder können über Nachrichten die Eltern der Familie fragen –
  aber nur, ob sie mit einer neuen Person kommunizieren dürfen (`CommunicationTopic`,
  `CommunicationLimits`). Eigene Fragen wie Wünsche nach Inhalten sind nicht vorgesehen
  (Stand WWDC 2025; bei Umsetzung erneut prüfen).
- **Google Family Link:** keine öffentliche Schnittstelle für Apps Dritter; Benachrichtigungen gibt es
  nur für Googles eigene Anfragen (App-Installation, Chrome). Das SidePhone hat zudem voraussichtlich
  keine Google-Dienste, also auch keinen Firebase-Push (Annahme, am Gerät zu bestätigen).
- **App Store, Kids-Kategorie (Richtlinie 1.3):** Wege aus der App (andere Apps, Websites) nur hinter
  einem Elterntor. Eine Nachricht, die das Kind selbst über die Nachrichten-App abschickt, wäre so ein
  Weg.

## Entscheidung

SideTube bekommt auf beiden Plattformen einen **freiwilligen Elternkanal**, immer zum **eigenen
Server** der Familie. Die App schlägt keinen Dienst vor und füllt nichts vor.

**Weg 1 – Nextcloud Talk (zuerst gebaut).** Viele Familien haben für SidePlay schon eine Nextcloud.
Talk (ab 17.1) kennt Bots, die mit einem gemeinsamen Schlüssel signierte Nachrichten in ein Gespräch
schreiben – ohne Nutzerkonto und ohne Passwort auf dem Kindergerät.

- Einmal auf dem Server (Kommandozeile, laut Talk-Doku aus Sicherheitsgründen nur dort):
  `occ talk:bot:install --feature=response --no-setup "SideTube" <Schlüssel> <URL> "<Beschreibung>"`
  (Schlüssel 40–128 Zeichen; die URL wird ohne Feature `webhook` nicht aufgerufen), Gespräch anlegen
  (`occ talk:room:create --owner=<eltern> --user=<eltern> "SideTube-Wünsche"`), Bot dort einschalten
  (`occ talk:bot:setup <bot-id> <token>`).
- **Die Meldung erwähnt die Eltern** (`@<nutzer>` am Anfang, je eingetragenem Elternteil). Talk meldet
  in Gruppengesprächen ohne Einstellung nur Erwähnungen; mit Erwähnung entsteht immer eine
  Benachrichtigung (04.10.2026: ohne Erwähnung keine, mit Erwähnung `mention_direct`).
- **Eltern brauchen keine Talk-App.** Die normale Nextcloud-App genügt: Hat ein Nutzer kein Gerät mit
  der Talk-App angemeldet, schickt Nextcloud Talk-Benachrichtigungen an die Nextcloud-App
  (`notifications/lib/Push.php`, Rückfall „If you don't have a talk device, we fall back to the files
  app"). Am 04.10.2026 auf einem iPhone mit der Nextcloud-App bestätigt. Grenze: Wer später die
  Talk-App anmeldet, bekommt die Meldungen nur noch dort.
- In SideTube: Server-Adresse, Gesprächs-Token, Bot-Schlüssel und die zu erwähnenden Nutzernamen,
  bequem per QR-Code.
- Senden: `POST /ocs/v2.php/apps/spreed/api/v1/bot/<token>/message` mit `{"message": …}`, Kopfzeilen
  `OCS-APIRequest: true`, `X-Nextcloud-Talk-Bot-Random` (Zufallswert) und
  `X-Nextcloud-Talk-Bot-Signature` = HMAC-SHA256(Schlüssel, Zufallswert + Nachrichtentext), hex.
  Am 04.10.2026 auf einer privaten Nextcloud (32.0.15, Talk 22.0.18) mit Antwort 201 erprobt.
- Der Schlüssel kann nur in Gespräche schreiben, in denen der Bot eingeschaltet ist – keine Dateien,
  kein Kalender, keine anderen Gespräche.

**Weg 2 – ntfy (für Familien ohne Nextcloud).** Eigener ntfy-Dienst (`auth-default-access: deny-all`,
ein Nutzer nur mit Schreibrecht auf ein Thema, Zugangs-Token), Eltern abonnieren das Thema in der
ntfy-App. Senden als einfacher HTTP-POST.

**Für beide Wege:**

- **Einrichten** hinter der PIN unter Einstellungen → „Eltern benachrichtigen", per QR-Code wie bei der
  Nextcloud-Anmeldung in SidePlay. Schlüssel und Token liegen im Schlüsselbund (iOS) bzw. verschlüsselt
  im Keystore (Android), nie im Klartext, nie im Log. „Test senden" prüft den Weg.
- **Auslöser:** ein neu angelegter Wunsch (nicht bei Dubletten). Wegen der Tagesgrenze aus ADR 0001
  höchstens drei Nachrichten je Profil und Tag.
- **Inhalt:** so wenig wie möglich – „@eltern SideTube: neuer Wunsch (2 offen)". Kein Name des Kindes, kein
  Videotitel, kein Thema. Was gewünscht wurde, sehen Eltern wie bisher in der Prüfliste.
- **Versand** im Hintergrund, ohne dass das Kind die App verlässt oder etwas sieht. Scheitert er (kein
  Netz, Dienst weg), wird nicht wiederholt; der Wunsch bleibt wie bisher in der Prüfliste.
- **Entscheiden** bleibt am Kindergerät hinter der PIN (unverändert). Freigeben vom Eltern-Handy aus
  ist ausdrücklich nicht Teil dieser Entscheidung.
- **Ohne Einrichtung** bleibt alles wie in ADR 0001: kein Netzweg, kein Push.

Verworfen:

- **Nachricht vom Kind** (vorbereitete SMS/iMessage, das Kind tippt „Senden"): braucht in der
  Kids-Kategorie ein Elterntor und damit genau die Eltern, die benachrichtigt werden sollen; auf dem
  SidePhone nur mit SIM.
- **iCloud-Freigabe (CloudKit) zwischen Kind- und Eltern-iPhone:** könnte auch das Freigeben vom
  Eltern-Handy tragen, schließt aber das SidePhone und gemischte Familien aus und braucht SideTube im
  Elternmodus auf dem Eltern-iPhone. Später als eigener Schritt denkbar.
- **Nextcloud mit Nutzerzugang** (App-Passwort, Benachrichtigungs-API): mehr Recht als nötig; der
  Talk-Bot kann nur in sein Gespräch schreiben.
- **ntfy.sh** (öffentlicher Dienst): IP-Adresse und Zeitpunkt des Kindergeräts gingen an einen Dritten;
  für die Kids-Kategorie (keine Daten an Dritte) nicht vertretbar.

## Begründung

- Ein Weg für beide Plattformen und für jedes Eltern-Handy; kein Konto bei SideKids, kein Server von
  SideKids. Das Kindergerät spricht nur mit dem Server der Familie.
- Talk-Push ans Eltern-Handy läuft über Nextclouds Push-Vermittlung (push-notifications.nextcloud.com)
  und Apple/Google; der Inhalt ist dabei mit dem Geräteschlüssel des Eltern-Handys verschlüsselt und vom
  Server signiert. Die Vermittlung sieht nur eine zufällige Gerätekennung, das Push-Token und Zeitpunkte
  (Nextcloud-Doku `push-v2.md`).
- Der Inhalt verrät nichts über das Kind; damit ist der Weg auch über fremde Dienste vertretbar.
- Bei einem selbst betriebenen ntfy-Dienst und iPhone als Eltern-Handy reicht der eigene Server für den
  Apple-Push nur die Nachrichten-ID und eine Prüfsumme der Themenadresse an ntfy.sh weiter; der Text
  bleibt auf dem eigenen Server (ntfy-Doku, `upstream-base-url`).
- **Preis:** ADR 0001 („kein Push, kein Netz außer Feeds") gilt nicht mehr ausnahmslos: Mit Einrichtung
  verlässt eine Meldung das Gerät. Eltern brauchen die ntfy-App und – für den sauberen Weg – einen
  eigenen Dienst. Ein Wunsch, dessen Meldung scheitert, fällt nicht auf (bewusst kein Wiederholen,
  damit keine Warteschlange auf dem Kindergerät entsteht).

## Folgen

- iOS und Android: Einstellungsseite „Eltern benachrichtigen" (mit QR-Scan und „Test senden"),
  Versand beim Anlegen eines Wunsches, Unit-Tests für Auslöser (neu ja, Dublette nein), Inhalt und
  Fehlerfall; die Schlüssel nur im sicheren Speicher.
- ADR 0001 bekommt bei Annahme einen Vermerk „ergänzt durch ADR 0005".
- Datenschutzerklärung (sidekids.github.io): der freiwillige Elternkanal, was gesendet wird und wohin.
- Anleitung für Eltern: Talk auf dem Server einschalten (Bot, Gespräch) bzw. ntfy-Dienst und -App; auf
  dem Eltern-Handy genügt die Nextcloud-App; am besten ein Skript, das Bot und Gespräch anlegt und den QR-Code ausgibt.
- Falle bei der Talk-Installation: War Talk früher einmal installiert, scheitert das erneute Einschalten
  an einer alten Migration („inCall … Bool and also NotNull"). Lösung laut Talk-Entwicklern
  (nextcloud/spreed#7140): Talk-Tabellen, `oc_migrations`- und `oc_appconfig`-Zeilen von spreed
  entfernen, neu installieren – auf einer privaten Nextcloud am 04.10.2026 so gelöst (vorher gesichert).
- Vorstellungsvideo: eine Szene „Wunsch → Meldung auf dem Eltern-Handy" ist erst sinnvoll, wenn ein
  Demo-Dienst ohne echte Zugangsdaten bereitsteht.

## Quellen (geprüft 04.10.2026)

- App Review Guidelines 1.3 (Kids): https://developer.apple.com/app-store/review/guidelines/
- Screen Time API (Family Controls), WWDC21: https://developer.apple.com/videos/play/wwdc2021/10123/
- PermissionKit, WWDC25 „Enhance child safety with PermissionKit": https://developer.apple.com/videos/play/wwdc2025/293/
- Google Family Link, Benachrichtigungen: https://support.google.com/families/answer/7184159
- ntfy, Konfiguration (Zugriffsrechte, Token, `upstream-base-url`): https://docs.ntfy.sh/config/
- Nextcloud Talk, Bots: https://nextcloud-talk.readthedocs.io/en/latest/bots/
- Nextcloud Push v2: https://github.com/nextcloud/notifications/blob/master/docs/push-v2.md
- Talk-Neuinstallation nach früherer Installation: https://github.com/nextcloud/spreed/issues/7140
