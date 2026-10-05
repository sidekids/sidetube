// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Prueft die Regeln, die in der Datenbank selbst stecken: Was das Kind sieht, was doppelt
 * nicht hereinkommt und was beim Loeschen eines Profils mitgeht.
 */
@RunWith(RobolectricTestRunner::class)
class SideTubeDatabaseTest {

    private lateinit var db: SideTubeDatabase

    private val profile = KidProfileEntity(id = "p1", name = "Mira", dailyLimitMinutes = 60)

    private fun item(id: String, contentId: String, status: String) = WhitelistItemEntity(
        id = id, profileId = profile.id, type = "VIDEO", contentId = contentId,
        title = "Video $contentId", approvalStatus = status
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), SideTubeDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `nur freigegebene Eintraege erreichen das Kind`() = runTest {
        db.kidProfiles().insert(profile)
        db.whitelist().insert(item("a", "v1", "approved"))
        db.whitelist().insert(item("b", "v2", "reviewRequired"))
        db.whitelist().insert(item("c", "v3", "rejected"))

        val visible = db.whitelist().observeApproved(profile.id).first()
        val pending = db.whitelist().observePending(profile.id).first()

        assertThat(visible.map { it.contentId }).containsExactly("v1")
        assertThat(pending.map { it.contentId }).containsExactly("v2")
    }

    @Test
    fun `dasselbe Video kommt im Profil nicht zweimal hinein`() = runTest {
        db.kidProfiles().insert(profile)
        db.whitelist().insert(item("a", "v1", "approved"))

        val zweiterVersuch = runCatching { db.whitelist().insert(item("b", "v1", "approved")) }

        assertThat(zweiterVersuch.isFailure).isTrue()
    }

    @Test
    fun `neue Eintraege sind von sich aus nicht freigegeben`() {
        val frisch = WhitelistItemEntity(id = "x", profileId = "p1", type = "VIDEO", contentId = "v9", title = "Neu")
        assertThat(frisch.approvalStatus).isEqualTo("reviewRequired")
    }

    @Test
    fun `mit dem Profil verschwinden seine Eintraege und sein Verlauf`() = runTest {
        db.kidProfiles().insert(profile)
        db.whitelist().insert(item("a", "v1", "approved"))
        db.watchHistory().insert(
            WatchHistoryEntity(profileId = profile.id, videoId = "v1", videoTitle = "Video", watchedSeconds = 60, watchedAt = 1_000)
        )

        db.kidProfiles().delete(profile)

        assertThat(db.whitelist().observeByProfile(profile.id).first()).isEmpty()
        assertThat(db.watchHistory().since(profile.id, 0)).isEmpty()
    }

    @Test
    fun `mit dem Profil verschwinden seine Wuensche und ihre Texte im Verlauf, Quellen-Ereignisse bleiben`() = runTest {
        val anderes = KidProfileEntity(id = "p2", name = "Ben")
        db.kidProfiles().insert(profile)
        db.kidProfiles().insert(anderes)
        val wuensche = xyz.steier.sidetube.core.repo.WunschRepository(db.wishes(), db.reviewEvents())
        wuensche.wuensche(xyz.steier.sidetube.core.curation.WunschEntwurf.Thema("Geheimes Thema"), profile.id, "Kind")
        wuensche.wuensche(xyz.steier.sidetube.core.curation.WunschEntwurf.Thema("Dinos"), anderes.id, "Kind")
        db.reviewEvents().insert(ReviewEventEntity(contentId = "source:UC1", profileId = null, decision = "trustChanged",
            actor = "Eltern", at = 1))
        val profiles = xyz.steier.sidetube.core.repo.ProfileRepository(db.kidProfiles(), db.reviewEvents()) { db.inTransaction(it) }

        profiles.delete(profile)

        assertThat(db.wishes().observeByProfile(profile.id).first()).isEmpty()
        assertThat(db.reviewEvents().forContent("thema:geheimes thema")).isEmpty()
        assertThat(db.reviewEvents().forContent("thema:dinos").single().actor).isEqualTo("Kind")
        assertThat(db.reviewEvents().forContent("source:UC1")).hasSize(1)
        assertThat(db.wishes().observeByProfile(anderes.id).first()).hasSize(1)
    }

