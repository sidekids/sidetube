// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class PlaybackSessionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `interrupted marker survives reopening and parent acknowledgement preserves history`() = runTest {
        val name = "session-test-${UUID.randomUUID()}.db"
        var db = SideTubeDatabase.open(context, name)
        try {
            db.kidProfiles().insert(KidProfileEntity(id = "p", name = "Example"))
            db.playbackSessions().begin(PlaybackSessionEntity("p", "first", 100))
            db.watchHistory().insert(WatchHistoryEntity(profileId = "p", videoId = "v", videoTitle = "Example", watchedSeconds = 30, watchedAt = 100))
            db.close()
            db = SideTubeDatabase.open(context, name)
            assertThat(db.playbackSessions().pending("p")?.token).isEqualTo("first")
            assertThat(runCatching { db.playbackSessions().begin(PlaybackSessionEntity("p", "second", 200)) }.isFailure).isTrue()
            db.playbackSessions().acknowledgeByParent("p")
            assertThat(db.playbackSessions().pending("p")).isNull()
            assertThat(db.watchHistory().secondsBetween("p", 0, 1000)).isEqualTo(30L)
            db.playbackSessions().begin(PlaybackSessionEntity("p", "second", 200))
            db.playbackSessions().finish("p", "first")
            assertThat(db.playbackSessions().pending("p")?.token).isEqualTo("second")
            db.playbackSessions().finish("p", "second")
            assertThat(db.playbackSessions().pending("p")).isNull()
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `markers are profile scoped and removed only with their own profile`() = runTest {
        val name = "session-test-${UUID.randomUUID()}.db"
        val db = SideTubeDatabase.open(context, name)
        try {
            val p = KidProfileEntity(id = "p", name = "Example")
            val q = p.copy(id = "q")
            db.kidProfiles().insert(p); db.kidProfiles().insert(q)
            db.playbackSessions().begin(PlaybackSessionEntity("p", "first", 100))
            db.playbackSessions().begin(PlaybackSessionEntity("q", "second", 100))
            db.kidProfiles().delete(p)
            assertThat(db.playbackSessions().pending("p")).isNull()
            assertThat(db.playbackSessions().pending("q")?.token).isEqualTo("second")
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `v1 migration preserves profiles and recorded time without inventing interrupted sessions`() = runTest {
        val name = "migration-test-${UUID.randomUUID()}.db"
        val schema = JSONObject(context.assets.open("xyz.steier.sidetube.core.db.SideTubeDatabase/1.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO kid_profiles VALUES ('p','Example',NULL,10,NULL,1,'kids',1,1,0,0,0,1,1200,390,60,NULL)")
            old.execSQL("INSERT INTO watch_history(profileId,videoId,videoTitle,watchedSeconds,watchedAt) VALUES ('p','v','Example',30,1)")
            old.version = 1
        }
        val db = SideTubeDatabase.open(context, name)
        try {
            assertThat(db.kidProfiles().byId("p")?.dailyLimitMinutes).isEqualTo(10)
            assertThat(db.watchHistory().secondsBetween("p", 0, 1000)).isEqualTo(30L)
            assertThat(db.playbackSessions().pending("p")).isNull()
            db.playbackSessions().begin(PlaybackSessionEntity("p", "new", 100))
            assertThat(db.playbackSessions().pending("p")?.token).isEqualTo("new")
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
