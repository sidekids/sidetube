// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.net.HttpClient
import java.io.StringReader
import java.net.URLEncoder

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * oEmbed: liefert Titel und Kanalname zu einem Video oder einer Playlist – ohne Schluessel und
 * ohne Kontingent. Deshalb der erste Weg, bevor die Data API bemueht wird.
 */
class OEmbedSource(private val http: HttpClient) {

    suspend fun resolve(type: WhitelistItemType, id: String): ContentDraft {
        val target = when (type) {
            WhitelistItemType.VIDEO -> "https://www.youtube.com/watch?v=$id"
            WhitelistItemType.PLAYLIST -> "https://www.youtube.com/playlist?list=$id"
            WhitelistItemType.CHANNEL -> throw ProviderError.Unsupported
        }
        val url = "https://www.youtube.com/oembed?format=json&url=" +
            URLEncoder.encode(target, Charsets.UTF_8.name())
        val response = http.get(url, emptyMap())
        if (response.status == 404) throw ProviderError.NotFound
        if (!response.isSuccess) throw ProviderError.Http(response.status)

        val json = runCatching { lenientJson.parseToJsonElement(response.body) as JsonObject }
            .getOrElse { throw ProviderError.Malformed("oembed") }
        val title = json["title"]?.jsonPrimitive?.contentOrNullSafe()
            ?: throw ProviderError.Malformed("oembed/title")

        return ContentDraft(
            type = type,
            contentId = id,
            title = title,
            // Das Vorschaubild aus oEmbed ist die grosse Fassung; wir merken uns die kleine.
            thumbnailUrl = if (type == WhitelistItemType.VIDEO) YouTubeThumbnails.url(id, 320)
            else json["thumbnail_url"]?.jsonPrimitive?.contentOrNullSafe().orEmpty(),
            channelTitle = json["author_name"]?.jsonPrimitive?.contentOrNullSafe(),
            sourceUrl = target,
            channelUrl = json["author_url"]?.jsonPrimitive?.contentOrNullSafe()
        )
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        content.takeIf { it.isNotBlank() && it != "null" }
}

/**
 * Der oeffentliche RSS-Feed eines Kanals: die fuenfzehn neuesten Videos, ohne Schluessel und
 * ohne Kontingent. Damit kommt der Alltag ohne Data API aus.
 */
class ChannelFeedSource(private val http: HttpClient) {

    suspend fun latest(channelId: String): List<ChannelVideo> =
        parse(YouTubeFeed.load(http, "https://www.youtube.com/feeds/videos.xml?channel_id=" + YouTubeFeed.encode(channelId)))

    /** Im Kanal-Feed ist der Feed-Titel der Kanalname. */
    internal fun parse(xml: String): List<ChannelVideo> =
        YouTubeFeed.parse(xml).map { it.copy(channelTitle = it.feedTitle) }.map(FeedEntry::video)
}

/**
 * Der oeffentliche RSS-Feed einer Playlist – dasselbe Atom-Format wie beim Kanal, ebenfalls ohne
 * Schluessel. YouTube liefert hier die **ersten fuenfzehn** Eintraege der Playlist (gemessen am
 * 02.10.2026 an „Blender Open Movies"); laengere Playlists sind ohne Data API nicht vollstaendig.
 * Jeder Eintrag nennt seinen eigenen Kanal – eine Playlist darf Videos fremder Kanaele enthalten.
 */
class PlaylistFeedSource(private val http: HttpClient) {

    suspend fun videos(playlistId: String): List<ChannelVideo> =
        parse(YouTubeFeed.load(http, "https://www.youtube.com/feeds/videos.xml?playlist_id=" + YouTubeFeed.encode(playlistId)))

    /** Steht ein Video mehrfach in der Playlist, zaehlt der erste Platz (die Liste braucht eindeutige Schluessel). */
    internal fun parse(xml: String): List<ChannelVideo> =
        YouTubeFeed.parse(xml).distinctBy { it.videoId }.map(FeedEntry::video)
}

/** Ein Eintrag eines YouTube-Feeds, samt dem Titel des ganzen Feeds. */
internal data class FeedEntry(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelTitle: String,
    val channelId: String?,
    val feedTitle: String,
    val position: Int,
    val publishedAt: Long? = null,
    val isShort: Boolean = false,
    val isUpcoming: Boolean = false
) {
    fun video() = ChannelVideo(videoId, title, thumbnailUrl, channelTitle, position, channelId, publishedAt, isShort, isUpcoming)
}

/** Laden und Lesen der YouTube-Atom-Feeds (Kanal und Playlist teilen das Format). */
internal object YouTubeFeed {

    /** Kennungen kommen aus eingefuegten Links; in der Adresse werden sie kodiert, nie roh angehaengt. */
    fun encode(id: String): String = URLEncoder.encode(id, Charsets.UTF_8.name())

    suspend fun load(http: HttpClient, url: String): String {
        val response = http.get(url, emptyMap())
        if (response.status == 404) throw ProviderError.NotFound
        if (!response.isSuccess) throw ProviderError.Http(response.status)
        return response.body
    }

