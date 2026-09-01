package io.github.degipe.youtubewhitelist.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.degipe.youtubewhitelist.core.database.entity.KidProfileEntity

/**
 * Real migrations instead of a destructive fallback: a schema change must never
 * cost a family its profiles and approvals.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE kid_profiles ADD COLUMN bedtimeEnabled INTEGER NOT NULL DEFAULT 1"
        )
        db.execSQL(
            "ALTER TABLE kid_profiles ADD COLUMN bedtimeStartMinutes INTEGER NOT NULL " +
                "DEFAULT ${KidProfileEntity.DEFAULT_BEDTIME_START_MINUTES}"
        )
        db.execSQL(
            "ALTER TABLE kid_profiles ADD COLUMN bedtimeEndMinutes INTEGER NOT NULL " +
                "DEFAULT ${KidProfileEntity.DEFAULT_BEDTIME_END_MINUTES}"
        )
        db.execSQL(
            "ALTER TABLE kid_profiles ADD COLUMN bedtimeWeekendOffsetMinutes INTEGER NOT NULL " +
                "DEFAULT ${KidProfileEntity.DEFAULT_BEDTIME_WEEKEND_OFFSET_MINUTES}"
        )
        db.execSQL("ALTER TABLE kid_profiles ADD COLUMN bedtimeSkipUntil INTEGER")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_3_4)
