// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Schema 2 → 3 (Wuensche, ADR 0001): Die alte Datenbank wird aus dem versionierten Schema-Abzug
 * gebaut, mit echten Zeilen gefuellt und von der App geoeffnet. Room prueft dabei das Ergebnis
 * gegen das erwartete Schema – eine falsche Migration faellt hier auf, nicht beim Kind.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun baueAlt(name: String, version: Int, daten: (android.database.sqlite.SQLiteDatabase) -> Unit) {
        val schema = JSONObject(context.assets.open("xyz.steier.sidetube.core.db.SideTubeDatabase/$version.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { alt ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                alt.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) {
                    alt.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) alt.execSQL(setup.getString(i))
            daten(alt)
            alt.version = version
        }
    }

    @Test fun `v2 nach v3 behaelt Freigaben, Verlauf und Kanal-Cache und legt Wuensche an`() = runTest {
        val name = "migration-v3-${UUID.randomUUID()}.db"
        baueAlt(name, 2) { alt ->
            alt.execSQL("INSERT INTO kid_profiles VALUES ('p','Kind',NULL,30,NULL,1,'kids',1,1,0,0,0,1,1200,390,60,NULL)")
            alt.execSQL("INSERT INTO whitelist_items (id,profileId,type,provider,contentId,title,thumbnailUrl,addedAt,approvalStatus,ageMin,sensitiveTopics,isNews,isShort,isLive) " +
                "VALUES ('i','p','VIDEO','youtube','v1','Spring','',1,'approved',0,'',0,0,0)")
            alt.execSQL("INSERT INTO review_events (contentId,profileId,decision,actor,at) VALUES ('v1','p','approved','Eltern',2)")
            alt.execSQL("INSERT INTO cached_channel_videos VALUES ('UCn','f1','Folge','t','NASA',0,3)")
            alt.execSQL("INSERT INTO playback_sessions VALUES ('p','tok',4)")
        }
        val db = SideTubeDatabase.open(context, name)
        try {
            assertThat(db.kidProfiles().byId("p")?.dailyLimitMinutes).isEqualTo(30)
            assertThat(db.whitelist().observeApproved("p").first().map { it.contentId }).containsExactly("v1")
            assertThat(db.reviewEvents().forContent("v1").single().decision).isEqualTo("approved")
            assertThat(db.playbackSessions().pending("p")?.token).isEqualTo("tok")
            val cache = db.channelVideoCache().byChannel("UCn").single()
            assertThat(cache.publishedAt).isNull()      // alter Eintrag: Datum unbekannt
            assertThat(cache.isShort).isFalse()
            assertThat(cache.isUpcoming).isFalse()
            assertThat(cache.videoChannelId).isNull()   // alter Eintrag: Kanal unbekannt → eigene Freigabe noetig
            db.channelVideoCache().upsert(listOf(cache.copy(videoId = "f2", videoChannelId = "UCn", isUpcoming = true)))
            val neu = db.channelVideoCache().byChannel("UCn").single { it.videoId == "f2" }
            assertThat(neu.videoChannelId).isEqualTo("UCn")
            assertThat(neu.isUpcoming).isTrue()
            assertThat(db.wishes().observeByProfile("p").first()).isEmpty()
            db.wishes().insert(WishEntity(id = "w", profileId = "p", kind = "thema", dedupeKey = "thema:dinos",
                topic = "Dinos", createdAt = 5))
            assertThat(db.wishes().countBetween("p", 0, 10)).isEqualTo(1)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun `v1 laeuft ueber beide Migrationen`() = runTest {
        val name = "migration-v1v3-${UUID.randomUUID()}.db"
        baueAlt(name, 1) { alt ->
            alt.execSQL("INSERT INTO kid_profiles VALUES ('p','Kind',NULL,10,NULL,1,'kids',1,1,0,0,0,1,1200,390,60,NULL)")
        }
        val db = SideTubeDatabase.open(context, name)
        try {
            assertThat(db.kidProfiles().byId("p")?.name).isEqualTo("Kind")
            assertThat(db.wishes().countBetween("p", 0, Long.MAX_VALUE)).isEqualTo(0)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
