// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

import Testing
@testable import sidetube

struct YouTubeURLParserTests {
    static let cases: [(String, ParsedYouTubeURL?)] = [
        ("https://www.youtube.com/watch?v=dQw4w9WgXcQ", .video(id: "dQw4w9WgXcQ")),
        ("https://m.youtube.com/watch?v=abc123&t=42s", .video(id: "abc123")),
        ("https://youtu.be/dQw4w9WgXcQ", .video(id: "dQw4w9WgXcQ")),
        ("https://youtu.be/dQw4w9WgXcQ?t=10", .video(id: "dQw4w9WgXcQ")),
        ("https://www.youtube.com/shorts/sh0rt1d", .video(id: "sh0rt1d")),
        ("https://www.youtube.com/embed/emb3d1d", .video(id: "emb3d1d")),
        ("https://www.youtube.com/live/l1v3id", .video(id: "l1v3id")),
        ("https://www.youtube.com/playlist?list=PL123", .playlist(id: "PL123")),
        ("https://www.youtube.com/watch?v=vid123&list=PL123", .playlist(id: "PL123")),
        ("https://www.youtube.com/channel/UCpeppa", .channel(id: "UCpeppa")),
        ("https://www.youtube.com/@MrBeast", .channelHandle("MrBeast")),
        ("https://www.youtube.com/@MrBeast/videos", .channelHandle("MrBeast")),
        ("https://www.youtube.com/c/PewDiePie", .channelCustomName("PewDiePie")),
        ("  https://www.youtube.com/watch?v=trimmed  ", .video(id: "trimmed")),
        ("youtube.com/watch?v=noscheme", .video(id: "noscheme")),
        ("https://www.youtube.com/watch", nil),
        ("https://www.youtube.com/watch?v=", nil),
        ("https://www.youtube.com/", nil),
        ("https://www.youtube.com/@", nil),
        ("https://vimeo.com/12345", nil),
        ("", nil),
        ("nicht mal eine url", nil),
    ]

    @Test(arguments: cases.indices)
    func parses(index: Int) {
        let (input, expected) = Self.cases[index]
        #expect(YouTubeURLParser.parse(input) == expected, "Eingabe: \(input)")
    }
}
