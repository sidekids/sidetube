// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Kinderprofil mit den Regeln, die fuer dieses Kind gelten. Zeiten stehen als Minuten seit
 * Mitternacht, damit Zeitzone und Sommerzeit sie nicht verschieben.
 */
@Entity(tableName = "kid_profiles")
data class KidProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val dailyLimitMinutes: Int? = null,
    val sleepPlaylistId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),

    val ageBand: String = "kids",
    val allowNews: Boolean = true,
    val allowManga: Boolean = true,
    val allowMangaEntertainment: Boolean = false,
    val allowShorts: Boolean = false,
    val autoplayNext: Boolean = false,

    val bedtimeEnabled: Boolean = true,
    val bedtimeStartMinutes: Int = DEFAULT_BEDTIME_START,
    val bedtimeEndMinutes: Int = DEFAULT_BEDTIME_END,
    val bedtimeWeekendOffsetMinutes: Int = DEFAULT_BEDTIME_WEEKEND_OFFSET,
    /** Bis zu diesem Zeitpunkt haben Eltern die Ruhezeit ausgesetzt. */
    val bedtimeSkipUntil: Long? = null
) {
    companion object {
        const val DEFAULT_BEDTIME_START = 20 * 60
        const val DEFAULT_BEDTIME_END = 6 * 60 + 30
        const val DEFAULT_BEDTIME_WEEKEND_OFFSET = 60
    }
}

/**
 * Ein freigegebener oder zu pruefender Eintrag. Der Freigabestatus steht bewusst nicht auf
 * "freigegeben": Was neu hereinkommt, sehen die Eltern zuerst.
 */
@Entity(
    tableName = "whitelist_items",
    foreignKeys = [ForeignKey(
        entity = KidProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId"), Index(value = ["profileId", "contentId"], unique = true)]
)
data class WhitelistItemEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val type: String,
    val provider: String = "youtube",
    /** Kennung beim Anbieter; bei PeerTube in der Form `pt:<host>:<id>`. */
    val contentId: String,
    val title: String,
    val thumbnailUrl: String = "",
    val channelTitle: String? = null,
    val sourceChannelId: String? = null,
    val sourceUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),

    val approvalStatus: String = "reviewRequired",
    val category: String? = null,
    val ageMin: Int = 0,
    val ageMax: Int? = null,
    val sensitiveTopics: String = "",
    val isNews: Boolean = false,
    val newsStatus: String? = null,
    val isShort: Boolean = false,
    val isLive: Boolean = false,
    val durationSeconds: Int? = null,
    val editorialNotes: String? = null,
    val parentNotes: String? = null,
    val approvedBy: String? = null,
    val approvedAt: Long? = null,
    val lastReviewedAt: Long? = null
)

/** Quelle mit Sicherheitsstufe. Elternentscheidungen ueberdauern spaetere Registerstaende. */
@Entity(tableName = "curated_sources")
data class CuratedSourceEntity(
    @PrimaryKey val channelId: String,
    val handle: String? = null,
    val title: String,
    val provider: String = "youtube",
    val trust: String = "perVideoReview",
    val isNewsSource: Boolean = false,
    val defaultAgeMin: Int = 0,
    val defaultCategory: String? = null,
    val notes: String? = null,
    val lastReviewedAt: Long? = null
)

/** Nachvollziehbarkeit: wer wann was entschieden hat. */
@Entity(tableName = "review_events", indices = [Index("contentId"), Index("at")])
data class ReviewEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: String,
    val profileId: String?,
    val decision: String,
    val actor: String,
    val at: Long,
    val note: String? = null
)

/** Gespielte Zeit, Grundlage fuer Tageslimit und Statistik. */
@Entity(
    tableName = "watch_history",
    foreignKeys = [ForeignKey(
        entity = KidProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId"), Index("watchedAt")]
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val videoId: String,
    val videoTitle: String,
    val watchedSeconds: Int,
    val watchedAt: Long
)

/** Zwischenspeicher der Kanalvideos: traegt die Kindersuche, die deshalb ohne Netz auskommt. */
@Entity(tableName = "cached_channel_videos", primaryKeys = ["channelId", "videoId"], indices = [Index("channelId")])
data class CachedChannelVideoEntity(
    val channelId: String,
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelTitle: String,
    val position: Int,
    val cachedAt: Long = System.currentTimeMillis(),
    /** Veroeffentlicht laut Feed; `null` bei Eintraegen von vor Schema 3 und bei angekuendigten Premieren. */
    val publishedAt: Long? = null,
    /** Der Feed verlinkt das Video als Short (`/shorts/`). */
    @ColumnInfo(defaultValue = "0") val isShort: Boolean = false,
    /** Angekuendigte Premiere oder Livestream laut Feed (`views="0"`). */
    @ColumnInfo(defaultValue = "0") val isUpcoming: Boolean = false,
    /**
     * Kanal des einzelnen Videos. Bei Kanaelen gleich [channelId]; bei Playlists (Schluessel
     * `playlist:<id>`) kann jedes Video aus einem anderen Kanal stammen. `null` bei Eintraegen von vor
     * Schema 3 – dann ist die Quelle unbekannt, und ein Playlist-Video braucht eine eigene Freigabe.
     */
    val videoChannelId: String? = null
)

/**
 * Ein Wunsch eines Kindes an die Eltern (ADR 0001). Bleibt auf dem Geraet; Eltern sehen ihn hinter
 * der PIN in der Pruefliste. [kind] und [status] tragen die Kennungen aus dem ADR, damit iOS und
 * Android dasselbe meinen: `thema` · `mehrDavon` · `neueFolge` und `offen` · `erfuellt` ·
 * `abgelehnt` · `besprechen`.
 */
@Entity(
    tableName = "wishes",
    foreignKeys = [ForeignKey(
        entity = KidProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("profileId"), Index(value = ["profileId", "dedupeKey"]), Index(value = ["profileId", "createdAt"])]
)
data class WishEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val kind: String,
    /** Gleicher Schluessel = derselbe Wunsch (gleiches Thema, gleiches Video). */
    val dedupeKey: String,
    /** Stichwort beim Themenwunsch, so geschrieben, wie das Kind es gewaehlt hat. */
    val topic: String? = null,
    /** Anlass bei „Mehr davon" bzw. die gewuenschte Folge. */
    val videoId: String? = null,
    val videoTitle: String? = null,
    val channelId: String? = null,
    val channelTitle: String? = null,
    val status: String = "offen",
    /** Kurze Antwort der Eltern an das Kind, freiwillig. */
    val parentReply: String? = null,
    /** Was die Eltern daraufhin aufgenommen haben (Kennung des Eintrags): der Weg zum Inhalt. */
    val resultContentId: String? = null,
    val createdAt: Long,
    val decidedAt: Long? = null
)
