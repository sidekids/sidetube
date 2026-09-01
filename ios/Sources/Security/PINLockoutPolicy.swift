import Foundation

/// Brute-Force-Schutz wie Android: alle 5 Fehlversuche Sperre von 30 s × 2^(Stufe−1).
enum PINLockoutPolicy {
    static let threshold = 5
    static let baseSeconds: TimeInterval = 30

   /// Sperrdauer, wenn nach `failures` Fehlversuchen eine Sperre fällig ist, sonst `nil`.
    static func lockoutDuration(afterFailures failures: Int) -> TimeInterval? {
        guard failures >= threshold, failures % threshold == 0 else { return nil }
        let tier = failures / threshold
        return baseSeconds * pow(2, Double(tier - 1))
    }

    static func attemptsRemaining(afterFailures failures: Int) -> Int {
        threshold - (failures % threshold)
    }
}
