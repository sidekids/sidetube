// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.provider

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.net.HttpClient
import xyz.steier.sidetube.core.net.HttpResponse

/** Antwortet mit hinterlegten Texten – die Pruefung braucht kein Netz. */
private class FakeHttp(private val responses: Map<String, HttpResponse>) : HttpClient {
    val requested = mutableListOf<Pair<String, Map<String, String>>>()

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        requested += url to headers
        return responses.entries.firstOrNull { url.contains(it.key) }?.value
            ?: HttpResponse(404, "")
    }
}

private fun fixture(name: String): String =
    checkNotNull(YouTubeSourcesTest::class.java.classLoader?.getResourceAsStream(name)) { "fehlt: $name" }
        .bufferedReader().use { it.readText() }

@RunWith(RobolectricTestRunner::class)
class YouTubeSourcesTest {

    @Test
    fun `oEmbed liefert Titel und Kanal ohne Schluessel`() = runTest {
        val http = FakeHttp(mapOf("oembed" to HttpResponse(200, fixture("oembed-video.json"))))

        val draft = OEmbedSource(http).resolve(WhitelistItemType.VIDEO, "_80pKGuyKWc")

        assertThat(draft.title).isEqualTo("Was ist Künstliche Intelligenz? | Die Maus | WDR")
        assertThat(draft.channelTitle).isEqualTo("Die Maus")
        assertThat(draft.channelUrl).isEqualTo("https://www.youtube.com/@diemaus")
        assertThat(draft.type).isEqualTo(WhitelistItemType.VIDEO)
        assertThat(draft.contentId).isEqualTo("_80pKGuyKWc")
        assertThat(http.requested.single().first).doesNotContain("key=")
    }

    @Test
    fun `oEmbed merkt sich das kleine Vorschaubild`() = runTest {
        val http = FakeHttp(mapOf("oembed" to HttpResponse(200, fixture("oembed-video.json"))))

        val draft = OEmbedSource(http).resolve(WhitelistItemType.VIDEO, "_80pKGuyKWc")

        assertThat(draft.thumbnailUrl).endsWith("mqdefault.jpg")
    }

    @Test
    fun `ein geloeschtes Video meldet sich als nicht gefunden`() = runTest {
        val http = FakeHttp(emptyMap())   // antwortet mit 404

        val fehler = runCatching { OEmbedSource(http).resolve(WhitelistItemType.VIDEO, "weg") }.exceptionOrNull()

        assertThat(fehler).isInstanceOf(ProviderError.NotFound::class.java)
    }

    @Test
    fun `der Kanal-Feed liefert die neuesten Videos`() = runTest {
        val http = FakeHttp(mapOf("feeds/videos.xml" to HttpResponse(200, fixture("channel-feed.xml"))))

        val videos = ChannelFeedSource(http).latest("UCRWSxXBnz9IRS4SgRhG2wpQ")

        assertThat(videos).hasSize(15)
        assertThat(videos.first().channelTitle).isEqualTo("Die Maus")
        assertThat(videos.map { it.videoId }.toSet()).hasSize(15)
        assertThat(videos.all { it.title.isNotBlank() }).isTrue()
        assertThat(videos.all { it.thumbnailUrl.startsWith("http") }).isTrue()
        assertThat(videos.map { it.position }).isInOrder()
    }

