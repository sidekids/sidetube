// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import kotlinx.coroutines.flow.first
import xyz.steier.sidetube.core.curation.ContentBundle
import xyz.steier.sidetube.core.curation.FolgenKanal
import xyz.steier.sidetube.core.curation.NeueFolge
import xyz.steier.sidetube.core.curation.NeueFolgen
import xyz.steier.sidetube.core.curation.RiskScreen
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.db.WishEntity
import xyz.steier.sidetube.core.model.SourceTrust
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.provider.ChannelVideo
import xyz.steier.sidetube.core.repo.ChannelVideoCacheRepository
import xyz.steier.sidetube.core.repo.CurationRepository
import xyz.steier.sidetube.core.repo.WhitelistRepository
import java.time.Duration
import java.time.Instant

/**
 * Trägt die Daten für „Neu bei deinen Kanälen" zusammen; die Regeln stehen in [NeueFolgen].
 *
 * Netz nur für die Feeds der Kanäle, die das Kind schon hat und die auf „Vertrauenswürdige Reihe"
 * stehen – wie die Kanalansicht, und höchstens alle [ABSTAND] je Kanal. Ohne Netz trägt der
 * Kanal-Cache.
 */
internal class KidNeueFolgen(
    private val whitelist: WhitelistRepository,
    private val curation: CurationRepository,
    private val cache: ChannelVideoCacheRepository,
    private val content: ContentBundle,
    private val feedHolen: suspend (String) -> List<ChannelVideo>
) {
    private val zuletztGeholt = mutableMapOf<String, Instant>()

    /** Die Reihen-Kanäle unter dem, was das Kind sieht. */
    suspend fun kanaele(visible: List<WhitelistItemEntity>): List<FolgenKanal> =
        visible.filter { it.type == WhitelistItemType.CHANNEL.name && it.provider == "youtube" }
            .mapNotNull { item ->
                val source = curation.source(item.contentId)
                if (SourceTrust.from(source?.trust) == SourceTrust.TRUSTED_SERIES) FolgenKanal(item.contentId, item.title, source) else null
            }

    suspend fun ausCache(kanaele: List<FolgenKanal>): Map<String, List<ChannelVideo>> =
        kanaele.associate { kanal ->
            kanal.channelId to cache.videos(kanal.channelId).map {
                ChannelVideo(it.videoId, it.title, it.thumbnailUrl, it.channelTitle, it.position, kanal.channelId, it.publishedAt, it.isShort)
            }
        }

    /** Holt fällige Feeds und legt sie in den Cache; Fehler bleiben still, der Cache gilt weiter. */
    suspend fun auffrischen(kanaele: List<FolgenKanal>, now: Instant): Boolean {
        var neu = false
        for (kanal in kanaele) {
            val zuletzt = zuletztGeholt[kanal.channelId]
            if (zuletzt != null && Duration.between(zuletzt, now) < ABSTAND) continue
            zuletztGeholt[kanal.channelId] = now
            val frisch = runCatching { feedHolen(kanal.channelId) }.getOrNull() ?: continue
            runCatching { cache.store(kanal.channelId, frisch) }
            neu = true
        }
        return neu
    }

    suspend fun auswahl(
        profile: KidProfileEntity, kanaele: List<FolgenKanal>, videos: Map<String, List<ChannelVideo>>,
        wuensche: List<WishEntity>, now: Instant
    ): List<NeueFolge> {
        if (kanaele.isEmpty()) return emptyList()
        val entscheidungen = whitelist.observeAll(profile.id).first().associateBy { it.contentId }
        return NeueFolgen.auswahl(profile, kanaele, videos, entscheidungen, wuensche, RiskScreen(content.riskTerms()), now)
    }

    private companion object {
        val ABSTAND: Duration = Duration.ofMinutes(30)
    }
}
