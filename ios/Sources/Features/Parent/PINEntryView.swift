// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import SwiftUI

/// PIN-Abfrage vor dem Elternbereich mit Restversuchen und Sperr-Countdown. FR-02.3–02.5
struct PINEntryView: View {
    @Environment(PINManager.self) private var pinManager
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @State private var message: String?
    @State private var lockedUntil: Date?
    let onSuccess: () -> Void

    var body: some View {
        NavigationStack {
            Group {
                if lockedUntil != nil, scenePhase == .active {
                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        let remaining = remainingSeconds(at: context.date)
                        pinContent(remaining: remaining)
                            .onChange(of: remaining, initial: true) { _, remaining in
                                if remaining == nil { lockedUntil = nil }
                            }
                    }
                } else {
                    pinContent(remaining: remainingSeconds(at: .now))
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
            }
            .onAppear { syncLockout() }
            .onChange(of: scenePhase) { _, phase in if phase == .active { syncLockout() } }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
                syncLockout()
            }
        }
    }

    private func pinContent(remaining: Int?) -> some View {
        VStack(spacing: 24) {
            Image(systemName: remaining == nil ? "lock" : "lock.slash")
                .font(.system(size: 48))
                .foregroundStyle(remaining == nil ? Color.accentColor : .red)
            Text("PIN eingeben").font(.title2.bold())
            if let remaining {
                Text("Gesperrt – noch \(remaining) s").font(.headline).foregroundStyle(.red)
            } else if let message {
                Text(message).font(.footnote).foregroundStyle(.red)
            }
            PINPadView(length: PINManager.minimumLength, onComplete: handle, disabled: remaining != nil)
        }
        .padding()
    }

    private func remainingSeconds(at date: Date) -> Int? {
        guard let lockedUntil else { return nil }
        let value = Int(lockedUntil.timeIntervalSince(date).rounded(.up))
        return value > 0 ? value : nil
    }

    private func syncLockout() {
        if let seconds = pinManager.lockoutRemainingSeconds() {
            lockedUntil = Date().addingTimeInterval(TimeInterval(seconds))
        } else { lockedUntil = nil }
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
