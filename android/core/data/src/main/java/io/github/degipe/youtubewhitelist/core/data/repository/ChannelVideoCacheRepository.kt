package io.github.degipe.youtubewhitelist.core.data.repository

import io.github.degipe.youtubewhitelist.core.data.model.PlaylistVideo
import kotlinx.coroutines.flow.Flow

interface ChannelVideoCacheRepository {
    fun getVideos(channelId: String): Flow<List<PlaylistVideo>>
    fun searchVideos(channelId: String, query: String): Flow<List<PlaylistVideo>>

    /**
     * Cached videos for the given ids, restricted to channels that are still
     * whitelisted — a removed channel must not resurface through the cache.
     */
    fun getVideosByIds(videoIds: List<String>, channelIds: List<String>): Flow<List<PlaylistVideo>>
    suspend fun cacheVideos(channelId: String, videos: List<PlaylistVideo>)

    /** Replaces the cached videos of a channel in one step (single UI update). */
    suspend fun replaceVideos(channelId: String, videos: List<PlaylistVideo>)
    suspend fun clearCache(channelId: String)
}
