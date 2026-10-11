// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.repo

import kotlinx.coroutines.CancellationException
import xyz.steier.sidetube.core.db.ChannelVideoCacheDao
import xyz.steier.sidetube.core.db.WatchHistoryDao
import xyz.steier.sidetube.core.db.WhitelistDao
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.db.WishDao
import xyz.steier.sidetube.core.model.ApprovalStatus
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.provider.ContentDraft
import xyz.steier.sidetube.core.provider.ProviderError

/**
 * YouTube-Daten hoechstens 30 Tage (ADR 0010; YouTube API Services Developer Policies III.E.4):
 * Freigaben auffrischen, Zwischenspeicher, Sehverlauf und entschiedene Wuensche loeschen, offene
 * Wuensche um YouTube-Titel kuerzen. Ohne Android und mit stellbarer Uhr, damit pruefbar.
 */
class YouTubeDatenPflege(
    private val whitelist: WhitelistDao,
    private val cache: ChannelVideoCacheDao,
    private val history: WatchHistoryDao,
    private val wishes: WishDao,
    /** Holt Titel, Kanal und Bild neu – derselbe schluesselfreie Weg wie beim Hinzufuegen. */
    private val holen: suspend (WhitelistItemType, String) -> ContentDraft,
    private val now: () -> Long = System::currentTimeMillis
) {
    data class Bericht(
        val aufgefrischt: Int = 0,
        val verschwunden: Int = 0,
        val spaeter: Int = 0,
        val zwischenspeicher: Int = 0,
        val sehverlauf: Int = 0,
        val wuenscheGeloescht: Int = 0,
        val wuenscheGekuerzt: Int = 0
    )

    suspend fun lauf(): Bericht {
        val grenze = grenze(now())
        var bericht = Bericht(
            zwischenspeicher = cache.loescheAelterAls(grenze),
            sehverlauf = history.loescheAelterAls(grenze),
            wuenscheGeloescht = wishes.loescheEntschiedeneAelterAls(grenze),
            wuenscheGekuerzt = wishes.leereYouTubeDatenAelterAls(grenze)
        )
        for (item in whitelist.metadatenFaellig(grenze, MAX_AUFFRISCHEN)) {
            val type = WhitelistItemType.entries.firstOrNull { it.name == item.type } ?: continue
            bericht = try {
                val neu = holen(type, item.contentId)
                whitelist.update(aufgefrischt(item, neu, now()))
                bericht.copy(aufgefrischt = bericht.aufgefrischt + 1)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (endgueltigWeg(e)) {
                    whitelist.update(verschwunden(item, now()))
                    bericht.copy(verschwunden = bericht.verschwunden + 1)
                } else bericht.copy(spaeter = bericht.spaeter + 1)
            }
        }
        return bericht
    }

    companion object {
        const val TAGE = 30
        const val MAX_AUFFRISCHEN = 25
        private const val TAG_MS = 24L * 60 * 60 * 1000

        fun grenze(now: Long): Long = now - TAGE * TAG_MS

        /** 404 heisst geloescht, 401/403 privat oder nicht mehr einbettbar – alles andere: spaeter nochmal. */
        fun endgueltigWeg(e: Exception): Boolean =
            e is ProviderError.NotFound || (e is ProviderError.Http && (e.status == 401 || e.status == 403))

        fun aufgefrischt(item: WhitelistItemEntity, neu: ContentDraft, at: Long) = item.copy(
            title = neu.title,
            channelTitle = neu.channelTitle ?: item.channelTitle,
            thumbnailUrl = neu.thumbnailUrl.ifBlank { item.thumbnailUrl },
            metadataAt = at
        )

        /**
         * Nicht mehr bei YouTube: YouTube-Daten weg, und der Eintrag geht zur Pruefung zurueck – das Kind
         * sieht ihn nicht mehr, die Eltern finden ihn in der Pruefliste und entscheiden.
         */
        fun verschwunden(item: WhitelistItemEntity, at: Long) = item.copy(
            title = "",
            channelTitle = null,
            thumbnailUrl = "",
            metadataAt = at,
            approvalStatus = if (item.approvalStatus == ApprovalStatus.APPROVED.id) ApprovalStatus.EXPIRED_REVIEW.id else item.approvalStatus
        )
    }
}