    @Test
    fun `der Playlist-Feed liefert die Videos in Reihenfolge mit ihrem eigenen Kanal`() = runTest {
        // Aufgezeichnet am 02.10.2026: „Blender Open Movies" (Blender Studio), gekuerzt auf vier Eintraege.
        val http = FakeHttp(mapOf("playlist_id=" to HttpResponse(200, fixture("playlist-feed.xml"))))

        val videos = PlaylistFeedSource(http).videos("PLav47HAVZMjnTFVZL-aImCQIC0uLZtNCz")

        assertThat(http.requested.single().first)
            .isEqualTo("https://www.youtube.com/feeds/videos.xml?playlist_id=PLav47HAVZMjnTFVZL-aImCQIC0uLZtNCz")
        assertThat(http.requested.single().first).doesNotContain("key=")
        assertThat(videos.map { it.videoId }).containsExactly("u9lj-c29dxI", "WhWc3b3KhnY", "SkVqJ1SGeL0", "Z4C82eyhwgU").inOrder()
        assertThat(videos.map { it.position }).containsExactly(0, 1, 2, 3).inOrder()
        // Nicht der Playlist-Titel, sondern der Kanal jedes Eintrags – Caminandes kommt von „Blender".
        assertThat(videos.map { it.channelTitle }).containsExactly("Blender Studio", "Blender Studio", "Blender", "Blender").inOrder()
        assertThat(videos[2].channelId).isEqualTo("UCSMOQeBJ2RAnuFungnQOxLg")
        assertThat(videos[3].title).isEqualTo("\"Caminandes 2: Gran Dillama\" - Blender Animated Short")
        assertThat(videos.none { it.title == "Blender Open Movies" }).isTrue()
    }

    @Test
    fun `eine geloeschte oder private Playlist meldet sich als nicht gefunden`() = runTest {
        val fehler = runCatching { PlaylistFeedSource(FakeHttp(emptyMap())).videos("PLweg") }.exceptionOrNull()

        assertThat(fehler).isInstanceOf(ProviderError.NotFound::class.java)
    }

    @Test
    fun `der Kanal-Feed nennt weiter den Kanal als Titel und jetzt auch die Kennung`() = runTest {
        val http = FakeHttp(mapOf("feeds/videos.xml" to HttpResponse(200, fixture("channel-feed.xml"))))

        val videos = ChannelFeedSource(http).latest("UCRWSxXBnz9IRS4SgRhG2wpQ")

        assertThat(videos.map { it.channelTitle }.toSet()).containsExactly("Die Maus")
        assertThat(videos.map { it.channelId }.toSet()).containsExactly("UCRWSxXBnz9IRS4SgRhG2wpQ")
    }

    @Test
    fun `die Kanalseite liefert Kennung und Titel ohne Schluessel`() = runTest {
        val http = FakeHttp(mapOf("youtube.com/@" to HttpResponse(200, fixture("channel-page.html"))))

        val info = ChannelPageSource(http).byHandle("diemaus")

        assertThat(info.channelId).isEqualTo("UCRWSxXBnz9IRS4SgRhG2wpQ")
        assertThat(info.title).isEqualTo("Die Maus")
        assertThat(info.thumbnailUrl).startsWith("https://")
        assertThat(info.uploadsPlaylistId).isEqualTo("UURWSxXBnz9IRS4SgRhG2wpQ")
    }

    @Test
    fun `die Kanalseite wird mit Zustimmungs-Cookie abgefragt`() = runTest {
        // Ohne das antwortet YouTube in der EU nur mit einer Zwischenseite.
        val http = FakeHttp(mapOf("youtube.com/@" to HttpResponse(200, fixture("channel-page.html"))))

        ChannelPageSource(http).byHandle("diemaus")

        val headers = http.requested.single().second
        assertThat(headers["Cookie"]).contains("SOCS=CAI")
        assertThat(headers["Accept-Language"]).contains("de")
    }

    @Test
    fun `eine Zwischenseite ohne Meta-Angaben wird als unbrauchbar gemeldet`() = runTest {
        val http = FakeHttp(mapOf("youtube.com/@" to HttpResponse(200, "<html><body>Bevor Sie zu YouTube weitergehen</body></html>")))

        val fehler = runCatching { ChannelPageSource(http).byHandle("diemaus") }.exceptionOrNull()

        assertThat(fehler).isInstanceOf(ProviderError.Malformed::class.java)
    }
}

class ThumbnailSizeTest {

    @Test
    fun `Vorschaubilder kommen in Anzeigegroesse`() {
        assertThat(YouTubeThumbnails.url("abc", 100)).endsWith("/default.jpg")
        assertThat(YouTubeThumbnails.url("abc", 300)).endsWith("/mqdefault.jpg")
        assertThat(YouTubeThumbnails.url("abc", 600)).endsWith("/hqdefault.jpg")
    }

