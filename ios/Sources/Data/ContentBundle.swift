import Foundation

/// Zugriff auf die gemeinsamen Kuratierungsdaten unter `content/` (Startpakete, Quellenregister, Risikobegriffe,
/// Taxonomie). Dieselben Dateien versorgen die Android-App; verbindlich ist das Verzeichnis im Repository.
enum ContentBundle {
    enum Directory: String {
        case root = "content"
        case libraries = "content/libraries"
        case schema = "content/schema"
    }

    static func url(_ name: String, in directory: Directory, bundle: Bundle = .main) -> URL? {
        bundle.url(forResource: name, withExtension: "json", subdirectory: directory.rawValue)
            ?? bundle.url(forResource: name, withExtension: "json")
    }

   /// Alle JSON-Dateien eines Ordners (Name ohne Endung) – so bestimmt der Inhalt die Auswahl, nicht der Code.
    static func names(in directory: Directory, bundle: Bundle = .main) -> [String] {
        let urls = bundle.urls(forResourcesWithExtension: "json", subdirectory: directory.rawValue) ?? []
        return urls.map { $0.deletingPathExtension().lastPathComponent }.sorted()
    }

    static func load<T: Decodable>(_ type: T.Type, _ name: String, in directory: Directory = .root, bundle: Bundle = .main) throws -> T {
        guard let url = url(name, in: directory, bundle: bundle) else { throw CocoaError(.fileNoSuchFile) }
        return try JSONDecoder().decode(T.self, from: Data(contentsOf: url))
    }
}
