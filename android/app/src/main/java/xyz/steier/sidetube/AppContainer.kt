// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube

import android.content.Context
import xyz.steier.sidetube.core.curation.ContentBundle
import xyz.steier.sidetube.core.curation.ContentSource
import xyz.steier.sidetube.core.curation.RiskScreen
import xyz.steier.sidetube.core.db.SideTubeDatabase
import xyz.steier.sidetube.core.db.inTransaction
import xyz.steier.sidetube.core.net.UrlConnectionHttpClient
import xyz.steier.sidetube.core.provider.ChannelFeedSource
import xyz.steier.sidetube.core.provider.ChannelPageSource
import xyz.steier.sidetube.core.provider.OEmbedSource
import xyz.steier.sidetube.core.provider.PlaylistFeedSource
import xyz.steier.sidetube.core.provider.YouTubeResolver
import xyz.steier.sidetube.core.repo.ChannelVideoCacheRepository
import xyz.steier.sidetube.core.repo.CurationRepository
import xyz.steier.sidetube.core.repo.ProfileRepository
import xyz.steier.sidetube.core.repo.StarterPackService
import xyz.steier.sidetube.core.repo.WatchTimeRepository
import xyz.steier.sidetube.core.repo.WhitelistRepository
import xyz.steier.sidetube.core.repo.WunschRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import xyz.steier.sidetube.core.elternkanal.ElternkanalAblage
import xyz.steier.sidetube.core.elternkanal.TalkBotMelder
import xyz.steier.sidetube.core.elternkanal.UrlConnectionPoster

/** Liest die gemeinsamen Kuratierungsdaten aus den Assets. */
private class AssetContentSource(private val context: Context) : ContentSource {
    override fun read(path: String): String? =
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

    override fun listLibraries(): List<String> =
        runCatching { context.assets.list("content/libraries")?.filter { it.endsWith(".json") }.orEmpty() }
            .getOrDefault(emptyList())
            .sorted()
}

/**
 * Verdrahtet die langlebigen Bausteine von Hand. Bei dieser Groesse traegt eine
 * Annotationsbibliothek ihren Aufwand nicht; hier steht in einer Datei, was wovon abhaengt.
 */
class AppContainer(context: Context) {

    val hasYouTubeApiKey: Boolean = BuildConfig.YOUTUBE_API_KEY.isNotBlank()

    private val database = SideTubeDatabase.open(context)
    private val http = UrlConnectionHttpClient()

    val content = ContentBundle(AssetContentSource(context))
    /** Texte fuer ViewModels und Zeilenaufbau; Compose nimmt `stringResource` direkt. */
    val texte: Texte = AndroidTexte(context)
    val pinStore = PinStore(context)
    val profilePreferences = ProfilePreferences(context)

    val profiles = ProfileRepository(database.kidProfiles(), database.reviewEvents()) { database.inTransaction(it) }
    val whitelist = WhitelistRepository(database.whitelist(), database.sources())
    val watchTime = WatchTimeRepository(database.watchHistory())
    val playbackSessions = database.playbackSessions()
    val channelCache = ChannelVideoCacheRepository(database.channelVideoCache())
    val curation = CurationRepository(
        database.whitelist(), database.sources(), database.reviewEvents(),
        RiskScreen(content.riskTerms())
    )
    val starterPacks = StarterPackService(content, curation, profiles)
    /** Wünsche von Kindern (ADR 0001): nur auf dem Gerät. */
    val wuensche = WunschRepository(database.wishes(), database.reviewEvents())

    /** Elternkanal (ADR 0005): Einrichtung im verschlüsselten Speicher, Meldung über den Talk-Bot. */
    val elternkanal: ElternkanalAblage = ElternkanalSpeicher(context)
    val elternmelder = TalkBotMelder({ elternkanal.lade() }, UrlConnectionPoster())
    private val meldeBereich = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Im Hintergrund, unabhängig vom Bildschirm; ein Fehlschlag ändert nichts am Wunsch. */
    fun meldeNeuenWunsch() {
        meldeBereich.launch { runCatching { elternmelder.neuerWunsch(wuensche.offeneAnzahl()) } }
    }

    val resolver = YouTubeResolver(OEmbedSource(http), ChannelPageSource(http))
    val channelFeed = ChannelFeedSource(http)
    val playlistFeed = PlaylistFeedSource(http)
    val channelPages = ChannelPageSource(http)

    /** Das mitgelieferte Quellenregister anlegen, ohne Elternentscheidungen zu überschreiben. */
    suspend fun seedSources() = curation.ensureSources(content.sources())
}
