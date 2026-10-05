// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import XCTest

/// Wünsche von Kindern (ADR 0001), Prüfung nach den Video-Szenen T14–T17: Der freigegebene Wunsch führt
/// unter „Meine Wünsche" zum Inhalt, und weitere Wünsche sind heute gesperrt (Tagesgrenze). Setzt nichts zurück.
final class WuenscheUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-sidetube.devBedtimeOff", "1"]
        app.launch()
    }

    func testFreigegebenerWunschFuehrtZumVideo() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        app.buttons["wishes.open"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Meine Wünsche"].waitForExistence(timeout: 5))
        let anschauen = app.buttons["wish.open"].firstMatch
        guard anschauen.waitForExistence(timeout: 3) else { throw XCTSkip("Kein erfüllter Wunsch – erst T14–T17 laufen lassen") }
        XCTAssertTrue(app.staticTexts["Heute geht kein Wunsch mehr."].exists)
        anschauen.tap()
        let schliessen = app.buttons["player.close"]
        XCTAssertTrue(schliessen.waitForExistence(timeout: 10), "Player öffnet nicht")
        sleep(3)
        schliessen.tap()

        // Tagesgrenze: die Suche bietet den Wunsch an, „Wünschen" ist aber gesperrt.
        app.tabBars.buttons["Suche"].tap()
        let feld = app.textFields["search.field"]
        XCTAssertTrue(feld.waitForExistence(timeout: 5))
        feld.tap()
        feld.typeText("Haie")
        app.buttons["search.done"].tap()
        let wuenschen = app.buttons["wish.topic.submit"]
        XCTAssertTrue(wuenschen.waitForExistence(timeout: 5))
        XCTAssertFalse(wuenschen.isEnabled)
    }
}
