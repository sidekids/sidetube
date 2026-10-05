// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Durable admission marker, not a second watch-history log. One session per profile. */
@Entity(tableName = "playback_sessions", foreignKeys = [ForeignKey(
    entity = KidProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"],
    onDelete = ForeignKey.CASCADE
)])
data class PlaybackSessionEntity(
    @PrimaryKey val profileId: String,
    val token: String,
    val startedAt: Long
)

@Dao
interface PlaybackSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun begin(session: PlaybackSessionEntity)

    @Query("SELECT * FROM playback_sessions WHERE profileId = :profileId")
    suspend fun pending(profileId: String): PlaybackSessionEntity?

    @Query("SELECT * FROM playback_sessions")
    fun observePending(): Flow<List<PlaybackSessionEntity>>

    @Query("DELETE FROM playback_sessions WHERE profileId = :profileId AND token = :token")
    suspend fun finish(profileId: String, token: String)

    /** Only called after explicit parent authorization, never from child-mode startup. */
    @Query("DELETE FROM playback_sessions WHERE profileId = :profileId")
    suspend fun acknowledgeByParent(profileId: String)
}
