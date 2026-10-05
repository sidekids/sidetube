// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import XCTest

/// Echte, videobasierte Produktdemo. Die Aufnahme selbst erfolgt außerhalb des Tests
/// über `simctl recordVideo`, daher werden hier keine Screenshots erzeugt.
final class DemoRecordingUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        // uiTestReset entfernt alle Profile und lokale Testdaten im Simulator.
        app.launchArguments += ["-sidetube.uiTestReset", "1", "-sidetube.devBedtimeOff", "1", "-sidetube.devRemoteWheel", "1", "-sidetube.demoOffline", "1"]
        app.launch()
    }

    private func enter(_ pin: String) {
        for digit in pin { app.buttons[String(digit)].firstMatch.tap() }
    }

    func testGuidedParentAndPlaybackTour() throws {
        XCTAssertTrue(app.staticTexts["PIN festlegen"].waitForExistence(timeout: 8))
        enter("1234")
        XCTAssertTrue(app.staticTexts["PIN wiederholen"].waitForExistence(timeout: 3))
        enter("1234")

        XCTAssertTrue(app.navigationBars["Einstellungen"].waitForExistence(timeout: 8))
        app.buttons["Neues Profil"].tap()
        let name = app.textFields["Name"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        name.tap()
        name.typeText("Paxi")
        app.buttons["Sichern"].tap()
        XCTAssertTrue(app.staticTexts["Paxi"].waitForExistence(timeout: 5))

        // ESA/Paxi als echter YouTube-Kanal über das Eingabefeld freigeben.
        app.staticTexts["Paxi"].tap()
        XCTAssertTrue(app.buttons["whitelist.add"].waitForExistence(timeout: 3))
        app.buttons["whitelist.add"].tap()
        let url = app.textFields["https://www.youtube.com/…"]
        XCTAssertTrue(url.waitForExistence(timeout: 3))
        url.tap()
        url.typeText("https://www.youtube.com/channel/UCIBaDdAbGlFDeS33shmlD0A")
        app.buttons["Prüfen"].tap()
        // ADR 0003: Kanal in der Vorschau einstufen (Vorauswahl übernehmen), Knopf steht unter den Wahlen.
        let approve = app.buttons["kanal.hinzufuegen"]
        var n = 0
        while !(approve.exists && approve.isHittable) && n < 30 {
            if app.staticTexts["Nur einzeln geprüfte Videos"].exists || approve.exists { app.swipeUp() } else { sleep(1) }
            n += 1
        }
        XCTAssertTrue(approve.isHittable, "ESA-Kanal konnte im Simulator nicht aufgelöst werden")
        approve.tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'European Space Agency'")).firstMatch.waitForExistence(timeout: 5))
        sleep(3) // Cover aus dem lokalen/öffentlichen Thumbnail-Cache sichtbar werden lassen.
        app.segmentedControls.buttons["Kanäle"].tap()
        sleep(2)

        // Kindermodus öffnen und Paxi-Wiedergabe starten.
        app.navigationBars.buttons.element(boundBy: 0).tap()
        app.buttons["Sperren"].tap()
        XCTAssertTrue(app.navigationBars["Paxi"].waitForExistence(timeout: 5))
        sleep(2)
        app.tabBars.buttons["Alle Videos"].tap()
        XCTAssertTrue(app.navigationBars["Alle Videos"].waitForExistence(timeout: 3))
        app.segmentedControls.buttons["Videos"].tap()
        let paxi = app.buttons.containing(NSPredicate(format: "label CONTAINS 'Paxi on the ISS'")).firstMatch
        XCTAssertTrue(paxi.waitForExistence(timeout: 8))
        paxi.tap()
        XCTAssertTrue(app.buttons["player.close"].waitForExistence(timeout: 8))
        sleep(4)
        app.buttons["remote.handle"].firstMatch.tap()
        sleep(3)
    }
}
