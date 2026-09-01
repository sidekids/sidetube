// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Testing
@testable import sidetube

struct PINHasherTests {
    @Test func hashHasSaltAndHashParts() {
        let stored = PINHasher.hash("1234")
        let parts = stored.split(separator: ":")
        #expect(parts.count == 2)
        #expect(Data(base64Encoded: String(parts[0]))?.count == 16)
        #expect(Data(base64Encoded: String(parts[1]))?.count == 32)
    }

    @Test func verifyAcceptsCorrectAndRejectsWrongPIN() {
        let stored = PINHasher.hash("1234")
        #expect(PINHasher.verify("1234", against: stored))
        #expect(!PINHasher.verify("1235", against: stored))
        #expect(!PINHasher.verify("1234", against: "kaputt"))
    }

    @Test func samePINDifferentSaltDifferentHash() {
        #expect(PINHasher.hash("1234") != PINHasher.hash("1234"))
    }
}

struct PINLockoutPolicyTests {
    @Test(arguments: [(5, 30.0), (10, 60.0), (15, 120.0), (20, 240.0), (25, 480.0), (30, 960.0)])
    func schedule(failures: Int, expected: Double) {
        #expect(PINLockoutPolicy.lockoutDuration(afterFailures: failures) == expected)
    }

    @Test(arguments: [0, 1, 4, 6, 9, 11])
    func noLockoutBetweenTiers(failures: Int) {
        #expect(PINLockoutPolicy.lockoutDuration(afterFailures: failures) == nil)
    }

    @Test func attemptsRemaining() {
        #expect(PINLockoutPolicy.attemptsRemaining(afterFailures: 1) == 4)
        #expect(PINLockoutPolicy.attemptsRemaining(afterFailures: 4) == 1)
        #expect(PINLockoutPolicy.attemptsRemaining(afterFailures: 6) == 4)
    }
}

/// Testuhr, damit Sperrzeiten ohne Warten prüfbar sind.
final class FakeClock {
    var now = Date(timeIntervalSince1970: 1_700_000_000)
    func advance(_ seconds: TimeInterval) { now = now.addingTimeInterval(seconds) }
}

struct PINManagerTests {
    private func makeManager() -> (PINManager, FakeClock) {
        let clock = FakeClock()
        let manager = PINManager(store: InMemoryPINStore(), now: { clock.now })
        return (manager, clock)
    }

    @Test func setupValidation() throws {
        let (manager, _) = makeManager()
        #expect(!manager.isPINSet)
        #expect(throws: PINSetupError.tooShort(minimum: 4)) { try manager.setPIN("123") }
        #expect(throws: PINSetupError.notNumeric) { try manager.setPIN("12a4") }
        try manager.setPIN("1234")
        #expect(manager.isPINSet)
        #expect(manager.verify("1234") == .success)
    }

    @Test func fiveFailuresLockFor30Seconds() throws {
        let (manager, clock) = makeManager()
        try manager.setPIN("1234")
        #expect(manager.verify("0000") == .failure(attemptsRemaining: 4))
        #expect(manager.verify("0000") == .failure(attemptsRemaining: 3))
        #expect(manager.verify("0000") == .failure(attemptsRemaining: 2))
        #expect(manager.verify("0000") == .failure(attemptsRemaining: 1))
        #expect(manager.verify("0000") == .lockedOut(remainingSeconds: 30))
        // Während der Sperre zählt auch die richtige PIN nicht.
        clock.advance(10)
        #expect(manager.verify("1234") == .lockedOut(remainingSeconds: 20))
        clock.advance(21)
        #expect(manager.lockoutRemainingSeconds() == nil)
        #expect(manager.verify("0000") == .failure(attemptsRemaining: 4))
    }

    @Test func secondTierLocksFor60Seconds() throws {
        let (manager, clock) = makeManager()
        try manager.setPIN("1234")
        for _ in 0..<5 { _ = manager.verify("0000") }
        clock.advance(31)
        var last: PINVerificationResult = .success
        for _ in 0..<5 { last = manager.verify("0000") }
        #expect(last == .lockedOut(remainingSeconds: 60))
    }

    @Test func successResetsCounter() throws {
        let (manager, _) = makeManager()
        try manager.setPIN("1234")
        for _ in 0..<4 { _ = manager.verify("0000") }
        #expect(manager.verify("1234") == .success)
        #expect(manager.verify("0000") == .failure(attemptsRemaining: 4))
    }

    @Test func changePINRequiresOldPIN() throws {
        let (manager, _) = makeManager()
        try manager.setPIN("1234")
        #expect(try manager.changePIN(from: "9999", to: "5678") == .failure(attemptsRemaining: 4))
        #expect(manager.verify("5678") != .success)
        #expect(try manager.changePIN(from: "1234", to: "5678") == .success)
        #expect(manager.verify("5678") == .success)
    }

    @Test func resetClearsEverything() throws {
        let (manager, _) = makeManager()
        try manager.setPIN("1234")
        manager.reset()
        #expect(!manager.isPINSet)
        #expect(manager.lockoutRemainingSeconds() == nil)
    }
}
