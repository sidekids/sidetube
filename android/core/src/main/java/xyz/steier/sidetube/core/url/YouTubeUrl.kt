// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.url

/** Was hinter einer eingegebenen Adresse steckt. */
sealed interface YouTubeTarget {
    data class Video(val id: String) : YouTubeTarget
    data class Playlist(val id: String) : YouTubeTarget
    data class Channel(val id: String) : YouTubeTarget
    data class ChannelHandle(val handle: String) : YouTubeTarget
    data class ChannelName(val name: String) : YouTubeTarget
}

/**
 * Erkennt YouTube-Adressen. Eltern fuegen Links ein, die sie irgendwo kopiert haben - mit und
 * ohne Schema, mit Tracking-Anhaengseln, als Kurzform oder als Freigabelink. Alles davon muss
 * zum richtigen Ziel fuehren, sonst scheitert die Aufnahme an einer Formalie.
 */
object YouTubeUrlParser {

    private val hosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")
    private val shortHosts = setOf("youtu.be", "www.youtu.be")

    fun parse(input: String): YouTubeTarget? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Ohne Schema laesst sich die Adresse nicht zerlegen; Eltern tippen es selten mit.
        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "https://$trimmed"

        val uri = runCatching { java.net.URI(normalized) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val query = queryOf(uri.rawQuery)

        if (host in shortHosts) {
            return segments.firstOrNull()?.let { videoOrNull(it) }
        }
        if (host !in hosts) return null

        // Eine Playlist-Kennung hat Vorrang: Wer eine Liste teilt, meint die Liste, auch wenn
        // der Link zusaetzlich das gerade laufende Video nennt.
        val list = query["list"]
        if (!list.isNullOrEmpty() && (segments.firstOrNull() == "playlist" || segments.firstOrNull() == "watch")) {
            return YouTubeTarget.Playlist(list)
        }

        return when (segments.firstOrNull()) {
            "watch" -> query["v"]?.let { videoOrNull(it) }
            "shorts", "embed", "live", "v" -> segments.getOrNull(1)?.let { videoOrNull(it) }
            "channel" -> segments.getOrNull(1)?.takeIf { it.startsWith("UC") }?.let { YouTubeTarget.Channel(it) }
            "c", "user" -> segments.getOrNull(1)?.let { YouTubeTarget.ChannelName(it) }
            "playlist" -> null
            else -> segments.firstOrNull()
                ?.takeIf { it.startsWith("@") && it.length > 1 }
                ?.let { YouTubeTarget.ChannelHandle(it.removePrefix("@")) }
        }
    }

    /** Video-Kennungen sind elf Zeichen aus einem festen Vorrat. */
    private fun videoOrNull(candidate: String): YouTubeTarget.Video? {
        val id = candidate.substringBefore('?').substringBefore('&')
        return if (id.length == 11 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            YouTubeTarget.Video(id)
        } else null
    }

    private fun queryOf(raw: String?): Map<String, String> =
        raw.orEmpty().split('&')
            .mapNotNull { part ->
                val name = part.substringBefore('=', "")
                val value = part.substringAfter('=', "")
                if (name.isEmpty() || value.isEmpty()) null else name to value
            }
            .toMap()
}
