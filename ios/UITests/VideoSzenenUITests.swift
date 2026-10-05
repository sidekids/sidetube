// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import XCTest

/// Szenen für das Vorstellungsvideo. Setzt den Stand aus `VideoEinrichtungUITests` voraus und setzt
/// nichts zurück. Jede Szene druckt `SZENE-START <Unix-Zeit>`; das Aufnahmeskript schneidet daran.
final class VideoSzenenUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-sidetube.devBedtimeOff", "1"]
        app.launch()
    }

    private var szenenBeginn = Date()
    private func szeneBeginnt() {
        szenenBeginn = Date()
        print("SZENE-START \(szenenBeginn.timeIntervalSince1970)")
    }
    /// Hält die App offen, bis die Szene ihre Länge hat – sonst zeigt die Aufnahme danach den Home-Bildschirm.
    private func szeneEndet(nach sekunden: Double) {
        let rest = sekunden + 1 - Date().timeIntervalSince(szenenBeginn)
        if rest > 0 { usleep(UInt32(rest * 1_000_000)) }
    }

    /// Ohne ganze Kanäle (Kanalbilder sind Logos) stehen die freigegebenen Videos unter „Alle Videos".
    private func zuAllenVideos() {
        XCTAssertTrue(app.tabBars.buttons["Alle Videos"].waitForExistence(timeout: 10))
        app.tabBars.buttons["Alle Videos"].tap()
        XCTAssertTrue(app.navigationBars["Alle Videos"].waitForExistence(timeout: 5))
        if app.segmentedControls.buttons["Videos"].exists { app.segmentedControls.buttons["Videos"].tap() }
        sleep(2)
    }

    /// T1: Startseite mit den Kanälen ESA und NASA, dann „Alle Videos".
    func testT1Start() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        sleep(2)
        szeneBeginnt()
        sleep(4)
        app.tabBars.buttons["Alle Videos"].tap()
        sleep(1)
        if app.segmentedControls.buttons["Videos"].exists { app.segmentedControls.buttons["Videos"].tap() }
        sleep(5)
    }

    /// T2: Kanal ESA öffnen, das oberste Video starten.
    func testT2Abspielen() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        sleep(2)
        szeneBeginnt()
        sleep(2)
        let esa = app.buttons.containing(NSPredicate(format: "label CONTAINS 'European Space Agency'")).firstMatch
        XCTAssertTrue(esa.waitForExistence(timeout: 5))
        esa.tap()
        let video = app.buttons.containing(NSPredicate(format: "label CONTAINS 'Smile'")).firstMatch
        XCTAssertTrue(video.waitForExistence(timeout: 15), "Video nicht im Kanal")
        sleep(1)
        video.tap()
        XCTAssertTrue(app.buttons["player.close"].waitForExistence(timeout: 10))
        sleep(10)
        // Ordentlich schließen: Endet der Test mitten in der Wiedergabe, gilt sie als unterbrochen,
        // und die nächste Wiedergabe braucht die Freigabe der Eltern.
        sleep(3)
        app.buttons["player.close"].tap()
        sleep(1)
    }

    /// T3: Suche öffnen, „bunny" tippen – Treffer nur aus Freigegebenem.
    func testT3Suche() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        sleep(2)
        szeneBeginnt()
        sleep(1)
        app.tabBars.buttons["Suche"].tap()
        let feld = app.textFields["search.field"]
        XCTAssertTrue(feld.waitForExistence(timeout: 5))
        sleep(1)
        feld.tap()
        sleep(1)
        feld.typeText("bunny")   // in einem Zug: zeichenweise dauert je Zeichen über eine Sekunde
        sleep(2)
        app.buttons["search.done"].tap()
        sleep(5)
    }

    private func elternbereichOeffnen() {
        let schloss = app.buttons.matching(identifier: "parent.lock").allElementsBoundByIndex.first(where: \.isHittable)
        XCTAssertNotNil(schloss, "Schloss nicht erreichbar")
        schloss?.tap()
        XCTAssertTrue(app.buttons["1"].firstMatch.waitForExistence(timeout: 5))
        for ziffer in "1234" { app.buttons[String(ziffer)].firstMatch.tap(); usleep(250_000) }
        XCTAssertTrue(app.navigationBars["Einstellungen"].waitForExistence(timeout: 8))
    }

    /// T4: Einstellungen, Prüfliste, ein Video freigeben. Verändert den Stand.
    func testT4Freigabe() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        sleep(1)
        szeneBeginnt()
        sleep(1)
        elternbereichOeffnen()
        sleep(1)
        app.staticTexts["Kind"].tap()
        XCTAssertTrue(app.buttons["Freigaben prüfen"].waitForExistence(timeout: 5))
        sleep(1)
        app.buttons["Freigaben prüfen"].tap()
        // Immer Caminandes 1 – Caminandes 3 braucht T12 (Später/Verlauf) noch in der Prüfliste.
        let zeile = app.buttons.matching(NSPredicate(format: "label CONTAINS 'Caminandes 1'")).firstMatch
        XCTAssertTrue(zeile.waitForExistence(timeout: 5))
        sleep(2)
        zeile.tap()
        XCTAssertTrue(app.buttons["Freigeben"].waitForExistence(timeout: 5))
        sleep(2)
        app.buttons["Freigeben"].tap()
        sleep(3)
    }

    /// T5: Schlafmodus (30 Minuten) starten.
    func testT5Schlafmodus() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        sleep(1)
        szeneBeginnt()
        sleep(1)
        elternbereichOeffnen()
        sleep(1)
        app.staticTexts["Kind"].tap()
        XCTAssertTrue(app.buttons["profile.menu"].firstMatch.waitForExistence(timeout: 5))
        app.buttons["profile.menu"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Schlafmodus"].waitForExistence(timeout: 3))
        app.buttons["Schlafmodus"].tap()
        // 30 Minuten sind die Voreinstellung und für das Video richtig; nichts verstellen.
        XCTAssertTrue(app.buttons["Schlafmodus starten und in den Kindermodus"].waitForExistence(timeout: 5))
        sleep(2)
        app.buttons["Schlafmodus starten und in den Kindermodus"].tap()
        sleep(4)
    }

    // MARK: - Einzelvideos (02.10.2026)

    private func warte(_ s: Double) { usleep(UInt32(s * 1_000_000)) }

    private func zelle(_ text: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", text)).allElementsBoundByIndex
            .first(where: \.isHittable) ?? app.buttons.matching(NSPredicate(format: "label CONTAINS[c] %@", text)).firstMatch
    }

    private func scrolleBis(_ element: XCUIElement, max: Int = 8) {
        var n = 0
        while !(element.exists && element.isHittable) && n < max { app.swipeUp(velocity: 400); n += 1 }
    }

    private func schliessePlayer() {
        // Player immer schließen, nie hart beenden (sonst Sperre bis Eltern-Freigabe).
        let zu = app.buttons.matching(NSPredicate(format: "label IN {'Schließen', 'Zurück', 'Fertig'}")).firstMatch
        if zu.waitForExistence(timeout: 3) { zu.tap() } else { app.swipeDown(velocity: 800) }
        warte(1.5)
    }

    /// T6: „Zuletzt geschaut" auf der Startseite – vorher einmal Spring angeschaut.
    func testT6ZuletztGeschaut() throws {
        zuAllenVideos()
        zelle("Spring").tap()
        warte(6)
        schliessePlayer()
        app.tabBars.buttons["Start"].tap()
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        warte(1.5)
        szeneBeginnt()
        warte(2)
        app.swipeUp(velocity: 250)
        warte(2.5)
        let zuletzt = app.staticTexts.matching(NSPredicate(format: "label IN {'Zuletzt geschaut', 'Weiterschauen'}")).firstMatch
        XCTAssertTrue(zuletzt.exists, "kein Zuletzt geschaut")
        warte(1)
        zelle("Spring").tap()
        warte(6)
        schliessePlayer()
        szeneEndet(nach: 19)
    }

    /// T7: „Alle Videos" – Kanäle, Videos, Sendungen.
    func testT7AlleVideos() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        warte(1)
        szeneBeginnt()
        warte(1.5)
        app.tabBars.buttons["Alle Videos"].tap()
        warte(2)
        for teil in ["Kanäle", "Videos", "Sendungen"] {
            app.segmentedControls.buttons[teil].tap()
            warte(3.5)
        }
        szeneEndet(nach: 18)
    }

    /// T8: Die Sendung „Blender Open Movies" öffnen und ein Video daraus abspielen.
    func testT8Sendungen() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        app.tabBars.buttons["Alle Videos"].tap()
        app.segmentedControls.buttons["Sendungen"].tap()
        warte(1.5)
        szeneBeginnt()
        warte(1.5)
        zelle("Blender Open Movies").tap()
        warte(4)
        app.swipeUp(velocity: 300)
        warte(2)
        app.swipeDown(velocity: 300)
        warte(1)
        zelle("WING IT").tap()
        warte(6)
        schliessePlayer()
        szeneEndet(nach: 22)
    }

    /// T9: Link hinzufügen – erst die Vorschau, dann zur Prüfung.
    func testT9Hinzufuegen() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        warte(1)
        szeneBeginnt()
        warte(1)
        elternbereichOeffnen()
        warte(0.8)
        app.staticTexts["Kind"].tap()
        XCTAssertTrue(app.buttons["whitelist.add"].waitForExistence(timeout: 5))
        warte(0.8)
        app.buttons["whitelist.add"].tap()
        let url = app.textFields["https://www.youtube.com/…"]
        XCTAssertTrue(url.waitForExistence(timeout: 3))
        url.tap()
        url.typeText("https://www.youtube.com/watch?v=Z4C82eyhwgU")
        warte(0.5)
        app.buttons["Prüfen"].tap()
        let merken = app.buttons["Erst zur Prüfung merken"]
        XCTAssertTrue(merken.waitForExistence(timeout: 25), "keine Vorschau")
        warte(4)
        merken.tap()
        warte(3)
        szeneEndet(nach: 22)
    }

    /// T10: Profil bearbeiten – Tageslimit 30 Minuten, Ruhezeit an. Danach zurückgestellt.
    func testT10Profil() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        warte(1)
        szeneBeginnt()
        warte(1)
        elternbereichOeffnen()
        warte(0.8)
        app.staticTexts["Kind"].tap()
        XCTAssertTrue(app.buttons["profile.menu"].firstMatch.waitForExistence(timeout: 5))
        app.buttons["profile.menu"].firstMatch.tap()
        app.buttons["Profil bearbeiten"].tap()
        XCTAssertTrue(app.navigationBars["Profil bearbeiten"].waitForExistence(timeout: 5))
        warte(1)
        let ruhe = app.switches["Ruhezeit"]
        scrolleBis(ruhe)
        warte(1)
        let limit = app.switches["Tageslimit"]
        scrolleBis(limit)
        if (limit.value as? String) != "1" { limit.switches.firstMatch.exists ? limit.switches.firstMatch.tap() : limit.tap() }
        warte(1)
        let schritt = app.steppers.matching(NSPredicate(format: "label CONTAINS 'Minuten pro Tag'")).firstMatch
        scrolleBis(schritt)
        for _ in 0..<6 { schritt.buttons.element(boundBy: 0).tap(); warte(0.3) }
        warte(2)
        app.buttons["Sichern"].tap()
        warte(3)
        // Zurückstellen (außerhalb der Szene): kein Tageslimit für die anderen Szenen.
        app.buttons["profile.menu"].firstMatch.tap()
        app.buttons["Profil bearbeiten"].tap()
        let limit2 = app.switches["Tageslimit"]
        scrolleBis(limit2)
        limit2.switches.firstMatch.exists ? limit2.switches.firstMatch.tap() : limit2.tap()
        app.buttons["Sichern"].tap()
        warte(1)
    }

    /// T11: Quellen und Vertrauensstufen.
    func testT11Quellen() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        warte(1)
        szeneBeginnt()
        warte(1)
        elternbereichOeffnen()
        warte(0.8)
        let menue = app.navigationBars["Einstellungen"].buttons.matching(NSPredicate(format: "label IN {'Weitere', 'Mehr', 'More'}")).firstMatch
        menue.tap()
        app.buttons["Quellen & Sicherheitsstufen"].tap()
        warte(3)
        app.swipeUp(velocity: 250)
        warte(3)
        app.swipeUp(velocity: 250)
        warte(3)
        szeneEndet(nach: 16)
    }

    /// T12: Später entscheiden – und der Verlauf zeigt es.
    func testT12Verlauf() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        warte(1)
        szeneBeginnt()
        warte(1)
        elternbereichOeffnen()
        warte(0.8)
        app.staticTexts["Kind"].tap()
        XCTAssertTrue(app.buttons["Freigaben prüfen"].waitForExistence(timeout: 5))
        app.buttons["Freigaben prüfen"].tap()
        let zeile = app.buttons.matching(NSPredicate(format: "label CONTAINS 'Caminandes 3'")).firstMatch
        XCTAssertTrue(zeile.waitForExistence(timeout: 5))
        warte(1)
        zeile.tap()
        XCTAssertTrue(app.buttons["Später"].waitForExistence(timeout: 5))
        warte(1.5)
        app.buttons["Später"].tap()
        warte(2)
        zeile.tap()
        let verlauf = app.staticTexts["Verlauf"]
        scrolleBis(verlauf)
        warte(4)
        app.buttons["Schließen"].tap()
        warte(1)
        szeneEndet(nach: 20)
    }

    /// T13: Profilwechsel – „Ole" und zurück zu „Kind".
    func testT13Profilwechsel() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        warte(1)
        szeneBeginnt()
        warte(1.5)
        let wahl = app.buttons.matching(NSPredicate(format: "label CONTAINS 'Kind'")).firstMatch
        XCTAssertTrue(wahl.waitForExistence(timeout: 5), "kein Profilmenü")
        wahl.tap()
        warte(1)
        app.buttons["Ole"].firstMatch.tap()
        warte(4)
        app.buttons.matching(NSPredicate(format: "label CONTAINS 'Ole'")).firstMatch.tap()
        warte(1)
        app.buttons["Kind"].firstMatch.tap()
        warte(3)
        szeneEndet(nach: 14)
    }

    // MARK: - Wünsche von Kindern (ADR 0001, 02.10.2026)
    //
    // Nach der Einrichtung in der Reihenfolge T14 → T17 laufen lassen: T14–T16 legen die drei Wünsche
    // des Tages an (Tagesgrenze 3), T17 entscheidet sie. Mit `TEST_RUNNER_SIDETUBE_SHOTS_DIR=<Ordner>`
    // legt jede Szene zusätzlich Belegbilder ab; die Aufnahme sieht davon nichts.

    private func beleg(_ name: String) {
        guard let ordner = ProcessInfo.processInfo.environment["SIDETUBE_SHOTS_DIR"], !ordner.isEmpty else { return }
        try? FileManager.default.createDirectory(atPath: ordner, withIntermediateDirectories: true)
        try? app.screenshot().pngRepresentation.write(to: URL(fileURLWithPath: ordner).appendingPathComponent("\(name).png"))
    }

    private func meldungBestaetigen() {
        let ok = app.alerts.buttons["OK"].firstMatch
        XCTAssertTrue(ok.waitForExistence(timeout: 5), "keine Rückmeldung zum Wunsch")
        warte(2.5)
        ok.tap()
    }

    /// T14: Themenwunsch – die Suche findet nichts, der Wunsch an die Eltern steht zuerst.
    func testT14Themenwunsch() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        warte(1)
        szeneBeginnt()
        warte(1.5)
        app.tabBars.buttons["Suche"].tap()
        let feld = app.textFields["search.field"]
        XCTAssertTrue(feld.waitForExistence(timeout: 5))
        warte(1)
        feld.tap()
        feld.typeText("Dinos")
        warte(1.5)
        app.buttons["search.done"].tap()
        let wuenschen = app.buttons["wish.topic.submit"]
        XCTAssertTrue(wuenschen.waitForExistence(timeout: 5), "kein Themenwunsch")
        warte(3)
        beleg("T14-themenwunsch")
        wuenschen.tap()
        XCTAssertTrue(app.staticTexts["wish.feedback"].waitForExistence(timeout: 5))
        warte(1)
        beleg("T14-themenwunsch-gesendet")
        szeneEndet(nach: 17)
    }

    /// T15: „Mehr davon" aus dem Player-Menü bei einem freigegebenen Video.
    func testT15MehrDavon() throws {
        zuAllenVideos()
        warte(1)
        szeneBeginnt()
        warte(1.5)
        zelle("Spring").tap()
        let mehr = app.buttons["wish.moreLikeThis"]
        XCTAssertTrue(mehr.waitForExistence(timeout: 10), "kein „Mehr davon“ im Player")
        warte(6)
        mehr.tap()
        XCTAssertTrue(app.alerts.firstMatch.waitForExistence(timeout: 5))
        beleg("T15-mehr-davon")
        meldungBestaetigen()
        warte(2)
        schliessePlayer()
        szeneEndet(nach: 18)
    }

    /// T16: „Neu bei deinen Kanälen" – gesperrte neue NASA-Folgen, eine davon wünschen.
    func testT16NeueFolgen() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        let abschnitt = app.staticTexts["Neu bei deinen Kanälen"]
        XCTAssertTrue(abschnitt.waitForExistence(timeout: 20), "keine neuen Folgen (Feed erreichbar? NASA auf „Vertrauenswürdige Reihe“?)")
        warte(1)
        szeneBeginnt()
        warte(1.5)
        app.swipeUp(velocity: 250)
        warte(2.5)
        let wuenschen = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH 'wish.newEpisode.' AND NOT (identifier BEGINSWITH 'wish.newEpisode.done')")).firstMatch
        scrolleBis(wuenschen, max: 4)
        XCTAssertTrue(wuenschen.exists, "kein „Wünschen“ bei den neuen Folgen")
        beleg("T16-neue-folgen")
        warte(1)
        wuenschen.tap()
        meldungBestaetigen()
        warte(1.5)
        beleg("T16-gewuenscht")
        // „Meine Wünsche": drei Wünsche, keiner geht heute mehr.
        app.buttons["wishes.open"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Meine Wünsche"].waitForExistence(timeout: 5))
        warte(4)
        beleg("T16-meine-wuensche")
        app.buttons["wishes.close"].tap()
        warte(1)
        szeneEndet(nach: 22)
    }

    /// T17: Eltern entscheiden – neue Folge freigeben, mit Antwort an das Kind; danach sieht das Kind den Stand.
    /// Die anderen beiden Wünsche bleiben offen (Szene muss in 25 s passen).
    func testT17WunschEntscheiden() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        warte(1)
        szeneBeginnt()
        elternbereichOeffnen()
        app.staticTexts["Kind"].tap()
        XCTAssertTrue(app.buttons["Freigaben prüfen"].waitForExistence(timeout: 5))
        app.buttons["Freigaben prüfen"].tap()
        let folge = app.buttons.matching(NSPredicate(format: "identifier == 'review.wish' AND label CONTAINS 'Neu bei deinen Kanälen'")).firstMatch
        XCTAssertTrue(folge.waitForExistence(timeout: 5), "Wunsch nicht in der Prüfliste")
        warte(0.8)
        beleg("T17-pruefliste")
        folge.tap()
        let antwort = app.textFields["wish.reply.field"].exists ? app.textFields["wish.reply.field"] : app.textViews["wish.reply.field"]
        XCTAssertTrue(antwort.waitForExistence(timeout: 5))
        antwort.tap()
        antwort.typeText("Viel Spaß!")
        app.buttons["wish.approve"].tap()
        warte(0.5)
        // Zurück in den Kindermodus und „Meine Wünsche" öffnen.
        app.navigationBars.element(boundBy: 0).buttons.element(boundBy: 0).tap()
        app.navigationBars.element(boundBy: 0).buttons.element(boundBy: 0).tap()
        app.buttons["Sperren"].firstMatch.tap()   // tap wartet selbst auf das Element
        app.buttons["wishes.open"].firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Deine Eltern: „Viel Spaß!“"].waitForExistence(timeout: 5))
        warte(2.5)
        beleg("T17-meine-wuensche")
        app.buttons["wishes.close"].tap()
        szeneEndet(nach: 24)
    }
}
