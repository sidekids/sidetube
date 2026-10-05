// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.provider

import xyz.steier.sidetube.core.model.ContentProvider
import xyz.steier.sidetube.core.model.WhitelistItemType

/** Was ein Anbieter zu einer Adresse liefert – die Vorstufe eines Whitelist-Eintrags. */
data class ContentDraft(
    val type: WhitelistItemType,
    val contentId: String,
    val title: String,
    val thumbnailUrl: String = "",
    val channelTitle: String? = null,
    val provider: ContentProvider = ContentProvider.YOUTUBE,
    val sourceChannelId: String? = null,
    val sourceUrl: String? = null,
    val description: String? = null,
    val durationSeconds: Int? = null,
    /** Kanaladresse laut oEmbed (`author_url`) – damit laesst sich zu einem Video der Kanal finden. */
    val channelUrl: String? = null
)

data class ChannelInfo(
    val channelId: String,
    val title: String,
    val thumbnailUrl: String = "",
    val description: String = "",
    val uploadsPlaylistId: String? = null
)

data class ChannelVideo(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelTitle: String,
    val position: Int,
    /** Kanal des einzelnen Videos; in einer Playlist kann jedes von einem anderen Kanal stammen. */
    val channelId: String? = null,
    /** Veroeffentlichung laut Feed (Millisekunden); `null`, wenn unbekannt oder angekuendigt (`views="0"`). */
    val publishedAt: Long? = null,
    /** Der Feed verlinkt das Video unter `/shorts/`. */
    val isShort: Boolean = false,
    /** Angekuendigte Premiere oder Livestream (`views="0"` im Feed), noch nicht gesendet. */
    val isUpcoming: Boolean = false
)

data class ChannelPage(val videos: List<ChannelVideo>, val nextPageToken: String?)

/** Fehler, die Eltern verstehen sollen – der Text dazu gehoert in die Oberflaeche, nicht hierher. */
sealed class ProviderError(message: String) : Exception(message) {
    object NotFound : ProviderError("nicht gefunden")
    object MissingApiKey : ProviderError("kein API-Schluessel")
    object Unsupported : ProviderError("nicht unterstuetzt")
    data class Http(val status: Int) : ProviderError("HTTP $status")
    data class Malformed(val where: String) : ProviderError("unerwartete Antwort: $where")
}

/** Vorschaubilder in der Groesse holen, in der sie gezeigt werden. */
object YouTubeThumbnails {
    fun url(videoId: String, widthPx: Int): String {
        val variant = when {
            widthPx <= 120 -> "default"
            widthPx <= 320 -> "mqdefault"
            else -> "hqdefault"
        }
        return "https://i.ytimg.com/vi/$videoId/$variant.jpg"
    }

    /**
     * Passt eine vorhandene Adresse an die Anzeigebreite an. YouTube liefert dieselbe Datei in
     * vier Stufen: `hqdefault` misst 480 x 360 und wiegt 40 kB, `default` misst 120 x 90 und
     * wiegt 5 kB. Fuer eine Listenzeile ist die grosse Fassung achtmal zu schwer.
     */
    fun resize(url: String, widthPx: Int): String {
        if (!url.contains("i.ytimg.com/vi/")) return url
        val variant = when {
            widthPx <= 120 -> "default"
            widthPx <= 320 -> "mqdefault"
            else -> "hqdefault"
        }
        for (known in listOf("maxresdefault", "sddefault", "hqdefault", "mqdefault", "default")) {
            if (url.contains("/$known.")) return url.replace("/$known.", "/$variant.")
        }
        return url
    }
}
