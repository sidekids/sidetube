// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.time.Instant
import java.time.ZoneId
import xyz.steier.sidetube.AppContainer
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.PlaybackSessionDao
import xyz.steier.sidetube.core.db.ReviewEventEntity
import xyz.steier.sidetube.core.player.BedtimePolicy
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.SourceTrust
import xyz.steier.sidetube.core.provider.ProviderError
import xyz.steier.sidetube.core.provider.YouTubeResolver
import xyz.steier.sidetube.core.repo.Approval
import xyz.steier.sidetube.core.repo.CurationRepository
import xyz.steier.sidetube.core.repo.ProfileRepository
import xyz.steier.sidetube.core.repo.StarterPackService
import xyz.steier.sidetube.core.repo.WhitelistRepository
import xyz.steier.sidetube.core.provider.ContentDraft
import xyz.steier.sidetube.core.repo.DiscoverError
import xyz.steier.sidetube.core.repo.StarterPack
import xyz.steier.sidetube.core.curation.Einzelpruefungsgrund
import xyz.steier.sidetube.core.curation.Kanaleinstufung
import xyz.steier.sidetube.core.curation.Sammelwahl
import xyz.steier.sidetube.core.curation.SourceDefinition
import xyz.steier.sidetube.core.repo.KanalErgebnis
import xyz.steier.sidetube.core.curation.WunschStatus
import xyz.steier.sidetube.core.db.WishEntity
import xyz.steier.sidetube.core.model.ApprovalStatus
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.provider.YouTubeThumbnails
import xyz.steier.sidetube.core.repo.WunschRepository

/** Rueckmeldung nach einer Sammelpruefung: „5 freigegeben – 2 Wünsche erfüllt. 1 braucht eine Einzelprüfung." */
internal fun sammelMeldung(was: String, entschieden: Int, einzeln: Int, wuenscheErfuellt: Int): String = buildString {
    append(if (entschieden == 1) "1 Eintrag $was" else "$entschieden Einträge $was")
    if (wuenscheErfuellt > 0) append(if (wuenscheErfuellt == 1) " – 1 Wunsch erfüllt" else " – $wuenscheErfuellt Wünsche erfüllt")
    append(".")
    if (einzeln > 0) append(if (einzeln == 1) " 1 braucht eine Einzelprüfung." else " $einzeln brauchen eine Einzelprüfung.")
}

data class ParentState(
    val profiles: List<KidProfileEntity> = emptyList(),
    val items: List<WhitelistItemEntity> = emptyList(),
    val pending: List<WhitelistItemEntity> = emptyList(),
    val sources: List<CuratedSourceEntity> = emptyList(),
    val starterPacks: List<StarterPack> = emptyList(),
    val isBusy: Boolean = false,
    val message: String? = null,
    val interruptedProfiles: Set<String> = emptySet(),
    /** Aufgeloeste Adresse, die Eltern vor dem Aufnehmen sehen (Bild, Titel, Kanal). */
    val vorschau: ContentDraft? = null,
    /** Verlauf des Eintrags, der gerade im Pruef-/Bearbeiten-Dialog offen ist, neueste zuerst. */
    val verlauf: List<ReviewEventEntity> = emptyList(),
    /** Offene Wuensche des geoeffneten Profils (offen und „besprechen"), aelteste zuerst. */
    val wuensche: List<WishEntity> = emptyList(),
    /** Zu diesem Wunsch wird gerade ein Link aufgenommen; die Vorschau verknuepft ihn. */
    val linkFuerWunsch: WishEntity? = null
) {
    /** Quelle eines Entwurfs bzw. Eintrags – traegt die Vorauswahl beim Einstufen (ADR 0003). */
    fun quelleZu(draft: ContentDraft): CuratedSourceEntity? =
        (draft.sourceChannelId ?: draft.contentId).let { id -> sources.firstOrNull { it.channelId == id } }

    fun quelleZu(item: WhitelistItemEntity): CuratedSourceEntity? =
        (item.sourceChannelId ?: item.contentId).let { id -> sources.firstOrNull { it.channelId == id } }
}

