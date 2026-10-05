// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import XCTest

/// Belegbilder für die SideUI-Konformität (docs/design/sideui.md): Akzentfarbe, Fokus bei offenem Rad,
/// Player-Ring, Elternschalter. Setzt den Stand aus `VideoEinrichtungUITests` voraus und ändert nichts
/// Dauerhaftes: Das Rad wird nur für diesen Lauf per DEBUG-Argument eingeschaltet.
/// Zielordner: `TEST_RUNNER_SIDEUI_BELEGE=/pfad xcodebuild test …`; ohne ihn landen die Bilder im Testergebnis.
final class SideUIBelegeUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-sidetube.devBedtimeOff", "1", "-sidetube.devRemoteWheel", "1"]
        app.launch()
    }

    private func beleg(_ name: String) {
        let shot = app.screenshot()
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        if let dir = ProcessInfo.processInfo.environment["SIDEUI_BELEGE"] {
            try? FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
            try? shot.pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
        }
    }

    private var handle: XCUIElement {
        app.buttons.matching(identifier: "remote.handle").allElementsBoundByIndex.first(where: \.isHittable)
            ?? app.buttons["remote.handle"].firstMatch
    }

    func testBelege() throws {
        XCTAssertTrue(app.staticTexts["Kanäle"].waitForExistence(timeout: 10))
        sleep(2)
        beleg("sidetube_start")

        // Rad offen: Fokus als eigene Fläche mit Rahmen; rechts = nächstes Objekt in der Reihe.
        handle.tap()
        let wheel = app.otherElements["Scrollrad"]
        XCTAssertTrue(wheel.waitForExistence(timeout: 3))
        wheel.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.86)).tap()   // runter: Reihe Kanäle
        wheel.coordinate(withNormalizedOffset: CGVector(dx: 0.86, dy: 0.5)).tap()   // rechts: nächster Kanal
        sleep(1)
        beleg("sidetube_fokus_rad_offen")
        app.buttons["remote.close"].tap()
        sleep(1)

        // Player mit offenem Rad: Ring zeigt 10-s-Spulen oben/unten und Video wechseln links/rechts.
        app.tabBars.buttons["Alle Videos"].tap()
        if app.segmentedControls.buttons["Videos"].waitForExistence(timeout: 3) { app.segmentedControls.buttons["Videos"].tap() }
        let tile = app.scrollViews.buttons.firstMatch
        XCTAssertTrue(tile.waitForExistence(timeout: 5))
        tile.tap()
        XCTAssertTrue(app.buttons["player.close"].waitForExistence(timeout: 8))
        app.buttons["remote.toolbar"].firstMatch.tap()
        XCTAssertTrue(wheel.waitForExistence(timeout: 3))
        sleep(2)
        beleg("sidetube_player_ring")
        app.buttons["remote.close"].tap()
        sleep(1)
        app.buttons["player.close"].tap()
        sleep(1)

        // Elternschalter im Profil (ab Werk aus).
        app.buttons.matching(identifier: "parent.lock").allElementsBoundByIndex.first(where: \.isHittable)?.tap()
        XCTAssertTrue(app.staticTexts["PIN eingeben"].waitForExistence(timeout: 3))
        for digit in "1234" { app.buttons[String(digit)].firstMatch.tap() }
        XCTAssertTrue(app.navigationBars["Einstellungen"].waitForExistence(timeout: 5))
        app.staticTexts["Kind"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Kind"].waitForExistence(timeout: 5))
        if !app.buttons["Profil bearbeiten"].exists {
            app.navigationBars["Kind"].buttons.element(boundBy: app.navigationBars["Kind"].buttons.count - 1).tap()
        }
        XCTAssertTrue(app.buttons["Profil bearbeiten"].waitForExistence(timeout: 3))
        app.buttons["Profil bearbeiten"].tap()
        XCTAssertTrue(app.switches["profile.remoteWheel"].waitForExistence(timeout: 3))
        XCTAssertEqual(app.switches["profile.remoteWheel"].value as? String, "0", "Rad ist im Demo-Profil aus")
        sleep(1)
        beleg("sidetube_elternschalter")
        app.buttons["Abbrechen"].tap()
    }
}
