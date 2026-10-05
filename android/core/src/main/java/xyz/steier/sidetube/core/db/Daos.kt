// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KidProfileDao {
    @Query("SELECT * FROM kid_profiles ORDER BY createdAt")
    fun observeAll(): Flow<List<KidProfileEntity>>

    @Query("SELECT * FROM kid_profiles WHERE id = :id")
    suspend fun byId(id: String): KidProfileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: KidProfileEntity)

    @Update
    suspend fun update(profile: KidProfileEntity)

    @Delete
    suspend fun delete(profile: KidProfileEntity)
}

@Dao
interface WhitelistDao {
    @Query("SELECT * FROM whitelist_items WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun observeByProfile(profileId: String): Flow<List<WhitelistItemEntity>>

    /** Nur, was das Kind sehen darf. Die Regel steht hier, nicht in der Oberflaeche. */
    @Query("SELECT * FROM whitelist_items WHERE profileId = :profileId AND approvalStatus = 'approved' ORDER BY addedAt DESC")
    fun observeApproved(profileId: String): Flow<List<WhitelistItemEntity>>

    @Query("SELECT * FROM whitelist_items WHERE profileId = :profileId AND approvalStatus IN ('reviewRequired', 'discovered', 'expiredReview') ORDER BY addedAt DESC")
    fun observePending(profileId: String): Flow<List<WhitelistItemEntity>>

    @Query("SELECT * FROM whitelist_items WHERE profileId = :profileId AND contentId = :contentId")
    suspend fun find(profileId: String, contentId: String): WhitelistItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: WhitelistItemEntity)

    @Update
    suspend fun update(item: WhitelistItemEntity)

    @Delete
    suspend fun delete(item: WhitelistItemEntity)
}

@Dao
interface CuratedSourceDao {
    @Query("SELECT * FROM curated_sources ORDER BY title")
    fun observeAll(): Flow<List<CuratedSourceEntity>>

    @Query("SELECT * FROM curated_sources WHERE channelId = :channelId")
    suspend fun byChannel(channelId: String): CuratedSourceEntity?

    /** Legt nur an, was fehlt: Eine Elternentscheidung darf ein spaeteres Register nicht ueberschreiben. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(sources: List<CuratedSourceEntity>)

    @Update
    suspend fun update(source: CuratedSourceEntity)
}

@Dao
interface ReviewEventDao {
    @Insert
    suspend fun insert(event: ReviewEventEntity)

    @Query("SELECT * FROM review_events WHERE contentId = :contentId ORDER BY at DESC")
    suspend fun forContent(contentId: String): List<ReviewEventEntity>

    /**
     * Beim Loeschen eines Profils: Ereignisse haengen nicht per Fremdschluessel am Profil (Quellen-
     * Ereignisse haben keins), deshalb ausdruecklich. Darunter stehen die Wunschtexte des Kindes.
     */
    @Query("DELETE FROM review_events WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}

@Dao
interface WatchHistoryDao {
    @Insert
    suspend fun insert(entry: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND watchedAt >= :since ORDER BY watchedAt DESC")
    suspend fun since(profileId: String, since: Long): List<WatchHistoryEntity>

    @Query("SELECT CASE WHEN MIN(watchedSeconds) < 0 THEN -1 ELSE COALESCE(SUM(watchedSeconds), 0) END FROM watch_history WHERE profileId = :profileId AND watchedAt >= :from AND watchedAt < :until")
    suspend fun secondsBetween(profileId: String, from: Long, until: Long): Long
}

@Dao
interface ChannelVideoCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(videos: List<CachedChannelVideoEntity>)

    @Query("SELECT * FROM cached_channel_videos WHERE channelId = :channelId ORDER BY position")
    suspend fun byChannel(channelId: String): List<CachedChannelVideoEntity>

    /** Sucht in Titel und Kanalname: Wer "Maus" tippt, meint meist den Kanal, nicht das Wort im Titel. */
    @Query(
        "SELECT * FROM cached_channel_videos " +
            "WHERE title LIKE '%' || :query || '%' OR channelTitle LIKE '%' || :query || '%' " +
            "ORDER BY position LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int = 50): List<CachedChannelVideoEntity>

    @Query("DELETE FROM cached_channel_videos WHERE channelId = :channelId")
    suspend fun clear(channelId: String)
}

@Dao
interface WishDao {
    @Query("SELECT * FROM wishes WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeByProfile(profileId: String): Flow<List<WishEntity>>

    /** Was noch eine Elternentscheidung braucht; das Aelteste zuerst. */
    @Query("SELECT * FROM wishes WHERE profileId = :profileId AND status IN ('offen', 'besprechen') ORDER BY createdAt")
    fun observeOpen(profileId: String): Flow<List<WishEntity>>

    /** Offene Wuensche aller Profile – die Zahl in der Meldung an die Eltern (ADR 0005). */
    @Query("SELECT COUNT(*) FROM wishes WHERE status = 'offen'")
    suspend fun countOffen(): Int

    @Query("SELECT COUNT(*) FROM wishes WHERE profileId = :profileId AND createdAt >= :from AND createdAt < :until")
    suspend fun countBetween(profileId: String, from: Long, until: Long): Int

    @Query("SELECT * FROM wishes WHERE profileId = :profileId AND dedupeKey = :dedupeKey ORDER BY createdAt DESC")
    suspend fun byKey(profileId: String, dedupeKey: String): List<WishEntity>

    @Query("SELECT * FROM wishes WHERE id = :id")
    suspend fun byId(id: String): WishEntity?

    /** Offene Wuensche, die eine Freigabe dieses Inhalts erfuellt. */
    @Query(
        "SELECT * FROM wishes WHERE profileId = :profileId AND status IN ('offen', 'besprechen') " +
            "AND (resultContentId = :contentId OR (kind = 'neueFolge' AND videoId = :contentId))"
    )
    suspend fun openFor(profileId: String, contentId: String): List<WishEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(wish: WishEntity)

    @Update
    suspend fun update(wish: WishEntity)
}
