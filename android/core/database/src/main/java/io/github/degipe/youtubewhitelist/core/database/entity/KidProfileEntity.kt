package io.github.degipe.youtubewhitelist.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "kid_profiles",
    foreignKeys = [
        ForeignKey(
            entity = ParentAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentAccountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentAccountId")]
)
data class KidProfileEntity(
    @PrimaryKey
    val id: String,
    val parentAccountId: String,
    val name: String,
    val avatarUrl: String? = null,
    val dailyLimitMinutes: Int? = null,
    val sleepPlaylistId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),

    // Ruhezeit: Minuten seit Mitternacht, damit Zeitzone und Sommerzeit
    // keine Rolle spielen.
    val bedtimeEnabled: Boolean = true,
    val bedtimeStartMinutes: Int = DEFAULT_BEDTIME_START_MINUTES,
    val bedtimeEndMinutes: Int = DEFAULT_BEDTIME_END_MINUTES,
    val bedtimeWeekendOffsetMinutes: Int = DEFAULT_BEDTIME_WEEKEND_OFFSET_MINUTES,
    /** Bis zu diesem Zeitpunkt hat ein Elternteil die Ruhezeit ausgesetzt. */
    val bedtimeSkipUntil: Long? = null
) {
    companion object {
        const val DEFAULT_BEDTIME_START_MINUTES = 20 * 60
        const val DEFAULT_BEDTIME_END_MINUTES = 6 * 60 + 30
        const val DEFAULT_BEDTIME_WEEKEND_OFFSET_MINUTES = 60
    }
}