    @Test
    fun `vorhandene Adressen werden angepasst, fremde nicht`() {
        val gross = "https://i.ytimg.com/vi/abc/hqdefault.jpg"
        assertThat(YouTubeThumbnails.resize(gross, 100)).isEqualTo("https://i.ytimg.com/vi/abc/default.jpg")
        val fremd = "https://framatube.org/previews/x.jpg"
        assertThat(YouTubeThumbnails.resize(fremd, 100)).isEqualTo(fremd)
    }
}

/** Veroeffentlichung und Shorts aus dem Feed – Grundlage fuer „Neu bei deinen Kanaelen". */
@RunWith(RobolectricTestRunner::class)
class FeedDatumTest {
    private val xml = """
        <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
         <title>NASA</title>
         <published>2008-06-03T18:44:30+00:00</published>
         <entry>
          <yt:videoId>v1</yt:videoId><yt:channelId>UCn</yt:channelId>
          <title>Crew-13 Arrival</title>
          <link rel="alternate" href="https://www.youtube.com/watch?v=v1"/>
          <author><name>NASA</name></author>
          <published>2026-10-02T02:02:18+00:00</published>
         </entry>
         <entry>
          <yt:videoId>v3</yt:videoId><yt:channelId>UCn</yt:channelId>
          <title>Premiere heute Abend</title>
          <published>2026-10-02T03:00:00+00:00</published>
          <media:group><media:community><media:statistics views="0"/></media:community></media:group>
         </entry>
         <entry>
          <yt:videoId>v2</yt:videoId><yt:channelId>UCn</yt:channelId>
          <title>Kurz gezeigt</title>
          <link rel="alternate" href="https://www.youtube.com/shorts/v2"/>
          <published>kaputt</published>
         </entry>
        </feed>
    """.trimIndent()

    @Test fun `Datum und Shorts-Verweis werden gelesen`() {
        val videos = ChannelFeedSource(FakeHttp(emptyMap())).parse(xml)
        assertThat(videos.map { it.videoId }).containsExactly("v1", "v3", "v2").inOrder()
        // Angekuendigt (views="0"): noch nicht veroeffentlicht, also ohne Datum.
        assertThat(videos[1].publishedAt).isNull()
        assertThat(videos[0].publishedAt).isEqualTo(java.time.Instant.parse("2026-10-02T02:02:18Z").toEpochMilli())
        assertThat(videos[0].isShort).isFalse()
        // Unlesbares Datum gilt als unbekannt, nicht als „jetzt"; das Datum des Kanals zaehlt nicht.
        assertThat(videos[2].publishedAt).isNull()
        assertThat(videos[2].isShort).isTrue()
        assertThat(videos.map { it.isUpcoming }).containsExactly(false, true, false).inOrder()
    }

    @Test fun `Playlist-Feed - Kennzeichen kommen mit, doppelte Videos einmal`() {
        val doppelt = xml.replace("</feed>", """
         <entry>
          <yt:videoId>v1</yt:videoId><yt:channelId>UCn</yt:channelId>
          <title>Crew-13 Arrival</title>
         </entry>
        </feed>""")
        val videos = PlaylistFeedSource(FakeHttp(emptyMap())).parse(doppelt)
        assertThat(videos.map { it.videoId }).containsExactly("v1", "v3", "v2").inOrder()
        assertThat(videos.single { it.videoId == "v2" }.isShort).isTrue()
        assertThat(videos.single { it.videoId == "v3" }.isUpcoming).isTrue()
    }

    @Test fun `Kennungen werden in der Feed-Adresse kodiert`() = kotlinx.coroutines.test.runTest {
        val http = FakeHttp(emptyMap())
        runCatching { PlaylistFeedSource(http).videos("PL a&b=c") }
        runCatching { ChannelFeedSource(http).latest("UC#x") }
        assertThat(http.requested.map { it.first }).containsExactly(
            "https://www.youtube.com/feeds/videos.xml?playlist_id=PL+a%26b%3Dc",
            "https://www.youtube.com/feeds/videos.xml?channel_id=UC%23x"
        ).inOrder()
    }
}
