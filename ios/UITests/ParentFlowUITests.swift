import XCTest

/// Durchklick des Eltern-Ablaufs (Fertig-Kriterium Phase 3): PIN anlegen → Elternbereich per PIN →
/// Profil anlegen → Video-Link (oEmbed, kein API-Key nötig) whitelisten → Eintrag entfernen.
final class ParentFlowUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-sidetube.uiTestReset", "1", "-sidetube.devBedtimeOff", "1"]
        app.launch()
    }

    /// Das Remote-Handle existiert je Tab (und im Player) – das sichtbare, antippbare nehmen.
    private var remoteHandle: XCUIElement {
        app.buttons.matching(identifier: "remote.handle").allElementsBoundByIndex.first(where: \.isHittable)
            ?? app.buttons["remote.handle"].firstMatch
    }

    private func enterPIN(_ pin: String) {
        for digit in pin { app.buttons[String(digit)].firstMatch.tap() }
    }

    func testCreateProfileWhitelistVideoAndRemove() throws {
        // PIN-Einrichtung (zweimal eingeben)
        XCTAssertTrue(app.staticTexts["Eltern-PIN festlegen"].waitForExistence(timeout: 5))
        enterPIN("1234")
        XCTAssertTrue(app.staticTexts["PIN wiederholen"].waitForExistence(timeout: 2))
        enterPIN("1234")

        // Nach der Einrichtung direkt im Elternbereich
        XCTAssertTrue(app.navigationBars["Elternbereich"].waitForExistence(timeout: 5))

        // Sperren → Kindermodus mit Rad → Schloss → PIN
        app.buttons["Sperren"].tap()
        XCTAssertTrue(app.buttons["remote.handle"].firstMatch.waitForExistence(timeout: 3))
        app.buttons.matching(identifier: "parent.lock").allElementsBoundByIndex.first(where: \.isHittable)?.tap()
        XCTAssertTrue(app.staticTexts["Eltern-PIN eingeben"].waitForExistence(timeout: 3))
        enterPIN("9999")
        XCTAssertTrue(app.staticTexts["Falsche PIN. Noch 4 Versuche."].waitForExistence(timeout: 2))
        enterPIN("1234")
        XCTAssertTrue(app.navigationBars["Elternbereich"].waitForExistence(timeout: 3))

        // Profil anlegen
        app.buttons["Profil anlegen"].tap()
        let nameField = app.textFields["Name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 3))
        nameField.tap()
        nameField.typeText("Mia")
        app.buttons["Sichern"].tap()
        XCTAssertTrue(app.staticTexts["Mia"].waitForExistence(timeout: 3))

        // Whitelist öffnen, Link prüfen (oEmbed live), hinzufügen
        app.staticTexts["Mia"].tap()
        XCTAssertTrue(app.buttons["Link hinzufügen"].waitForExistence(timeout: 3))
        app.buttons["Link hinzufügen"].tap()
        let urlField = app.textFields["https://www.youtube.com/…"]
        XCTAssertTrue(urlField.waitForExistence(timeout: 3))
        urlField.tap()
        urlField.typeText("https://youtu.be/dQw4w9WgXcQ")
        app.buttons["Prüfen"].tap()
        let addButton = app.buttons["Jetzt freigeben"]
        XCTAssertTrue(addButton.waitForExistence(timeout: 20), "oEmbed-Auflösung (Netz) fehlgeschlagen")
        addButton.tap()
        let row = app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Rick Astley'")).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 5))

        // Dublette wird vor dem Netzaufruf erkannt
        app.buttons["Hinzufügen"].tap()
        let urlField2 = app.textFields["https://www.youtube.com/…"]
        XCTAssertTrue(urlField2.waitForExistence(timeout: 3))
        urlField2.tap()
        urlField2.typeText("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        app.buttons["Prüfen"].tap()
        XCTAssertTrue(app.staticTexts["Steht schon auf der Whitelist von Mia."].waitForExistence(timeout: 3))
        app.buttons["Abbrechen"].tap()

        // Entfernen per Wischen
        row.swipeLeft()
        app.buttons["Entfernen"].tap()
        XCTAssertTrue(app.staticTexts["Whitelist ist leer"].waitForExistence(timeout: 3))
    }

    /// Kindermodus: Home ist ruhig (keine Fernbedienung sichtbar), Mediathek → Videos → Video öffnet den Player,
    /// Fernbedienung als Sheet: Wheel-Mitte = Play/Pause, Zurück schließt den Player, Home führt zum Home-Tab.
    func testKidModeTabsPlayerAndRemoteSheet() throws {
        XCTAssertTrue(app.staticTexts["Eltern-PIN festlegen"].waitForExistence(timeout: 5))
        enterPIN("1234"); enterPIN("1234")
        XCTAssertTrue(app.navigationBars["Elternbereich"].waitForExistence(timeout: 5))
        app.buttons["Profil anlegen"].tap()
        let nameField = app.textFields["Name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 3))
        nameField.tap(); nameField.typeText("Tom")
        app.buttons["Sichern"].tap()
        app.staticTexts["Tom"].tap()
        app.buttons["Link hinzufügen"].tap()
        let urlField = app.textFields["https://www.youtube.com/…"]
        XCTAssertTrue(urlField.waitForExistence(timeout: 3))
        urlField.tap(); urlField.typeText("https://youtu.be/dQw4w9WgXcQ")
        app.buttons["Prüfen"].tap()
        let addButton = app.buttons["Jetzt freigeben"]
        XCTAssertTrue(addButton.waitForExistence(timeout: 20))
        addButton.tap()
        app.navigationBars.buttons.element(boundBy: 0).tap()   // zurück zum Dashboard
        app.buttons["Sperren"].tap()

        // Home: ruhig – Tabs, Remote-Handle, kein Wheel
        XCTAssertTrue(app.navigationBars["Tom"].waitForExistence(timeout: 5))
        XCTAssertTrue(remoteHandle.exists)
        XCTAssertFalse(app.otherElements["Scrollrad"].exists, "Click-Wheel darf Home nicht dominieren")
        XCTAssertTrue(app.staticTexts["Noch keine Kanäle"].exists)
        XCTAssertTrue(app.staticTexts["Noch nichts geschaut"].exists)
        XCTAssertTrue(app.buttons["parent.lock"].firstMatch.exists)

        // Mediathek → Videos → Video antippen → Player
        app.tabBars.buttons["Mediathek"].tap()
        XCTAssertTrue(app.navigationBars["Mediathek"].waitForExistence(timeout: 3))
        app.segmentedControls.buttons["Videos"].tap()
        let tile = app.buttons.containing(NSPredicate(format: "label CONTAINS 'Rick Astley'")).firstMatch
        XCTAssertTrue(tile.waitForExistence(timeout: 3))
        tile.tap()
        XCTAssertTrue(app.staticTexts["1 von 1"].waitForExistence(timeout: 5))
#if targetEnvironment(simulator)
        // Der Simulator spielt YouTube nicht ab (bleibt im Puffern) — die Wiedergabe wird auf dem Geraet geprueft.
        _ = app.staticTexts["Spielt"].waitForExistence(timeout: 5)
#else
        XCTAssertTrue(app.staticTexts["Spielt"].waitForExistence(timeout: 30), "Player hat nicht gestartet (IFrame-API/Bridge)")
#endif
        XCTAssertTrue(app.staticTexts["Als Nächstes"].exists)

        // Kids-Kategorie: Empfehlen (verlässt die App) verlangt im Kindermodus die Eltern-PIN
        app.buttons["recommend.menu"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Link kopieren"].waitForExistence(timeout: 3))
        app.buttons["Link kopieren"].tap()
        XCTAssertTrue(app.staticTexts["Eltern-PIN eingeben"].waitForExistence(timeout: 3), "Parental Gate fehlt")
        app.buttons["Abbrechen"].tap()

        // Fernbedienung öffnen → Wheel ist zentral; Zurück schließt den Player
        remoteHandle.tap()
        let wheel = app.otherElements["Scrollrad"]
        XCTAssertTrue(wheel.waitForExistence(timeout: 3))
        XCTAssertTrue(app.buttons["remote.back"].exists)
        XCTAssertTrue(app.buttons["remote.home"].exists)

        // Vollbild gehört dem Video: die offene Fernbedienung tritt zur Seite und kommt danach zurück.
        app.buttons["player.fullscreenToggle"].tap()
        XCTAssertTrue(app.otherElements["player.fullscreen"].waitForExistence(timeout: 3), "Vollbild kommt nicht")
        XCTAssertFalse(wheel.exists, "Fernbedienung verdeckt das Vollbild")
        app.buttons["player.fullscreenExit"].tap()
        XCTAssertTrue(wheel.waitForExistence(timeout: 3), "Fernbedienung kehrt nach dem Vollbild nicht zurück")
        app.buttons["remote.back"].tap()
        XCTAssertTrue(app.navigationBars["Mediathek"].waitForExistence(timeout: 5))
        XCTAssertTrue(tile.waitForExistence(timeout: 3), "Mediathek-Zustand bleibt erhalten")

        // Querformat im Player = Vollbild
        tile.tap()
        XCTAssertTrue(app.staticTexts["1 von 1"].waitForExistence(timeout: 5))
#if !targetEnvironment(simulator)
        // Der Simulator uebernimmt die Drehung nicht (Fenster bleibt im Hochformat), deshalb nur auf dem Geraet.
        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.otherElements["player.fullscreen"].waitForExistence(timeout: 5), "Querformat schaltet nicht auf Vollbild")
        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(app.staticTexts["1 von 1"].waitForExistence(timeout: 5), "Hochformat kehrt nicht zur Warteschlange zurueck")
#endif
        app.buttons["player.close"].tap()

        // Remote im Browse-Modus: Home-Taste führt zum Home-Tab, Wheel-Auswahl bleibt bedienbar
        remoteHandle.tap()
        XCTAssertTrue(wheel.waitForExistence(timeout: 3))
        wheel.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.86)).tap()   // Ring unten = ↓
        app.buttons["remote.home"].tap()
        XCTAssertTrue(app.navigationBars["Tom"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Weiterschauen"].waitForExistence(timeout: 3), "gespieltes Video erscheint zum Weiterschauen")
    }

    /// Schlafmodus: im Elternbereich starten (Timer per DEBUG-Argument auf 4 s) → Kindermodus zeigt Timer →
    /// „Gute Nacht"-Overlay → Eltern-PIN beendet den Schlafmodus.
    func testSleepModeOverlayAndParentPIN() throws {
        app.terminate()
        app.launchArguments += ["-sidetube.devPIN", "1234", "-sidetube.devParent", "1", "-sidetube.devSleepSeconds", "4", "-sidetube.devBedtimeOff", "1"]
        app.launch()
        XCTAssertTrue(app.navigationBars["Elternbereich"].waitForExistence(timeout: 5))
        app.buttons["Profil anlegen"].tap()
        let nameField = app.textFields["Name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 3))
        nameField.tap(); nameField.typeText("Lea")
        app.buttons["Sichern"].tap()
        app.staticTexts["Lea"].tap()
        // Schlafmodus und Profilbearbeitung liegen im Menü der Titelleiste
        XCTAssertTrue(app.buttons["profile.menu"].firstMatch.waitForExistence(timeout: 3))
        app.buttons["profile.menu"].firstMatch.tap()
        XCTAssertTrue(app.buttons["Schlafmodus"].waitForExistence(timeout: 3))
        app.buttons["Schlafmodus"].tap()
        let start = app.buttons["Schlafmodus starten und in den Kindermodus"]
        XCTAssertTrue(start.waitForExistence(timeout: 3))
        start.tap()

        // Kindermodus mit laufendem Timer
        XCTAssertTrue(app.navigationBars["Lea"].waitForExistence(timeout: 5))
        // Ablauf → Overlay
        XCTAssertTrue(app.staticTexts["Gute Nacht!"].waitForExistence(timeout: 10))
        app.buttons["Eltern-PIN"].tap()
        XCTAssertTrue(app.staticTexts["Eltern-PIN eingeben"].waitForExistence(timeout: 3))
        enterPIN("1234")
        XCTAssertFalse(app.staticTexts["Gute Nacht!"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.navigationBars["Lea"].exists, "nach der PIN geht es im Kindermodus weiter")
    }

    /// Empfehlungslink: öffnet die Vorschau, das Hinzufügen verlangt die Eltern-PIN, danach steht das Video unter Videos.
    func testIncomingRecommendationNeedsParentPIN() throws {
        app.terminate()
        app.launchArguments += ["-sidetube.devPIN", "1234", "-sidetube.devParent", "1", "-sidetube.devBedtimeOff", "1"]
        app.launch()
        XCTAssertTrue(app.navigationBars["Elternbereich"].waitForExistence(timeout: 5))
        app.buttons["Profil anlegen"].tap()
        let nameField = app.textFields["Name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 3))
        nameField.tap(); nameField.typeText("Mia")
        app.buttons["Sichern"].tap()
        XCTAssertTrue(app.staticTexts["Mia"].waitForExistence(timeout: 5), "Profil muss angelegt sein")
        app.buttons["Sperren"].tap()
        XCTAssertTrue(app.navigationBars["Mia"].waitForExistence(timeout: 5))

        // open(_:) startet die App ggf. neu – ohne Reset-Argument, sonst wäre die Datenbank wieder leer.
        app.launchArguments = ["-sidetube.devPIN", "1234", "-sidetube.devBedtimeOff", "1"]
        app.open(URL(string: "sidetube://add?v=dQw4w9WgXcQ")!)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let openButton = springboard.buttons["Öffnen"]
        if openButton.waitForExistence(timeout: 5) { openButton.tap() }

        XCTAssertTrue(app.staticTexts["Empfohlenes Video"].waitForExistence(timeout: 20), "Vorschau (oEmbed) fehlt")
        let addButton = app.buttons["Zur Prüfung übernehmen (Eltern-PIN)"]
        XCTAssertTrue(addButton.waitForExistence(timeout: 3))
        addButton.tap()
        XCTAssertTrue(app.staticTexts["Eltern-PIN eingeben"].waitForExistence(timeout: 3), "ohne PIN darf nichts in die Prüfschleife")
        enterPIN("1234")
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Prüfen'")).firstMatch.waitForExistence(timeout: 5))
        app.buttons["Fertig"].tap()

        // Empfehlung ist NICHT sichtbar, bis Eltern sie in der Redaktionsansicht freigeben
        app.tabBars.buttons["Mediathek"].tap()
        app.segmentedControls.buttons["Videos"].tap()
        XCTAssertFalse(app.buttons.containing(NSPredicate(format: "label CONTAINS 'Rick Astley'")).firstMatch.waitForExistence(timeout: 3),
                       "nicht freigegebenes Video darf im Kinderprofil nicht erscheinen")
        XCTAssertTrue(app.staticTexts["Noch keine Videos"].exists)
    }

    /// Ruhezeit sperrt den Kindermodus; nur die Eltern-PIN hebt sie bis zum Morgen auf.
    func testBedtimeBlocksKidModeUntilParentPIN() throws {
        app.terminate()
        app.launchArguments += ["-sidetube.devPIN", "1234", "-sidetube.devParent", "1", "-sidetube.devBedtimeNow", "1"]
        app.launch()
        XCTAssertTrue(app.navigationBars["Elternbereich"].waitForExistence(timeout: 5))
        app.buttons["Profil anlegen"].tap()
        let nameField = app.textFields["Name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 3))
        nameField.tap(); nameField.typeText("Mira")
        app.buttons["Sichern"].tap()
        XCTAssertTrue(app.staticTexts["Mira"].waitForExistence(timeout: 5))
        app.buttons["Sperren"].tap()

        // Ruhezeit greift sofort
        XCTAssertTrue(app.staticTexts["Schlafenszeit"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.staticTexts["Jetzt ist Ruhezeit. Morgen früh geht es weiter."].exists)
        XCTAssertFalse(app.buttons["remote.handle"].firstMatch.isHittable, "Fernbedienung ist gesperrt")

        // Eltern-PIN hebt die Sperre auf
        app.buttons["Eltern-PIN"].tap()
        XCTAssertTrue(app.staticTexts["Eltern-PIN eingeben"].waitForExistence(timeout: 3))
        enterPIN("1234")
        XCTAssertTrue(app.navigationBars["Mira"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Schlafenszeit"].exists)
    }
}
