// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.url

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Die Faelle stammen aus dem, was Eltern tatsaechlich einfuegen: kopierte Adressleisten,
 * Teilen-Links mit Anhaengseln, Kurzformen, App-Links ohne Schema.
 */
class YouTubeUrlParserTest {

    @Test
    fun `gewoehnlicher Videolink`() {
        assertThat(YouTubeUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
            .isEqualTo(YouTubeTarget.Video("dQw4w9WgXcQ"))
    }

    @Test
    fun `Kurzform mit Zeitmarke`() {
        assertThat(YouTubeUrlParser.parse("https://youtu.be/dQw4w9WgXcQ?t=42"))
            .isEqualTo(YouTubeTarget.Video("dQw4w9WgXcQ"))
    }

    @Test
    fun `ohne Schema eingefuegt`() {
        assertThat(YouTubeUrlParser.parse("youtube.com/watch?v=dQw4w9WgXcQ"))
            .isEqualTo(YouTubeTarget.Video("dQw4w9WgXcQ"))
    }

    @Test
    fun `Shorts, Embed und Live fuehren zum Video`() {
        for (path in listOf("shorts", "embed", "live", "v")) {
            assertThat(YouTubeUrlParser.parse("https://www.youtube.com/$path/dQw4w9WgXcQ"))
                .isEqualTo(YouTubeTarget.Video("dQw4w9WgXcQ"))
        }
    }

    @Test
    fun `Playlist hat Vorrang vor dem laufenden Video`() {
        assertThat(YouTubeUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123"))
            .isEqualTo(YouTubeTarget.Playlist("PL123"))
        assertThat(YouTubeUrlParser.parse("https://www.youtube.com/playlist?list=PL123"))
            .isEqualTo(YouTubeTarget.Playlist("PL123"))
    }

    @Test
    fun `Kanal ueber Kennung, Handle und alten Namen`() {
        assertThat(YouTubeUrlParser.parse("https://www.youtube.com/channel/UCRWSxXBnz9IRS4SgRhG2wpQ"))
            .isEqualTo(YouTubeTarget.Channel("UCRWSxXBnz9IRS4SgRhG2wpQ"))
        assertThat(YouTubeUrlParser.parse("https://www.youtube.com/@diemaus"))
            .isEqualTo(YouTubeTarget.ChannelHandle("diemaus"))
        assertThat(YouTubeUrlParser.parse("https://www.youtube.com/c/DieMaus"))
            .isEqualTo(YouTubeTarget.ChannelName("DieMaus"))
        assertThat(YouTubeUrlParser.parse("https://www.youtube.com/user/DieMaus"))
            .isEqualTo(YouTubeTarget.ChannelName("DieMaus"))
    }

    @Test
    fun `mobile und Musik gelten auch`() {
        assertThat(YouTubeUrlParser.parse("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
            .isEqualTo(YouTubeTarget.Video("dQw4w9WgXcQ"))
        assertThat(YouTubeUrlParser.parse("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
            .isEqualTo(YouTubeTarget.Video("dQw4w9WgXcQ"))
    }

    @Test
    fun `was kein YouTube ist, wird abgelehnt`() {
        assertThat(YouTubeUrlParser.parse("https://vimeo.com/12345")).isNull()
        assertThat(YouTubeUrlParser.parse("https://www.youtube.com/")).isNull()
        assertThat(YouTubeUrlParser.parse("")).isNull()
        assertThat(YouTubeUrlParser.parse("   ")).isNull()
        assertThat(YouTubeUrlParser.parse("kein Link")).isNull()
    }

    @Test
    fun `zu kurze oder zu lange Videokennungen gelten nicht`() {
        assertThat(YouTubeUrlParser.parse("https://youtu.be/abc")).isNull()
        assertThat(YouTubeUrlParser.parse("https://youtu.be/abcdefghijkl12345")).isNull()
    }

    @Test
    fun `eine Kanalkennung muss mit UC beginnen`() {
        assertThat(YouTubeUrlParser.parse("https://www.youtube.com/channel/XYZ123")).isNull()
    }
}
