// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import XCTest

/// ADR 0003: Kanal beim Hinzufügen einstufen. Nutzt die netzfreie ESA-Vorschau (`sidetube.demoOffline`,
/// nur DEBUG), damit der Ablauf ohne YouTube-Antwort prüfbar ist. Erst sperren, dann neu einstufen –
/// so zeigt sich auch, dass die Vorauswahl die gespeicherte Stufe übernimmt.
final class KanalEinstufenUITests: XCTestCase {
    private var app: XCUIApplication!
    private let esa = "https://www.youtube.com/channel/UCIBaDdAbGlFDeS33shmlD0A"

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        // uiTestReset leert auch die PIN – sie wird deshalb im Test festgelegt.
        app.launchArguments += ["-sidetube.uiTestReset", "1", "-sidetube.devBedtimeOff", "1", "-sidetube.demoOffline", "1"]
        app.launch()
    }

    func testKanalSperrenUndDannEinstufen() throws {
        XCTAssertTrue(app.staticTexts["PIN festlegen"].waitForExistence(timeout: 8))
        pin("1234")
        XCTAssertTrue(app.staticTexts["PIN wiederholen"].waitForExistence(timeout: 3))
        pin("1234")
        XCTAssertTrue(app.navigationBars["Einstellungen"].waitForExistence(timeout: 8))
        let neu = app.buttons["Neues Profil"].exists ? app.buttons["Neues Profil"] : app.buttons["Profil anlegen"]
        neu.tap()
        let name = app.textFields["Name"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        name.tap(); name.typeText("Mia")
        app.buttons["Sichern"].tap()
        XCTAssertTrue(app.staticTexts["Mia"].waitForExistence(timeout: 5))
        app.staticTexts["Mia"].tap()
        XCTAssertTrue(app.buttons["whitelist.add"].waitForExistence(timeout: 5))
        // Das Startpaket bringt ESA schon mit – erst entfernen, damit der Link nicht als Dublette endet.
        app.segmentedControls.buttons["Kanäle"].tap()
        let startEintrag = app.buttons.containing(NSPredicate(format: "label CONTAINS 'European Space Agency'")).firstMatch
        if startEintrag.waitForExistence(timeout: 3) {
            startEintrag.swipeLeft()
            app.buttons["Entfernen"].tap()
            XCTAssertFalse(startEintrag.waitForExistence(timeout: 2))
        }
        app.buttons["whitelist.add"].tap()

        let url = app.textFields["https://www.youtube.com/…"]
        XCTAssertTrue(url.waitForExistence(timeout: 3))
        url.tap(); url.typeText(esa)
        app.buttons["Prüfen"].tap()

        // Alle fünf Stufen mit Erklärung stehen offen da.
        XCTAssertTrue(app.staticTexts["Nur einzeln geprüfte Videos"].waitForExistence(timeout: 10), "keine Kanal-Vorschau")
        XCTAssertTrue(app.staticTexts["Das Kind sieht nur Videos dieses Kanals, die ihr einzeln freigegeben habt."].exists)
        sleep(1)
        anhaengen("kanal-vorschau-oben")
        app.swipeUp()
        sleep(1)
        anhaengen("kanal-vorschau-stufen")

        // „Gesperrt" → eigener Knopf, Meldung, nichts in der Liste.
        app.staticTexts["Gesperrt"].tap()
        let sperren = sichtbar(app.buttons["kanal.sperren"])
        XCTAssertFalse(app.buttons["kanal.hinzufuegen"].exists)
        sperren.tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'ist jetzt gesperrt'")).firstMatch.waitForExistence(timeout: 3))

        // Erneut prüfen: Vorauswahl ist jetzt „Gesperrt"; Eltern stufen bewusst neu ein.
        app.swipeDown()
        app.buttons["Prüfen"].tap()
        XCTAssertTrue(sichtbar(app.buttons["kanal.sperren"]).exists, "Vorauswahl muss die gespeicherte Stufe sein")
        app.swipeDown()
        app.staticTexts["Vertrauenswürdige Reihe"].tap()
        let hinzu = sichtbar(app.buttons["kanal.hinzufuegen"])
        anhaengen("kanal-vorschau-alter-kategorie")
        hinzu.tap()

        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'European Space Agency'")).firstMatch.waitForExistence(timeout: 5),
                      "Kanal muss nach dem Einstufen in der Liste stehen")
    }

    private func pin(_ code: String) {
        for ziffer in code { app.buttons[String(ziffer)].firstMatch.tap() }
    }

    /// Wischt, bis das Element tippbar ist – Form-Zeilen unterhalb des Bildschirms gibt es sonst noch nicht.
    @discardableResult
    private func sichtbar(_ element: XCUIElement) -> XCUIElement {
        var n = 0
        while !(element.exists && element.isHittable) && n < 8 { app.swipeUp(); n += 1 }
        XCTAssertTrue(element.isHittable, "\(element) nicht erreichbar")
        return element
    }

    private func anhaengen(_ name: String) {
        let anhang = XCTAttachment(screenshot: app.screenshot())
        anhang.name = name
        anhang.lifetime = .keepAlways
        add(anhang)
    }
}
