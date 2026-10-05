// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import XCTest

/// Erzeugt Dokumentations-Screenshots (Home, Mediathek, Suche, Fernbedienung, Einstellungen).
/// Nur aktiv mit `TEST_RUNNER_SIDETUBE_SHOTS_DIR=<Ordner>`; Dateiname trägt den Gerätenamen.
final class ScreenshotUITests: XCTestCase {
    func testCaptureScreens() throws {
        guard let directory = ProcessInfo.processInfo.environment["SIDETUBE_SHOTS_DIR"], !directory.isEmpty else {
            throw XCTSkip("Set SIDETUBE_SHOTS_DIR explicitly to capture documentation screenshots")
        }
        let device = UIDevice.current.name.replacingOccurrences(of: " ", with: "-")
        let app = XCUIApplication()
        app.launchArguments += ["-sidetube.uiTestReset", "1", "-sidetube.devPIN", "1234", "-sidetube.devBedtimeOff", "1", "-sidetube.devRemoteWheel", "1",
                                "-sidetube.devSeedChannels", "UCLA_DiR1FfKNvjuUpBHmylQ,UCIBaDdAbGlFDeS33shmlD0A"]
        app.launch()
        func save(_ name: String) {
            let data = app.screenshot().pngRepresentation
            try? FileManager.default.createDirectory(atPath: directory, withIntermediateDirectories: true)
            try? data.write(to: URL(fileURLWithPath: directory).appendingPathComponent("\(name)_\(device).png"))
        }

        XCTAssertTrue(app.navigationBars["Beispiel"].waitForExistence(timeout: 10))
        // Kanäle brauchen einen Moment (Kanalseite ohne Key)
        _ = app.staticTexts["Die Maus"].waitForExistence(timeout: 20)
        save("home")

        app.tabBars.buttons["Alle Videos"].tap()
        XCTAssertTrue(app.navigationBars["Alle Videos"].waitForExistence(timeout: 3))
        sleep(1)
        save("uebersicht")
        app.segmentedControls.buttons["Videos"].tap()
        sleep(1)
        save("filme")

        // Playback showcase: only curated Big Buck Bunny / NASA / ESA-Paxi content.
        let paxi = app.buttons.containing(NSPredicate(format: "label CONTAINS 'Paxi on the ISS'")).firstMatch
        if paxi.waitForExistence(timeout: 3) {
            paxi.tap()
            if app.buttons["player.close"].waitForExistence(timeout: 5) {
                sleep(2)
                save("paxi-wiedergabe")
                app.buttons["player.close"].firstMatch.tap()
            }
        }

        app.tabBars.buttons["Suche"].tap()
        XCTAssertTrue(app.navigationBars["Suche"].waitForExistence(timeout: 3))
        sleep(1)
        save("suche")

        app.tabBars.buttons["Start"].tap()
        app.buttons.matching(identifier: "remote.handle").allElementsBoundByIndex.first(where: \.isHittable)?.tap()
        XCTAssertTrue(app.otherElements["Scrollrad"].waitForExistence(timeout: 3))
        sleep(1)
        save("fernbedienung")
        app.buttons["remote.close"].tap()

        app.buttons.matching(identifier: "parent.lock").allElementsBoundByIndex.first(where: \.isHittable)?.tap()
        XCTAssertTrue(app.staticTexts["PIN eingeben"].waitForExistence(timeout: 3))
        for digit in "1234" { app.buttons[String(digit)].firstMatch.tap() }
        XCTAssertTrue(app.navigationBars["Einstellungen"].waitForExistence(timeout: 5))
        sleep(1)
        save("elternbereich")

        // Profile management showcase: open an existing profile, remove one approved item,
        // then create a second child profile. Everything is local to the simulator.
        let existingProfile = app.staticTexts["Beispiel"].firstMatch
        if existingProfile.waitForExistence(timeout: 3) {
            existingProfile.tap()
            if app.buttons["whitelist.add"].waitForExistence(timeout: 3) {
                sleep(1)
                save("freigaben")
                let bunny = app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Big Buck Bunny'")).firstMatch
                if bunny.exists {
                    bunny.swipeLeft()
                    if app.buttons["Entfernen"].waitForExistence(timeout: 2) {
                        app.buttons["Entfernen"].tap()
                        sleep(1)
                        save("freigabe-geloescht")
                    }
                }
            }
            app.navigationBars.buttons.element(boundBy: 0).tap()
        }

        let createProfile = app.buttons["Neues Profil"].firstMatch.exists
            ? app.buttons["Neues Profil"].firstMatch
            : app.buttons["Profil anlegen"].firstMatch
        if createProfile.waitForExistence(timeout: 3) {
            createProfile.tap()
            let name = app.textFields["Name"]
            if name.waitForExistence(timeout: 3) {
                name.tap()
                name.typeText("Paxi")
                app.buttons["Sichern"].tap()
                _ = app.staticTexts["Paxi"].waitForExistence(timeout: 3)
                save("profil-paxi")
            }
        }
    }
}
