package io.github.degipe.youtubewhitelist.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.degipe.youtubewhitelist.core.database.entity.CachedChannelVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedChannelVideoDao {

    @Query("SELECT * FROM cached_channel_videos WHERE channelId = :channelId ORDER BY position ASC")
    fun getVideosByChannel(channelId: String): Flow<List<CachedChannelVideoEntity>>

    @Query("SELECT * FROM cached_channel_videos WHERE channelId = :channelId AND title LIKE '%' || :query || '%' ORDER BY position ASC")
    fun searchVideosInChannel(channelId: String, query: String): Flow<List<CachedChannelVideoEntity>>

    @Query(
        "SELECT * FROM cached_channel_videos " +
            "WHERE videoId IN (:videoIds) AND channelId IN (:channelIds)"
    )
    fun getVideosByIds(
        videoIds: List<String>,
        channelIds: List<String>
    ): Flow<List<CachedChannelVideoEntity>>

    @Upsert
    suspend fun upsertAll(videos: List<CachedChannelVideoEntity>)

    @Query("DELETE FROM cached_channel_videos WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: String)

    /**
     * Swaps the cached page of a channel in one transaction, so observers see a
     * single update instead of an empty list followed by the new one.
     */
    @Transaction
    suspend fun replaceChannelVideos(channelId: String, videos: List<CachedChannelVideoEntity>) {
        deleteByChannel(channelId)
        upsertAll(videos)
    }

    @Query("SELECT MAX(position) FROM cached_channel_videos WHERE channelId = :channelId")
    suspend fun getMaxPosition(channelId: String): Int?
}
