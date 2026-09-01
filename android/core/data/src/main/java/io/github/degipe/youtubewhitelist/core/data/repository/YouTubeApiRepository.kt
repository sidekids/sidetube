package io.github.degipe.youtubewhitelist.core.data.repository

import io.github.degipe.youtubewhitelist.core.common.result.AppResult
import io.github.degipe.youtubewhitelist.core.data.model.PaginatedPlaylistResult
import io.github.degipe.youtubewhitelist.core.data.model.PlaylistVideo
import io.github.degipe.youtubewhitelist.core.data.model.YouTubeMetadata

interface YouTubeApiRepository {
    suspend fun getChannelById(channelId: String): AppResult<YouTubeMetadata.Channel>
    suspend fun getChannelByHandle(handle: String): AppResult<YouTubeMetadata.Channel>
    suspend fun getVideoById(videoId: String): AppResult<YouTubeMetadata.Video>
    suspend fun getPlaylistById(playlistId: String): AppResult<YouTubeMetadata.Playlist>
    suspend fun getPlaylistItems(playlistId: String): AppResult<List<PlaylistVideo>>
    suspend fun getPlaylistItemsPage(playlistId: String, pageToken: String? = null): AppResult<PaginatedPlaylistResult>
    suspend fun searchVideosInChannel(channelId: String, query: String): AppResult<List<PlaylistVideo>>

    /**
     * Channel search for the parent area. The kid side never searches YouTube;
     * this is the only way to approve a subscription on devices whose WebView
     * cannot render youtube.com.
     */
    suspend fun searchChannels(query: String): AppResult<List<YouTubeMetadata.Channel>>
}
