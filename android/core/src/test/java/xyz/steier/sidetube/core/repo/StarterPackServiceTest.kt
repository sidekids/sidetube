// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.repo

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
import xyz.steier.sidetube.core.curation.ContentBundle
import xyz.steier.sidetube.core.curation.RiskScreen
import xyz.steier.sidetube.core.db.SideTubeDatabase

@RunWith(RobolectricTestRunner::class)
class StarterPackServiceTest {

    private lateinit var db: SideTubeDatabase
    private lateinit var service: StarterPackService
    private lateinit var profiles: ProfileRepository
    private lateinit var curation: CurationRepository

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), SideTubeDatabase::class.java
        ).build()
        val bundle = ContentBundle(Content)
        curation = CurationRepository(db.whitelist(), db.sources(), db.reviewEvents(), RiskScreen(bundle.riskTerms()))
        curation.ensureSources(bundle.sources())
        profiles = ProfileRepository(db.kidProfiles(), db.reviewEvents())
        service = StarterPackService(bundle, curation, profiles)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `die mitgelieferten Pakete lassen sich auflisten`() {
        val packs = service.available()

        assertThat(packs).isNotEmpty()
        assertThat(packs.all { it.title.isNotBlank() }).isTrue()
        assertThat(packs.any { it.videoCount > 0 }).isTrue()
    }

    @Test
    fun `ein Paket landet zur Pruefung im gewaehlten Profil`() = runTest {
        val erstes = profiles.create("Erstes Kind")
        val mira = profiles.create("Mira")
        val pack = service.available().first { it.videoCount > 0 }

        val result = service.import(pack.fileName, mira, applyPreset = false)

        assertThat(result.added).isGreaterThan(0)
        assertThat(curation.observePending(mira.id).first()).hasSize(result.added)
        assertThat(curation.observePending(erstes.id).first()).isEmpty()
        assertThat(db.whitelist().observeApproved(mira.id).first()).isEmpty()
    }

    @Test
    fun `ein zweiter Import legt nichts doppelt an`() = runTest {
        val profile = profiles.create("Mira")
        val pack = service.available().first { it.videoCount > 0 }
        val erster = service.import(pack.fileName, profile, applyPreset = false)

        val zweiter = service.import(pack.fileName, profile, applyPreset = false)

        assertThat(zweiter.added).isEqualTo(0)
        assertThat(zweiter.skipped).isEqualTo(erster.added)
    }

    @Test
    fun `die Profilvorgabe wird nur auf Wunsch uebernommen`() = runTest {
        val pack = service.available().firstOrNull { it.hasProfilePreset } ?: return@runTest
        // Ein Profil, das anders eingestellt ist als die Vorgabe - sonst gaebe es nichts zu aendern.
        suspend fun abweichend(name: String) =
            profiles.create(name).copy(ageBand = "tween", allowShorts = true, bedtimeEnabled = false)
                .also { profiles.update(it) }

        val ohne = abweichend("Ohne")
        service.import(pack.fileName, ohne, applyPreset = false)
        assertThat(profiles.byId(ohne.id)?.allowShorts).isTrue()
        assertThat(profiles.byId(ohne.id)?.bedtimeEnabled).isFalse()

        val mit = abweichend("Mit")
        val result = service.import(pack.fileName, mit, applyPreset = true)

        assertThat(result.presetApplied).isTrue()
        val danach = checkNotNull(profiles.byId(mit.id))
        assertThat(danach.ageBand).isEqualTo("kids")
        assertThat(danach.allowShorts).isFalse()
        assertThat(danach.bedtimeEnabled).isTrue()
        assertThat(danach.bedtimeStartMinutes).isEqualTo(1200)
    }

    @Test
    fun `eine Vorgabe, die nichts aendert, wird nicht als Aenderung gemeldet`() = runTest {
        val pack = service.available().firstOrNull { it.hasProfilePreset } ?: return@runTest
        val frisch = profiles.create("Frisch")   // Standardwerte entsprechen der Vorgabe

        val result = service.import(pack.fileName, frisch, applyPreset = true)

        assertThat(result.presetApplied).isFalse()
    }
}
