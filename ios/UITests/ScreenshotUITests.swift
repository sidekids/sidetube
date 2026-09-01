import XCTest

/// Erzeugt Dokumentations-Screenshots (Home, Mediathek, Suche, Fernbedienung, Elternbereich).
/// Nur aktiv mit `TEST_RUNNER_SIDETUBE_SHOTS_DIR=<Ordner>`; Dateiname trägt den Gerätenamen.
final class ScreenshotUITests: XCTestCase {
    func testCaptureScreens() throws {
        guard let directory = ProcessInfo.processInfo.environment["SIDETUBE_SHOTS_DIR"], !directory.isEmpty else {
            throw XCTSkip("SIDETUBE_SHOTS_DIR nicht gesetzt")
        }
        let device = UIDevice.current.name.replacingOccurrences(of: " ", with: "-")
        let app = XCUIApplication()
        app.launchArguments += ["-sidetube.uiTestReset", "1", "-sidetube.devPIN", "1234", "-sidetube.devBedtimeOff", "1",
                                "-sidetube.devSeedChannels", "UCRWSxXBnz9IRS4SgRhG2wpQ,UCxFvLj7FDoMChztQTSRDAbw,UCkYs7CD2fGjsrHyBafws02w"]
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

        app.tabBars.buttons["Mediathek"].tap()
        XCTAssertTrue(app.navigationBars["Mediathek"].waitForExistence(timeout: 3))
        sleep(1)
        save("mediathek")

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
        XCTAssertTrue(app.staticTexts["Eltern-PIN eingeben"].waitForExistence(timeout: 3))
        for digit in "1234" { app.buttons[String(digit)].firstMatch.tap() }
        XCTAssertTrue(app.navigationBars["Elternbereich"].waitForExistence(timeout: 5))
        sleep(1)
        save("elternbereich")
    }
}
