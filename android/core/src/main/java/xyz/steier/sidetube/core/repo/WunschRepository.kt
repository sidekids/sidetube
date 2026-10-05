// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.steier.sidetube.core.curation.WunschArt
import xyz.steier.sidetube.core.curation.WunschEntwurf
import xyz.steier.sidetube.core.curation.WunschRegeln
import xyz.steier.sidetube.core.curation.WunschStatus
import xyz.steier.sidetube.core.db.ReviewEventDao
import xyz.steier.sidetube.core.db.ReviewEventEntity
import xyz.steier.sidetube.core.db.WishDao
import xyz.steier.sidetube.core.db.WishEntity
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Was aus einem Wunsch geworden ist. */
sealed interface WunschErgebnis {
    data class Geschickt(val wunsch: WishEntity, val heuteNoch: Int) : WunschErgebnis
    /** Gibt es schon und ist nicht erfuellt; es entsteht kein zweiter. */
    data class SchonGewuenscht(val wunsch: WishEntity) : WunschErgebnis
    /** Die Tagesgrenze ist erreicht. */
    data object Grenze : WunschErgebnis
    /** Kein Stichwort. */
    data object Leer : WunschErgebnis
}

/**
 * Wuensche eines Kindes (ADR 0001). Alles bleibt auf dem Geraet. Jeder Wunsch und jede
 * Entscheidung landet im Verlauf der Freigaben ([ReviewEventEntity]) – bei Videos unter der
 * Video-Kennung, damit der Verlauf des Videos zeigt, dass es gewuenscht wurde; beim Themenwunsch
 * unter `thema:<stichwort>`.
 *
 * Pruefen und Anlegen laufen unter einer Sperre: Zwei schnelle Druecke duerfen weder die
 * Tagesgrenze ueberschreiten noch einen doppelten Wunsch erzeugen.
 */
