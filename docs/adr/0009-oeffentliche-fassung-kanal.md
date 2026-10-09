# ADR 0009 – Öffentliche Fassung: zuerst GitHub-Release, Stores später

Status: angenommen (Christian, 09.10.2026)

## Kontext

Die Checkliste in `docs/release/device-checklist.md` fragte den Eigentümer nach Zielgruppe und Kanal
(O1, O5). Bis dahin war offen, ob SideTube eine Familien-App für den eigenen Haushalt bleibt oder
öffentlich wird; davon hängt ab, wie verbindlich Anbieter-Bedingungen (YouTube), Kinderschutz-
Erklärungen, Provenienz der Medien und Store-Angaben sind. Seit ADR 0006 liegt ein Zwischenstand ohne
Historie samt Test-APK auf GitHub.

## Entscheidung

1. SideTube **wird öffentlich.** Zielgruppe sind Familien allgemein, nicht nur der eigene Haushalt.
2. **Kanal in dieser Reihenfolge:** zuerst Quelltext und signierte Test-APK als GitHub-Release
   (Verfahren aus ADR 0006: Einzel-Commit mit dem Baum von Gitea-`main`, keine Historie, kein
   Force-Push, Scans vorher), iOS für Bekannte per TestFlight. **Google Play und App Store folgen
   später**; ihre Prüfungen werden ab jetzt vorbereitet, aber noch nichts eingereicht.
3. Jede Veröffentlichung nach außen (Push auf GitHub, Release, TestFlight-Build, Store-Einreichung)
   braucht weiterhin die ausdrückliche Freigabe des Eigentümers im Einzelfall.

## Begründung

Ein GitHub-Release ist der kleinste öffentliche Schritt: Er macht den Stand prüfbar und erlaubt Tests
durch Dritte, ohne die Store-Prüfungen abzuwarten, die Belege vom echten Gerät und eine Rechtsprüfung
der YouTube-Bedingungen brauchen. Der Preis: Zwei Veröffentlichungswege nebeneinander (GitHub jetzt,
Stores später) und die Pflicht, Provenienz, Medienrechte und Datenschutztext schon jetzt in Ordnung
zu bringen, weil der Quelltext von jedem gelesen werden kann.

## Folgen

- `device-checklist.md`: O1 und O5 sind entschieden; O2 (Provenienz, Rechte) und O4 (Store-Angaben)
  bleiben offen und werden Pflicht vor der Store-Einreichung, O2 schon vor dem nächsten GitHub-Release.
- Nächster Zwischenstand `v0.1.0-test.2` aus dem aktuellen `main` (Sperre, Player-Härtung, zwei
  Sprachen, Nutzung), nach den Scans und mit Freigabe.
- Vor der Store-Phase: Bewertung des Anbieter-Modells (YouTube API Services Terms, kindgerichtete
  Apps), Datenschutzerklärung aus dem Entwurf fertigstellen, Store-Checklisten abarbeiten.
