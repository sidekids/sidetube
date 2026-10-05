// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.curation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.SensitiveTopic

/** Liest die echten Dateien aus `content/` – dieselben, die auch die iOS-Fassung versorgt. */
private object TestContent : ContentSource {
    override fun read(path: String): String? =
        TestContent::class.java.classLoader?.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }

    override fun listLibraries(): List<String> = listOf("general.json", "alter-9-11.json")
}

private val bundle = ContentBundle(TestContent)

class RiskScreenTest {

    private val screen = RiskScreen(bundle.riskTerms())

    @Test
    fun `Begriffe zaehlen nur am Wortanfang`() {
        // Belege aus einem echten Kanalbestand, die vorher falsch anschlugen
        assertThat(screen.assess("Alle halten ihn für schwach doch er ist der stärkste Attentäter").topics).isEmpty()
        assertThat(screen.assess("Verlassen als schwach doch ist SS Rang und knackt alle").topics).isEmpty()
        assertThat(screen.assess("Ohne Magie geboren doch wird er zum stärksten Krieger").topics).isEmpty()
        assertThat(screen.assess("Neuer Schüler schockt die Schule mit seinen gewaltigen Kampfkünsten").topics).isEmpty()
    }

    @Test
    fun `echte Treffer schlagen weiterhin an`() {
        assertThat(screen.assess("Krieg in der Ukraine").topics).contains(SensitiveTopic.WAR)
        assertThat(screen.assess("Kriegsgebiet aus der Luft").topics).contains(SensitiveTopic.WAR)
        assertThat(screen.assess("Gewalt an Schulen").topics).contains(SensitiveTopic.VIOLENCE)
        assertThat(screen.assess("Entführung aufgeklärt").topics).contains(SensitiveTopic.CRIME)
        assertThat(screen.assess("Er entführt die Prinzessin").topics).contains(SensitiveTopic.CRIME)
    }

    @Test
    fun `harte Begriffe fuehren zur Ablehnung, aehnliche Woerter nicht`() {
        assertThat(screen.assess("Hentai Compilation").isHardBlocked).isTrue()
        assertThat(screen.assess("Dragoon baut eine Burg").isHardBlocked).isFalse()
        assertThat(screen.assess("Wie man Schokolade macht").isHardBlocked).isFalse()
    }

    @Test
    fun `kurze Videos und Livestreams werden markiert`() {
        assertThat(screen.assess("Zeichnen lernen", durationSeconds = 45).isShort).isTrue()
        assertThat(screen.assess("Zeichnen lernen #shorts").isShort).isTrue()
        assertThat(screen.assess("Jetzt live dabei sein").isLive).isTrue()
    }

    @Test
    fun `die Vorpruefung gibt nichts frei`() {
        val harmlos = screen.assess("Wie entsteht ein Regenbogen")
        assertThat(harmlos.isHardBlocked).isFalse()
        assertThat(harmlos.requiresReview).isFalse()
        // Auch ohne Befund bleibt die Entscheidung bei den Eltern: Der Filter kennt kein "freigegeben".
    }
}

class ContentBundleTest {

    @Test
    fun `das Quellenregister ist lesbar und vollstaendig`() {
        val sources = bundle.sources()

        assertThat(sources).isNotEmpty()
        assertThat(sources.map { it.channelId }.toSet()).hasSize(sources.size)
        assertThat(sources.all { it.title.isNotBlank() }).isTrue()
        assertThat(sources.all { xyz.steier.sidetube.core.model.SourceTrust.from(it.trust) != null })
            .isTrue()
    }

    @Test
    fun `die Startpakete sind lesbar und verweisen auf bekannte Quellen`() {
        val known = bundle.sources().associateBy { it.channelId }

        for (name in bundle.libraryNames()) {
            val library = checkNotNull(bundle.library(name)) { "nicht lesbar: $name" }
            assertThat(library.videos.isNotEmpty() || library.channels.isNotEmpty()).isTrue()
            assertThat(library.videos.map { it.id }.toSet()).hasSize(library.videos.size)
            for (video in library.videos) {
                val source = video.channelId?.let { known[it] }
                assertThat(source?.trust).isNotEqualTo("blocked")
            }
        }
    }

    @Test
    fun `die Begriffslisten enthalten Ausnahmen gegen Fehlalarme`() {
        val terms = bundle.riskTerms()
        assertThat(terms.hardBlock).isNotEmpty()
        assertThat(terms.topics).isNotEmpty()
        assertThat(terms.exceptions).contains("krieger")
    }
}

