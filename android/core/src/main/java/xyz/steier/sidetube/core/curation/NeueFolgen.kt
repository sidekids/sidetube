// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.curation

import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.db.WishEntity
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.provider.ChannelVideo
import java.time.Duration
import java.time.Instant

/** Eine gesperrte neue Folge auf der Startseite: Bild und Titel, aber nur wuenschbar. */
data class NeueFolge(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelTitle: String,
    val publishedAt: Long,
    /** Das Kind hat sie sich schon gewuenscht, die Eltern haben noch nicht entschieden. */
    val gewuenscht: Boolean
)

/** Ein Kanal des Kindes mit seiner Quelle; nur solche mit „Vertrauenswuerdige Reihe" zaehlen. */
data class FolgenKanal(val channelId: String, val title: String, val source: CuratedSourceEntity?)

/**
 * „Neu bei deinen Kanaelen" (ADR 0001, Weg 3). Rein und ohne Datenbank: Wer die Grenzen aendern
 * will, aendert sie hier, und die Tests zeigen, was dann durchrutscht.
 *
 * - nur Kanaele mit der Stufe „Vertrauenswuerdige Reihe" ([ContentPolicy.canShowLockedNewEpisode]),
 * - hoechstens [JE_KANAL] je Kanal, die neuesten zuerst,
 * - nicht aelter als [MAX_ALTER]; ohne bekanntes Datum nicht (lieber eine Folge zu wenig),
 * - keine Shorts und Livestreams, Risikofilter auf den Titel,
 * - nichts, wozu es schon eine Elternentscheidung gibt (freigegeben, abgelehnt, zurueckgestellt,
 *   in Pruefung) und nichts, dessen Wunsch abgelehnt wurde.
 */
object NeueFolgen {
    const val JE_KANAL = 6
    val MAX_ALTER: Duration = Duration.ofDays(60)

    fun auswahl(
        profile: KidProfileEntity,
        kanaele: List<FolgenKanal>,
        videos: Map<String, List<ChannelVideo>>,
        entscheidungen: Map<String, WhitelistItemEntity>,
        wuensche: List<WishEntity>,
        riskScreen: RiskScreen,
        now: Instant
    ): List<NeueFolge> {
        val grenze = now.minus(MAX_ALTER).toEpochMilli()
        val folgenWuensche = wuensche.filter { it.kind == WunschArt.NEUE_FOLGE.id && it.videoId != null }
            .groupBy { it.videoId!! }
        val ergebnis = mutableListOf<NeueFolge>()
        for (kanal in kanaele.distinctBy { it.channelId }) {
            val liste = videos[kanal.channelId].orEmpty()
                .distinctBy { it.videoId }
                .filter { video ->
                    val datum = video.publishedAt ?: return@filter false
                    if (datum < grenze || datum > now.toEpochMilli() + ZUKUNFT_TOLERANZ_MS) return@filter false
                    // Ein Video aus einem fremden Kanal (falls der Feed eines liefert) gehoert nicht hierher.
                    if (video.channelId != null && video.channelId != kanal.channelId) return@filter false
                    val wunsch = folgenWuensche[video.videoId].orEmpty()
                    if (wunsch.any { WunschStatus.from(it.status) in AUSGESCHLOSSEN }) return@filter false
                    val candidate = WhitelistItemEntity(
                        id = video.videoId, profileId = profile.id, type = WhitelistItemType.VIDEO.name,
                        contentId = video.videoId, title = video.title, channelTitle = video.channelTitle,
                        sourceChannelId = kanal.channelId, isShort = video.isShort
                    )
                    ContentPolicy.canShowLockedNewEpisode(
                        candidate, profile, kanal.source, entscheidungen[video.videoId], riskScreen.assess(video.title)
                    )
                }
                .sortedByDescending { it.publishedAt }
                .take(JE_KANAL)
            ergebnis += liste.map { video ->
                NeueFolge(
                    videoId = video.videoId, title = video.title, channelId = kanal.channelId,
                    channelTitle = video.channelTitle.ifBlank { kanal.title }, publishedAt = video.publishedAt!!,
                    gewuenscht = folgenWuensche[video.videoId].orEmpty().any { WunschStatus.from(it.status)?.istOffen == true }
                )
            }
        }
        return ergebnis.sortedByDescending { it.publishedAt }
    }

    /** Erfuellt heisst freigegeben (dann ist es ohnehin abspielbar), abgelehnt heisst: nicht mehr anbieten. */
    private val AUSGESCHLOSSEN = setOf(WunschStatus.ERFUELLT, WunschStatus.ABGELEHNT)

    /** Eine Uhr, die etwas nachgeht, soll die neueste Folge nicht verstecken. */
    private const val ZUKUNFT_TOLERANZ_MS = 24L * 60 * 60 * 1000
}
