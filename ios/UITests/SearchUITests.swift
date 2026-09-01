// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import XCTest

/// Sichert die Bedienbarkeit der Suche: Eingabefeld nimmt Text an, Tastatur erscheint,
/// und die Fernbedienung bleibt erreichbar (Trefferlisten werden auch mit dem Rad durchgeblättert).
final class SearchUITests: XCTestCase {
    func testSearchFieldOpensKeyboardAndKeepsRemote() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-sidetube.uiTestReset", "1", "-sidetube.devPIN", "1234", "-sidetube.devBedtimeOff", "1"]
        app.launch()

        XCTAssertTrue(app.navigationBars["Start"].waitForExistence(timeout: 10))
        app.tabBars.buttons["Suche"].tap()

        let field = app.textFields["search.field"]
        XCTAssertTrue(field.waitForExistence(timeout: 5), "Suchfeld ist nicht sichtbar")
        field.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5), "Tastatur oeffnet nicht")
        field.typeText("maus")
        XCTAssertEqual(field.value as? String, "maus")

        // Ausweg: "Fertig" schliesst die Tastatur, danach ist die Tab-Leiste wieder erreichbar.
        app.buttons["search.done"].tap()
        XCTAssertFalse(app.keyboards.firstMatch.waitForExistence(timeout: 2), "Tastatur bleibt stehen")
        app.tabBars.buttons["Start"].tap()
        XCTAssertTrue(app.navigationBars["Start"].waitForExistence(timeout: 3), "Suche laesst sich nicht verlassen")

        // Das Scrollrad wird auch hier gebraucht, um durch die Treffer zu navigieren.
        app.tabBars.buttons["Suche"].tap()
        let handle = app.buttons.matching(identifier: "remote.handle").allElementsBoundByIndex.first(where: \.exists)
        XCTAssertNotNil(handle, "Griff der Fernbedienung fehlt auf der Suchseite")
    }
}
