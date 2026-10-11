// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import xyz.steier.sidetube.core.db.CachedChannelVideoEntity
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.SideTubeDatabase
import xyz.steier.sidetube.core.db.WatchHistoryEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.db.WishEntity
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.provider.ContentDraft
import xyz.steier.sidetube.core.provider.ProviderError

/** ADR 0010 gegen eine echte Room-Datenbank: die Abfragen selbst sind Teil der Pruefung. */
@RunWith(RobolectricTestRunner::class)
class YouTubeDatenPflegeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, SideTubeDatabase::class.java).allowMainThreadQueries().build()
    @After fun zu() = db.close()

    private val tag = 24L * 60 * 60 * 1000
    private val jetzt = 100 * tag
    private val alt = jetzt - 31 * tag
    private val frisch = jetzt - 2 * tag

    private fun item(id: String, at: Long, status: String = "approved", type: String = "VIDEO", provider: String = "youtube", metadataAt: Long? = null) =
        WhitelistItemEntity(id = id, profileId = "p", type = type, provider = provider, contentId = "c-$id", title = "alt $id",
            thumbnailUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg", channelTitle = "Kanal alt", addedAt = at,
            approvalStatus = status, metadataAt = metadataAt)

    private suspend fun fuellen() {
        db.kidProfiles().insert(KidProfileEntity("p", "Kind"))
        db.whitelist().insert(item("a", alt))                              // faellig, wird aufgefrischt
        db.whitelist().insert(item("b", alt))                              // faellig, bei YouTube geloescht
        db.whitelist().insert(item("c", alt))                              // faellig, Netzfehler
        db.whitelist().insert(item("d", alt, metadataAt = frisch))         // vor kurzem aufgefrischt
        db.whitelist().insert(item("e", frisch))                           // frisch hinzugefuegt
        db.whitelist().insert(item("f", alt, provider = "peertube"))       // kein YouTube
        db.channelVideoCache().upsert(listOf(
            CachedChannelVideoEntity("UC1", "v-alt", "T", "", "K", 0, cachedAt = alt),
            CachedChannelVideoEntity("UC1", "v-neu", "T", "", "K", 1, cachedAt = frisch)))
        db.watchHistory().insert(WatchHistoryEntity(profileId = "p", videoId = "x", videoTitle = "alt", watchedSeconds = 60, watchedAt = alt))
        db.watchHistory().insert(WatchHistoryEntity(profileId = "p", videoId = "y", videoTitle = "neu", watchedSeconds = 30, watchedAt = frisch))
        db.wishes().insert(WishEntity(id = "w1", profileId = "p", kind = "mehrDavon", dedupeKey = "k1", videoId = "v", videoTitle = "Titel",
            channelTitle = "Kanal", status = "erfuellt", createdAt = alt))
        db.wishes().insert(WishEntity(id = "w2", profileId = "p", kind = "mehrDavon", dedupeKey = "k2", videoId = "v", videoTitle = "Titel",
            channelTitle = "Kanal", status = "offen", createdAt = alt))
        db.wishes().insert(WishEntity(id = "w3", profileId = "p", kind = "thema", dedupeKey = "k3", topic = "Dinos", status = "offen", createdAt = alt))
        db.wishes().insert(WishEntity(id = "w4", profileId = "p", kind = "mehrDavon", dedupeKey = "k4", videoId = "v", videoTitle = "Neu",
            channelTitle = "Kanal", status = "erfuellt", createdAt = frisch))
    }

    private val geholt = mutableListOf<String>()
    private val pflege = YouTubeDatenPflege(db.whitelist(), db.channelVideoCache(), db.watchHistory(), db.wishes(),
        holen = { type, id ->
            geholt += id
            when (id) {
                "c-a" -> ContentDraft(type, id, title = "neu a", thumbnailUrl = "https://i.ytimg.com/vi/a/neu.jpg", channelTitle = "Kanal neu")
                "c-b" -> throw ProviderError.NotFound
                else -> throw ProviderError.Http(503)
            }
        }, now = { jetzt })

    @Test fun `ein Lauf frischt auf, ersetzt Verschwundenes und loescht Altes`() = runTest {
        fuellen()
        val bericht = pflege.lauf()
        assertThat(geholt).containsExactly("c-a", "c-b", "c-c")
        assertThat(bericht).isEqualTo(YouTubeDatenPflege.Bericht(aufgefrischt = 1, verschwunden = 1, spaeter = 1,
            zwischenspeicher = 1, sehverlauf = 1, wuenscheGeloescht = 1, wuenscheGekuerzt = 1))

        val alle = db.whitelist().observeByProfile("p").first().associateBy { it.id }
        assertThat(alle.getValue("a").title).isEqualTo("neu a")
        assertThat(alle.getValue("a").channelTitle).isEqualTo("Kanal neu")
        assertThat(alle.getValue("a").metadataAt).isEqualTo(jetzt)
        // Bei YouTube weg: keine YouTube-Daten mehr, und das Kind sieht den Eintrag nicht mehr.
        assertThat(alle.getValue("b").title).isEmpty()
        assertThat(alle.getValue("b").thumbnailUrl).isEmpty()
        assertThat(alle.getValue("b").approvalStatus).isEqualTo("expiredReview")
        assertThat(db.whitelist().observeApproved("p").first().map { it.id }).doesNotContain("b")
        // Netzfehler: unveraendert, der naechste Lauf versucht es wieder.
        assertThat(alle.getValue("c").title).isEqualTo("alt c")
        assertThat(alle.getValue("c").metadataAt).isNull()
        assertThat(alle.getValue("f").title).isEqualTo("alt f")

        assertThat(db.channelVideoCache().byChannel("UC1").map { it.videoId }).containsExactly("v-neu")
        assertThat(db.watchHistory().since("p", 0).map { it.videoId }).containsExactly("y")
        val wuensche = db.wishes().observeByProfile("p").first().associateBy { it.id }
        assertThat(wuensche.keys).containsExactly("w2", "w3", "w4")
        assertThat(wuensche.getValue("w2").videoTitle).isNull()
        assertThat(wuensche.getValue("w2").channelTitle).isNull()
        assertThat(wuensche.getValue("w3").topic).isEqualTo("Dinos")
        assertThat(wuensche.getValue("w4").videoTitle).isEqualTo("Neu")
    }

    @Test fun `ein zweiter Lauf fragt nur noch nach, was beim ersten nicht ging`() = runTest {
        fuellen()
        pflege.lauf(); geholt.clear()
        val zweiter = pflege.lauf()
        assertThat(geholt).containsExactly("c-c")
        assertThat(zweiter.aufgefrischt + zweiter.verschwunden).isEqualTo(0)
        assertThat(zweiter.sehverlauf + zweiter.zwischenspeicher + zweiter.wuenscheGeloescht + zweiter.wuenscheGekuerzt).isEqualTo(0)
    }

    @Test fun `401 und 403 gelten als endgueltig, 5xx und Netz nicht`() {
        assertThat(YouTubeDatenPflege.endgueltigWeg(ProviderError.NotFound)).isTrue()
        assertThat(YouTubeDatenPflege.endgueltigWeg(ProviderError.Http(401))).isTrue()
        assertThat(YouTubeDatenPflege.endgueltigWeg(ProviderError.Http(403))).isTrue()
        assertThat(YouTubeDatenPflege.endgueltigWeg(ProviderError.Http(500))).isFalse()
        assertThat(YouTubeDatenPflege.endgueltigWeg(java.io.IOException("offline"))).isFalse()
    }
}
