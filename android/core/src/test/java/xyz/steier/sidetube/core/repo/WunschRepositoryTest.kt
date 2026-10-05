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
import xyz.steier.sidetube.core.curation.WunschEntwurf
import xyz.steier.sidetube.core.curation.WunschStatus
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.SideTubeDatabase
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Tagesgrenze, Dubletten, Statusuebergaenge und Verlauf der Wuensche – gegen echtes Room. */
@RunWith(RobolectricTestRunner::class)
class WunschRepositoryTest {
    private lateinit var db: SideTubeDatabase
    private lateinit var repo: WunschRepository
    private var jetzt = Instant.parse("2026-10-02T08:00:00Z")
    private var nummer = 0
    private val zone = ZoneId.of("Europe/Berlin")

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SideTubeDatabase::class.java).build()
        db.kidProfiles().insert(KidProfileEntity(id = "p", name = "Kind"))
        db.kidProfiles().insert(KidProfileEntity(id = "q", name = "Ole"))
        repo = WunschRepository(db.wishes(), db.reviewEvents(), now = { jetzt }, zone = { zone }, newId = { "w${++nummer}" })
    }

    @After fun tearDown() = db.close()

    private suspend fun thema(text: String, profil: String = "p") = repo.wuensche(WunschEntwurf.Thema(text), profil, "Kind")
    private fun folge(id: String) = WunschEntwurf.NeueFolge(id, "Folge $id", "UCn", "NASA")

    @Test fun `offene Wuensche aller Profile werden gezaehlt`() = runTest {
        val dinos = (thema("Dinos") as WunschErgebnis.Geschickt).wunsch
        thema("Vulkane", "q")
        assertThat(repo.offeneAnzahl()).isEqualTo(2)
        repo.entscheide(dinos, WunschStatus.BESPRECHEN, null, "Eltern")
        assertThat(repo.offeneAnzahl()).isEqualTo(1)
    }

    @Test fun `hoechstens drei je Profil und Tag, am naechsten Tag wieder`() = runTest {
        assertThat(thema("Dinos")).isInstanceOf(WunschErgebnis.Geschickt::class.java)
        assertThat((thema("Pferde") as WunschErgebnis.Geschickt).heuteNoch).isEqualTo(1)
        assertThat((repo.wuensche(folge("v1"), "p", "Kind") as WunschErgebnis.Geschickt).heuteNoch).isEqualTo(0)
        assertThat(thema("Vulkane")).isEqualTo(WunschErgebnis.Grenze)
        assertThat(repo.heuteNoch("p")).isEqualTo(0)
        // Ein anderes Profil hat seine eigenen drei.
        assertThat(thema("Vulkane", "q")).isInstanceOf(WunschErgebnis.Geschickt::class.java)
        // Lokale Mitternacht (Berlin), nicht UTC.
        jetzt = Instant.parse("2026-10-02T22:00:00Z")
        assertThat(repo.heuteNoch("p")).isEqualTo(3)
        assertThat(thema("Vulkane")).isInstanceOf(WunschErgebnis.Geschickt::class.java)
    }

    @Test fun `gleiches Thema und gleiches Video entstehen nicht neu und kosten nichts`() = runTest {
        thema("Dinos")
        assertThat(thema("  dinos ")).isInstanceOf(WunschErgebnis.SchonGewuenscht::class.java)
        repo.wuensche(folge("v1"), "p", "Kind")
        assertThat(repo.wuensche(folge("v1"), "p", "Kind")).isInstanceOf(WunschErgebnis.SchonGewuenscht::class.java)
        assertThat(repo.heuteNoch("p")).isEqualTo(1)
        assertThat(db.wishes().observeByProfile("p").first()).hasSize(2)
        // Auch am naechsten Tag nicht, solange der erste offen ist oder besprochen wird.
        val dinos = db.wishes().observeByProfile("p").first().single { it.topic == "Dinos" }
        repo.entscheide(dinos, WunschStatus.BESPRECHEN, "Erzähl mal", "Eltern")
        jetzt += Duration.ofDays(1)
        assertThat(thema("Dinos")).isInstanceOf(WunschErgebnis.SchonGewuenscht::class.java)
    }

    @Test fun `nach erfuellt oder nicht jetzt darf derselbe Wunsch neu entstehen`() = runTest {
        val erster = (thema("Dinos") as WunschErgebnis.Geschickt).wunsch
        repo.entscheide(erster, WunschStatus.ERFUELLT, null, "Eltern")
        val zweiter = (thema("Dinos") as WunschErgebnis.Geschickt).wunsch
        repo.entscheide(zweiter, WunschStatus.ABGELEHNT, "Heute nicht", "Eltern")
        assertThat(thema("Dinos")).isInstanceOf(WunschErgebnis.Geschickt::class.java)
        // Die Tagesgrenze begrenzt die Wiederholungen.
        assertThat(thema("Dinos")).isInstanceOf(WunschErgebnis.SchonGewuenscht::class.java)
        assertThat(repo.heuteNoch("p")).isEqualTo(0)
    }

    @Test fun `leeres Stichwort ist kein Wunsch`() = runTest {
        assertThat(thema("   ")).isEqualTo(WunschErgebnis.Leer)
        assertThat(repo.heuteNoch("p")).isEqualTo(3)
    }

    @Test fun `Statusuebergaenge nur aus offen oder besprechen, mit Antwort und Verlauf`() = runTest {
        val w = (thema("Dinos") as WunschErgebnis.Geschickt).wunsch
        val besprechen = repo.entscheide(w, WunschStatus.BESPRECHEN, "Erzähl mir mehr", "Eltern")!!
        assertThat(besprechen.status).isEqualTo("besprechen")
        assertThat(besprechen.parentReply).isEqualTo("Erzähl mir mehr")
        // Besprechen → besprechen: neue Antwort; zurück auf offen geht nicht.
        assertThat(repo.entscheide(besprechen, WunschStatus.BESPRECHEN, "x".repeat(250), "Eltern")!!.parentReply).hasLength(200)
        assertThat(repo.entscheide(besprechen, WunschStatus.OFFEN, null, "Eltern")).isNull()
        val erfuellt = repo.entscheide(besprechen, WunschStatus.ERFUELLT, null, "Eltern")!!
        assertThat(erfuellt.parentReply).hasLength(200)   // eine leere Antwort laesst die fruehere stehen
        assertThat(erfuellt.decidedAt).isEqualTo(jetzt.toEpochMilli())
        // Abgeschlossen ist abgeschlossen.
        assertThat(repo.entscheide(erfuellt, WunschStatus.ABGELEHNT, "doch nicht", "Eltern")).isNull()
        assertThat(repo.byId(w.id)?.status).isEqualTo("erfuellt")

        val verlauf = db.reviewEvents().forContent("thema:dinos").map { it.decision }
        assertThat(verlauf).containsExactly("wished", "wishDiscuss", "wishDiscuss", "wishFulfilled")
    }

    @Test fun `Video-Wuensche stehen im Verlauf des Videos`() = runTest {
        repo.wuensche(WunschEntwurf.MehrDavon("bbb", "Big Buck Bunny", "UCb", "Blender"), "p", "Kind")
        val ereignis = db.reviewEvents().forContent("bbb").single()
        assertThat(ereignis.decision).isEqualTo("wished")
        assertThat(ereignis.note).isEqualTo("Mehr davon: Big Buck Bunny")
    }

    @Test fun `eine Freigabe erfuellt die passenden offenen Wuensche`() = runTest {
        val folgeWunsch = (repo.wuensche(folge("v1"), "p", "Kind") as WunschErgebnis.Geschickt).wunsch
        val themaWunsch = (thema("Dinos") as WunschErgebnis.Geschickt).wunsch
        repo.verknuepfe(themaWunsch, "dino-video", "Eltern")
        val anderes = (thema("Pferde") as WunschErgebnis.Geschickt).wunsch

        repo.erfuelleDurchFreigabe("p", "v1", "Eltern")
        repo.erfuelleDurchFreigabe("p", "dino-video", "Eltern")

        assertThat(repo.byId(folgeWunsch.id)?.status).isEqualTo("erfuellt")
        assertThat(repo.byId(folgeWunsch.id)?.resultContentId).isEqualTo("v1")
        assertThat(repo.byId(themaWunsch.id)?.status).isEqualTo("erfuellt")
        assertThat(repo.byId(anderes.id)?.status).isEqualTo("offen")
        assertThat(db.wishes().observeOpen("p").first().map { it.id }).containsExactly(anderes.id)
    }

    @Test fun `mit dem Profil verschwinden seine Wuensche`() = runTest {
        thema("Dinos")
        db.kidProfiles().delete(KidProfileEntity(id = "p", name = "Kind"))
        assertThat(db.wishes().observeByProfile("p").first()).isEmpty()
    }
}
