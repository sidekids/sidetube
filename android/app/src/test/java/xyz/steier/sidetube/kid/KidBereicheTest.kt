// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import xyz.steier.sidetube.core.db.CachedChannelVideoEntity
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.WatchHistoryEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.input.KeyAction
import xyz.steier.sidetube.core.player.PlaybackModel
import xyz.steier.sidetube.core.player.PlaybackStatus
import xyz.steier.sidetube.core.provider.ChannelVideo

/**
 * Startseite in Abschnitten, Mediathek mit Umschalter, „Zuletzt geschaut", Playlists und die
 * Endkarte – alles mit denselben Tasten wie am SidePhone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KidBereicheTest {

    private val playlistId = "PLav47HAVZMjnTFVZL-aImCQIC0uLZtNCz"

    /**
     * Was der Parser aus dem aufgezeichneten Feed „Blender Open Movies" (02.10.2026) liest – den
     * Parser selbst prüft `YouTubeSourcesTest` im Kern mit der Aufzeichnung.
     */
    private val feed = listOf(
        ChannelVideo("u9lj-c29dxI", "WING IT! - Blender Open Movie", "", "Blender Studio", 0, "UCz75RVbH8q2jdBJ4SnwuZZQ"),
        ChannelVideo("WhWc3b3KhnY", "Spring - Blender Open Movie", "", "Blender Studio", 1, "UCz75RVbH8q2jdBJ4SnwuZZQ"),
        ChannelVideo("SkVqJ1SGeL0", "Caminandes 3: Llamigos", "", "Blender", 2, "UCSMOQeBJ2RAnuFungnQOxLg"),
        ChannelVideo("Z4C82eyhwgU", "\"Caminandes 2: Gran Dillama\" - Blender Animated Short", "", "Blender", 3, "UCSMOQeBJ2RAnuFungnQOxLg")
    )

    private fun approved(id: String, type: String, title: String, channel: String? = null) =
        WhitelistItemEntity(id, "p", type, contentId = id, title = title, channelTitle = channel, approvalStatus = "approved")

    private fun fixture() = KidFixture().apply {
        items.value = listOf(
            approved("UCkanal", "CHANNEL", "Blender"),
            approved(playlistId, "PLAYLIST", "Blender Open Movies", "Blender Studio"),
            approved("v1", "VIDEO", "Video 1"),
            approved("v2", "VIDEO", "Video 2"),
            approved("v3", "VIDEO", "Video 3"),
            approved("v4", "VIDEO", "Video 4"),
            approved("v5", "VIDEO", "Video 5")
        )
    }

    private fun test(block: suspend TestScope.(KidFixture, () -> KidViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fx = fixture()
        var vm: KidViewModel? = null
        try {
            block(fx) { fx.model().also { vm = it } }
        } finally { vm?.viewModelScope?.cancel(); Dispatchers.resetMain() }
    }

    private fun KidViewModel.rows() = state.value.rows
    private fun KidViewModel.down(times: Int) = repeat(times) { onKey(KeyAction.FocusNext) }

    @Test fun `Startseite in Abschnitten, Videos begrenzt, zuletzt Alle Videos`() = test { _, make ->
        val vm = make(); runCurrent()
        assertThat(vm.rows().map { it.section to it.title }).containsExactly(
            "Kanäle" to "Blender",
            "Sendungen" to "Blender Open Movies",
            "Videos" to "Video 1", "Videos" to "Video 2", "Videos" to "Video 3", "Videos" to "Video 4",
            "Videos" to "Alle Videos",
            "Wünsche" to "Meine Wünsche"          // ADR 0001: am Ende, die alten Wege bleiben gleich
        ).inOrder()
        assertThat(vm.rows()[1].action).isEqualTo(KidAction.OpenPlaylist(playlistId, "Blender Open Movies"))
    }

    @Test fun `Mediathek mit Tasten - Umschalter, Sendungen, Zurueck an dieselbe Stelle`() = test { _, make ->
        val vm = make(); runCurrent()
        vm.down(6)                                  // auf „Alle Videos"
        vm.onKey(KeyAction.Select)
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Library(LibrarySegment.VIDEOS))
        assertThat(vm.state.value.title).isEqualTo("Alle Videos")
        assertThat(vm.rows().first().action).isEqualTo(KidAction.NextSegment)
        assertThat(vm.rows().drop(1).map { it.title }).containsExactly("Video 1", "Video 2", "Video 3", "Video 4", "Video 5").inOrder()

        vm.onKey(KeyAction.Select)                  // Umschalter: Videos → Sendungen
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Library(LibrarySegment.PLAYLISTS))
        assertThat(vm.state.value.focusIndex).isEqualTo(0)
        assertThat(vm.rows().drop(1).map { it.title }).containsExactly("Blender Open Movies")
        vm.onKey(KeyAction.Select)                  // → Kanäle
        assertThat(vm.rows().drop(1).single().isChannel).isTrue()

        vm.selectSegment(LibrarySegment.VIDEOS)     // per Finger direkt
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Library(LibrarySegment.VIDEOS))

        vm.onKey(KeyAction.Back)
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Home)
        assertThat(vm.state.value.focusIndex).isEqualTo(6)
    }

    @Test fun `Playlist oeffnen, Videos aus dem Feed abspielen, Zurueck zur Startseite`() = test { fx, make ->
        fx.playlists[playlistId] = feed
        val vm = make(); runCurrent()
        vm.onKey(KeyAction.FocusNext)               // Kanal → Sendung
        vm.onKey(KeyAction.Select); runCurrent()
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Playlist(playlistId, "Blender Open Movies"))
        assertThat(fx.requested.single()).isEqualTo(playlistId)
        assertThat(vm.rows().map { it.title }).containsExactly(
            "WING IT! - Blender Open Movie", "Spring - Blender Open Movie",
            "Caminandes 3: Llamigos", "\"Caminandes 2: Gran Dillama\" - Blender Animated Short"
        ).inOrder()
        assertThat(vm.rows().first().thumbnailUrl).contains("u9lj-c29dxI")
        // Zwischengespeichert fuer offline und fuer „Zuletzt geschaut".
        assertThat(fx.cached["playlist:$playlistId"]?.map { it.videoId }).hasSize(4)

        vm.onKey(KeyAction.FocusNext)
        vm.onKey(KeyAction.Select); runCurrent()
        assertThat(vm.state.value.playback?.current?.videoId).isEqualTo("WhWc3b3KhnY")
        assertThat(vm.state.value.playback?.queue).hasSize(4)
        assertThat(fx.commands).contains(PlaybackModel.Command.Load("WhWc3b3KhnY"))

        vm.closePlayer(); runCurrent()
        vm.onKey(KeyAction.Back)
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Home)
        assertThat(vm.state.value.focusIndex).isEqualTo(1)
    }

    @Test fun `Playlist zeigt keine Shorts, keine abgelehnten Videos und nichts aus gesperrten Quellen`() = test { fx, make ->
        fx.playlists[playlistId] = feed.map { if (it.videoId == "SkVqJ1SGeL0") it.copy(title = "Caminandes 3 #shorts") else it }
        fx.items.value += WhitelistItemEntity("u9lj-c29dxI", "p", "VIDEO", contentId = "u9lj-c29dxI",
            title = "WING IT!", approvalStatus = "rejected")
        // Die Kennung des Kanals steht im Feed bei jedem Eintrag (Blender, nicht Blender Studio).
        fx.sources.value = listOf(CuratedSourceEntity("UCSMOQeBJ2RAnuFungnQOxLg", title = "Blender", trust = "blocked"))
        val vm = make(); runCurrent()
        vm.onKey(KeyAction.FocusNext)
        vm.onKey(KeyAction.Select); runCurrent()
        assertThat(vm.rows().map { it.title }).containsExactly("Spring - Blender Open Movie")
    }

    @Test fun `Playlist - Short oder Premiere laut Feed bleibt draussen, auch mit harmlosem Titel`() = test { fx, make ->
        fx.playlists[playlistId] = feed.map {
            when (it.videoId) {
                "SkVqJ1SGeL0" -> it.copy(isShort = true)
                "Z4C82eyhwgU" -> it.copy(isUpcoming = true)
                else -> it
            }
        }
        val vm = make(); runCurrent()
        vm.onKey(KeyAction.FocusNext)
        vm.onKey(KeyAction.Select); runCurrent()
        assertThat(vm.rows().map { it.title }).containsExactly(
            "WING IT! - Blender Open Movie", "Spring - Blender Open Movie").inOrder()
        // Der Zwischenspeicher behaelt die Kennzeichen: offline bleibt es genauso.
        val gespeichert = fx.cached["playlist:$playlistId"].orEmpty().associateBy { it.videoId }
        assertThat(gespeichert.getValue("SkVqJ1SGeL0").isShort).isTrue()
        assertThat(gespeichert.getValue("Z4C82eyhwgU").isUpcoming).isTrue()
        assertThat(gespeichert.getValue("SkVqJ1SGeL0").videoChannelId).isEqualTo("UCSMOQeBJ2RAnuFungnQOxLg")
    }

    @Test fun `Playlist - steht ein Video doppelt drin, erscheint es einmal`() = test { fx, make ->
        fx.playlists[playlistId] = feed + feed[1].copy(position = 4)
        val vm = make(); runCurrent()
        vm.onKey(KeyAction.FocusNext)
        vm.onKey(KeyAction.Select); runCurrent()
        assertThat(vm.rows().map { it.id }).containsNoDuplicates()
        assertThat(vm.rows().map { it.title }).hasSize(4)
    }

    @Test fun `Zwischenspeicher - gesperrter Kanal sperrt ueber die Kanal-ID, nicht ueber den Namen`() = test { fx, make ->
        // Die Quelle heisst anders als der Autorname im Feed – frueher fand die Namenssuche nichts.
        fx.sources.value = listOf(CuratedSourceEntity("UCSMOQeBJ2RAnuFungnQOxLg", title = "Blender Foundation", trust = "blocked"))
        fx.cached["playlist:$playlistId"] = listOf(
            CachedChannelVideoEntity("playlist:$playlistId", "WhWc3b3KhnY", "Spring", "", "Blender Studio", 0,
                videoChannelId = "UCz75RVbH8q2jdBJ4SnwuZZQ"),
            CachedChannelVideoEntity("playlist:$playlistId", "SkVqJ1SGeL0", "Caminandes 3", "", "Blender", 1,
                videoChannelId = "UCSMOQeBJ2RAnuFungnQOxLg")
        )
        val vm = make(); runCurrent()
        vm.onKey(KeyAction.FocusNext)
        vm.onKey(KeyAction.Select); runCurrent()   // kein Feed: wie ohne Netz
        assertThat(vm.rows().map { it.title }).containsExactly("Spring")
    }

    @Test fun `Zwischenspeicher ohne Kanal-ID - nur Videos mit eigener Freigabe`() = test { fx, make ->
        fx.cached["playlist:$playlistId"] = listOf(
            CachedChannelVideoEntity("playlist:$playlistId", "WhWc3b3KhnY", "Spring", "", "Blender Studio", 0),
            CachedChannelVideoEntity("playlist:$playlistId", "v1", "Video 1", "", "Blender", 1)
        )
        val vm = make(); runCurrent()
        vm.onKey(KeyAction.FocusNext)
        vm.onKey(KeyAction.Select); runCurrent()
        assertThat(vm.rows().map { it.title }).containsExactly("Video 1")
    }

    @Test fun `eine Playlist ueber dem Alter des Kindes erscheint nicht und oeffnet nichts`() = test { fx, make ->
        fx.items.value = fx.items.value.map { if (it.id == playlistId) it.copy(ageMin = 16) else it }
        val vm = make(); runCurrent()
        assertThat(vm.rows().map { it.title }).doesNotContain("Blender Open Movies")
        assertThat(fx.requested).isEmpty()
    }

    @Test fun `ohne Netz zeigt die Playlist den Zwischenspeicher`() = test { fx, make ->
        fx.cached["playlist:$playlistId"] = listOf(
            CachedChannelVideoEntity("playlist:$playlistId", "WhWc3b3KhnY", "Spring", "", "Blender Studio", 0,
                videoChannelId = "UCz75RVbH8q2jdBJ4SnwuZZQ")
        )
        val vm = make(); runCurrent()
        vm.onKey(KeyAction.FocusNext)
        vm.onKey(KeyAction.Select); runCurrent()   // kein Feed: wie ohne Netz
        assertThat(vm.rows().map { it.title }).containsExactly("Spring")
        assertThat(vm.state.value.isLoading).isFalse()
    }

    @Test fun `Zuletzt geschaut zeigt nur noch Erlaubtes, jedes Video einmal, neueste zuerst`() = test { fx, make ->
        val jetzt = System.currentTimeMillis()
        fx.cached["playlist:$playlistId"] = listOf(
            CachedChannelVideoEntity("playlist:$playlistId", "WhWc3b3KhnY", "Spring", "", "Blender Studio", 0,
                videoChannelId = "UCz75RVbH8q2jdBJ4SnwuZZQ")
        )
        fx.historyEntries += listOf(
            WatchHistoryEntity(profileId = "p", videoId = "v1", videoTitle = "Video 1", watchedSeconds = 30, watchedAt = jetzt - 5_000),
            WatchHistoryEntity(profileId = "p", videoId = "weg", videoTitle = "Entzogen", watchedSeconds = 30, watchedAt = jetzt - 4_000),
            WatchHistoryEntity(profileId = "p", videoId = "WhWc3b3KhnY", videoTitle = "Spring", watchedSeconds = 30, watchedAt = jetzt - 3_000),
            WatchHistoryEntity(profileId = "p", videoId = "v1", videoTitle = "Video 1", watchedSeconds = 30, watchedAt = jetzt - 1_000),
            WatchHistoryEntity(profileId = "anderes", videoId = "v2", videoTitle = "Video 2", watchedSeconds = 30, watchedAt = jetzt)
        )
        val vm = make(); runCurrent()
        val recent = vm.rows().filter { it.section == "Zuletzt geschaut" }
        assertThat(recent.map { it.title }).containsExactly("Video 1", "Spring").inOrder()
        assertThat(vm.rows().first().section).isEqualTo("Zuletzt geschaut")

        // Entzieht die Familie ein Video, verschwindet es auch aus dem Verlauf.
        fx.items.value = fx.items.value.filterNot { it.id == "v1" }
        runCurrent()
        assertThat(vm.rows().filter { it.section == "Zuletzt geschaut" }.map { it.title }).containsExactly("Spring")

        // Die Warteschlange ist nur der Abschnitt, aus dem gestartet wurde.
        vm.onKey(KeyAction.Select); runCurrent()
        assertThat(vm.state.value.playback?.queue?.map { it.videoId }).containsExactly("WhWc3b3KhnY")
    }

    @Test fun `Endkarte - Nochmal startet dasselbe Video, Zurueck schliesst, kein Autoplay`() = test { fx, make ->
        val vm = make(); runCurrent()
        vm.down(2)                                  // erstes Video
        vm.onKey(KeyAction.Select); runCurrent()
        vm.onPlayerEvent(PlayerEventKind.State, 1)
        vm.onPlayerEvent(PlayerEventKind.State, 0)   // Video zu Ende
        assertThat(vm.state.value.playback?.status).isEqualTo(PlaybackStatus.Ended)
        assertThat(vm.state.value.endFocus).isEqualTo(0)
        assertThat(fx.commands.filterIsInstance<PlaybackModel.Command.Load>().map { it.videoId }).containsExactly("v1")

        vm.onKey(KeyAction.Select)                  // „Nochmal"
        assertThat(fx.commands.filterIsInstance<PlaybackModel.Command.Load>().map { it.videoId }).containsExactly("v1", "v1")
        assertThat(vm.state.value.playback?.status).isEqualTo(PlaybackStatus.Loading)

        vm.onPlayerEvent(PlayerEventKind.State, 1)
        vm.onPlayerEvent(PlayerEventKind.State, 0)
        vm.onKey(KeyAction.FocusNext)               // „Mehr davon wünschen" (ADR 0001)
        assertThat(vm.state.value.endFocus).isEqualTo(1)
        vm.onKey(KeyAction.FocusNext)               // „Zurück zu den Videos"
        assertThat(vm.state.value.endFocus).isEqualTo(2)
        vm.onKey(KeyAction.FocusNext)               // kein Umlauf
        assertThat(vm.state.value.endFocus).isEqualTo(2)
        vm.onKey(KeyAction.Select)
        assertThat(vm.state.value.playback).isNull()
        assertThat(vm.state.value.focusIndex).isEqualTo(2)
    }

    @Test fun `Suche per Lupe und Zurueck an die alte Stelle`() = test { _, make ->
        val vm = make(); runCurrent()
        vm.down(3)
        vm.openSearch()
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Search)
        vm.appendToQuery('7'); vm.appendToQuery('7')   // „sp" – nichts Passendes
        vm.onKey(KeyAction.Back); vm.onKey(KeyAction.Back)
        assertThat(vm.state.value.query).isEmpty()
        vm.onKey(KeyAction.Back)
        assertThat(vm.state.value.screen).isEqualTo(KidScreen.Home)
        assertThat(vm.state.value.focusIndex).isEqualTo(3)
    }
}