    @Test
    fun `eine Elternentscheidung ueberlebt ein spaeteres Quellenregister`() = runTest {
        val quelle = CuratedSourceEntity(channelId = "UC1", title = "Ein Kanal", trust = "perVideoReview")
        db.sources().insertMissing(listOf(quelle))
        db.sources().update(quelle.copy(trust = "trustedChildSource"))

        db.sources().insertMissing(listOf(quelle))   // Register wird erneut eingespielt

        assertThat(db.sources().byChannel("UC1")?.trust).isEqualTo("trustedChildSource")
    }

    @Test
    fun `Sehzeit wird tageweise summiert`() = runTest {
        db.kidProfiles().insert(profile)
        for (t in listOf(1_000L, 2_000L, 50_000L)) {
            db.watchHistory().insert(
                WatchHistoryEntity(profileId = profile.id, videoId = "v", videoTitle = "V", watchedSeconds = 30, watchedAt = t)
            )
        }

        assertThat(db.watchHistory().secondsBetween(profile.id, 0, 10_000)).isEqualTo(60L)
        assertThat(db.watchHistory().secondsBetween(profile.id, 0, 100_000)).isEqualTo(90L)
    }

    @Test
    fun `large history sums cannot wrap into additional budget`() = runTest {
        db.kidProfiles().insert(profile)
        val repository = xyz.steier.sidetube.core.repo.WatchTimeRepository(db.watchHistory(), now = { 1000L })
        repeat(3) { repository.record(profile.id, "v", "Example", Int.MAX_VALUE) }
        assertThat(db.watchHistory().secondsBetween(profile.id, 0, 10000)).isEqualTo(3L * Int.MAX_VALUE)
        assertThat(repository.remainingSeconds(profile)).isEqualTo(0)
    }

    @Test
    fun `invalid limits and negative history fail closed without deleting evidence`() = runTest {
        db.kidProfiles().insert(profile)
        val repository = xyz.steier.sidetube.core.repo.WatchTimeRepository(db.watchHistory(), now = { 1000L })
        for (limit in listOf(-1, 0, Int.MAX_VALUE)) {
            assertThat(repository.remainingSeconds(profile.copy(dailyLimitMinutes = limit))).isEqualTo(0)
        }
        db.watchHistory().insert(WatchHistoryEntity(profileId = profile.id, videoId = "v", videoTitle = "Example", watchedSeconds = -10, watchedAt = 1000))
        assertThat(repository.remainingSeconds(profile)).isEqualTo(0)
        assertThat(db.watchHistory().since(profile.id, 0)).hasSize(1)
        assertThat(repository.remainingSeconds(profile.copy(dailyLimitMinutes = null))).isNull()
    }

    @Test
    fun `die Kindersuche findet im Zwischenspeicher, ohne Netz`() = runTest {
        db.channelVideoCache().upsert(listOf(
            CachedChannelVideoEntity("UC1", "v1", "Wie entsteht ein Regenbogen", "t", "Die Maus", 0),
            CachedChannelVideoEntity("UC1", "v2", "Sendung mit der Maus", "t", "Die Maus", 1),
        ))

        assertThat(db.channelVideoCache().search("Regenbogen").map { it.videoId }).containsExactly("v1")
        assertThat(db.channelVideoCache().search("Maus")).hasSize(2)   // ueber den Kanalnamen
        assertThat(db.channelVideoCache().search("Sendung").map { it.videoId }).containsExactly("v2")
        assertThat(db.channelVideoCache().search("Dinosaurier")).isEmpty()
    }
}
