// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

enum PlaybackAdmissionError: Error, Equatable {
    /// A marker for this profile already exists; the caller must not create a second one.
    case alreadyPending
}

protocol PlaybackAdmissionRecording {
    func isPending(for profileID: UUID) throws -> Bool
    func begin(for profile: KidProfile, token: UUID, at date: Date) throws
    func finish(profileID: UUID, token: UUID) throws
    func acknowledgeByParent(profileID: UUID) throws
    func interruptedProfileIDs() throws -> Set<UUID>
}

/// Durable admission marker around a playback session (see `PlaybackAdmission`).
/// "At most one marker per profile" is enforced here, in application logic — not by a
/// store-level uniqueness constraint, since SwiftData's `.unique` conflict behavior on
/// insert is not something this codebase relies on without an observed, verified failure mode.
struct PlaybackAdmissionRepository: PlaybackAdmissionRecording {
    let context: ModelContext

    func isPending(for profileID: UUID) throws -> Bool {
        try marker(for: profileID) != nil
    }

    func begin(for profile: KidProfile, token: UUID, at date: Date = .now) throws {
        guard try marker(for: profile.id) == nil else { throw PlaybackAdmissionError.alreadyPending }
        let admission = PlaybackAdmission(profileID: profile.id, token: token, startedAt: date)
        admission.profile = profile
        context.insert(admission)
        try context.save()
    }

    /// Only removes the marker this exact session created; a stale completion for an
    /// already-superseded session must never clear a newer one.
    func finish(profileID: UUID, token: UUID) throws {
        try removeMarker(profileID: profileID, token: token)
    }

    /// UI entry point exists only behind the parent PIN, never from child-mode startup.
    /// Clears the marker without touching `WatchHistoryEntry` rows: acknowledgement preserves
    /// whatever was already recorded and does not estimate the missing remainder.
    func acknowledgeByParent(profileID: UUID) throws {
        try removeMarker(profileID: profileID, token: nil)
    }

    private func removeMarker(profileID: UUID, token: UUID?) throws {
        // Keep a failed deletion out of the shared UI context. Otherwise a later
        // fetch/retry could mistake a pending, unsaved deletion for durable recovery.
        // Do not roll back that context: it may contain unrelated parent edits.
        let writeContext = ModelContext(context.container)
        writeContext.autosaveEnabled = false
        let writer = PlaybackAdmissionRepository(context: writeContext)
        guard let admission = try writer.marker(for: profileID),
              token == nil || admission.token == token else { return }
        writeContext.delete(admission)
        try writeContext.save()
    }

    func interruptedProfileIDs() throws -> Set<UUID> {
        Set(try context.fetch(FetchDescriptor<PlaybackAdmission>()).map(\.profileID))
    }

    private func marker(for profileID: UUID) throws -> PlaybackAdmission? {
        var descriptor = FetchDescriptor<PlaybackAdmission>(
            predicate: #Predicate { $0.profileID == profileID })
        descriptor.fetchLimit = 1
        return try context.fetch(descriptor).first
    }
}
