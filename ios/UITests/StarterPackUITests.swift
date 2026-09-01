import XCTest

/// Startpaket und Nachbearbeitung: Das Paket landet im geöffneten Profil, und ein freigegebener
/// Eintrag lässt sich später erneut einstufen.
final class StarterPackUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-sidetube.uiTestReset", "1", "-sidetube.devBedtimeOff", "1"]
        app.launch()
    }

    private func enterPIN(_ pin: String) {
        for digit in pin { app.buttons[String(digit)].firstMatch.tap() }
    }

    /// Anzahl aus dem Titel „Prüfen (n)“ – so bleibt der Test unabhängig vom Umfang des Startpakets.
    private func pendingCount(timeout: TimeInterval = 5) -> Int? {
        let bar = app.navigationBars.matching(NSPredicate(format: "identifier BEGINSWITH 'Prüfen ('")).firstMatch
        guard bar.waitForExistence(timeout: timeout) else { return nil }
        return Int(bar.identifier.dropFirst("Prüfen (".count).dropLast())
    }

    func testStarterPackLandsInTheOpenProfileAndStaysEditable() throws {
        XCTAssertTrue(app.staticTexts["Eltern-PIN festlegen"].waitForExistence(timeout: 5))
        enterPIN("1234")
        XCTAssertTrue(app.staticTexts["PIN wiederholen"].waitForExistence(timeout: 2))
        enterPIN("1234")
        XCTAssertTrue(app.navigationBars["Elternbereich"].waitForExistence(timeout: 5))

        // Zwei Profile: das Paket muss im zweiten landen, nicht im ersten.
        for name in ["Erstes Kind", "Mira"] {
            // Ohne Profil steht der Knopf im Leerzustand, danach nur noch in der Titelleiste.
            let add = app.buttons["Profil anlegen"].exists ? app.buttons["Profil anlegen"] : app.buttons["Neues Profil"]
            add.firstMatch.tap()
            let nameField = app.textFields["Name"]
            XCTAssertTrue(nameField.waitForExistence(timeout: 3))
            nameField.tap()
            nameField.typeText(name)
            app.buttons["Sichern"].tap()
            XCTAssertTrue(app.staticTexts[name].waitForExistence(timeout: 3))
        }

        app.staticTexts["Mira"].tap()
        XCTAssertTrue(app.navigationBars["Mira"].waitForExistence(timeout: 3))

        // Startpaket aus dem Profil heraus laden
        app.buttons["profile.menu"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Startpaket laden (zur Prüfung)"].waitForExistence(timeout: 3))
        app.buttons["Startpaket laden (zur Prüfung)"].tap()
        XCTAssertTrue(app.buttons["Startpaket 9–11 Jahre"].waitForExistence(timeout: 3))
        app.buttons["Startpaket 9–11 Jahre"].tap()

        let alert = app.alerts["Startpaket"]
        XCTAssertTrue(alert.waitForExistence(timeout: 5), "Rückmeldung zum Import fehlt")
        let text = alert.staticTexts.element(boundBy: 1).label
        XCTAssertTrue(text.contains("für Mira"), "Import ging ins falsche Profil: \(text)")
        XCTAssertTrue(text.contains("Kandidaten zur Prüfung"), "Rückmeldung ohne Anzahl: \(text)")
        alert.buttons["OK"].tap()

        // Prüfliste: freigeben
        app.buttons["Freigaben prüfen"].tap()
        let pending = try XCTUnwrap(pendingCount(), "Prüfliste zeigt keine Anzahl")
        XCTAssertGreaterThan(pending, 0, "Startpaket hat nichts zur Prüfung gebracht")
        app.buttons["review.row"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Prüfen"].waitForExistence(timeout: 3))
        app.buttons["Freigeben"].tap()
        XCTAssertEqual(pendingCount(), pending - 1, "Freigabe nimmt den Eintrag aus der Prüfliste")

        // Zurück in die Whitelist: freigegebener Eintrag bleibt bearbeitbar
        app.navigationBars.element(boundBy: 0).buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.navigationBars["Mira"].waitForExistence(timeout: 3))
        app.buttons["whitelist.row"].firstMatch.tap()
        XCTAssertTrue(app.navigationBars["Bearbeiten"].waitForExistence(timeout: 3), "Antippen öffnet keine Bearbeitung")
        XCTAssertTrue(app.buttons["Änderungen sichern"].exists)
        XCTAssertTrue(app.buttons["Zurück zur Prüfung"].exists)
        app.buttons["Zurück zur Prüfung"].tap()

        // Der Eintrag ist wieder in der Prüfung
        XCTAssertTrue(app.navigationBars["Mira"].waitForExistence(timeout: 3))
        app.buttons["Freigaben prüfen"].tap()
        XCTAssertEqual(pendingCount(), pending, "Zurückgestellter Eintrag steht wieder in der Prüfliste")

        // Sammelaktion: offene Kandidaten in einem Zug verwerfen
        app.buttons["review.discardAll"].tap()
        let discard = app.buttons.matching(NSPredicate(format: "label ENDSWITH %@", "Einträge verwerfen")).firstMatch
        XCTAssertTrue(discard.waitForExistence(timeout: 3))
        discard.tap()
        XCTAssertEqual(pendingCount(), 0, "nach dem Verwerfen ist die Prüfliste leer")
        XCTAssertTrue(app.staticTexts["Nichts zu prüfen"].exists)
    }
}
