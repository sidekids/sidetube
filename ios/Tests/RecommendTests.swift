// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Foundation
import Testing
@testable import sidetube

struct RecommendLinkTests {
    @Test func linkIsTheOriginalYouTubeVideo() {
        #expect(RecommendLink.url(videoId: "dQw4w9WgXcQ").absoluteString == "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }

    @Test func messageContainsTitleAndLink() {
        let text = RecommendLink.message(title: "Elmo – Und du so?", videoId: "abc123")
        #expect(text.hasPrefix("Schau mal: Elmo – Und du so?"))
        #expect(text.contains("https://www.youtube.com/watch?v=abc123"))
        #expect(text.hasSuffix("sidetube://add?v=abc123"))
    }

    @Test func incomingLinkParsing() throws {
        #expect(IncomingLink.videoId(from: IncomingLink.url(videoId: "dQw4w9WgXcQ")) == "dQw4w9WgXcQ")
        #expect(IncomingLink.videoId(from: URL(string: "sidetube://add?v=abc_-1")!) == "abc_-1")
        #expect(IncomingLink.videoId(from: URL(string: "sidetube://add?v=")!) == nil)
        #expect(IncomingLink.videoId(from: URL(string: "sidetube://add?v=a b")!) == nil)
        #expect(IncomingLink.videoId(from: URL(string: "sidetube://other?v=abc")!) == nil)
        #expect(IncomingLink.videoId(from: URL(string: "https://www.youtube.com/watch?v=abc")!) == nil)
    }
}
