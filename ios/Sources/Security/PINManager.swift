import Foundation
import Observation

enum PINVerificationResult: Equatable, Sendable {
    case success
    case failure(attemptsRemaining: Int)
    case lockedOut(remainingSeconds: Int)
}

enum PINSetupError: Error, Equatable {
    case tooShort(minimum: Int)
    case notNumeric
}

/// Zentrale PIN-Logik (FR-02): Anlegen, Prüfen mit Sperrstufen, Ändern. Uhr injizierbar für Tests.
@Observable
final class PINManager {
    static let minimumLength = 4

    private let store: PINStore
    private let now: () -> Date
    private(set) var isPINSet: Bool

    init(store: PINStore, now: @escaping () -> Date = { Date() }) {
        self.store = store
        self.now = now
        self.isPINSet = store.pinHash != nil
    }

    func setPIN(_ pin: String) throws {
        try Self.validate(pin)
        store.pinHash = PINHasher.hash(pin)
        store.failCount = 0
        store.lockoutUntil = nil
        isPINSet = true
    }

   /// Ändert die PIN; liefert das Prüfergebnis der alten PIN (nur bei `.success` wird geändert).
    func changePIN(from oldPIN: String, to newPIN: String) throws -> PINVerificationResult {
        try Self.validate(newPIN)
        let result = verify(oldPIN)
        if result == .success { try setPIN(newPIN) }
        return result
    }

    func lockoutRemainingSeconds() -> Int? {
        guard let until = store.lockoutUntil else { return nil }
        let remaining = until.timeIntervalSince(now())
        return remaining > 0 ? Int(remaining.rounded(.up)) : nil
    }

    func verify(_ pin: String) -> PINVerificationResult {
        if let remaining = lockoutRemainingSeconds() {
            return .lockedOut(remainingSeconds: remaining)
        }
        guard let hash = store.pinHash, PINHasher.verify(pin, against: hash) else {
            store.failCount += 1
            if let duration = PINLockoutPolicy.lockoutDuration(afterFailures: store.failCount) {
                store.lockoutUntil = now().addingTimeInterval(duration)
                return .lockedOut(remainingSeconds: Int(duration))
            }
            return .failure(attemptsRemaining: PINLockoutPolicy.attemptsRemaining(afterFailures: store.failCount))
        }
        store.failCount = 0
        store.lockoutUntil = nil
        return .success
    }

   /// Entfernt PIN und Zähler (z. B. bei „Alle Daten löschen").
    func reset() {
        store.pinHash = nil
        store.failCount = 0
        store.lockoutUntil = nil
        isPINSet = false
    }

    static func validate(_ pin: String) throws {
        guard pin.count >= minimumLength else { throw PINSetupError.tooShort(minimum: minimumLength) }
        guard pin.allSatisfy(\.isNumber) else { throw PINSetupError.notNumeric }
    }
}
