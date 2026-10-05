// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.curation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.db.WishEntity
import xyz.steier.sidetube.core.provider.ChannelVideo
import java.time.Duration
import java.time.Instant

/** „Neu bei deinen Kanaelen" (ADR 0001): was gesperrt gezeigt werden darf – und was nie. */
class NeueFolgenTest {
    private val jetzt = Instant.parse("2026-10-02T12:00:00Z")
    private val profil = KidProfileEntity(id = "p", name = "Kind", ageBand = "kids")
    private val reihe = CuratedSourceEntity(channelId = "UCn", title = "NASA", trust = "trustedSeries")
    private val risiko = RiskScreen(RiskTerms(hardBlock = listOf("nsfw"), topics = mapOf("war" to listOf("krieg"))))

    private fun video(id: String, tageAlt: Long, titel: String = "Folge $id", short: Boolean = false, kanal: String = "UCn") =
        ChannelVideo(id, titel, "", "NASA", 0, kanal, jetzt.minus(Duration.ofDays(tageAlt)).toEpochMilli(), short)

    private fun auswahl(
        videos: List<ChannelVideo>,
        quelle: CuratedSourceEntity? = reihe,
        entscheidungen: Map<String, WhitelistItemEntity> = emptyMap(),
        wuensche: List<WishEntity> = emptyList(),
        profile: KidProfileEntity = profil
    ) = NeueFolgen.auswahl(profile, listOf(FolgenKanal("UCn", "NASA", quelle)), mapOf("UCn" to videos),
        entscheidungen, wuensche, risiko, jetzt)

    private fun wunsch(videoId: String, status: String) = WishEntity(
        id = "w$videoId", profileId = "p", kind = "neueFolge", dedupeKey = "neueFolge:$videoId",
        videoId = videoId, status = status, createdAt = 0
    )

    @Test fun `hoechstens sechs je Kanal, die neuesten zuerst`() {
        val folgen = auswahl((1..9L).map { video("v$it", it) })
        assertThat(folgen.map { it.videoId }).containsExactly("v1", "v2", "v3", "v4", "v5", "v6").inOrder()
    }

    @Test fun `nicht aelter als sechzig Tage und nie ohne Datum`() {
        val ohneDatum = video("x", 1).copy(publishedAt = null)
        val folgen = auswahl(listOf(video("neu", 59), video("alt", 61), ohneDatum))
        assertThat(folgen.map { it.videoId }).containsExactly("neu")
    }

    @Test fun `keine Shorts, keine Livestreams, Risikofilter auf den Titel`() {
        val folgen = auswahl(listOf(
            video("ok", 1), video("short", 1, short = true), video("kurz", 1, titel = "Mars #shorts"),
            video("live", 1, titel = "Start live vom Pad"), video("krieg", 1, titel = "Krieg der Sterne"),
            video("hart", 1, titel = "nsfw clip")
        ))
        assertThat(folgen.map { it.videoId }).containsExactly("ok")
    }

    @Test fun `schon entschiedene Videos erscheinen nicht`() {
        val entschieden = listOf("approved", "rejected", "reviewRequired").associate { status ->
            "v-$status" to WhitelistItemEntity("i-$status", "p", "VIDEO", contentId = "v-$status", title = "x", approvalStatus = status)
        }
        val folgen = auswahl(entschieden.keys.map { video(it, 1) } + video("frei", 1), entscheidungen = entschieden)
        assertThat(folgen.map { it.videoId }).containsExactly("frei")
    }

    @Test fun `abgelehnter Wunsch verschwindet, offener ist als gewuenscht markiert`() {
        // (Der Wunsch selbst darf nach „nicht jetzt" neu entstehen – hier wird die Folge aber nicht mehr angeboten.)
        val folgen = auswahl(listOf(video("a", 1), video("b", 2), video("c", 3)),
            wuensche = listOf(wunsch("a", "abgelehnt"), wunsch("b", "offen"), wunsch("c", "besprechen")))
        assertThat(folgen.map { it.videoId to it.gewuenscht }).containsExactly("b" to true, "c" to true).inOrder()
    }

    @Test fun `nur die Stufe Vertrauenswuerdige Reihe, gesperrte Quellen nie`() {
        val videos = listOf(video("v", 1))
        for (stufe in listOf("trustedChildSource", "perVideoReview", "parentOnly", "blocked")) {
            assertThat(auswahl(videos, quelle = reihe.copy(trust = stufe))).isEmpty()
        }
        assertThat(auswahl(videos, quelle = null)).isEmpty()
        assertThat(auswahl(videos)).hasSize(1)
    }

    @Test fun `Alter der Quelle gilt, Nachrichtenquellen nie`() {
        val videos = listOf(video("v", 1))
        assertThat(auswahl(videos, quelle = reihe.copy(defaultAgeMin = 12))).isEmpty()
        assertThat(auswahl(videos, quelle = reihe.copy(isNewsSource = true))).isEmpty()
        assertThat(auswahl(videos, quelle = reihe.copy(defaultCategory = "animeManga"))).isEmpty()   // ab 12
    }

    @Test fun `Videos fremder Kanaele im Feed zaehlen nicht`() {
        assertThat(auswahl(listOf(video("fremd", 1, kanal = "UCanders")))).isEmpty()
    }
}
