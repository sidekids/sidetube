package io.github.degipe.youtubewhitelist.core.network.invidious

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class InvidiousApiService(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {

    suspend fun getVideo(baseUrl: String, videoId: String): InvidiousVideoDto {
        val url = "$baseUrl/api/v1/videos/$videoId?fields=videoId,title,author,authorId,videoThumbnails"
        val body = fetchJson(url)
        return json.decodeFromString<InvidiousVideoDto>(body)
    }

    suspend fun getChannel(baseUrl: String, channelId: String): InvidiousChannelDto {
        val url = "$baseUrl/api/v1/channels/$channelId?fields=authorId,author,authorThumbnails,latestVideos"
        val body = fetchJson(url)
        return json.decodeFromString<InvidiousChannelDto>(body)
    }

    suspend fun getPlaylist(baseUrl: String, playlistId: String): InvidiousPlaylistDto {
        val url = "$baseUrl/api/v1/playlists/$playlistId"
        val body = fetchJson(url)
        return json.decodeFromString<InvidiousPlaylistDto>(body)
    }

    suspend fun resolveChannel(baseUrl: String, handle: String): String {
        val encodedUrl = java.net.URLEncoder.encode(
            "https://www.youtube.com/@$handle", "UTF-8"
        )
        val url = "$baseUrl/api/v1/resolveurl?url=$encodedUrl"
        val body = fetchJson(url)
        val response = json.decodeFromString<ResolveUrlResponse>(body)
        return response.ucid
    }

    /** Channel search for the parent area; used when the YouTube API is unavailable. */
    suspend fun searchChannels(baseUrl: String, query: String): List<InvidiousChannelDto> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/api/v1/search?q=$encodedQuery&type=channel"
        val body = fetchJson(url)
        return json.decodeFromString<List<InvidiousChannelDto>>(body)
    }

    private fun fetchJson(url: String): String {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Invidious API error: ${response.code}")
        }
        val body = response.body?.string() ?: throw IOException("Empty response body")
        // Several public instances answer with an anti-bot page and status 200.
        // That is a broken instance, not data — report it so the next one is used.
        val firstChar = body.trimStart().firstOrNull()
        if (firstChar != '{' && firstChar != '[') {
            throw IOException("Invidious instance returned no JSON")
        }
        return body
    }

    @kotlinx.serialization.Serializable
    internal data class ResolveUrlResponse(
        val ucid: String = "",
        val pageType: String = ""
    )
}
