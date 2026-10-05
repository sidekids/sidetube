// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import XCTest

/// Demo-Stand für das Vorstellungsvideo: Profil „Kind" mit den Kanälen NASA und ESA und frei
/// lizenzierten Filmen der Blender Foundation (CC BY). Drei Filme freigegeben, zwei in der Prüfliste
/// (Szene T4). ESA als „Vertrauenswürdige Kinderquelle", NASA als „Vertrauenswürdige Reihe" (T16).
/// Setzt alles zurück.
final class VideoEinrichtungUITests: XCTestCase {
    private var app: XCUIApplication!
    private let freigeben = ["u9lj-c29dxI", "WhWc3b3KhnY", "YE7VzlLtp-4"]
    /// Kanäle, auf Christians Wunsch (01.10.2026): NASA und ESA. Kanalbilder sind deren Logos –
    /// Restrisiko bewusst getragen, im Abspann genannt.
    private let kanaele = ["UCLA_DiR1FfKNvjuUpBHmylQ", "UCIBaDdAbGlFDeS33shmlD0A"]
    private let pruefen = ["SkVqJ1SGeL0", "JOhiWY7XmoY"]
    /// Sendung „Blender Open Movies" (Blender Studio, CC BY); darin für Kinder Ungeeignetes einzeln abgelehnt.
    private let sendung = "PLav47HAVZMjnTFVZL-aImCQIC0uLZtNCz"
    private let ablehnen = [("UXqq0ZvbOnk", "CHARGE"), ("_cMxraX_5RE", "Sprite Fright"), ("mN0zPOpADL4", "Agent 327"),
                            ("R6MlUcmOul8", "Tears of Steel"), ("Y-rmzh0PI3c", "Cosmos Laundromat")]
    /// Zweites Profil für den Profilwechsel (T13).
    private let zweitesProfil = "Ole"

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-sidetube.uiTestReset", "1", "-sidetube.devBedtimeOff", "1"]
        app.launch()
    }

    private func enterPIN(_ pin: String) {
        for digit in pin { app.buttons[String(digit)].firstMatch.tap() }
    }

    private func fuegeHinzu(_ id: String, sofort: Bool) {
        XCTAssertTrue(app.buttons["whitelist.add"].waitForExistence(timeout: 5))
        app.buttons["whitelist.add"].tap()
        let url = app.textFields["https://www.youtube.com/…"]
        XCTAssertTrue(url.waitForExistence(timeout: 3))
        url.tap()
        url.typeText("https://www.youtube.com/watch?v=\(id)")
        app.buttons["Prüfen"].tap()
        let knopf = app.buttons[sofort ? "Jetzt freigeben" : "Erst zur Prüfung merken"]
        XCTAssertTrue(knopf.waitForExistence(timeout: 25), "Video \(id) nicht aufgelöst")
        knopf.tap()
        sleep(2)
    }

    func testDemoStandEinrichten() throws {
        XCTAssertTrue(app.staticTexts["PIN festlegen"].waitForExistence(timeout: 8))
        enterPIN("1234")
        XCTAssertTrue(app.staticTexts["PIN wiederholen"].waitForExistence(timeout: 3))
        enterPIN("1234")
        XCTAssertTrue(app.navigationBars["Einstellungen"].waitForExistence(timeout: 8))

        app.buttons["Profil anlegen"].firstMatch.tap()
        let name = app.textFields["Name"]
        XCTAssertTrue(name.waitForExistence(timeout: 3))
        name.tap()
        name.typeText("Kind")
        app.buttons["Sichern"].tap()
        XCTAssertTrue(app.staticTexts["Kind"].waitForExistence(timeout: 3))
        app.staticTexts["Kind"].tap()
        XCTAssertTrue(app.navigationBars["Kind"].waitForExistence(timeout: 5))

        // Ein neues Profil bekommt Willkommensvorschläge (NASA, ESA, Blender) in die Prüfliste.
        // Deren Lizenzen sind nicht durchweg frei: verwerfen, bevor die eigenen Filme kommen.
        if app.buttons["Freigaben prüfen"].waitForExistence(timeout: 3) {
            app.buttons["Freigaben prüfen"].tap()
            let alle = app.buttons["review.discardAll"]
            if alle.waitForExistence(timeout: 3) {
                alle.tap()
                let bestaetigen = app.buttons.matching(NSPredicate(format: "label ENDSWITH 'Einträge verwerfen'")).firstMatch
                XCTAssertTrue(bestaetigen.waitForExistence(timeout: 3))
                bestaetigen.tap()
                sleep(1)
            }
            app.navigationBars.element(boundBy: 0).buttons.element(boundBy: 0).tap()
            XCTAssertTrue(app.navigationBars["Kind"].waitForExistence(timeout: 5))
        }

        for kanal in kanaele {
            XCTAssertTrue(app.buttons["whitelist.add"].waitForExistence(timeout: 5))
            app.buttons["whitelist.add"].tap()
            let url = app.textFields["https://www.youtube.com/…"]
            XCTAssertTrue(url.waitForExistence(timeout: 3))
            url.tap()
            url.typeText("https://www.youtube.com/channel/\(kanal)")
            app.buttons["Prüfen"].tap()
            // ADR 0003: Kanäle werden in der Vorschau eingestuft; der Knopf steht unter den Wahlen.
            let hinzu = app.buttons["kanal.hinzufuegen"]
            var n = 0
            while !(hinzu.exists && hinzu.isHittable) && n < 30 {
                if app.staticTexts["Nur einzeln geprüfte Videos"].exists || hinzu.exists { app.swipeUp() } else { sleep(1) }
                n += 1
            }
            XCTAssertTrue(hinzu.isHittable, "Kanal \(kanal) nicht aufgelöst")
            hinzu.tap()
            sleep(2)
        }
        for id in pruefen { fuegeHinzu(id, sofort: false) }
        for id in freigeben { fuegeHinzu(id, sofort: true) }
        // Ungeeignetes zur Prüfung aufnehmen und dort ablehnen – die Elternentscheidung geht in der Sendung vor.
        for (id, _) in ablehnen { fuegeHinzu(id, sofort: false) }
        XCTAssertTrue(app.buttons["whitelist.add"].waitForExistence(timeout: 5))
        app.buttons["whitelist.add"].tap()
        let url = app.textFields["https://www.youtube.com/…"]
        XCTAssertTrue(url.waitForExistence(timeout: 3))
        url.tap()
        url.typeText("https://www.youtube.com/playlist?list=\(sendung)")
        app.buttons["Prüfen"].tap()
        let sendungFrei = app.buttons.matching(NSPredicate(format: "label IN {'Jetzt freigeben', 'Zur Whitelist hinzufügen'}")).firstMatch
        XCTAssertTrue(sendungFrei.waitForExistence(timeout: 25), "Sendung nicht aufgelöst")
        sendungFrei.tap()
        sleep(2)
        app.buttons["Freigaben prüfen"].tap()
        for (_, titel) in ablehnen {
            let zeile = app.cells.containing(NSPredicate(format: "label CONTAINS[c] %@", titel)).firstMatch
            var n = 0
            while !(zeile.exists && zeile.isHittable) && n < 8 { app.swipeUp(); n += 1 }
            XCTAssertTrue(zeile.exists, "\(titel) nicht in der Prüfliste")
            zeile.swipeLeft()
            let nein = app.buttons["Ablehnen"].firstMatch
            XCTAssertTrue(nein.waitForExistence(timeout: 3))
            nein.tap()
            sleep(1)
        }
        app.navigationBars.element(boundBy: 0).buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.navigationBars["Kind"].waitForExistence(timeout: 5))
        // ESA als „Vertrauenswürdige Kinderquelle": Nur diese Stufe erlaubt das Stöbern im Kanal (Szene T2).
        // NASA bleibt auf der mitgelieferten Stufe.
        app.navigationBars.element(boundBy: 0).buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.navigationBars["Einstellungen"].waitForExistence(timeout: 5))
        // „Quellen & Sicherheitsstufen" steht im Menü hinter dem „…"-Knopf der Titelleiste.
        let leiste = app.navigationBars["Einstellungen"]
        let menue = leiste.buttons.matching(NSPredicate(format: "label IN {'Weitere', 'Mehr', 'More'}")).firstMatch
        XCTAssertTrue(menue.waitForExistence(timeout: 5), "Menü nicht gefunden")
        menue.tap()
        let quellen = app.buttons["Quellen & Sicherheitsstufen"]
        XCTAssertTrue(quellen.waitForExistence(timeout: 5))
        quellen.tap()
        let esaZelle = app.cells.containing(NSPredicate(format: "label CONTAINS 'European Space Agency'")).firstMatch
        var versuche = 0
        while !(esaZelle.exists && esaZelle.isHittable) && versuche < 12 { app.swipeUp(); versuche += 1 }
        XCTAssertTrue(esaZelle.exists, "ESA nicht in den Quellen")
        esaZelle.buttons.firstMatch.tap()
        let stufe = app.buttons["Vertrauenswürdige Kinderquelle"]
        XCTAssertTrue(stufe.waitForExistence(timeout: 3))
        stufe.tap()
        sleep(1)
        // NASA ausdrücklich auf „Vertrauenswürdige Reihe": neue Folgen erscheinen gesperrt zum Wünschen (T16, ADR 0001).
        let nasaZelle = app.cells.containing(NSPredicate(format: "label BEGINSWITH 'NASA'")).firstMatch
        versuche = 0
        // Alphabetisch steht NASA hinter ESA: erst weiter nach unten, notfalls zurück nach oben.
        while !(nasaZelle.exists && nasaZelle.isHittable) && versuche < 16 { versuche < 8 ? app.swipeUp() : app.swipeDown(); versuche += 1 }
        XCTAssertTrue(nasaZelle.exists, "NASA nicht in den Quellen")
        nasaZelle.buttons.firstMatch.tap()
        let reihe = app.buttons["Vertrauenswürdige Reihe"]
        XCTAssertTrue(reihe.waitForExistence(timeout: 3))
        reihe.tap()
        sleep(1)
        XCTAssertTrue(nasaZelle.buttons["Vertrauenswürdige Reihe"].exists || nasaZelle.staticTexts["Vertrauenswürdige Reihe"].exists
                      || (nasaZelle.buttons.firstMatch.value as? String) == "Vertrauenswürdige Reihe"
                      || nasaZelle.buttons.firstMatch.label.contains("Vertrauenswürdige Reihe"),
                      "NASA nicht auf „Vertrauenswürdige Reihe“")
        // Zweites Profil anlegen (T13 Profilwechsel).
        app.navigationBars.element(boundBy: 0).buttons.element(boundBy: 0).tap()
        XCTAssertTrue(app.navigationBars["Einstellungen"].waitForExistence(timeout: 5))
        app.buttons["Neues Profil"].firstMatch.tap()
        let name2 = app.textFields["Name"]
        XCTAssertTrue(name2.waitForExistence(timeout: 3))
        name2.tap()
        name2.typeText(zweitesProfil)
        app.buttons["Sichern"].tap()
        XCTAssertTrue(app.staticTexts[zweitesProfil].waitForExistence(timeout: 5))
        print("VIDEO: \(freigeben.count) freigegeben, \(pruefen.count) zur Prüfung, ESA hochgestuft, NASA Reihe")
    }
}
