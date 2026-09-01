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

   /// Löscht Profil samt Whitelist und Sehzeit (Cascade).
    func delete(_ profile: KidProfile) throws {
        context.delete(profile)
        try context.save()
    }

    func save() throws { try context.save() }

    enum ValidationError: Error, Equatable { case emptyName }
}
