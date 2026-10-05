# SideTube

SideTube ist eine Video-App für Familien mit zwei nativen Anwendungen in einem Monorepo: Swift/SwiftUI für iOS und Kotlin/Compose für Android.

**Vorabversion, noch nicht für einen weltweiten Release freigegeben.** Der [Release-Audit](docs/release/pre-release-audit.md) und die [Funktionsmatrix](docs/release/feature-parity.md) benennen die offenen Blocker.

Eltern verwalten Profile, Quellen und Freigaben. Neue Inhalte und iOS-Startvorschläge benötigen eine Prüfung. Freigegebene vertrauenswürdige Kanäle erlauben weitergehendes Stöbern; das ist keine Einzelprüfung jedes künftigen Videos.

Profildaten liegen lokal. Externe Videoanbieter erhalten jedoch unter anderem IP-Adresse, angefragte Medien und Wiedergabekontext. iOS verwendet nichtpersistenten WebKit-Speicher; Android leert den Cookie-Speicher des Players zu Beginn und am Ende jeder Wiedergabe, nimmt aber währenddessen noch Drittanbieter-Cookies an. Aussagen wie „alles bleibt lokal“ oder „YouTube kann nicht verknüpfen“ sind deshalb nicht haltbar. Siehe [Datenschutzmodell](docs/privacy/privacy-model.md).

**Eltern benachrichtigen (freiwillig, [ADR 0005](docs/adr/0005-eltern-ueber-wuensche-benachrichtigen.md)).** Wünscht sich ein Kind etwas, kann SideTube die Eltern über die eigene Nextcloud der Familie benachrichtigen – ohne SideTube-Server.
1. Auf dem Nextcloud-Server Talk einschalten und `scripts/talk-wunschkanal.sh --server https://… --eltern <nutzer>` ausführen (`OCC` bzw. `OCC_SSH` setzen, siehe `--help`). Das Skript legt einen Bot, der nur senden darf, und das Gespräch „SideTube-Wünsche“ an, schickt eine Testnachricht und zeigt einen Einrichtungscode als QR-Code.
2. In SideTube hinter der PIN: Einstellungen → „Eltern benachrichtigen“ → Code scannen oder einfügen → „Test senden“.
3. Auf dem Telefon der Eltern genügt die normale Nextcloud-App. Die Nachricht nennt nur die erwähnten Eltern und die Zahl offener Wünsche – keinen Namen, kein Thema, keinen Titel.

Die verbindlichen Build-Anleitungen, Struktur, Versionierung, Herkunft und Lizenzhinweise stehen im [Root-README](README.md), plattformspezifische Details unter [iOS](ios/README.md) und [Android](android/README.md). Gemeinsame Inhalte liegen unter `content/`; nur die Dateien aus `content/public-files.txt` gelangen in Builds.

**Marken und Inhalte:** YouTube, PeerTube sowie die Namen von Sendern, Kanälen und Organisationen (z. B. KiKA, WDR, NASA, ESA) gehören ihren Inhabern. SideKids steht mit ihnen in keiner Verbindung und wird von ihnen nicht unterstützt. Die Listen unter `content/` nennen fremde Kanäle und Videos nur als Verweise; deren Inhalte sind nicht Teil dieses Repositorys.

Produktidee und Kuratierung: Christian-Maximilian Steier / SideKids. Die aktuelle Android-Fassung erklärt MPL-2.0; die vorherige GPL-Fork-Herkunft und der Nachweis einer unabhängigen Neuentwicklung bleiben zu prüfen. Keine Veröffentlichung oder Änderung der öffentlichen Historie wurde vorgenommen.