/**
 * Zustand des Elternbereichs. Haelt nur, was die Oberflaeche zeigt; jede Entscheidung geht
 * durch die Kuratierung, damit die Regeln an einer Stelle bleiben.
 */
class ParentViewModel internal constructor(
    private val profiles: ProfileRepository,
    private val whitelist: WhitelistRepository,
    private val curation: CurationRepository,
    private val playbackSessions: PlaybackSessionDao,
    private val starterPacks: StarterPackService,
    private val resolver: YouTubeResolver,
    private val seedSources: suspend () -> Unit,
    private val wuensche: WunschRepository
) : ViewModel() {

    constructor(container: AppContainer) : this(
        container.profiles, container.whitelist, container.curation, container.playbackSessions,
        container.starterPacks, container.resolver, { container.seedSources() }, container.wuensche
    )

    private val _state = MutableStateFlow(ParentState())
    val state: StateFlow<ParentState> = _state.asStateFlow()

    private var openProfileId: String? = null
    private var itemsJob: Job? = null
    private var pendingJob: Job? = null
    private var wishJob: Job? = null

    init {
        viewModelScope.launch {
            playbackSessions.observePending().collect { sessions ->
                _state.update { it.copy(interruptedProfiles = sessions.map { session -> session.profileId }.toSet()) }
            }
        }
        viewModelScope.launch {
            seedSources()
            _state.update { it.copy(starterPacks = starterPacks.available()) }
        }
        viewModelScope.launch {
            profiles.observeAll().collect { profiles ->
                _state.update { it.copy(profiles = profiles) }
            }
        }
        viewModelScope.launch {
            curation.observeSources().collect { sources ->
                _state.update { it.copy(sources = sources) }
            }
        }
    }

    fun openProfile(profile: KidProfileEntity) {
        openProfileId = profile.id
        itemsJob?.cancel()
        pendingJob?.cancel()
        wishJob?.cancel()
        _state.update { it.copy(items = emptyList(), pending = emptyList(), wuensche = emptyList()) }
        wishJob = viewModelScope.launch {
            wuensche.observeOffen(profile.id).collect { offen -> _state.update { it.copy(wuensche = offen) } }
        }
        itemsJob = viewModelScope.launch {
            whitelist.observeAll(profile.id).collect { items ->
                _state.update { it.copy(items = items) }
            }
        }
        pendingJob = viewModelScope.launch {
            curation.observePending(profile.id).collect { pending ->
                _state.update { it.copy(pending = pending) }
            }
        }
    }

    fun createProfile(name: String) = launchWithMessage {
        profiles.create(name)
        "Profil „$name“ angelegt."
    }

    fun deleteProfile(profile: KidProfileEntity) = launchWithMessage {
        profiles.delete(profile)
        "Profil „${profile.name}“ entfernt."
    }

    fun updateProfile(profile: KidProfileEntity) = launchWithMessage {
        profiles.update(profile)
        null
    }

    /**
     * Sichert die Profilmaske. Gelesen wird das Profil frisch aus der Datenbank, nicht die Fassung
     * beim Oeffnen: Eine inzwischen gesetzte Ausnahme von der Ruhezeit bliebe sonst auf der
     * Strecke. Die Durchsetzung (Tageslimit, Ruhezeit) beobachtet das Profil und greift sofort.
     */
    fun saveProfile(profileId: String, draft: ProfileDraft) = launchWithMessage {
        if (!draft.canSave) return@launchWithMessage "Bitte einen Namen eingeben."
        val current = profiles.byId(profileId) ?: return@launchWithMessage "Das Profil gibt es nicht mehr."
        val updated = draft.applyTo(current)
        profiles.update(updated)
        "Profil „${updated.name}“ gesichert."
    }

    /** Die neue PIN liegt schon im Speicher; hier geht es nur um die Rueckmeldung. */
    fun pinChanged() = _state.update { it.copy(message = "Die PIN ist geändert.") }

    /**
     * Setzt die laufende Ruhezeit bis zu ihrem Ende aus - nicht unbegrenzt und nicht fuer eine
     * frei gewaehlte Dauer, damit aus einer Ausnahme kein dauerhaftes Abschalten wird.
     */
    fun skipBedtimeTonight(profile: KidProfileEntity) = launchWithMessage {
        val until = BedtimePolicy.endOfCurrentWindow(profile, Instant.now(), ZoneId.systemDefault())
        if (until == null) {
            "Gerade laeuft keine Ruhezeit, oder die gespeicherten Zeiten sind unplausibel."
        } else {
            profiles.update(profile.copy(bedtimeSkipUntil = until.toEpochMilli()))
            "Ruhezeit ausgesetzt bis ${ParentFormat.time(until)}."
        }
    }

    fun clearBedtimeException(profile: KidProfileEntity) = launchWithMessage {
        profiles.update(profile.copy(bedtimeSkipUntil = null))
        "Die Ausnahme ist aufgehoben."
    }

    /**
     * Adresse aufloesen und zuerst zeigen – wie auf iOS. Erst [nimmVorschauAuf] nimmt den
     * Kandidaten auf; die Freigabe folgt getrennt. Vorher landete jede Adresse ungesehen in
     * der Pruefliste, und ein Tippfehler fiel erst dort auf.
     */
    fun previewFromUrl(url: String) = launchWithMessage {
        try {
            val draft = resolver.resolve(url)
            _state.update { it.copy(vorschau = draft) }
            null
        } catch (_: ProviderError.NotFound) {
            "Dazu gibt es nichts – gelöscht oder privat?"
        } catch (_: ProviderError.Unsupported) {
            "Diese Adresse versteht SideTube nicht."
        } catch (error: Exception) {
            "Hat nicht geklappt: ${error.message ?: "unbekannter Fehler"}"
        }
    }

    fun nimmVorschauAuf(profileId: String) = launchWithMessage {
        val draft = _state.value.vorschau ?: return@launchWithMessage null
        val wunsch = _state.value.linkFuerWunsch
        _state.update { it.copy(vorschau = null, linkFuerWunsch = null) }
        try {
            curation.discover(draft, profileId, actor = "Eltern")
            wunsch?.let { wuensche.verknuepfe(it, draft.contentId, "Eltern") }
            if (wunsch != null) "„${draft.title}“ wartet auf die Prüfung. Mit der Freigabe ist der Wunsch erfüllt."
            else "„${draft.title}“ wartet auf die Prüfung."
        } catch (_: DiscoverError.Duplicate) {
            // Steht schon da: Der Wunsch zeigt trotzdem darauf.
            wunsch?.let { wuensche.verknuepfe(it, draft.contentId, "Eltern") }
            "Das steht schon in der Liste."
        } catch (_: DiscoverError.BlockedSource) {
            "Diese Quelle ist gesperrt."
        }
    }

    /**
     * Kanal aus der Vorschau mit der Einstufung der Eltern aufnehmen (ADR 0003). Anders als bei
     * Videos und Playlists ist die Einstufung schon die Pruefung: Der Kanal ist danach freigegeben,
     * ein verknuepfter Wunsch damit erfuellt.
     */
    fun nimmKanalAuf(profileId: String, einstufung: Kanaleinstufung) = launchWithMessage {
        val draft = _state.value.vorschau ?: return@launchWithMessage null
        val wunsch = _state.value.linkFuerWunsch
        _state.update { it.copy(vorschau = null, linkFuerWunsch = null) }
        val ergebnis = curation.nimmKanalAuf(draft, profileId, einstufung, actor = "Eltern")
        // Wie bei „Aufnehmen": Steht ein Eintrag da, zeigt der Wunsch darauf – auch wenn er noch nicht frei ist.
        if (wunsch != null && ergebnis !is KanalErgebnis.Gesperrt) wuensche.verknuepfe(wunsch, draft.contentId, "Eltern")
        kanalMeldung(draft.title, profileId, draft.contentId, ergebnis)
    }

    /** Kanal-Kandidat aus der Pruefliste (oder ein freigegebener Kanal) mit Stufe, Alter und Kategorie. */
    fun stufeKanalEin(item: WhitelistItemEntity, einstufung: Kanaleinstufung, ageMax: Int?, notes: String?) =
        launchWithMessage {
            val ergebnis = curation.stufeKanalEin(item, einstufung, actor = "Eltern", ageMax = ageMax, parentNotes = notes)
            kanalMeldung(item.title, item.profileId, item.contentId, ergebnis)
        }

    private suspend fun kanalMeldung(titel: String, profileId: String, contentId: String, ergebnis: KanalErgebnis): String =
        when (ergebnis) {
            KanalErgebnis.Gesperrt -> "„$titel“ ist gesperrt und erscheint nicht beim Kind. Links aus diesem Kanal werden abgewiesen."
            is KanalErgebnis.Freigegeben -> {
                // Was ein Kind sich gewuenscht hat, ist mit der Freigabe erfuellt (ADR 0001).
                val erfuellt = wuensche.erfuelleDurchFreigabe(profileId, contentId, "Eltern")
                if (erfuellt.isEmpty()) "„$titel“ ist eingestuft und freigegeben."
                else "„$titel“ ist eingestuft und freigegeben – Wunsch erfüllt."
            }
            is KanalErgebnis.VomFilterAbgelehnt ->
                "Der Filter hat „$titel“ abgelehnt (${ergebnis.item.editorialNotes.orEmpty()}). Bitte in der Liste selbst ansehen."
            is KanalErgebnis.SchonAbgelehnt -> "„$titel“ ist schon abgelehnt. Bitte in der Liste selbst ansehen."
        }

    fun verwirfVorschau() = _state.update { it.copy(vorschau = null, linkFuerWunsch = null) }

    fun approve(item: WhitelistItemEntity, approval: Approval) = launchWithMessage {
        curation.approve(item, approval, actor = "Eltern")
        // Was ein Kind sich gewuenscht hat, ist mit der Freigabe erfuellt (ADR 0001).
        val erfuellt = wuensche.erfuelleDurchFreigabe(item.profileId, item.contentId, "Eltern")
        if (erfuellt.isEmpty()) "„${item.title}“ ist freigegeben." else "„${item.title}“ ist freigegeben – Wunsch erfüllt."
    }

    // ── Wuensche (ADR 0001) ────────────────────────────────────────────────────────────────

    /**
     * Neue Folge freigeben: legt einen freigegebenen Eintrag an (Alter und Kategorie aus der Quelle)
     * und erfuellt den Wunsch. Lehnt der Filter das Video hart ab, wird nichts freigegeben.
     */
    fun wunschFreigeben(wunsch: WishEntity, antwort: String?) = launchWithMessage {
        val videoId = wunsch.videoId ?: return@launchWithMessage "Zu diesem Wunsch gehört kein Video."
        val draft = ContentDraft(
            type = WhitelistItemType.VIDEO, contentId = videoId, title = wunsch.videoTitle ?: videoId,
            thumbnailUrl = YouTubeThumbnails.url(videoId, 320), channelTitle = wunsch.channelTitle,
            sourceChannelId = wunsch.channelId, sourceUrl = "https://www.youtube.com/watch?v=$videoId"
        )
        val item = try {
            curation.discover(draft, wunsch.profileId, actor = "Eltern").also {
                if (ApprovalStatus.from(it.approvalStatus) == ApprovalStatus.REJECTED) {
                    return@launchWithMessage "Der Filter hat „${draft.title}“ abgelehnt (${it.editorialNotes.orEmpty()}). Bitte selbst ansehen."
                }
            }
        } catch (_: DiscoverError.Duplicate) {
            val vorhanden = whitelist.find(wunsch.profileId, videoId) ?: return@launchWithMessage "Der Eintrag ist verschwunden."
            // Wie ohne Eintrag: Was abgelehnt ist (von den Eltern oder vom Filter), gibt dieser Knopf nicht frei.
            if (ApprovalStatus.from(vorhanden.approvalStatus) == ApprovalStatus.REJECTED) {
                return@launchWithMessage "„${vorhanden.title}“ ist schon abgelehnt" +
                    (vorhanden.editorialNotes?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") +
                    ". Bitte in der Liste selbst ansehen."
            }
            vorhanden
        } catch (_: DiscoverError.BlockedSource) {
            return@launchWithMessage "Diese Quelle ist gesperrt."
        }
        val approval = ReviewInput(item.ageMin, false, 12, item.category, "").toApproval()
        curation.approve(item, approval, actor = "Eltern")
        wuensche.entscheide(wunsch, WunschStatus.ERFUELLT, antwort, "Eltern", ergebnis = videoId)
        "„${item.title}“ ist freigegeben – Wunsch erfüllt."
    }

    /** Ablehnen, Besprechen, Erledigt – mit freiwilliger Antwort an das Kind. */
    fun wunschEntscheiden(wunsch: WishEntity, status: WunschStatus, antwort: String?) = launchWithMessage {
        wuensche.entscheide(wunsch, status, antwort, "Eltern") ?: return@launchWithMessage "Der Wunsch ist schon entschieden."
        when (status) {
            WunschStatus.ABGELEHNT -> "Wunsch abgelehnt („nicht jetzt“)."
            WunschStatus.BESPRECHEN -> "Wunsch steht auf „Sprechen wir drüber“."
            WunschStatus.ERFUELLT -> "Wunsch erledigt."
            WunschStatus.OFFEN -> null
        }
    }

    /**
     * „Kanal prüfen" bei „Mehr davon": Fehlt der Kanal in der Liste des Kindes, kommt er als
     * Kandidat in die Pruefliste; steht er schon da, geht es zur Stufe ([zuQuellen]). Die Quelle
     * wird dafuer angelegt, falls das Register sie nicht kennt – mit der vorsichtigsten Stufe, die
     * nichts sichtbar macht, was nicht schon sichtbar war.
     */
    fun wunschKanalPruefen(wunsch: WishEntity, zuQuellen: () -> Unit) = launchWithMessage {
        // Videos, die per Link kamen, kennen ihren Kanal oft nur dem Namen nach; dann ueber oEmbed.
        val gefunden = if (wunsch.channelId == null) kanalZumVideo(wunsch)
            ?: return@launchWithMessage "Der Kanal zu diesem Video ließ sich nicht finden. Ist das Internet an?"
            else null
        val kanal = gefunden?.contentId ?: wunsch.channelId!!
        val titel = gefunden?.title ?: wunsch.channelTitle ?: kanal
        if (whitelist.find(wunsch.profileId, kanal) == null) {
            try {
                curation.discover(gefunden ?: ContentDraft(
                    type = WhitelistItemType.CHANNEL, contentId = kanal, title = titel, channelTitle = titel,
                    sourceChannelId = kanal, sourceUrl = "https://www.youtube.com/channel/$kanal"
                ), wunsch.profileId, actor = "Eltern")
                "„$titel“ wartet als Kanal in der Prüfliste – dort Stufe, Alter und Kategorie wählen."
            } catch (_: DiscoverError.BlockedSource) {
                "Dieser Kanal ist gesperrt."
            }
        } else {
            curation.ensureSources(listOf(SourceDefinition(channelId = kanal, title = titel, trust = "perVideoReview")))
            zuQuellen()
            "„$titel“ steht schon in der Liste – hier die Stufe ändern."
        }
    }

    private suspend fun kanalZumVideo(wunsch: WishEntity): ContentDraft? = runCatching {
        val video = resolver.resolve("https://www.youtube.com/watch?v=${wunsch.videoId ?: return null}")
        video.channelUrl?.let { resolver.resolve(it) }?.takeIf { it.type == WhitelistItemType.CHANNEL }
    }.getOrNull()

    /** Link zum Wunsch: derselbe Ablauf mit Vorschau wie „Link hinzufügen". */
    fun wunschLink(wunsch: WishEntity, url: String) {
        _state.update { it.copy(linkFuerWunsch = wunsch) }
        previewFromUrl(url)
    }

    /** Verlauf eines Wunsches: unter dem Video bzw. unter `thema:<stichwort>`. */
    fun loadWunschVerlauf(wunsch: WishEntity) {
        _state.update { it.copy(verlauf = emptyList()) }
        viewModelScope.launch {
            val events = runCatching { curation.history(WunschRepository.verlaufKennung(wunsch)) }.getOrDefault(emptyList())
                .filter { it.profileId == null || it.profileId == wunsch.profileId }
            _state.update { it.copy(verlauf = events) }
        }
    }

    // ── Sammelpruefung (ADR 0004) ──────────────────────────────────────────────────────────

    /** Warum ein Eintrag der Pruefliste einzeln geprueft werden muss, oder `null`, wenn er sammelbar ist. */
    fun sammelGrund(item: WhitelistItemEntity): Einzelpruefungsgrund? =
        curation.sammelGrund(item, _state.value.quelleZu(item))

    /**
     * Auswahl gemeinsam freigeben. Jeder Eintrag bekommt seinen eigenen Verlaufseintrag; Wuensche,
     * die damit erfuellt sind, gelten wie im Einzelweg als erfuellt (ADR 0001).
     */
    fun sammelFreigeben(items: List<WhitelistItemEntity>, wahl: Sammelwahl) = launchWithMessage {
        val ergebnis = curation.sammelFreigeben(items, wahl, actor = "Eltern")
        val erfuellt = ergebnis.entschieden.sumOf { wuensche.erfuelleDurchFreigabe(it.profileId, it.contentId, "Eltern").size }
        sammelMeldung("freigegeben", ergebnis.entschieden.size, ergebnis.einzeln.size, erfuellt)
    }

    fun sammelAblehnen(items: List<WhitelistItemEntity>) = launchWithMessage {
        val ergebnis = curation.sammelAblehnen(items, actor = "Eltern")
        sammelMeldung("abgelehnt", ergebnis.entschieden.size, ergebnis.einzeln.size, 0)
    }

    fun reject(item: WhitelistItemEntity) = launchWithMessage {
        curation.reject(item, actor = "Eltern")
        "„${item.title}“ abgelehnt."
    }

    fun backToReview(item: WhitelistItemEntity) = launchWithMessage {
        curation.defer(item, actor = "Eltern")
        "„${item.title}“ steht wieder zur Prüfung."
    }

    /**
     * „Später“ wie auf iOS: Der Eintrag bleibt in der Pruefliste, der Verlauf haelt fest, dass er
     * angesehen und zurueckgestellt wurde. Nichts wird dadurch sichtbar.
     */
    fun later(item: WhitelistItemEntity) = launchWithMessage {
        curation.defer(item, actor = "Eltern")
        "„${item.title}“ zurückgestellt."
    }

    /**
     * Verlauf eines Eintrags fuer den Dialog. Dieselbe Inhaltskennung kann in mehreren Profilen
     * stehen; gezeigt wird nur, was dieses Profil betrifft (und profillose Eintraege).
     */
    fun loadHistory(item: WhitelistItemEntity) {
        _state.update { it.copy(verlauf = emptyList()) }
        viewModelScope.launch {
            val events = runCatching { curation.history(item.contentId) }.getOrDefault(emptyList())
                .filter { it.profileId == null || it.profileId == item.profileId }
            _state.update { it.copy(verlauf = events) }
        }
    }

    fun remove(item: WhitelistItemEntity) = launchWithMessage {
        whitelist.remove(item)
        "„${item.title}“ entfernt."
    }

    /** Alle offenen Kandidaten verwerfen – Freigegebenes bleibt unberuehrt. */
    fun discardPending() = launchWithMessage {
        val open = _state.value.pending
        open.forEach { whitelist.remove(it) }
        "${open.size} offene Einträge verworfen."
    }

    fun setTrust(source: CuratedSourceEntity, trust: SourceTrust) = launchWithMessage {
        curation.setTrust(source, trust, actor = "Eltern")
        null
    }

    fun importStarterPack(pack: StarterPack, profile: KidProfileEntity, applyPreset: Boolean) = launchWithMessage {
        val result = starterPacks.import(pack.fileName, profile, applyPreset)
        buildString {
            append("${pack.title}: ${result.added} zur Prüfung für ${profile.name}")
            if (result.skipped > 0) append(", ${result.skipped} schon vorhanden")
            if (result.blocked > 0) append(", ${result.blocked} aus gesperrten Quellen")
            append(".")
            if (result.presetApplied) append(" Profilvorgaben übernommen.")
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun launchWithMessage(block: suspend () -> String?) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val message = runCatching { block() }.getOrElse { "Fehler: ${it.message}" }
            _state.update { it.copy(isBusy = false, message = message) }
        }
    }
}
