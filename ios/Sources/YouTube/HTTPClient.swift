import Foundation

/// Minimaler HTTP-Zugriff, damit Clients ohne Netz testbar sind.
protocol HTTPClient {
    func get(_ url: URL, headers: [String: String]) async throws -> (data: Data, status: Int)
}

extension HTTPClient {
    func get(_ url: URL) async throws -> (data: Data, status: Int) { try await get(url, headers: [:]) }
}

final class URLSessionHTTPClient: HTTPClient {
    private let session: URLSession

    init(timeout: TimeInterval = 15) {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = timeout
        configuration.waitsForConnectivity = false
        session = URLSession(configuration: configuration)
    }

    func get(_ url: URL, headers: [String: String]) async throws -> (data: Data, status: Int) {
        var request = URLRequest(url: url)
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
        do {
            let (data, response) = try await session.data(for: request)
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            return (data, status)
        } catch {
            throw YouTubeError.network(error.localizedDescription)
        }
    }
}