class ContentPolicyTest {

    private val profile = KidProfileEntity(id = "p", name = "Mira", ageBand = "kids")

    private fun item(
        status: String = "approved", ageMin: Int = 0, category: String? = null,
        isNews: Boolean = false, newsStatus: String? = null, isShort: Boolean = false, isLive: Boolean = false
    ) = WhitelistItemEntity(
        id = "i", profileId = "p", type = "VIDEO", contentId = "v", title = "Video",
        approvalStatus = status, ageMin = ageMin, category = category,
        isNews = isNews, newsStatus = newsStatus, isShort = isShort, isLive = isLive
    )

    @Test
    fun `nur Freigegebenes ist sichtbar`() {
        assertThat(ContentPolicy.isVisible(item(), profile, null)).isTrue()
        for (status in listOf("reviewRequired", "discovered", "rejected", "expiredReview")) {
            assertThat(ContentPolicy.isVisible(item(status = status), profile, null)).isFalse()
        }
    }

    @Test
    fun `eine gesperrte Quelle verdeckt auch freigegebene Videos`() {
        val gesperrt = CuratedSourceEntity(channelId = "UC1", title = "X", trust = "blocked")
        assertThat(ContentPolicy.isVisible(item(), profile, gesperrt)).isFalse()
    }

    @Test
    fun `Altersgrenzen greifen`() {
        assertThat(ContentPolicy.isVisible(item(ageMin = 12), profile, null)).isFalse()
        assertThat(ContentPolicy.isVisible(item(ageMin = 9), profile, null)).isTrue()
    }

    @Test
    fun `Anime bleibt aus, solange die Eltern es nicht erlauben`() {
        val streng = profile.copy(allowMangaEntertainment = false)
        assertThat(ContentPolicy.isVisible(item(category = "animeManga"), streng, null)).isFalse()

        val erlaubt = profile.copy(ageBand = "tween", allowManga = true, allowMangaEntertainment = true)
        assertThat(ContentPolicy.isVisible(item(category = "animeManga"), erlaubt, null)).isTrue()
    }

    @Test
    fun `belastende Nachrichten bleiben verborgen und nie auf der Startseite`() {
        val schwer = item(isNews = true, newsStatus = "sensitive")
        assertThat(ContentPolicy.isVisible(schwer, profile, null)).isFalse()
        assertThat(ContentPolicy.isHomeHighlightable(schwer)).isFalse()

        val leicht = item(isNews = true, newsStatus = "safe")
        assertThat(ContentPolicy.isVisible(leicht, profile, null)).isTrue()
        assertThat(ContentPolicy.isHomeHighlightable(leicht)).isTrue()
    }

    @Test
    fun `Shorts und Livestreams bleiben aus, solange sie nicht erlaubt sind`() {
        assertThat(ContentPolicy.isVisible(item(isShort = true), profile, null)).isFalse()
        assertThat(ContentPolicy.isVisible(item(isShort = true), profile.copy(allowShorts = true), null)).isTrue()
        assertThat(ContentPolicy.isVisible(item(isLive = true), profile.copy(allowShorts = true), null)).isFalse()
    }

    @Test
    fun `im Kanal stoebern darf nur, wer als Kinderquelle gilt`() {
        assertThat(ContentPolicy.allowsChannelBrowsing(null)).isFalse()
        assertThat(ContentPolicy.allowsChannelBrowsing(CuratedSourceEntity("UC", title = "X", trust = "perVideoReview"))).isFalse()
        assertThat(ContentPolicy.allowsChannelBrowsing(CuratedSourceEntity("UC", title = "X", trust = "trustedChildSource"))).isTrue()
    }

    @Test
    fun `eine unbekannte PeerTube-Instanz liefert nichts`() {
        val peertube = item().copy(provider = "peertube")
        assertThat(ContentPolicy.isVisible(peertube, profile, null)).isFalse()
    }
}

/** Videos aus einer freigegebenen Playlist: Die Freigabe traegt, aber nicht blind. */
class PlaylistPolicyTest {

    private val profile = KidProfileEntity(id = "p", name = "Mira", ageBand = "kids")
    private val playlist = WhitelistItemEntity(id = "PL1", profileId = "p", type = "PLAYLIST", contentId = "PL1",
        title = "Open Movies", approvalStatus = "approved", ageMin = 6, category = "story")
    private val video = WhitelistItemEntity(id = "v", profileId = "p", type = "VIDEO", contentId = "v", title = "Spring",
        sourceChannelId = "UCvideo")
    private val ohneRisiko = RiskAssessment()

