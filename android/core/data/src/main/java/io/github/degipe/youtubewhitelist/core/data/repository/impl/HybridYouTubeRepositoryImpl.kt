package io.github.degipe.youtubewhitelist.core.data.repository.impl

import io.github.degipe.youtubewhitelist.core.common.di.IoDispatcher
import io.github.degipe.youtubewhitelist.core.common.result.AppResult
import io.github.degipe.youtubewhitelist.core.data.mapper.InvidiousMapper
import io.github.degipe.youtubewhitelist.core.data.mapper.OEmbedMapper
import io.github.degipe.youtubewhitelist.core.data.model.PaginatedPlaylistResult
import io.github.degipe.youtubewhitelist.core.data.model.PlaylistVideo
import io.github.degipe.youtubewhitelist.core.data.model.YouTubeMetadata
import io.github.degipe.youtubewhitelist.core.data.repository.YouTubeApiRepository
import io.github.degipe.youtubewhitelist.core.network.api.YouTubeApiService
import io.github.degipe.youtubewhitelist.core.network.dto.ThumbnailSet
import io.github.degipe.youtubewhitelist.core.network.invidious.InvidiousApiService
import io.github.degipe.youtubewhitelist.core.network.invidious.InvidiousInstanceManager
import io.github.degipe.youtubewhitelist.core.network.oembed.OEmbedService
import io.github.degipe.youtubewhitelist.core.network.rss.RssFeedParser
import io.github.degipe.youtubewhitelist.core.network.rss.RssVideoEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import io.github.degipe.youtubewhitelist.core.common.ui.UiMessage
import io.github.degipe.youtubewhitelist.core.common.R

