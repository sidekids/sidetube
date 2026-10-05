// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import UIKit
import XCTest

/// ADR 0005: Elternkanal einrichten und „Test senden" – gegen eine echte Nextcloud. Der
/// Einrichtungscode kommt nur über die Umgebung (`TEST_RUNNER_ELTERNKANAL_CODE`), nie aus dem Quelltext;
/// ohne ihn wird der Test übersprungen.
final class ElternkanalUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-sidetube.uiTestReset", "1", "-sidetube.devBedtimeOff", "1"]
    }

    func testEinrichtenTestSendenEntfernen() throws {
        guard let code = ProcessInfo.processInfo.environment["ELTERNKANAL_CODE"], !code.isEmpty else {
            throw XCTSkip("Kein Einrichtungscode in der Umgebung")
        }
        app.launch()
        XCTAssertTrue(app.staticTexts["PIN festlegen"].waitForExistence(timeout: 8))
        pin("1234")
        XCTAssertTrue(app.staticTexts["PIN wiederholen"].waitForExistence(timeout: 3))
        pin("1234")
        XCTAssertTrue(app.navigationBars["Einstellungen"].waitForExistence(timeout: 8))

        app.navigationBars["Einstellungen"].buttons.element(boundBy: app.navigationBars["Einstellungen"].buttons.count - 1).tap()
        XCTAssertTrue(app.buttons["Eltern benachrichtigen"].waitForExistence(timeout: 3))
        app.buttons["Eltern benachrichtigen"].tap()

        UIPasteboard.general.string = code
        let einfuegen = app.buttons["elternkanal.einfuegen"]
        XCTAssertTrue(einfuegen.waitForExistence(timeout: 5))
        einfuegen.tap()
        UIPasteboard.general.string = ""
        XCTAssertTrue(app.staticTexts["Eingerichtet"].waitForExistence(timeout: 5) || app.staticTexts["EINGERICHTET"].exists)
        anhaengen("eingerichtet")

        app.buttons["Test senden"].tap()
        let meldung = app.staticTexts["elternkanal.meldung"]
        let gesendet = NSPredicate(format: "label BEGINSWITH 'Gesendet'")
        expectation(for: gesendet, evaluatedWith: meldung)
        waitForExpectations(timeout: 25)
        anhaengen("gesendet")

        app.buttons["Entfernen"].firstMatch.tap()
        // Der Bestätigungsdialog bringt einen zweiten „Entfernen"-Knopf; den sichtbaren treffen.
        let titel = app.staticTexts["Benachrichtigung entfernen?"]
        XCTAssertTrue(titel.waitForExistence(timeout: 3))
        let bestaetigen = app.buttons.matching(identifier: "Entfernen").allElementsBoundByIndex.last { $0.isHittable }
        try XCTUnwrap(bestaetigen, "Bestätigung nicht sichtbar").tap()
        XCTAssertTrue(app.buttons["Einrichtungscode scannen"].waitForExistence(timeout: 5))
    }

    private func pin(_ code: String) {
        for ziffer in code { app.buttons[String(ziffer)].firstMatch.tap() }
    }

    private func anhaengen(_ name: String) {
        let anhang = XCTAttachment(screenshot: app.screenshot())
        anhang.name = name
        anhang.lifetime = .keepAlways
        add(anhang)
    }
}
