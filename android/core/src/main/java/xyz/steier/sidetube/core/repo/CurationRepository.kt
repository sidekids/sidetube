// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.repo

import kotlinx.coroutines.flow.Flow
import xyz.steier.sidetube.core.curation.Kanaleinstufung
import xyz.steier.sidetube.core.curation.Einzelpruefungsgrund
import xyz.steier.sidetube.core.curation.RiskScreen
import xyz.steier.sidetube.core.curation.Sammelfreigabe
import xyz.steier.sidetube.core.curation.Sammelpruefung
import xyz.steier.sidetube.core.curation.Sammelwahl
import xyz.steier.sidetube.core.curation.SourceDefinition
import xyz.steier.sidetube.core.db.CuratedSourceDao
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.ReviewEventDao
import xyz.steier.sidetube.core.db.ReviewEventEntity
import xyz.steier.sidetube.core.db.WhitelistDao
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.ApprovalStatus
import xyz.steier.sidetube.core.model.SourceTrust
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.provider.ContentDraft
import java.util.UUID

/** Warum ein Entwurf nicht aufgenommen wurde. */
sealed class DiscoverError(message: String) : Exception(message) {
    object Duplicate : DiscoverError("schon in der Liste")
    object BlockedSource : DiscoverError("Quelle gesperrt")
}

/** Was Eltern bei der Freigabe festlegen. */
data class Approval(
    val ageMin: Int,
    val ageMax: Int? = null,
    val category: String? = null,
    val newsStatus: String? = null,
    val parentNotes: String? = null
)

/** Was aus einer Kanaleinstufung (ADR 0003) geworden ist – die Oberflaeche sagt es den Eltern. */
sealed interface KanalErgebnis {
    /** Quelle als gesperrt gespeichert; in der Liste des Kindes steht nichts (mehr) davon. */
    data object Gesperrt : KanalErgebnis
    data class Freigegeben(val item: WhitelistItemEntity) : KanalErgebnis
    /** Die Vorpruefung hat hart abgelehnt; freigegeben wird dann nur von Hand in der Pruefmaske. */
    data class VomFilterAbgelehnt(val item: WhitelistItemEntity) : KanalErgebnis
    /** Steht schon abgelehnt in der Liste; ein Link soll das nicht still umdrehen. */
    data class SchonAbgelehnt(val item: WhitelistItemEntity) : KanalErgebnis
}

/** Ergebnis einer Sammelpruefung (ADR 0004): was entschieden wurde und was einzeln bleibt. */
data class Sammelergebnis(
    val entschieden: List<WhitelistItemEntity>,
    val einzeln: Map<WhitelistItemEntity, Einzelpruefungsgrund>
)

/**
 * Der Weg eines Inhalts von der Adresse bis zur Freigabe.
 *
 * Alles kommt hier als *zu pruefen* an, nie als freigegeben – auch dann nicht, wenn die
 * automatische Vorpruefung nichts findet. Nur ein ausdruecklicher [approve] macht etwas sichtbar.
 */
