import SwiftUI

/// PIN-Abfrage vor dem Elternbereich mit Restversuchen und Sperr-Countdown. FR-02.3–02.5
struct PINEntryView: View {
    @Environment(PINManager.self) private var pinManager
    @Environment(\.dismiss) private var dismiss
    @State private var message: String?
    @State private var lockedUntil: Date?
    let onSuccess: () -> Void

    var body: some View {
        NavigationStack {
            TimelineView(.periodic(from: .now, by: 1)) { context in
                let remaining = remainingSeconds(at: context.date)
                VStack(spacing: 24) {
                    Image(systemName: remaining == nil ? "lock" : "lock.slash")
                        .font(.system(size: 48))
                        .foregroundStyle(remaining == nil ? Color.accentColor : .red)
                    Text("Eltern-PIN eingeben").font(.title2.bold())
                    if let remaining {
                        Text("Gesperrt – noch \(remaining) s").font(.headline).foregroundStyle(.red)
                    } else if let message {
                        Text(message).font(.footnote).foregroundStyle(.red)
                    }
                    PINPadView(length: PINManager.minimumLength, onComplete: handle, disabled: remaining != nil)
                }
                .padding()
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
            }
            .onAppear { syncLockout() }
        }
    }

    private func remainingSeconds(at date: Date) -> Int? {
        guard let lockedUntil else { return nil }
        let value = Int(lockedUntil.timeIntervalSince(date).rounded(.up))
        return value > 0 ? value : nil
    }

    private func syncLockout() {
        if let seconds = pinManager.lockoutRemainingSeconds() {
            lockedUntil = Date().addingTimeInterval(TimeInterval(seconds))
        }
    }

    private func handle(_ pin: String) {
        switch pinManager.verify(pin) {
        case .success:
            onSuccess()
        case .failure(let attemptsRemaining):
            message = "Falsche PIN. Noch \(attemptsRemaining) Versuch\(attemptsRemaining == 1 ? "" : "e")."
        case .lockedOut(let seconds):
            message = nil
            lockedUntil = Date().addingTimeInterval(TimeInterval(seconds))
        }
    }
}