    private fun darf(
        playlist: WhitelistItemEntity = this.playlist, profile: KidProfileEntity = this.profile,
        playlistSource: CuratedSourceEntity? = null, videoSource: CuratedSourceEntity? = null,
        prior: WhitelistItemEntity? = null, risk: RiskAssessment = ohneRisiko,
        video: WhitelistItemEntity = this.video
    ) = ContentPolicy.canPlayFromPlaylist(video, profile, playlist, playlistSource, videoSource, prior, risk)

    @Test
    fun `ein unauffaelliges Video einer freigegebenen Playlist darf laufen`() {
        assertThat(darf()).isTrue()
    }

    @Test
    fun `eine nicht freigegebene oder zu alte Playlist gibt nichts frei`() {
        assertThat(darf(playlist = playlist.copy(approvalStatus = "reviewRequired"))).isFalse()
        assertThat(darf(playlist = playlist.copy(ageMin = 12))).isFalse()
        assertThat(darf(playlist = playlist.copy(type = "CHANNEL"))).isFalse()
    }

    @Test
    fun `die Elternentscheidung zum einzelnen Video geht vor`() {
        assertThat(darf(prior = video.copy(approvalStatus = "rejected"))).isFalse()
        assertThat(darf(prior = video.copy(approvalStatus = "reviewRequired"))).isFalse()
        assertThat(darf(prior = video.copy(approvalStatus = "approved"))).isTrue()
    }

    @Test
    fun `Risikotreffer, Shorts und Livestreams bleiben draussen`() {
        assertThat(darf(risk = RiskAssessment(hardBlockTerms = listOf("x")))).isFalse()
        assertThat(darf(risk = RiskAssessment(topics = setOf(SensitiveTopic.HORROR)))).isFalse()
        assertThat(darf(risk = RiskAssessment(isShort = true))).isFalse()
        assertThat(darf(risk = RiskAssessment(isLive = true))).isFalse()
    }

    @Test
    fun `eine gesperrte Quelle sperrt - die der Playlist wie die des Videos`() {
        val gesperrt = CuratedSourceEntity(channelId = "UC1", title = "X", trust = "blocked")
        assertThat(darf(playlistSource = gesperrt)).isFalse()
        assertThat(darf(videoSource = gesperrt)).isFalse()
        assertThat(darf(videoSource = gesperrt.copy(trust = "parentOnly"))).isFalse()
    }

    @Test
    fun `Shorts und angekuendigte Videos laut Feed bleiben draussen, auch mit harmlosem Titel`() {
        assertThat(darf(video = video.copy(isShort = true))).isFalse()
        assertThat(darf(video = video.copy(isShort = true), profile = profile.copy(allowShorts = true))).isTrue()
        assertThat(darf(video = video.copy(isLive = true))).isFalse()
    }

    @Test
    fun `ohne Kanal-ID nur mit eigener Freigabe`() {
        val ohneKanal = video.copy(sourceChannelId = null)
        assertThat(darf(video = ohneKanal)).isFalse()
        assertThat(darf(video = ohneKanal, prior = ohneKanal.copy(approvalStatus = "approved"))).isTrue()
    }

    @Test
    fun `Kanal mit Einzelpruefung braucht auch in der Playlist eine eigene Freigabe (ADR 0002)`() {
        val einzeln = CuratedSourceEntity(channelId = "UCvideo", title = "Sender", trust = "perVideoReview")
        assertThat(darf(videoSource = einzeln)).isFalse()
        assertThat(darf(videoSource = einzeln, prior = video.copy(approvalStatus = "approved"))).isTrue()
        assertThat(darf(videoSource = einzeln, prior = video.copy(approvalStatus = "rejected"))).isFalse()
        // Andere Stufen und unbekannte Kanaele traegt die Playlist-Freigabe weiterhin.
        assertThat(darf(videoSource = einzeln.copy(trust = "trustedSeries"))).isTrue()
        assertThat(darf(videoSource = einzeln.copy(trust = "trustedChildSource"))).isTrue()
        assertThat(darf(videoSource = null)).isTrue()
    }

    @Test
    fun `Regeln der Playlist gelten fuer jedes Video`() {
        val nachrichten = playlist.copy(category = "news", isNews = true, newsStatus = "safe")
        assertThat(darf(playlist = nachrichten)).isTrue()
        assertThat(darf(playlist = nachrichten, profile = profile.copy(allowNews = false))).isFalse()
    }
}