class WunschRepository(
    private val dao: WishDao,
    private val events: ReviewEventDao,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    private val sperre = Mutex()

    fun observe(profileId: String): Flow<List<WishEntity>> = dao.observeByProfile(profileId)

    fun observeOffen(profileId: String): Flow<List<WishEntity>> = dao.observeOpen(profileId)

    /** Offene Wuensche aller Profile (fuer die Meldung an die Eltern). */
    suspend fun offeneAnzahl(): Int = dao.countOffen()

    suspend fun heuteNoch(profileId: String): Int {
        val (von, bis) = WunschRegeln.tag(now(), zone())
        return (WunschRegeln.TAGESGRENZE - dao.countBetween(profileId, von, bis)).coerceAtLeast(0)
    }

    suspend fun wuensche(entwurf: WunschEntwurf, profileId: String, actor: String): WunschErgebnis = sperre.withLock {
        val thema = (entwurf as? WunschEntwurf.Thema)?.let { WunschRegeln.thema(it.text) }
        if (thema != null && WunschRegeln.schluessel(entwurf) == "thema:") return WunschErgebnis.Leer
        val schluessel = WunschRegeln.schluessel(entwurf)
        val vorhandene = dao.byKey(profileId, schluessel)
        if (WunschRegeln.istDoppelt(vorhandene)) {
            return WunschErgebnis.SchonGewuenscht(vorhandene.first { WunschStatus.from(it.status)?.istOffen == true })
        }
        val frei = heuteNoch(profileId)
        if (frei <= 0) return WunschErgebnis.Grenze

        val zeit = now().toEpochMilli()
        val wunsch = when (entwurf) {
            is WunschEntwurf.Thema -> WishEntity(
                id = newId(), profileId = profileId, kind = WunschArt.THEMA.id, dedupeKey = schluessel,
                topic = thema, createdAt = zeit
            )
            is WunschEntwurf.MehrDavon -> WishEntity(
                id = newId(), profileId = profileId, kind = WunschArt.MEHR_DAVON.id, dedupeKey = schluessel,
                videoId = entwurf.videoId, videoTitle = entwurf.videoTitle,
                channelId = entwurf.channelId, channelTitle = entwurf.channelTitle, createdAt = zeit
            )
            is WunschEntwurf.NeueFolge -> WishEntity(
                id = newId(), profileId = profileId, kind = WunschArt.NEUE_FOLGE.id, dedupeKey = schluessel,
                videoId = entwurf.videoId, videoTitle = entwurf.videoTitle,
                channelId = entwurf.channelId, channelTitle = entwurf.channelTitle, createdAt = zeit
            )
        }
        dao.insert(wunsch)
        events.insert(ReviewEventEntity(
            contentId = verlaufKennung(wunsch), profileId = profileId, decision = "wished",
            actor = actor, at = zeit, note = beschreibung(wunsch)
        ))
        WunschErgebnis.Geschickt(wunsch, frei - 1)
    }

    /**
     * Elternentscheidung: offen → erfuellt/abgelehnt/besprechen, besprechen → erfuellt/abgelehnt/
     * besprechen (neue Antwort). Ein abgeschlossener Wunsch (erfuellt, abgelehnt) aendert sich nicht
     * mehr, damit eine spaete Doppelentscheidung nichts umschreibt, was das Kind schon gesehen hat.
     * Eine leere Antwort laesst eine fruehere stehen.
     */
    suspend fun entscheide(
        wunsch: WishEntity, status: WunschStatus, antwort: String?, actor: String, ergebnis: String? = null
    ): WishEntity? = sperre.withLock {
        val aktuell = dao.byId(wunsch.id) ?: return null
        if (!erlaubt(WunschStatus.from(aktuell.status), status)) return null
        val zeit = now().toEpochMilli()
        val neu = aktuell.copy(
            status = status.id,
            parentReply = antwort?.trim()?.take(WunschRegeln.ANTWORT_MAX)?.takeIf { it.isNotEmpty() } ?: aktuell.parentReply,
            resultContentId = ergebnis ?: aktuell.resultContentId,
            decidedAt = if (status == WunschStatus.OFFEN) aktuell.decidedAt else zeit
        )
        dao.update(neu)
        events.insert(ReviewEventEntity(
            contentId = verlaufKennung(neu), profileId = neu.profileId, decision = ENTSCHEIDUNG.getValue(status),
            actor = actor, at = zeit, note = neu.parentReply.takeIf { antwort?.isNotBlank() == true }
        ))
        neu
    }

    /** Eltern haben zum Wunsch einen Inhalt aufgenommen; erfuellt ist er erst mit dessen Freigabe. */
    suspend fun verknuepfe(wunsch: WishEntity, contentId: String, actor: String) = sperre.withLock {
        val aktuell = dao.byId(wunsch.id) ?: return@withLock
        if (WunschStatus.from(aktuell.status)?.istOffen != true) return@withLock
        dao.update(aktuell.copy(resultContentId = contentId))
        events.insert(ReviewEventEntity(
            contentId = verlaufKennung(aktuell), profileId = aktuell.profileId, decision = "wishLinked",
            actor = actor, at = now().toEpochMilli(), note = contentId
        ))
    }

    /** Eine Freigabe erfuellt die offenen Wuensche, die genau diesen Inhalt meinen. */
    suspend fun erfuelleDurchFreigabe(profileId: String, contentId: String, actor: String): List<WishEntity> =
        dao.openFor(profileId, contentId).mapNotNull { entscheide(it, WunschStatus.ERFUELLT, null, actor, contentId) }

    suspend fun byId(id: String): WishEntity? = dao.byId(id)

    companion object {
        /** Wer im Verlauf als Absender eines Wunsches steht – nie der Name des Kindes (wie iOS). */
        const val ACTOR_KIND = "Kind"

        /**
         * Unter dieser Kennung steht der Wunsch im Verlauf der Freigaben: bei Videos die Video-ID,
         * beim Thema `thema:<stichwort>` (wie iOS).
         */
        fun verlaufKennung(wunsch: WishEntity): String = wunsch.videoId ?: wunsch.dedupeKey

        fun erlaubt(von: WunschStatus?, nach: WunschStatus): Boolean =
            von?.istOffen == true && nach != WunschStatus.OFFEN

        private val ENTSCHEIDUNG = mapOf(
            WunschStatus.ERFUELLT to "wishFulfilled",
            WunschStatus.ABGELEHNT to "wishRejected",
            WunschStatus.BESPRECHEN to "wishDiscuss"
        )

        fun beschreibung(wunsch: WishEntity): String = when (WunschArt.from(wunsch.kind)) {
            WunschArt.THEMA -> "Themenwunsch: ${wunsch.topic.orEmpty()}"
            WunschArt.MEHR_DAVON -> "Mehr davon: ${wunsch.videoTitle.orEmpty()}"
            WunschArt.NEUE_FOLGE -> "Neue Folge: ${wunsch.videoTitle.orEmpty()}"
            null -> wunsch.kind
        }
    }
}
