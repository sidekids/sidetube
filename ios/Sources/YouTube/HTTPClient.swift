// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

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
        // Thumbnails und öffentliche Metadaten dürfen innerhalb einer Sitzung aus dem
        // RAM wiederverwendet werden. Kein Disk-Cache, keine dauerhafte Tracking-Spur.
        configuration.urlCache = URLCache(memoryCapacity: 8 * 1024 * 1024, diskCapacity: 0)
        configuration.requestCachePolicy = .useProtocolCachePolicy
        session = URLSession(configuration: configuration)
    }

    func get(_ url: URL, headers: [String: String]) async throws -> (data: Data, status: Int) {
        var request = URLRequest(url: url)
        request.cachePolicy = .useProtocolCachePolicy
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