class CurationRepository(
    private val whitelist: WhitelistDao,
    private val sources: CuratedSourceDao,
    private val events: ReviewEventDao,
    private val riskScreen: RiskScreen,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {

    fun observeSources(): Flow<List<CuratedSourceEntity>> = sources.observeAll()

    suspend fun source(channelId: String?): CuratedSourceEntity? =
        channelId?.let { sources.byChannel(it) }

    /** Legt nur an, was fehlt: Eine Elternentscheidung ueberlebt ein spaeteres Register. */
    suspend fun ensureSources(definitions: List<SourceDefinition>) {
        sources.insertMissing(definitions.map { it.toEntity() })
    }

    suspend fun setTrust(source: CuratedSourceEntity, trust: SourceTrust, actor: String) {
        sources.update(source.copy(trust = trust.id, lastReviewedAt = now()))
        events.insert(ReviewEventEntity(
            contentId = "source:${source.channelId}", profileId = null,
            decision = if (trust == SourceTrust.BLOCKED) "blockedSource" else "trustChanged",
            actor = actor, at = now(), note = "${source.title} → ${trust.id}"
        ))
    }

    /**
     * Speichert Stufe, Mindestalter und Kategorie in der Quelle (ADR 0003) – angelegt, falls das
     * Register sie nicht kennt. Bei „Gesperrt" bleiben Alter und Kategorie der Quelle stehen: Sie
     * gelten erst wieder, wenn die Eltern die Sperre aufheben, und sollen dann nicht verloren sein.
     */
    suspend fun speichereQuelle(
        channelId: String, title: String, provider: String, einstufung: Kanaleinstufung, actor: String
    ): CuratedSourceEntity {
        val gesperrt = einstufung.ergebnis == Kanaleinstufung.Ergebnis.NICHT_AUFNEHMEN
        val vorhanden = sources.byChannel(channelId)
        val neu = (vorhanden ?: CuratedSourceEntity(channelId = channelId, title = title, provider = provider)).let {
            if (gesperrt) it.copy(trust = einstufung.trust.id, lastReviewedAt = now())
            else it.copy(
                trust = einstufung.trust.id, defaultAgeMin = einstufung.effektivesMindestalter,
                defaultCategory = einstufung.category?.id, lastReviewedAt = now()
            )
        }
        if (vorhanden == null) sources.insertMissing(listOf(neu)) else sources.update(neu)
        events.insert(ReviewEventEntity(
            contentId = "source:$channelId", profileId = null,
            decision = if (gesperrt) "blockedSource" else "trustChanged",
            actor = actor, at = now(), note = einstufung.verlaufsNotiz(neu.title)
        ))
        return neu
    }

    /**
     * Kanal aus der Vorschau aufnehmen (ADR 0003): Die Einstufung der Eltern ist die Pruefung, der
     * Umweg ueber die Pruefliste entfaellt. „Gesperrt" legt nichts an. Was schon in der Liste steht,
     * wird neu eingestuft – ausser es ist abgelehnt: Ein erneut eingefuegter Link soll eine fruehere
     * Ablehnung (vielleicht durch den Filter) nicht unbemerkt aufheben.
     */
    suspend fun nimmKanalAuf(
        draft: ContentDraft, profileId: String, einstufung: Kanaleinstufung, actor: String
    ): KanalErgebnis {
        require(draft.type == WhitelistItemType.CHANNEL) { "nur fuer Kanaele" }
        whitelist.find(profileId, draft.contentId)?.let { vorhanden ->
            if (ApprovalStatus.from(vorhanden.approvalStatus) == ApprovalStatus.REJECTED &&
                einstufung.ergebnis != Kanaleinstufung.Ergebnis.NICHT_AUFNEHMEN
            ) return KanalErgebnis.SchonAbgelehnt(vorhanden)
            return stufeKanalEin(vorhanden, einstufung, actor)
        }
        speichereQuelle(draft.sourceChannelId ?: draft.contentId, draft.title, draft.provider.id, einstufung, actor)
        val freigabe = einstufung.freigabe() ?: return KanalErgebnis.Gesperrt
        // Ueber discover, damit die Vorpruefung und der Verlauf („Aufgenommen") wie ueberall laufen.
        val item = discover(draft, profileId, actor)
        if (ApprovalStatus.from(item.approvalStatus) == ApprovalStatus.REJECTED) return KanalErgebnis.VomFilterAbgelehnt(item)
        approve(item, freigabe, actor)
        return KanalErgebnis.Freigegeben(whitelist.find(profileId, item.contentId) ?: item)
    }

    /**
     * Einen vorhandenen Kanal-Eintrag einstufen (Pruefliste, Bearbeiten). „Gesperrt" lehnt den Eintrag
     * ab, damit er die Pruefliste verlaesst und nichts davon beim Kind steht.
     */
    suspend fun stufeKanalEin(
        item: WhitelistItemEntity, einstufung: Kanaleinstufung, actor: String,
        ageMax: Int? = null, parentNotes: String? = null, vermerk: String? = null
    ): KanalErgebnis {
        require(item.type == WhitelistItemType.CHANNEL.name) { "nur fuer Kanaele" }
        speichereQuelle(item.sourceChannelId ?: item.contentId, item.channelTitle ?: item.title, item.provider, einstufung, actor)
        val freigabe = einstufung.freigabe(ageMax, parentNotes)
        if (freigabe == null) {
            if (ApprovalStatus.from(item.approvalStatus) != ApprovalStatus.REJECTED) reject(item, actor, "Quelle gesperrt")
            return KanalErgebnis.Gesperrt
        }
        approve(item, freigabe, actor, vermerk)
        return KanalErgebnis.Freigegeben(whitelist.find(item.profileId, item.contentId) ?: item)
    }

    /**
     * Nimmt einen Entwurf als Kandidaten auf. Die Vorpruefung darf hart ablehnen; alles andere
     * landet zur Pruefung bei den Eltern.
     */
    suspend fun discover(draft: ContentDraft, profileId: String, actor: String = "System"): WhitelistItemEntity {
        val source = source(draft.sourceChannelId)
        if (SourceTrust.from(source?.trust) == SourceTrust.BLOCKED) throw DiscoverError.BlockedSource
        if (whitelist.find(profileId, draft.contentId) != null) throw DiscoverError.Duplicate

        val risk = riskScreen.assess(draft.title, draft.description, draft.durationSeconds)
        val status = if (risk.isHardBlocked) ApprovalStatus.REJECTED else ApprovalStatus.REVIEW_REQUIRED

        val item = WhitelistItemEntity(
            id = newId(),
            profileId = profileId,
            type = draft.type.name,
            provider = draft.provider.id,
            contentId = draft.contentId,
            title = draft.title,
            thumbnailUrl = draft.thumbnailUrl,
            channelTitle = draft.channelTitle,
            sourceChannelId = draft.sourceChannelId,
            sourceUrl = draft.sourceUrl,
            addedAt = now(),
            approvalStatus = status.id,
            category = source?.defaultCategory,
            ageMin = source?.defaultAgeMin ?: 0,
            sensitiveTopics = risk.topics.joinToString(",") { it.id },
            isNews = source?.isNewsSource ?: false,
            newsStatus = if (source?.isNewsSource == true) riskScreen.newsStatus(risk).id else null,
            isShort = risk.isShort,
            isLive = risk.isLive,
            durationSeconds = draft.durationSeconds,
            editorialNotes = risk.matchedTerms.takeIf { it.isNotEmpty() }?.joinToString(", ")
        )
        whitelist.insert(item)
        events.insert(ReviewEventEntity(
            contentId = item.contentId, profileId = profileId,
            decision = if (risk.isHardBlocked) "autoRejected" else "discovered",
            actor = actor, at = now(),
            note = risk.hardBlockTerms.takeIf { it.isNotEmpty() }?.joinToString(", ")
        ))
        return item
    }

    fun observePending(profileId: String): Flow<List<WhitelistItemEntity>> = whitelist.observePending(profileId)

    /** [vermerk] steht im Verlauf vor der Anmerkung, z. B. „Sammelprüfung" (ADR 0004). */
    suspend fun approve(item: WhitelistItemEntity, approval: Approval, actor: String, vermerk: String? = null) {
        whitelist.update(item.copy(
            approvalStatus = ApprovalStatus.APPROVED.id,
            ageMin = approval.ageMin,
            ageMax = approval.ageMax,
            category = approval.category,
            newsStatus = if (item.isNews) approval.newsStatus ?: item.newsStatus else item.newsStatus,
            parentNotes = approval.parentNotes,
            approvedBy = actor,
            approvedAt = now(),
            lastReviewedAt = now()
        ))
        events.insert(ReviewEventEntity(
            contentId = item.contentId, profileId = item.profileId,
            decision = "approved", actor = actor, at = now(),
            note = listOfNotNull(vermerk, approval.parentNotes).joinToString(" · ").ifEmpty { null }
        ))
    }

    suspend fun reject(item: WhitelistItemEntity, actor: String, note: String? = null) {
        whitelist.update(item.copy(approvalStatus = ApprovalStatus.REJECTED.id, lastReviewedAt = now()))
        events.insert(ReviewEventEntity(
            contentId = item.contentId, profileId = item.profileId,
            decision = "rejected", actor = actor, at = now(), note = note
        ))
    }

    /**
     * Grund, warum dieser Eintrag einzeln geprueft werden muss (ADR 0004), oder `null`. Der Titel wird
     * dafuer frisch vorgeprueft: Ein harter Treffer soll nicht gesammelt freigegeben werden, auch wenn
     * er am Eintrag nicht (mehr) vermerkt ist.
     */
    fun sammelGrund(item: WhitelistItemEntity, quelle: CuratedSourceEntity?): Einzelpruefungsgrund? =
        Sammelpruefung.grund(item, quelle, riskScreen.assess(item.title, null, item.durationSeconds))

    /**
     * Gibt die Auswahl mit der Sammelwahl frei (ADR 0004). Jeder Eintrag wird frisch gelesen und
     * erneut geprueft; was nicht sammelbar ist, bleibt unberuehrt. Jeder freigegebene Eintrag bekommt
     * seinen eigenen Verlaufseintrag mit dem Vermerk „Sammelprüfung", Kanaele zusaetzlich die Stufe
     * ihrer Quelle (ADR 0003).
     */
    suspend fun sammelFreigeben(items: List<WhitelistItemEntity>, wahl: Sammelwahl, actor: String): Sammelergebnis =
        sammeln(items) { item, quelle ->
            when (val freigabe = Sammelpruefung.freigabe(item, quelle, wahl)) {
                is Sammelfreigabe.Inhalt -> approve(item, freigabe.approval, actor, Sammelpruefung.VERMERK)
                is Sammelfreigabe.Kanal -> stufeKanalEin(
                    item, freigabe.einstufung, actor, freigabe.ageMax, freigabe.parentNotes, Sammelpruefung.VERMERK
                )
            }
        }

    /** Lehnt die Auswahl ab – jeden Eintrag einzeln im Verlauf; nicht Sammelbares bleibt stehen. */
    suspend fun sammelAblehnen(items: List<WhitelistItemEntity>, actor: String): Sammelergebnis =
        sammeln(items) { item, _ -> reject(item, actor, Sammelpruefung.VERMERK) }

    private suspend fun sammeln(
        items: List<WhitelistItemEntity>,
        entscheide: suspend (WhitelistItemEntity, CuratedSourceEntity?) -> Unit
    ): Sammelergebnis {
        val entschieden = mutableListOf<WhitelistItemEntity>()
        val einzeln = linkedMapOf<WhitelistItemEntity, Einzelpruefungsgrund>()
        for (alt in items) {
            val item = whitelist.find(alt.profileId, alt.contentId) ?: continue
            val quelle = source(item.sourceChannelId ?: item.contentId)
            val grund = sammelGrund(item, quelle)
            if (grund != null) { einzeln[item] = grund; continue }
            entscheide(item, quelle)
            entschieden += item
        }
        return Sammelergebnis(entschieden, einzeln)
    }

    /** Zurueck in die Pruefung – auch fuer bereits Freigegebenes, wenn Eltern es sich anders ueberlegen. */
    suspend fun defer(item: WhitelistItemEntity, actor: String) {
        whitelist.update(item.copy(approvalStatus = ApprovalStatus.REVIEW_REQUIRED.id))
        events.insert(ReviewEventEntity(
            contentId = item.contentId, profileId = item.profileId,
            decision = "deferred", actor = actor, at = now()
        ))
    }

    suspend fun history(contentId: String): List<ReviewEventEntity> = events.forContent(contentId)
}
