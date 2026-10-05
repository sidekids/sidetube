// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import SwiftData

struct ProfileRepository {
    let context: ModelContext

    func all() throws -> [KidProfile] {
        try context.fetch(FetchDescriptor<KidProfile>(sortBy: [SortDescriptor(\.createdAt)]))
    }

    func count() throws -> Int {
        try context.fetchCount(FetchDescriptor<KidProfile>())
    }

    @discardableResult
    func create(name: String, avatarUrl: String? = nil, dailyLimitMinutes: Int? = nil) throws -> KidProfile {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { throw ValidationError.emptyName }
        let profile = KidProfile(name: trimmed, avatarUrl: avatarUrl, dailyLimitMinutes: dailyLimitMinutes)
        context.insert(profile)
        try context.save()
        return profile
    }

   /// Löscht Profil samt Whitelist und Sehzeit (Cascade), seine Wünsche und seine Einträge im Verlauf der
   /// Freigaben – darunter die Wunschtexte des Kindes (beides ohne Beziehung, daher hier). Profilunabhängige
   /// Ereignisse (Stufe einer Quelle, `profileId == nil`) bleiben. Ein gemeinsames `save()`.
    func delete(_ profile: KidProfile) throws {
        let profileID: UUID? = profile.id
        let wishID = profile.id
        for wish in try context.fetch(FetchDescriptor<KidWish>(predicate: #Predicate { $0.profileID == wishID })) {
            context.delete(wish)
        }
        for event in try context.fetch(FetchDescriptor<ReviewEvent>(predicate: #Predicate { $0.profileId == profileID })) {
            context.delete(event)
        }
        context.delete(profile)
        try context.save()
    }

    func save() throws { try context.save() }

    enum ValidationError: Error, Equatable { case emptyName }
}
