import Foundation
import SwiftData

enum ModelContainerFactory {
    static let schema = Schema([
        KidProfile.self, WhitelistItem.self, WatchHistoryEntry.self, CachedChannelVideo.self,
        CuratedSource.self, ReviewEvent.self,
    ])

   /// `inMemory: true` für Tests und Previews. Auf Platte liegt der Store unter
   /// Application Support/sidetube/sidetube.store (Verzeichnis wird angelegt – ohne das
   /// verweigert der Simulator-Sandbox beim ersten Start das Erzeugen des Default-Stores).
    static var storeDirectory: URL {
        get throws {
            try FileManager.default
                .url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                .appending(path: "sidetube", directoryHint: .isDirectory)
        }
    }

   /// Löscht den Store komplett (nur für UI-Tests/Entwicklung gedacht).
    static func removeStore() {
        guard let directory = try? storeDirectory else { return }
        try? FileManager.default.removeItem(at: directory)
    }

    static func make(inMemory: Bool = false) throws -> ModelContainer {
        let configuration: ModelConfiguration
        if inMemory {
            configuration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        } else {
            let directory = try storeDirectory
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            configuration = ModelConfiguration(schema: schema, url: directory.appending(path: "sidetube.store"))
        }
        return try ModelContainer(for: schema, configurations: [configuration])
    }
}
