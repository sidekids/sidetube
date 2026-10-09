# ADR 0006 – Zwischenstand öffentlich machen: Quelltext ohne Historie, Test-APK als Vorabversion

Status: angenommen (Christian, 05.10.2026)

## Kontext

SideTube wird im privaten Entwicklungs-Repository gebaut; öffentlich war nur ein älterer Stand vom
01.09.2026 unter `github.com/sidekids/sidetube` – mit eigener, unverwandter Historie. Interessierte Eltern
mit SidePhone fragen nach einer Möglichkeit zum Ausprobieren (Sideloading). Die Release-Prüfung
(`docs/release/pre-release-audit.md`) stuft SideTube weiter als BLOCKER ein: Auf Android fehlen ein
gesperrter Vollbildschirm bei Ruhezeit, Schlafmodus und Tageslimit, die eigene Player-Steuerung und die
Sperre von Drittanbieter-Cookies; Gerätetests sind lückenhaft. Die Entwicklungs-Historie enthält
Bildschirmfotos mit fremden Logos und Vorschaubildern sowie private Namen und Serverangaben.

## Entscheidung

1. **Quelltext als Zwischenstand, ohne Historie.** `main` auf GitHub wurde am 05.10.2026 durch einen
   einzelnen Commit ersetzt, dessen Inhalt dem bereinigten Entwicklungs-`main` entspricht (fremde
   Bildschirmfotos und Demovideos entfernt, private Namen und Serverangaben neutralisiert, Doku auf Stand,
   Marken- und Rechtehinweise). Die bisherige öffentliche Historie bleibt unter dem Tag
   `archiv/vor-zwischenstand-2026-10-05` erhalten.
2. **Künftige Stände** kommen als weitere Einzel-Commits oben auf den GitHub-`main` (Inhalt = jeweiliger
   bereinigter Entwicklungs-`main`, Vorgänger = bisheriger GitHub-`main`) – kein weiterer Force-Push. Vor
   jedem Stand: Geheimnis-Scan, Suche nach privaten Angaben und fremden Medien, Tests beider Plattformen.
   Autor und Committer dieser Commits: „Christian-Maximilian Steier <christian@x-berg.de>“ – die Adresse ist
   dem GitHub-Konto `christiansteier` zugeordnet. Am 05.10.2026 wurden die bis dahin vorhandenen GitHub-Commits
   (zwei auf `main`, vier unter dem Archiv-Tag) mit unverändertem Inhalt, Text und Datum auf diese Adresse
   umgeschrieben.
3. **Herkunft:** Die Frage, ob der Android-Client vom früheren GPL-Fork (degipe/YouTubeWhitelist)
   unabhängig ist, trägt Christian; veröffentlicht wird unter MPL-2.0, das Originalprojekt bleibt in
   `README.md` und `LICENSE` genannt. Ein Vergleich am 05.10.2026 fand keinen übernommenen Code, aber
   gleiche Tabellen- und Klassennamen.
4. **Test-APK für Android als GitHub-Vorabversion** (Pre-release), ausdrücklich nicht für den
   unbeaufsichtigten Einsatz bei Kindern, mit Liste der offenen Kinderschutz-Punkte. Keine iOS-Testfassung.
5. **Signatur:** ein eigener SideKids-Schlüssel, der für alle künftigen Sideload-APKs bleibt – nur so gehen
   spätere Updates ohne Neuinstallation und Datenverlust. Der Schlüssel liegt außerhalb des Repos; das
   Passwort im Schlüsselbund des Entwicklungsrechners; eine Sicherung verwahrt Christian getrennt davon.
   Signiert wird nach dem Release-Bau mit `apksigner`; die Bau-Dateien kennen den Schlüssel nicht.
6. Jede APK hängt an einem Tag auf dem GitHub-Commit, aus dessen Inhalt sie gebaut wurde, mit SHA-256-Summe.

## Begründung

- Ohne Historie gelangen weder die fremden Bilder noch frühere private Angaben an die Öffentlichkeit; die
  Historie umzuschreiben wäre aufwendiger und fehleranfälliger.
- Der Archiv-Tag hält die frühere öffentliche Entwicklung und ihre Herkunft nachvollziehbar.
- Eine Vorabversion mit klarer Warnung erlaubt Eltern, SideTube kennenzulernen, ohne Sicherheit zu
  versprechen, die die App auf Android noch nicht bietet.
- **Preis:** Die öffentliche Geschichte bricht am 05.10.2026; wer den alten Stand geklont hat, muss neu
  klonen. Die Test-APK kann Kindern zugänglich werden, obwohl Schutzfunktionen fehlen – die Verantwortung
  liegt bei den Eltern, die Warnung muss deshalb unübersehbar sein. Geht der Signierschlüssel verloren,
  müssen alle Tester neu installieren.

## Folgen

- Website: Die SideTube-Seite kann auf die Vorabversion verweisen – erst nach eigener Freigabe.
- Die Kinderschutz-Punkte auf Android bleiben vor einer Fassung „für Familien“ Pflicht.
- Store-Angaben (App Privacy, Data Safety) müssen Kamera und Nextcloud-Aufruf nennen, sobald es um
  Stores geht.

## Nachtrag (09.10.2026)

Zweiter Zwischenstand nach demselben Verfahren: GitHub-`main` = `692ea32` (Einzel-Commit mit dem Baum
des Entwicklungs-`main` vom 09.10.2026, Vorgänger `f55fcb5`), Tag `v0.1.0-test.2`, Vorabversion mit
`SideTube-0.1.0-test.2.apk` (Build-Nummer 2, SHA-256 `cf753e4a…e2494`, gleiches Zertifikat) und
`SHA256SUMS.txt`. Scans vorher: gitleaks ohne Fund, keine privaten Hosts oder Namen, keine fremden
Medien. Stolperstein: Legt `gh release create` ein Release an, bevor der Tag gepusht ist, setzt GitHub
den Tag auf den alten `main`; der Tag musste einmal gelöscht und neu gesetzt werden, das Release wurde
dabei kurz zum Entwurf und danach wieder veröffentlicht. Künftig: erst Commit und Tag pushen, dann das
Release anlegen. Freigabe durch Christian am 09.10.2026 (ADR 0009).
