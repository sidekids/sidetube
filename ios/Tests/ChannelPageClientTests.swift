// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Testing
@testable import sidetube

struct ChannelPageClientTests {
    // Ausschnitt der echten Kanalseite von @KiKA (2026-08-31)
    static let kikaHTML = """
    <html><head><link rel="canonical" href="https://www.youtube.com/@KiKA">
    <meta itemprop="identifier" content="UCxFvLj7FDoMChztQTSRDAbw">
    <meta property="og:title" content="KiKA von ARD &amp; ZDF">
    <meta property="og:image" content="https://yt3.googleusercontent.com/VlcM=s900-c-k-c0x00ffffff-no-rj">
    </head><body></body></html>
    """

    @Test func parsesMetaTags() {
        let meta = ChannelPageClient.parse(Self.kikaHTML)
        #expect(meta?.id == "UCxFvLj7FDoMChztQTSRDAbw")
        #expect(meta?.title == "KiKA von ARD & ZDF")
        #expect(meta?.thumbnailUrl.hasPrefix("https://yt3.googleusercontent.com/") == true)
        #expect(meta?.uploadsPlaylistId == "UUxFvLj7FDoMChztQTSRDAbw")
        #expect(ChannelPageClient.parse("<html><head><title>x</title></head></html>") == nil)
    }

    @Test func repositoryFallsBackToChannelPageWithoutAPIKey() async throws {
        let http = StubHTTPClient()
        http.on("youtube.com/@KiKA", body: Self.kikaHTML)
        http.on("youtube.com/channel/UCxFvLj7FDoMChztQTSRDAbw", body: Self.kikaHTML)
        let repo = YouTubeRepository(http: http, apiKey: nil)
        let draft = try await repo.resolve(urlString: "https://www.youtube.com/@KiKA")
        #expect(draft.type == .channel)
        #expect(draft.youtubeId == "UCxFvLj7FDoMChztQTSRDAbw")
        #expect(draft.title == "KiKA von ARD & ZDF")
        let byId = try await repo.channel(id: "UCxFvLj7FDoMChztQTSRDAbw")
        #expect(byId.title == "KiKA von ARD & ZDF")
        await #expect(throws: YouTubeError.notFound) { try await repo.channel(id: "UCandere") }
    }

    @Test func repositoryPrefersAPIWhenAvailable() async throws {
        let http = StubHTTPClient()
        http.on("channels?", body: Fixtures.apiChannel)
        http.on("youtube.com/@", body: Self.kikaHTML)
        let repo = YouTubeRepository(http: http, apiKey: "KEY")
        let channel = try await repo.channel(handle: "GoogleDevelopers")
        #expect(channel.subscriberCount == "2500000", "API-Daten, nicht Kanalseite")
        #expect(http.calls(containing: "youtube.com/@") == 0)
    }
}
