package io.github.degipe.youtubewhitelist.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The quiet hours columns must reach existing installations without taking the
 * profiles and approvals with them.
 */
@RunWith(RobolectricTestRunner::class)
class Migration3To4Test {

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)

        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createVersion3(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )
    }

    @After
    fun tearDown() {
        helper.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(DB_NAME)
    }

    @Test
    fun `migration keeps profiles and adds the quiet hours defaults`() {
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO parent_accounts (id, googleAccountId, email, pinHash, createdAt) " +
                "VALUES ('acc-1', 'local', '', 'salt:hash', 1000)"
        )
        db.execSQL(
            "INSERT INTO kid_profiles (id, parentAccountId, name, avatarUrl, dailyLimitMinutes, " +
                "sleepPlaylistId, createdAt) VALUES ('kid-1', 'acc-1', 'Mia', NULL, 60, NULL, 2000)"
        )

        MIGRATION_3_4.migrate(db)

        db.query("SELECT name, dailyLimitMinutes, bedtimeEnabled, bedtimeStartMinutes, " +
            "bedtimeEndMinutes, bedtimeWeekendOffsetMinutes, bedtimeSkipUntil FROM kid_profiles")
            .use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("Mia")
                assertThat(cursor.getInt(1)).isEqualTo(60)
                assertThat(cursor.getInt(2)).isEqualTo(1)
                assertThat(cursor.getInt(3)).isEqualTo(20 * 60)
                assertThat(cursor.getInt(4)).isEqualTo(6 * 60 + 30)
                assertThat(cursor.getInt(5)).isEqualTo(60)
                assertThat(cursor.isNull(6)).isTrue()
            }
    }

    private fun createVersion3(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS parent_accounts (" +
                "id TEXT NOT NULL PRIMARY KEY, googleAccountId TEXT NOT NULL, " +
                "email TEXT NOT NULL, pinHash TEXT NOT NULL, createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS kid_profiles (" +
                "id TEXT NOT NULL PRIMARY KEY, parentAccountId TEXT NOT NULL, name TEXT NOT NULL, " +
                "avatarUrl TEXT, dailyLimitMinutes INTEGER, sleepPlaylistId TEXT, " +
                "createdAt INTEGER NOT NULL)"
        )
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