class HybridYouTubeRepositoryImpl @Inject constructor(
    private val youTubeApiService: YouTubeApiService,
    private val oEmbedService: OEmbedService,
    private val rssFeedParser: RssFeedParser,
    private val invidiousApiService: InvidiousApiService,
    private val invidiousInstanceManager: InvidiousInstanceManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : YouTubeApiRepository {

    override suspend fun getVideoById(videoId: String): AppResult<YouTubeMetadata.Video> =
        withContext(ioDispatcher) {
            // Try 1: oEmbed (free, no quota)
            tryOEmbedVideo(videoId)
                ?: tryApiVideo(videoId)
                ?: tryInvidiousVideo(videoId)
                ?: AppResult.Error(
            message = "video metadata unavailable",
            uiMessage = UiMessage(R.string.error_video_metadata)
        )
        }

    override suspend fun getPlaylistById(playlistId: String): AppResult<YouTubeMetadata.Playlist> =
        withContext(ioDispatcher) {
            // Try 1: oEmbed (free, no quota)
            tryOEmbedPlaylist(playlistId)
                ?: tryApiPlaylist(playlistId)
                ?: tryInvidiousPlaylist(playlistId)
                ?: AppResult.Error(
            message = "playlist metadata unavailable",
            uiMessage = UiMessage(R.string.error_playlist_metadata)
        )
        }

    override suspend fun getChannelById(channelId: String): AppResult<YouTubeMetadata.Channel> =
        withContext(ioDispatcher) {
            // No oEmbed for channels — go directly to API
            tryApiChannel(channelId)
                ?: tryInvidiousChannel(channelId)
                ?: AppResult.Error(
            message = "channel metadata unavailable",
            uiMessage = UiMessage(R.string.error_channel_metadata)
        )
        }

    override suspend fun getChannelByHandle(handle: String): AppResult<YouTubeMetadata.Channel> =
        withContext(ioDispatcher) {
            // No free alternative for handle resolution
            tryApiChannelByHandle(handle)
                ?: tryInvidiousChannelByHandle(handle)
                ?: AppResult.Error(
            message = "channel handle unresolved",
            uiMessage = UiMessage(R.string.error_channel_handle)
        )
        }

    override suspend fun getPlaylistItems(playlistId: String): AppResult<List<PlaylistVideo>> =
        withContext(ioDispatcher) {
            // Try 1: RSS feed (free, max 15 videos — only for channel uploads playlists)
            // RSS only works with channel IDs, not arbitrary playlist IDs.
            // For uploads playlists (UU...), extract the channel ID.
            val channelId = extractChannelIdFromUploadsPlaylist(playlistId)
            val rssResult = if (channelId != null) tryRssFeed(channelId) else null

            rssResult
                ?: tryApiPlaylistItems(playlistId)
                ?: tryInvidiousPlaylistItems(playlistId)
                ?: AppResult.Error(
            message = "playlist items unavailable",
            uiMessage = UiMessage(R.string.error_playlist_items)
        )
        }

    override suspend fun getPlaylistItemsPage(
        playlistId: String,
        pageToken: String?
    ): AppResult<PaginatedPlaylistResult> = withContext(ioDispatcher) {
        // pageToken != null means continuation — skip RSS (no pagination support)
        if (pageToken == null) {
            val channelId = extractChannelIdFromUploadsPlaylist(playlistId)
            val rssResult = if (channelId != null) tryRssFeedPaginated(channelId) else null
            if (rssResult != null) return@withContext rssResult
        }

        tryApiPlaylistItemsPage(playlistId, pageToken)
            ?: tryInvidiousPlaylistItemsPage(playlistId)
            ?: AppResult.Error(
            message = "playlist items unavailable",
            uiMessage = UiMessage(R.string.error_playlist_items)
        )
    }

    override suspend fun searchVideosInChannel(
        channelId: String,
        query: String
    ): AppResult<List<PlaylistVideo>> = withContext(ioDispatcher) {
        // Search was removed from kid mode; this is kept for API compatibility
        // Only YouTube API supports search — no oEmbed/RSS/Invidious alternative
        tryApiSearch(channelId, query)
            ?: AppResult.Error(
            message = "search failed",
            uiMessage = UiMessage(R.string.error_search_failed)
        )
    }

    override suspend fun searchChannels(
        query: String
    ): AppResult<List<YouTubeMetadata.Channel>> = withContext(ioDispatcher) {
        // A handle lookup costs one quota unit, a search costs a hundred, and it
        // still works once the daily search quota is used up. Only for an
        // explicit "@handle" though: a handle does not have to belong to the
        // channel of the same name, and approving the wrong channel would be
        // worse than spending quota.
        if (query.trim().startsWith("@")) {
            tryHandleAsSearchResult(query)?.let { return@withContext it }
        }

        val apiResponseCode = IntArray(1)
        tryApiChannelSearch(query, apiResponseCode)
            ?: tryInvidiousChannelSearch(query)
            ?: AppResult.Error(
                message = "channel search failed (${apiResponseCode[0]})",
                uiMessage = if (apiResponseCode[0] == HTTP_TOO_MANY_REQUESTS) {
                    UiMessage(R.string.error_rate_limited)
                } else {
                    UiMessage(R.string.error_search_failed)
                }
            )
    }

    /** Resolves an explicit "@handle" query without spending search quota. */
    private suspend fun tryHandleAsSearchResult(
        query: String
    ): AppResult<List<YouTubeMetadata.Channel>>? {
        val handle = query.trim().removePrefix("@").replace(" ", "")
        if (handle.isBlank()) return null

        val result = tryApiChannelByHandle(handle) ?: return null
        return (result as? AppResult.Success)?.let { AppResult.Success(listOf(it.data)) }
    }

    private suspend fun tryApiChannelSearch(
        query: String,
        responseCodeOut: IntArray
    ): AppResult<List<YouTubeMetadata.Channel>>? {
        return try {
            val response = youTubeApiService.search(query = query, type = "channel")
            responseCodeOut[0] = response.code()
            if (!response.isSuccessful) return null
            val channels = response.body()?.items.orEmpty().mapNotNull { item ->
                val channelId = item.id?.channelId ?: return@mapNotNull null
                val snippet = item.snippet ?: return@mapNotNull null
                YouTubeMetadata.Channel(
                    youtubeId = channelId,
                    title = snippet.title,
                    thumbnailUrl = snippet.thumbnails.bestUrl(),
                    description = snippet.description,
                    subscriberCount = null,
                    videoCount = null,
                    uploadsPlaylistId = null
                )
            }
            AppResult.Success(channels)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryInvidiousChannelSearch(
        query: String
    ): AppResult<List<YouTubeMetadata.Channel>>? {
        return withInvidiousFallback { baseUrl ->
            val results = invidiousApiService.searchChannels(baseUrl, query)
            AppResult.Success(results.map { InvidiousMapper.toChannel(it) })
        }
    }

    // --- oEmbed ---

    private suspend fun tryOEmbedVideo(videoId: String): AppResult<YouTubeMetadata.Video>? {
        return try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val response = oEmbedService.getOEmbed(url)
            if (response.isSuccessful) {
                val body = response.body() ?: return null
                AppResult.Success(OEmbedMapper.toVideo(videoId, body))
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryOEmbedPlaylist(playlistId: String): AppResult<YouTubeMetadata.Playlist>? {
        return try {
            val url = "https://www.youtube.com/playlist?list=$playlistId"
            val response = oEmbedService.getOEmbed(url)
            if (response.isSuccessful) {
                val body = response.body() ?: return null
                AppResult.Success(OEmbedMapper.toPlaylist(playlistId, body))
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // --- RSS ---

    private suspend fun tryRssFeed(channelId: String): AppResult<List<PlaylistVideo>>? {
        return try {
            val entries = rssFeedParser.fetchChannelVideos(channelId)
            if (entries.isNotEmpty()) {
                AppResult.Success(entries.mapIndexed { index, entry -> entry.toPlaylistVideo(index) })
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryRssFeedPaginated(channelId: String): AppResult<PaginatedPlaylistResult>? {
        return try {
            val entries = rssFeedParser.fetchChannelVideos(channelId)
            if (entries.isNotEmpty()) {
                val videos = entries.mapIndexed { index, entry -> entry.toPlaylistVideo(index) }
                AppResult.Success(PaginatedPlaylistResult(videos = videos, nextPageToken = null))
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // --- YouTube API ---

    private suspend fun tryApiVideo(videoId: String): AppResult<YouTubeMetadata.Video>? {
        return try {
            val response = youTubeApiService.getVideos(id = videoId)
            if (!response.isSuccessful) return null
            val video = response.body()?.items?.firstOrNull() ?: return null
            AppResult.Success(
                YouTubeMetadata.Video(
                    youtubeId = video.id,
                    title = video.snippet?.title ?: "",
                    thumbnailUrl = video.snippet?.thumbnails.bestUrl(),
                    channelId = video.snippet?.channelId ?: "",
                    channelTitle = video.snippet?.channelTitle ?: "",
                    description = video.snippet?.description ?: "",
                    duration = video.contentDetails?.duration
                )
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryApiPlaylist(playlistId: String): AppResult<YouTubeMetadata.Playlist>? {
        return try {
            val response = youTubeApiService.getPlaylists(id = playlistId)
            if (!response.isSuccessful) return null
            val playlist = response.body()?.items?.firstOrNull() ?: return null
            AppResult.Success(
                YouTubeMetadata.Playlist(
                    youtubeId = playlist.id,
                    title = playlist.snippet?.title ?: "",
                    thumbnailUrl = playlist.snippet?.thumbnails.bestUrl(),
                    channelId = playlist.snippet?.channelId ?: "",
                    channelTitle = playlist.snippet?.channelTitle ?: "",
                    description = playlist.snippet?.description ?: ""
                )
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryApiChannel(channelId: String): AppResult<YouTubeMetadata.Channel>? {
        return try {
            val response = youTubeApiService.getChannels(id = channelId)
            if (!response.isSuccessful) return null
            val channel = response.body()?.items?.firstOrNull() ?: return null
            AppResult.Success(
                YouTubeMetadata.Channel(
                    youtubeId = channel.id,
                    title = channel.snippet?.title ?: "",
                    thumbnailUrl = channel.snippet?.thumbnails.bestUrl(),
                    description = channel.snippet?.description ?: "",
                    subscriberCount = channel.statistics?.subscriberCount,
                    videoCount = channel.statistics?.videoCount,
                    uploadsPlaylistId = channel.contentDetails?.relatedPlaylists?.uploads
                )
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryApiChannelByHandle(handle: String): AppResult<YouTubeMetadata.Channel>? {
        return try {
            val response = youTubeApiService.getChannels(forHandle = handle)
            if (!response.isSuccessful) return null
            val channel = response.body()?.items?.firstOrNull() ?: return null
            AppResult.Success(
                YouTubeMetadata.Channel(
                    youtubeId = channel.id,
                    title = channel.snippet?.title ?: "",
                    thumbnailUrl = channel.snippet?.thumbnails.bestUrl(),
                    description = channel.snippet?.description ?: "",
                    subscriberCount = channel.statistics?.subscriberCount,
                    videoCount = channel.statistics?.videoCount,
                    uploadsPlaylistId = channel.contentDetails?.relatedPlaylists?.uploads
                )
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryApiPlaylistItems(playlistId: String): AppResult<List<PlaylistVideo>>? {
        return try {
            val response = youTubeApiService.getPlaylistItems(playlistId = playlistId)
            if (!response.isSuccessful) return null
            val items = response.body()?.items.orEmpty()
            val videos = items.mapNotNull { item ->
                val snippet = item.snippet ?: return@mapNotNull null
                val videoId = snippet.resourceId?.videoId ?: return@mapNotNull null
                PlaylistVideo(
                    videoId = videoId,
                    title = snippet.title,
                    thumbnailUrl = snippet.thumbnails.bestUrl(),
                    channelTitle = snippet.channelTitle,
                    position = snippet.position
                )
            }
            AppResult.Success(videos)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryApiPlaylistItemsPage(
        playlistId: String,
        pageToken: String?
    ): AppResult<PaginatedPlaylistResult>? {
        return try {
            val response = youTubeApiService.getPlaylistItems(
                playlistId = playlistId,
                pageToken = pageToken
            )
            if (!response.isSuccessful) return null
            val body = response.body() ?: return null
            val videos = body.items.mapNotNull { item ->
                val snippet = item.snippet ?: return@mapNotNull null
                val videoId = snippet.resourceId?.videoId ?: return@mapNotNull null
                PlaylistVideo(
                    videoId = videoId,
                    title = snippet.title,
                    thumbnailUrl = snippet.thumbnails.bestUrl(),
                    channelTitle = snippet.channelTitle,
                    position = snippet.position
                )
            }
            AppResult.Success(PaginatedPlaylistResult(videos = videos, nextPageToken = body.nextPageToken))
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryApiSearch(channelId: String, query: String): AppResult<List<PlaylistVideo>>? {
        return try {
            val response = youTubeApiService.search(channelId = channelId, query = query, maxResults = 10)
            if (!response.isSuccessful) return null
            val items = response.body()?.items.orEmpty()
            val videos = items.mapNotNull { item ->
                val videoId = item.id?.videoId ?: return@mapNotNull null
                val snippet = item.snippet ?: return@mapNotNull null
                PlaylistVideo(
                    videoId = videoId,
                    title = snippet.title,
                    thumbnailUrl = snippet.thumbnails.bestUrl(),
                    channelTitle = snippet.channelTitle,
                    position = 0
                )
            }
            AppResult.Success(videos)
        } catch (_: Exception) {
            null
        }
    }

    // --- Invidious ---

    private suspend fun tryInvidiousVideo(videoId: String): AppResult<YouTubeMetadata.Video>? {
        return withInvidiousFallback { baseUrl ->
            val dto = invidiousApiService.getVideo(baseUrl, videoId)
            AppResult.Success(InvidiousMapper.toVideo(dto))
        }
    }

    private suspend fun tryInvidiousPlaylist(playlistId: String): AppResult<YouTubeMetadata.Playlist>? {
        return withInvidiousFallback { baseUrl ->
            val dto = invidiousApiService.getPlaylist(baseUrl, playlistId)
            AppResult.Success(InvidiousMapper.toPlaylist(dto))
        }
    }

    private suspend fun tryInvidiousChannel(channelId: String): AppResult<YouTubeMetadata.Channel>? {
        return withInvidiousFallback { baseUrl ->
            val dto = invidiousApiService.getChannel(baseUrl, channelId)
            AppResult.Success(InvidiousMapper.toChannel(dto))
        }
    }

    private suspend fun tryInvidiousChannelByHandle(handle: String): AppResult<YouTubeMetadata.Channel>? {
        return withInvidiousFallback { baseUrl ->
            val channelId = invidiousApiService.resolveChannel(baseUrl, handle)
            val dto = invidiousApiService.getChannel(baseUrl, channelId)
            AppResult.Success(InvidiousMapper.toChannel(dto))
        }
    }

    private suspend fun tryInvidiousPlaylistItemsPage(playlistId: String): AppResult<PaginatedPlaylistResult>? {
        val channelId = extractChannelIdFromUploadsPlaylist(playlistId)
        if (channelId != null) {
            return withInvidiousFallback { baseUrl ->
                val dto = invidiousApiService.getChannel(baseUrl, channelId)
                val videos = InvidiousMapper.channelVideosToPlaylistVideos(dto)
                AppResult.Success(PaginatedPlaylistResult(videos = videos, nextPageToken = null))
            }
        }
        return withInvidiousFallback { baseUrl ->
            val dto = invidiousApiService.getPlaylist(baseUrl, playlistId)
            val videos = InvidiousMapper.playlistVideosToPlaylistVideos(dto)
            AppResult.Success(PaginatedPlaylistResult(videos = videos, nextPageToken = null))
        }
    }

    private suspend fun tryInvidiousPlaylistItems(playlistId: String): AppResult<List<PlaylistVideo>>? {
        // Check if this is a channel uploads playlist (UU...) — use channel endpoint
        val channelId = extractChannelIdFromUploadsPlaylist(playlistId)
        if (channelId != null) {
            return withInvidiousFallback { baseUrl ->
                val dto = invidiousApiService.getChannel(baseUrl, channelId)
                AppResult.Success(InvidiousMapper.channelVideosToPlaylistVideos(dto))
            }
        }
        // Otherwise, it's a regular playlist
        return withInvidiousFallback { baseUrl ->
            val dto = invidiousApiService.getPlaylist(baseUrl, playlistId)
            AppResult.Success(InvidiousMapper.playlistVideosToPlaylistVideos(dto))
        }
    }

    private suspend fun <T> withInvidiousFallback(
        block: suspend (baseUrl: String) -> AppResult<T>
    ): AppResult<T>? {
        // Try up to 3 instances
        repeat(3) {
            val baseUrl = invidiousInstanceManager.getHealthyInstance() ?: return null
            try {
                val result = block(baseUrl)
                invidiousInstanceManager.reportSuccess(baseUrl)
                return result
            } catch (_: IOException) {
                // Network error — mark instance as unhealthy
                invidiousInstanceManager.reportFailure(baseUrl)
            } catch (_: Exception) {
                // An instance that answers with something undecodable is broken
                // too — otherwise the loop keeps asking the same one.
                invidiousInstanceManager.reportFailure(baseUrl)
            }
        }
        return null
    }

    // --- Utilities ---

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
    }

    private fun extractChannelIdFromUploadsPlaylist(playlistId: String): String? {
        // YouTube uploads playlists have the format "UU" + channelId (without "UC" prefix)
        // e.g., channel UC-yBVzHNBKEx34GBJf8WNQA → uploads playlist UU-yBVzHNBKEx34GBJf8WNQA
        return if (playlistId.startsWith("UU")) {
            "UC${playlistId.removePrefix("UU")}"
        } else null
    }

    private fun RssVideoEntry.toPlaylistVideo(index: Int) = PlaylistVideo(
        videoId = videoId,
        title = title,
        thumbnailUrl = thumbnailUrl,
        channelTitle = channelTitle,
        position = index
    )

    private fun ThumbnailSet?.bestUrl(): String {
        if (this == null) return ""
        return high?.url?.takeIf { it.isNotBlank() }
            ?: medium?.url?.takeIf { it.isNotBlank() }
            ?: default?.url?.takeIf { it.isNotBlank() }
            ?: ""
    }
}
