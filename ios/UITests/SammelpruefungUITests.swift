// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import XCTest

/// ADR 0004: mehrere Einträge auf einmal prüfen. Das allgemeine Startpaket bringt netzfrei genug Kandidaten
/// mit, darunter solche mit Risikotreffer – so zeigt sich auch der Hinweis auf die Einzelprüfung.
final class SammelpruefungUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-sidetube.uiTestReset", "1", "-sidetube.devBedtimeOff", "1"]
        app.launch()
    }

    func testMehrereFreigebenUndAblehnen() throws {
        XCTAssertTrue(app.staticTexts["PIN festlegen"].waitForExistence(timeout: 8))
        pin("1234")
        XCTAssertTrue(app.staticTexts["PIN wiederholen"].waitForExistence(timeout: 3))
        pin("1234")
        XCTAssertTrue(app.navigationBars["Einstellungen"].waitForExistence(timeout: 8))
        let neu = app.buttons["Neues Profil"].exists ? app.buttons["Neues Profil"] : app.buttons["Profil anlegen"]
        neu.firstMatch.tap()
        let name = app.textFields["Name"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        name.tap(); name.typeText("Mia")
        app.buttons["Sichern"].tap()
        XCTAssertTrue(app.staticTexts["Mia"].waitForExistence(timeout: 5))
        app.staticTexts["Mia"].tap()

        app.buttons["profile.menu"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Startpaket laden (zur Prüfung)"].waitForExistence(timeout: 3))
        app.buttons["Startpaket laden (zur Prüfung)"].tap()
        // Datei ohne eigenen Titel – das Menü nennt sie beim Dateinamen.
        XCTAssertTrue(app.buttons["general"].waitForExistence(timeout: 3))
        app.buttons["general"].tap()
        let alert = app.alerts["Startpaket"]
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        alert.buttons["OK"].tap()

        app.buttons["Freigaben prüfen"].tap()
        let vorher = try XCTUnwrap(offen(), "Prüfliste zeigt keine Anzahl")
        XCTAssertGreaterThan(vorher, 2)

        // Auswahlmodus: alle auswählen, eins wieder abwählen.
        app.buttons["review.auswaehlen"].tap()
        let alle = app.buttons["review.alleAuswaehlen"]
        XCTAssertTrue(alle.waitForExistence(timeout: 3))
        alle.tap()
        let freigeben = app.buttons["review.sammelFreigeben"]
        XCTAssertEqual(freigeben.label, "Freigeben (\(vorher))")
        app.buttons["review.row"].firstMatch.tap()
        XCTAssertEqual(freigeben.label, "Freigeben (\(vorher - 1))")
        XCTAssertEqual(app.buttons["review.sammelAblehnen"].label, "Ablehnen (\(vorher - 1))")
        anhaengen("sammel-auswahl")

        // Sammel-Maske: Hinweis auf Einzelprüfungen, Alter setzen, freigeben.
        freigeben.tap()
        XCTAssertTrue(app.navigationBars["Gemeinsam freigeben"].waitForExistence(timeout: 3))
        let anzahl = app.staticTexts["sammel.anzahl"].label
        let sammelbar = try XCTUnwrap(Int(anzahl.split(separator: " ").first ?? ""), anzahl)
        XCTAssertGreaterThan(sammelbar, 0)
        let einzeln = (vorher - 1) - sammelbar
        XCTAssertGreaterThan(einzeln, 0, "Das Paket enthält Risikotreffer – die dürfen nicht mitlaufen")
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "\(einzeln) brauchen eine Einzelprüfung")).firstMatch.exists
                      || app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'braucht eine Einzelprüfung'")).firstMatch.exists,
                      "Hinweis auf nicht sammelbare Einträge fehlt")
        sleep(1)
        anhaengen("sammel-maske")
        let wieVorgeschlagen = sichtbar(app.switches["sammel.alterWieVorgeschlagen"])
        wieVorgeschlagen.switches.firstMatch.tap()
        XCTAssertTrue(app.otherElements["sammel.alter"].exists || app.steppers["sammel.alter"].exists || app.staticTexts["Ab 6 Jahren"].exists)
        sleep(1)
        anhaengen("sammel-maske-alter")
        sichtbar(app.buttons["sammel.freigeben"]).tap()

        XCTAssertEqual(offen(), vorher - sammelbar, "Nur die sammelbaren verlassen die Prüfliste")
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'freigegeben'")).firstMatch.waitForExistence(timeout: 3))
        anhaengen("sammel-nachher")

        // Sammelablehnung mit Bestätigung: alles, was noch offen ist – Nicht-Sammelbares bleibt auch hier stehen.
        app.buttons["review.auswaehlen"].tap()
        app.buttons["review.alleAuswaehlen"].tap()
        let rest = vorher - sammelbar
        XCTAssertEqual(app.buttons["review.sammelAblehnen"].label, "Ablehnen (\(rest))")
        app.buttons["review.sammelAblehnen"].tap()
        let bestaetigen = app.buttons["review.sammelAblehnenBestaetigen"].firstMatch
        XCTAssertTrue(bestaetigen.waitForExistence(timeout: 3), "Ablehnen ohne Rückfrage")
        let abgelehnt = try XCTUnwrap(Int(bestaetigen.label.split(separator: " ").first ?? ""), bestaetigen.label)
        sleep(1)
        anhaengen("sammel-ablehnen")
        bestaetigen.tap()
        XCTAssertEqual(offen(), rest - abgelehnt)
        XCTAssertGreaterThanOrEqual(rest - abgelehnt, einzeln, "Risikotreffer dürfen auch nicht gesammelt abgelehnt werden")
    }

    /// Anzahl aus dem Titel „Prüfen (n)" (wie `StarterPackUITests`).
    private func offen(timeout: TimeInterval = 5) -> Int? {
        let bar = app.navigationBars.matching(NSPredicate(format: "identifier BEGINSWITH 'Prüfen ('")).firstMatch
        guard bar.waitForExistence(timeout: timeout) else { return nil }
        return Int(bar.identifier.dropFirst("Prüfen (".count).dropLast())
    }

    private func pin(_ code: String) {
        for ziffer in code { app.buttons[String(ziffer)].firstMatch.tap() }
    }

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
