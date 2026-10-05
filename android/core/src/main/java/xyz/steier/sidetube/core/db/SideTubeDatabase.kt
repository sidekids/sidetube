// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Die Datenbank der App.
 *
 * **Kein `fallbackToDestructiveMigration`.** Hier liegen die Freigaben der Eltern und der
 * Sehverlauf eines Kindes; ein stilles Leeren bei einem Schemafehler waere ein Datenverlust
 * ohne Vorwarnung. Jede Schemaaenderung bekommt eine geschriebene Migration und einen Test,
 * der sie mit echten Daten durchlaeuft.
 */
@Database(
    entities = [
        KidProfileEntity::class,
        WhitelistItemEntity::class,
        CuratedSourceEntity::class,
        ReviewEventEntity::class,
        WatchHistoryEntity::class,
        CachedChannelVideoEntity::class,
        PlaybackSessionEntity::class,
        WishEntity::class,
    ],
    version = 3,
    exportSchema = true
)
abstract class SideTubeDatabase : RoomDatabase() {
    abstract fun kidProfiles(): KidProfileDao
    abstract fun whitelist(): WhitelistDao
    abstract fun sources(): CuratedSourceDao
    abstract fun reviewEvents(): ReviewEventDao
    abstract fun watchHistory(): WatchHistoryDao
    abstract fun channelVideoCache(): ChannelVideoCacheDao
    abstract fun playbackSessions(): PlaybackSessionDao
    abstract fun wishes(): WishDao

    companion object {
        const val NAME = "sidetube.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `playback_sessions` (`profileId` TEXT NOT NULL, `token` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, PRIMARY KEY(`profileId`), FOREIGN KEY(`profileId`) REFERENCES `kid_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }
        /**
         * Wuensche von Kindern (ADR 0001), Veroeffentlichungsdatum/Short-/Premieren-Merker im Kanal-Cache
         * fuer „Neu bei deinen Kanaelen" und Playlists sowie die Kanal-ID je Cache-Eintrag (Quelle eines
         * Playlist-Videos). Nur Hinzufuegen, nichts wird umgeschrieben.
         */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `wishes` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `kind` TEXT NOT NULL, `dedupeKey` TEXT NOT NULL, `topic` TEXT, `videoId` TEXT, `videoTitle` TEXT, `channelId` TEXT, `channelTitle` TEXT, `status` TEXT NOT NULL, `parentReply` TEXT, `resultContentId` TEXT, `createdAt` INTEGER NOT NULL, `decidedAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`profileId`) REFERENCES `kid_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wishes_profileId` ON `wishes` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wishes_profileId_dedupeKey` ON `wishes` (`profileId`, `dedupeKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wishes_profileId_createdAt` ON `wishes` (`profileId`, `createdAt`)")
                db.execSQL("ALTER TABLE `cached_channel_videos` ADD COLUMN `publishedAt` INTEGER")
                db.execSQL("ALTER TABLE `cached_channel_videos` ADD COLUMN `isShort` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cached_channel_videos` ADD COLUMN `isUpcoming` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cached_channel_videos` ADD COLUMN `videoChannelId` TEXT")
            }
        }
        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun open(context: Context, name: String = NAME): SideTubeDatabase =
            Room.databaseBuilder(context.applicationContext, SideTubeDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}

/** Mehrere Schreibvorgaenge ueber DAOs hinweg: alles oder nichts. */
suspend fun SideTubeDatabase.inTransaction(block: suspend () -> Unit) = withTransaction { block() }
