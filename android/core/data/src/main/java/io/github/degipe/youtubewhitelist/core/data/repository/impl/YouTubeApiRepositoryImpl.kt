package io.github.degipe.youtubewhitelist.core.data.repository.impl

import io.github.degipe.youtubewhitelist.core.common.di.IoDispatcher
import io.github.degipe.youtubewhitelist.core.common.result.AppResult
import io.github.degipe.youtubewhitelist.core.data.model.PaginatedPlaylistResult
import io.github.degipe.youtubewhitelist.core.data.model.PlaylistVideo
import io.github.degipe.youtubewhitelist.core.data.model.YouTubeMetadata
import io.github.degipe.youtubewhitelist.core.data.repository.YouTubeApiRepository
import io.github.degipe.youtubewhitelist.core.network.api.YouTubeApiService
import io.github.degipe.youtubewhitelist.core.network.dto.ChannelDto
import io.github.degipe.youtubewhitelist.core.network.dto.PlaylistDto
import io.github.degipe.youtubewhitelist.core.network.dto.ThumbnailSet
import io.github.degipe.youtubewhitelist.core.network.dto.VideoDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import io.github.degipe.youtubewhitelist.core.common.ui.UiMessage
import io.github.degipe.youtubewhitelist.core.common.R

class YouTubeApiRepositoryImpl @Inject constructor(
    private val youTubeApiService: YouTubeApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : YouTubeApiRepository {

    override suspend fun getChannelById(channelId: String): AppResult<YouTubeMetadata.Channel> =
        withContext(ioDispatcher) {
            safeApiCall {
                val response = youTubeApiService.getChannels(id = channelId)
                if (!response.isSuccessful) {
                    return@safeApiCall AppResult.Error("API error: ${response.code()}")
                }
                val channel = response.body()?.items?.firstOrNull()
                    ?: return@safeApiCall AppResult.Error("channel not found", uiMessage = UiMessage(R.string.error_channel_not_found))
                AppResult.Success(channel.toDomain())
            }
        }

    override suspend fun getChannelByHandle(handle: String): AppResult<YouTubeMetadata.Channel> =
        withContext(ioDispatcher) {
            safeApiCall {
                val response = youTubeApiService.getChannels(forHandle = handle)
                if (!response.isSuccessful) {
                    return@safeApiCall AppResult.Error("API error: ${response.code()}")
                }
                val channel = response.body()?.items?.firstOrNull()
                    ?: return@safeApiCall AppResult.Error("channel not found for handle @$handle", uiMessage = UiMessage(R.string.error_channel_not_found))
                AppResult.Success(channel.toDomain())
            }
        }

    override suspend fun getVideoById(videoId: String): AppResult<YouTubeMetadata.Video> =
        withContext(ioDispatcher) {
            safeApiCall {
                val response = youTubeApiService.getVideos(id = videoId)
                if (!response.isSuccessful) {
                    return@safeApiCall AppResult.Error("API error: ${response.code()}")
                }
                val video = response.body()?.items?.firstOrNull()
                    ?: return@safeApiCall AppResult.Error("video not found", uiMessage = UiMessage(R.string.error_video_not_found))
                AppResult.Success(video.toDomain())
            }
        }

    override suspend fun getPlaylistById(playlistId: String): AppResult<YouTubeMetadata.Playlist> =
        withContext(ioDispatcher) {
            safeApiCall {
                val response = youTubeApiService.getPlaylists(id = playlistId)
                if (!response.isSuccessful) {
                    return@safeApiCall AppResult.Error("API error: ${response.code()}")
                }
                val playlist = response.body()?.items?.firstOrNull()
                    ?: return@safeApiCall AppResult.Error("playlist not found", uiMessage = UiMessage(R.string.error_playlist_not_found))
                AppResult.Success(playlist.toDomain())
            }
        }

    override suspend fun getPlaylistItems(playlistId: String): AppResult<List<PlaylistVideo>> =
        withContext(ioDispatcher) {
            safeApiCall {
                val response = youTubeApiService.getPlaylistItems(playlistId = playlistId)
                if (!response.isSuccessful) {
                    return@safeApiCall AppResult.Error("API error: ${response.code()}")
                }
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
            }
        }

    override suspend fun getPlaylistItemsPage(
        playlistId: String,
        pageToken: String?
    ): AppResult<PaginatedPlaylistResult> = withContext(ioDispatcher) {
        safeApiCall {
            val response = youTubeApiService.getPlaylistItems(
                playlistId = playlistId,
                pageToken = pageToken
            )
            if (!response.isSuccessful) {
                return@safeApiCall AppResult.Error("API error: ${response.code()}")
            }
            val body = response.body()
            val items = body?.items.orEmpty()
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
            AppResult.Success(PaginatedPlaylistResult(videos = videos, nextPageToken = body?.nextPageToken))
        }
    }

    override suspend fun searchChannels(
        query: String
    ): AppResult<List<YouTubeMetadata.Channel>> = withContext(ioDispatcher) {
        safeApiCall {
            val response = youTubeApiService.search(query = query, type = "channel")
            if (!response.isSuccessful) {
                return@safeApiCall AppResult.Error(
                    "API error: ${response.code()}",
                    uiMessage = UiMessage(R.string.error_search_failed)
                )
            }
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
        }
    }

    override suspend fun searchVideosInChannel(
        channelId: String,
        query: String
    ): AppResult<List<PlaylistVideo>> = withContext(ioDispatcher) {
        safeApiCall {
            val response = youTubeApiService.search(
                channelId = channelId,
                query = query,
                maxResults = 10
            )
            if (!response.isSuccessful) {
                return@safeApiCall AppResult.Error("API error: ${response.code()}")
            }
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
        }
    }

    private suspend fun <T> safeApiCall(block: suspend () -> AppResult<T>): AppResult<T> {
        return try {
            block()
        } catch (e: IOException) {
            AppResult.Error("network error", e, UiMessage(R.string.error_network))
        } catch (e: Exception) {
            AppResult.Error("unexpected error", e, UiMessage(R.string.error_unexpected))
        }
    }

    companion object {
        fun ChannelDto.toDomain(): YouTubeMetadata.Channel = YouTubeMetadata.Channel(
            youtubeId = id,
            title = snippet?.title ?: "",
            thumbnailUrl = snippet?.thumbnails.bestUrl(),
            description = snippet?.description ?: "",
            subscriberCount = statistics?.subscriberCount,
            videoCount = statistics?.videoCount,
            uploadsPlaylistId = contentDetails?.relatedPlaylists?.uploads
        )

        fun VideoDto.toDomain(): YouTubeMetadata.Video = YouTubeMetadata.Video(
            youtubeId = id,
            title = snippet?.title ?: "",
            thumbnailUrl = snippet?.thumbnails.bestUrl(),
            channelId = snippet?.channelId ?: "",
            channelTitle = snippet?.channelTitle ?: "",
            description = snippet?.description ?: "",
            duration = contentDetails?.duration
        )

        fun PlaylistDto.toDomain(): YouTubeMetadata.Playlist = YouTubeMetadata.Playlist(
            youtubeId = id,
            title = snippet?.title ?: "",
            thumbnailUrl = snippet?.thumbnails.bestUrl(),
            channelId = snippet?.channelId ?: "",
            channelTitle = snippet?.channelTitle ?: "",
            description = snippet?.description ?: ""
        )

        fun ThumbnailSet?.bestUrl(): String {
            if (this == null) return ""
            return high?.url?.takeIf { it.isNotBlank() }
                ?: medium?.url?.takeIf { it.isNotBlank() }
                ?: default?.url?.takeIf { it.isNotBlank() }
                ?: ""
        }
    }
}