    fun parse(xml: String): List<FeedEntry> {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
            // Keine externen Entitaeten: Eine Antwort aus dem Netz darf nichts nachladen.
            runCatching { setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }

        val entries = mutableListOf<FeedEntry>()
        var feedTitle = ""
        var feedAuthor = ""
        var inEntry = false
        var inAuthor = false
        var videoId = ""
        var title = ""
        var thumbnail = ""
        var author = ""
        var channelId = ""
        var published = ""
        var isShort = false
        var views: String? = null
        var currentTag = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (parser.name) {
                        "entry" -> {
                            inEntry = true; videoId = ""; title = ""; thumbnail = ""; author = ""; channelId = ""
                            published = ""; isShort = false; views = null
                        }
                        "media:statistics" -> if (inEntry) views = parser.getAttributeValue(null, "views")
                        // Shorts verlinkt der Feed unter /shorts/ statt /watch.
                        "link" -> if (inEntry && parser.getAttributeValue(null, "href").orEmpty().contains("/shorts/")) isShort = true
                        "author" -> inAuthor = true
                        "media:thumbnail" -> if (inEntry) thumbnail = parser.getAttributeValue(null, "url").orEmpty()
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) when {
                        currentTag == "yt:videoId" && inEntry -> videoId = text
                        currentTag == "yt:channelId" && inEntry -> channelId = text
                        currentTag == "published" && inEntry && published.isEmpty() -> published = text
                        currentTag == "title" && inEntry && title.isEmpty() -> title = text
                        currentTag == "title" && !inEntry && feedTitle.isEmpty() -> feedTitle = text
                        currentTag == "name" && inAuthor && inEntry && author.isEmpty() -> author = text
                        currentTag == "name" && inAuthor && !inEntry && feedAuthor.isEmpty() -> feedAuthor = text
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "author" -> inAuthor = false
                        "entry" -> {
                            if (videoId.isNotEmpty() && title.isNotEmpty()) {
                                entries += FeedEntry(
                                    videoId = videoId,
                                    title = title,
                                    thumbnailUrl = thumbnail.ifEmpty { YouTubeThumbnails.url(videoId, 320) },
                                    channelTitle = author.ifEmpty { feedAuthor },
                                    channelId = channelId.ifEmpty { null },
                                    feedTitle = feedTitle,
                                    position = entries.size,
                                    // Angekuendigte Premieren und Livestreams stehen mit views="0" im Feed:
                                    // noch nicht veroeffentlicht, also ohne Datum (wie iOS).
                                        publishedAt = if (views == "0") null else parseTime(published),
                                    isShort = isShort,
                                    isUpcoming = views == "0"
                                )
                            }
                            inEntry = false
                        }
                    }
                    currentTag = ""
                }
            }
            parser.next()
        }
        return entries
    }

    /** Atom-Zeitstempel wie `2026-10-02T02:02:18+00:00`; Unlesbares gilt als unbekannt. */
    private fun parseTime(text: String): Long? =
        if (text.isEmpty()) null
        else runCatching { java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
}

/**
 * Die oeffentliche Kanalseite: liefert Kennung, Titel und Bild aus den Meta-Angaben – ohne
 * Schluessel. In der EU antwortet YouTube ohne Zustimmung nur mit einer Zwischenseite, deshalb
 * das minimale Cookie; es wird nur fuer diese eine Anfrage gesendet.
 *
 * Die Seite misst rund 1,9 MB, und die Meta-Angaben stehen erst nach etwa 0,7 MB – davor liegt
 * ein grosser Skriptblock. Wer die Antwort zu frueh abschneidet, findet nichts und haelt den
 * Kanal faelschlich fuer unbrauchbar (gemessen am 01.09.2026).
 */
class ChannelPageSource(private val http: HttpClient) {

    private val headers = mapOf(
        "Cookie" to "SOCS=CAI; CONSENT=YES+cb",
        "Accept-Language" to "de-DE,de;q=0.9"
    )

    suspend fun byHandle(handle: String): ChannelInfo = load("https://www.youtube.com/@$handle")

    suspend fun byName(name: String): ChannelInfo = load("https://www.youtube.com/c/$name")

    suspend fun byId(channelId: String): ChannelInfo = load("https://www.youtube.com/channel/$channelId")

    private suspend fun load(url: String): ChannelInfo {
        val response = http.get(url, headers)
        if (response.status == 404) throw ProviderError.NotFound
        if (!response.isSuccess) throw ProviderError.Http(response.status)
        return parse(response.body) ?: throw ProviderError.Malformed("channel page")
    }

    internal fun parse(html: String): ChannelInfo? {
        val id = meta(html, "itemprop", "identifier") ?: return null
        val title = meta(html, "property", "og:title") ?: return null
        return ChannelInfo(
            channelId = id,
            title = title,
            thumbnailUrl = meta(html, "property", "og:image").orEmpty(),
            description = meta(html, "property", "og:description").orEmpty(),
            uploadsPlaylistId = if (id.startsWith("UC")) "UU" + id.removePrefix("UC") else null
        )
    }

    private fun meta(html: String, attribute: String, value: String): String? {
        val pattern = Regex("""<meta[^>]*\b$attribute=["']$value["'][^>]*>""", RegexOption.IGNORE_CASE)
        val tag = pattern.find(html)?.value ?: return null
        return Regex("""content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }
}
