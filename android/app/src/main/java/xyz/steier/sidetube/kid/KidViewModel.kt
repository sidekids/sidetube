// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import xyz.steier.sidetube.AppContainer
import xyz.steier.sidetube.core.curation.ContentPolicy
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.input.FocusModel
import xyz.steier.sidetube.core.input.KeyAction
import xyz.steier.sidetube.core.input.RadTaste
import xyz.steier.sidetube.core.input.Radsuche
import xyz.steier.sidetube.core.input.T9
import xyz.steier.sidetube.core.model.SourceTrust
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.player.PlayableVideo
import xyz.steier.sidetube.core.player.PlaybackModel
import xyz.steier.sidetube.core.player.PlaybackStatus
import xyz.steier.sidetube.core.player.BedtimePolicy
import xyz.steier.sidetube.core.player.BedtimeState
import xyz.steier.sidetube.core.player.SleepTimer
import xyz.steier.sidetube.core.player.SleepTimerPolicy
import java.time.Instant
import java.time.ZoneId
import java.time.Duration
import java.util.UUID
import xyz.steier.sidetube.core.db.PlaybackSessionEntity
import xyz.steier.sidetube.core.provider.ChannelVideo
import xyz.steier.sidetube.ProfilePreferenceStore
import xyz.steier.sidetube.core.curation.NeueFolge
import xyz.steier.sidetube.core.curation.WunschEntwurf
import xyz.steier.sidetube.core.curation.WunschRegeln
import xyz.steier.sidetube.core.curation.WunschStatus
import xyz.steier.sidetube.core.db.WishEntity
import xyz.steier.sidetube.core.repo.WunschErgebnis
import xyz.steier.sidetube.core.repo.WunschRepository

/** Welche Art Meldung der Player geschickt hat. */
enum class PlayerEventKind { State, Error, Time }

data class KidState(
    val profile: KidProfileEntity? = null,
   /** Fuer den Profil-Umschalter im Kopf des Kindermodus; leer/einzeln zeigt ihn nicht an. */
    val profiles: List<KidProfileEntity> = emptyList(),
    val screen: KidScreen = KidScreen.Home,
    val rows: List<KidRow> = emptyList(),
    val focusIndex: Int = 0,
    val title: String = "",
    val query: String = "",
    val playback: xyz.steier.sidetube.core.player.PlaybackState? = null,
    val isLoading: Boolean = false,
    val hint: String? = null,
    val remainingMinutes: Int? = null,
    /** Restzeit des Schlaf-Timers; `null` wenn keiner laeuft, `0` nach Ablauf. */
    val sleepRemainingSeconds: Int? = null,
    /** Kanal- oder Playlistbild im Kopf der Detailansicht; sonst `null`. */
    val titelBild: String? = null,
    /** Auswahl auf der Endkarte nach dem Video: 0 = „Nochmal", 1 = „Zurück zu den Videos". */
    val endFocus: Int = 0,
    /** Das Buchstabenrad der Suche; [query] ist dazu die Eingabe in Suchform. */
    val rad: RadZustand = RadZustand(),
    /** Wie viele Wünsche heute noch gehen (ADR 0001: 3 je Profil und Tag). */
    val wuenscheHeute: Int = WunschRegeln.TAGESGRENZE,
    /** Ob „Mehr davon" zum laufenden Video geht – Endkarte und Player-Menü. */
    val mehrDavon: WunschMoeglich = WunschMoeglich.JA,
    /** Player-Menü offen: Auswahl 0 = „Mehr davon wünschen", 1 = „Weiterschauen"; sonst `null`. */
    val playerMenu: Int? = null
)

/**
 * Kindermodus. Der Fokus laeuft linear durch alles, was der Bildschirm zeigt – so ist die
 * Bedienung mit Tasten und mit Beruehrung dieselbe, und es gibt keine unerreichbaren Stellen.
 */
class KidViewModel internal constructor(
    private val profiles: xyz.steier.sidetube.core.repo.ProfileRepository,
    private val whitelist: xyz.steier.sidetube.core.repo.WhitelistRepository,
    private val watchTime: xyz.steier.sidetube.core.repo.WatchTimeRepository,
    private val playbackSessions: xyz.steier.sidetube.core.db.PlaybackSessionDao,
    private val curation: xyz.steier.sidetube.core.repo.CurationRepository,
    private val channelCache: xyz.steier.sidetube.core.repo.ChannelVideoCacheRepository,
    private val content: xyz.steier.sidetube.core.curation.ContentBundle,
    private val channelFeed: xyz.steier.sidetube.core.provider.ChannelFeedSource,
    /** Die Uhr, nach der Ruhezeit und Schlaf-Timer entscheiden; im Test stellbar. */
    private val now: () -> Instant = Instant::now,
    private val profilePreferences: ProfilePreferenceStore = object : ProfilePreferenceStore {
        override var lastProfileId: String? = null
    },
    /** Bildadresse eines Kanals von seiner Kanalseite; im Test ohne Netz. */
    private val kanalbildHolen: suspend (String) -> String? = { null },
    /** Videos einer Playlist aus ihrem Feed ([xyz.steier.sidetube.core.provider.PlaylistFeedSource]); im Test ohne Netz. */
    private val playlistHolen: suspend (String) -> List<ChannelVideo>,
    /** Wünsche des Kindes (ADR 0001); bleiben auf dem Gerät. */
    private val wuensche: WunschRepository,
    /** Meldet den Eltern einen neu angelegten Wunsch (ADR 0005); läuft außerhalb dieses ViewModels. */
    private val neuerWunschGemeldet: () -> Unit = {}
) : ViewModel() {
    constructor(container: AppContainer) : this(
        container.profiles, container.whitelist, container.watchTime, container.playbackSessions,
        container.curation, container.channelCache, container.content, container.channelFeed,
        profilePreferences = container.profilePreferences,
        kanalbildHolen = { id -> container.channelPages.byId(id).thumbnailUrl.takeIf { it.isNotBlank() } },
        playlistHolen = container.playlistFeed::videos,
        wuensche = container.wuensche,
        neuerWunschGemeldet = container::meldeNeuenWunsch
    )

    /**
     * Nachgeholte Kanalbilder, je Kanal-ID. Startpakete liefern keine mit (sie aendern sich beim
     * Anbieter); ohne Nachholen bliebe so ein Kanal dauerhaft beim Platzhalter – wie auf iOS
     * (`ChannelAvatarBackfill`). **Nur im Speicher, nicht in der Datenbank:** Jede Aenderung an
     * den Freigaben schliesst den Player und meldet „Freigaben aktualisiert"; ein Bild ist
     * keine Freigabe.
     */
    private val kanalbilder = mutableMapOf<String, String>()
    /** Schon einmal gefragt (auch erfolglos): je Kanal hoechstens ein Abruf pro App-Start (`network-services.md`). */
    private val kanalbildGefragt = mutableSetOf<String>()
    private var kanalbilderJob: Job? = null

    private fun holeFehlendeKanalbilder() {
        val fehlend = visible.filter {
            it.type == WhitelistItemType.CHANNEL.name && it.provider == "youtube" &&
                it.thumbnailUrl.isBlank() && it.contentId !in kanalbilder && it.contentId !in kanalbildGefragt
        }
        if (fehlend.isEmpty() || kanalbilderJob?.isActive == true) return
        kanalbilderJob = viewModelScope.launch {
            var neu = false
            for (item in fehlend) {
                val bild = KANALBILD_ERSATZ[item.contentId]
                    ?: (if (kanalbildGefragt.add(item.contentId)) runCatching { kanalbildHolen(item.contentId) }.getOrNull() else null)
                    ?: continue
                kanalbilder[item.contentId] = bild
                neu = true
            }
            if (neu) refresh()
        }
    }


    private val abspielbar = KidAbspielbar(whitelist, curation, channelCache, content, watchTime)
    private val folgenLader = KidNeueFolgen(whitelist, curation, channelCache, content) { channelFeed.latest(it) }

    /** Wünsche des aktiven Profils, neueste zuerst; „Neu bei deinen Kanälen" daraus und aus dem Cache. */
    private var meineWuensche: List<WishEntity> = emptyList()
    private var neueFolgen: List<NeueFolge> = emptyList()
    private var wunschJob: Job? = null
    /** Kanäle mit Stufe „gesperrt“ oder „nur für Eltern“: Ihre Videos zeigt „Meine Wünsche“ ohne Bild und Titel. */
    private var gesperrteKanaele: Set<String> = emptySet()
    private var quellenJob: Job? = null
    private var folgenJob: Job? = null
    /** Spielte das Video, als das Player-Menü aufging? Dann läuft es beim Schließen weiter. */
    private var menuPausierte = false

    private val _state = MutableStateFlow(KidState())
    val state: StateFlow<KidState> = _state.asStateFlow()

    private val focus = FocusModel()
    private val radsuche = KidRadsuche()
    private var visible: List<WhitelistItemEntity> = emptyList()
    private var playback: xyz.steier.sidetube.core.player.PlaybackModel? = null
    private var playbackProfile: KidProfileEntity? = null
    private var startJob: Job? = null
    private var recordJob: Job? = null
    private var budgetJob: Job? = null
    private var bedtimeJob: Job? = null
    private var sleepTimer: SleepTimer? = null
    private var sleepJob: Job? = null
    private val accountingFailures = mutableSetOf<String>()
    private var sessionToken: String? = null
    private var selectedProfileId: String? = null
    private var lastProfilesList: List<KidProfileEntity> = emptyList()

    /** Woher das Kind kam: Zurück führt dorthin, mit der Auswahl von damals. */
    private val backStack = ArrayDeque<Return>()
    private data class Return(val screen: KidScreen, val focusIndex: Int, val query: String, val rad: RadZustand)

    /** „Zuletzt geschaut" – nur, was heute noch erlaubt ist; siehe [refreshRecent]. */
    private var recentRows: List<KidRow> = emptyList()
    private var recentJob: Job? = null

    /** Wird von der Oberflaeche gesetzt: Es gibt genau eine WebView je Sitzung. */
    var onPlaybackCommand: ((xyz.steier.sidetube.core.player.PlaybackModel.Command) -> Unit)? = null

    init {
        if (selectedProfileId == null) selectedProfileId = profilePreferences.lastProfileId
        viewModelScope.launch {
            profiles.observeAll().collect { allProfiles ->
                lastProfilesList = allProfiles
                // Keep the current selection across unrelated list refreshes (a whitelist change
                // elsewhere must not silently switch which child is active); fall back to the
                // remembered last profile, then the first one, only when the selection is gone.
                val profile = allProfiles.find { it.id == selectedProfileId }
                    ?: allProfiles.find { it.id == profilePreferences.lastProfileId }
                    ?: allProfiles.firstOrNull()
                selectedProfileId = profile?.id
                _state.update { it.copy(profile = profile, profiles = allProfiles) }
                if (profile != null) observe(profile) else {
                    visibleJob?.cancel()
                    channelJob?.cancel()
                    observing = null
                    visible = emptyList()
                    closePlayer()
                    goHome()
                }
            }
        }
    }

    /** Manueller Profilwechsel im Kindermodus (Kopfzeile), Android-Gegenstueck zu iOS' `KidSession.select`. */
    fun selectProfile(profileId: String) {
        val profile = lastProfilesList.find { it.id == profileId } ?: return
        if (profile.id == selectedProfileId) return
        selectedProfileId = profileId
        profilePreferences.lastProfileId = profileId
        _state.update { it.copy(profile = profile) }
        observe(profile)
    }

    private var observing: KidProfileEntity? = null
    private var visibleJob: Job? = null
    private var channelJob: Job? = null

    private fun observe(profile: KidProfileEntity) {
        if (observing == profile) return
        observing = profile
        visibleJob?.cancel()
        channelJob?.cancel()
        closePlayer()
        visible = emptyList()
        recentRows = emptyList()   // nie den Verlauf eines anderen Kindes zeigen
        meineWuensche = emptyList()   // nie die Wünsche eines anderen Kindes zeigen
        neueFolgen = emptyList()
        wunschJob?.cancel()
        folgenJob?.cancel()
        quellenJob?.cancel()
        goHome()
        quellenJob = viewModelScope.launch {
            curation.observeSources().collect { quellen ->
                val gesperrt = quellen.filter {
                    SourceTrust.from(it.trust) == SourceTrust.BLOCKED || SourceTrust.from(it.trust) == SourceTrust.PARENT_ONLY
                }.map { it.channelId }.toSet()
                if (gesperrt != gesperrteKanaele) { gesperrteKanaele = gesperrt; refreshKeepingFocus() }
            }
        }
        wunschJob = viewModelScope.launch {
            wuensche.observe(profile.id).collect { liste ->
                meineWuensche = liste
                wuenscheHeute()
                publishMehrDavon()
                ladeNeueFolgen(mitNetz = false)
                refreshKeepingFocus()
            }
        }
        visibleJob = viewModelScope.launch {
            whitelist.observeVisible(profile).collect { items ->
                // Every authorization emission invalidates active AND pending playback, even
                // on Home/Search or when the visible approved list happens to be unchanged.
                // Room can emit after a source change or an explicit rejection of a channel item.
                val interrupted = playback != null || startJob?.isActive == true
                closePlayer()
                if (interrupted) _state.update { it.copy(hint = "Die Freigaben wurden aktualisiert. Bitte ein Video erneut auswählen.") }
                visible = items
                // Erst leeren, dann neu lesen: Ein eben entzogenes Video darf auch nicht fuer die
                // Millisekunden bis zur Neuberechnung im Verlauf stehen.
                recentRows = emptyList()
                val screen = _state.value.screen
                if (screen is KidScreen.Channel || screen is KidScreen.Playlist) { channelJob?.cancel(); goHome() }
                refresh()
                holeFehlendeKanalbilder()
                refreshRecent()
                ladeNeueFolgen(mitNetz = true)
            }
        }
        viewModelScope.launch {
            val remaining = watchTime.remainingSeconds(profile)
            _state.update { it.copy(remainingMinutes = remaining?.let { s -> s / 60 }) }
        }
    }

    fun onKey(action: KeyAction) {
        // Laeuft ein Video, gilt der Ring im Player (SideUI ADR 0015): Mitte pausiert,
        // links/rechts wechseln das Video, hoch/runter spulen 10 s. Lautstaerke liegt nicht am Ring.
        playback?.let { model ->
            if (model.state.status == PlaybackStatus.Ended) { onEndCardKey(action); return }
            if (_state.value.playerMenu != null) { onPlayerMenuKey(action); return }
            when (action) {
                KeyAction.Select -> togglePlayback()
                // Wie die Knöpfe: Der neue Titel steht sofort im Player, nicht erst nach dem
                // nächsten Ereignis der Brücke.
                KeyAction.MediaNext -> playerNext()
                KeyAction.MediaPrevious -> playerPrevious()
                KeyAction.FocusNext -> run(model.seekBy(SPULSCHRITT_S))
                KeyAction.FocusPrevious -> run(model.seekBy(-SPULSCHRITT_S))
                KeyAction.Back -> closePlayer()
                KeyAction.Home -> { closePlayer(); goHome() }
                // Außen oben rechts: Suche, wie überall (ADR 0013).
                KeyAction.Search -> { closePlayer(); openSearch() }
                // Außen unten rechts lang: das Player-Menü mit „Mehr davon wünschen".
                KeyAction.ContextMenu -> openPlayerMenu()
                KeyAction.Settings -> Unit   // fuehrt die Activity aus: Elternbereich hinter der PIN
            }
            return
        }
        if (_state.value.screen.hatRad && onSearchKey(action)) return
        when (action) {
            KeyAction.FocusPrevious -> if (focus.move(-1)) publishFocus()
            KeyAction.FocusNext -> if (focus.move(1)) publishFocus()
            KeyAction.Select -> activate()
            KeyAction.Back -> back()
            KeyAction.Home -> goHome()
            KeyAction.Search -> openSearch()
            KeyAction.MediaPrevious -> if (focus.move(-1)) publishFocus()
            KeyAction.MediaNext -> if (focus.move(1)) publishFocus()
            // Außerhalb des Players hat SideTube kein Kontextmenü; Einstellungen führt die Activity aus.
            KeyAction.ContextMenu, KeyAction.Settings -> Unit
        }
    }

    fun focusRow(index: Int) { leaveWheel(); focus.focus(index); publishFocus() }

    fun activateRow(index: Int) { leaveWheel(); focus.focus(index); activate() }

    /**
     * Die Suche mit dem Rad (SideUI ADR 0014): Rad und Trefferliste sind Reihen. Im Rad wählen
     * links/rechts den Buchstaben, Mitte nimmt ihn; runter geht zu den Treffern, hoch vom ersten
     * Treffer zurück ins Rad. In der Liste bewegen beide Achsen gleich. Kein Umlauf (ADR 0006).
     *
     * @return ob die Taste hier verbraucht wurde; sonst gilt die normale Listenbedienung.
     */
    private fun onSearchKey(action: KeyAction): Boolean {
        val rad = _state.value.rad
        val inList = !rad.aktiv
        return when (action) {
            KeyAction.MediaPrevious, KeyAction.MediaNext -> {
                val step = if (action == KeyAction.MediaNext) 1 else -1
                if (inList) return onSearchKey(if (step > 0) KeyAction.FocusNext else KeyAction.FocusPrevious)
                val target = (rad.fokus + step).coerceIn(0, (rad.tasten.size - 1).coerceAtLeast(0))
                if (target != rad.fokus) _state.update { it.copy(rad = rad.copy(fokus = target)) }
                true
            }
            KeyAction.FocusNext -> {
                if (inList) return false
                if (_state.value.rows.isNotEmpty()) {
                    focus.focus(0)
                    _state.update { it.copy(rad = rad.copy(aktiv = false), focusIndex = focus.index) }
                }
                true
            }
            KeyAction.FocusPrevious -> {
                if (inList && focus.index > 0) return false
                if (inList) _state.update { it.copy(rad = rad.copy(aktiv = true)) }
                true
            }
            KeyAction.Select -> {
                if (inList) return false
                takeWheelKey(rad.fokus)
                true
            }
            else -> false
        }
    }

    /** Ein Feld am Rad angetippt: Fokus dorthin und übernehmen – wie mit der Mitte. */
    fun tapWheelKey(index: Int) {
        if (!_state.value.screen.hatRad) return
        _state.update { it.copy(rad = it.rad.copy(fokus = index, aktiv = true)) }
        takeWheelKey(index)
    }

    private fun takeWheelKey(index: Int) {
        when (val taste = _state.value.rad.tasten.getOrNull(index) ?: return) {
            is RadTaste.Buchstabe -> type(taste.zeichen.wert, taste.zeichen.anzeige)
            RadTaste.Luecke -> type(' ', ' ')
            RadTaste.Loeschen -> backspaceQuery()
        }
    }

    private fun leaveWheel() {
        if (_state.value.rad.aktiv) _state.update { it.copy(rad = it.rad.copy(aktiv = false)) }
    }

    /**
     * Zeichen von einer Tastatur; das Suchfeld braucht dafuer keinen Fokus. Ziffern auf leerer
     * Eingabe beginnen eine T9-Folge (falls ein Geraet Ziffern sendet), alles andere geht durch
     * dieselbe Umrechnung wie das Rad – ein getipptes „ä" ist dort ein a.
     */
    fun appendToQuery(char: Char) {
        val current = _state.value
        if (!current.screen.hatRad) return
        // Das freie Rad des Themenwunsches kennt nur Buchstaben; T9 gibt es nur in der Suche.
        if (current.screen is KidScreen.ThemaWunsch && !char.isLetter() && char != ' ') return
        if (current.screen is KidScreen.Search && char.isDigit() && (current.query.isEmpty() || current.rad.t9)) {
            _state.update { it.copy(query = it.query + char, rad = it.rad.copy(getippt = it.rad.getippt + char, t9 = true)) }
            refresh()
            return
        }
        val grund = Radsuche.grundzeichen(char)
        type(grund ?: ' ', if (grund == null) ' ' else char.lowercaseChar())
    }

    /** Ein Zeichen anhaengen; Luecken nur zwischen Woertern, nie doppelt. */
    private fun type(normal: Char, shown: Char) {
        val current = _state.value
        if (normal == ' ' && (current.query.isEmpty() || current.query.endsWith(' '))) return
        if (current.screen is KidScreen.ThemaWunsch && current.rad.getippt.length >= WunschRegeln.THEMA_MAX) return
        val t9 = current.rad.t9
        val query = if (t9) Radsuche.eingabe(current.query) else current.query
        _state.update { it.copy(query = query + normal, rad = it.rad.copy(getippt = it.rad.getippt + shown, t9 = false)) }
        refresh()
        placeFocusAfterInput()
    }

    /**
     * Ist der Name fertig – das Rad bietet keinen Buchstaben und keine Lücke mehr –, springt der
     * Fokus auf den ersten Treffer. Sonst laege er auf „Löschen", und die naechste Mitte (die ein
     * Kind zum Abspielen drueckt) nähme den letzten Buchstaben wieder weg. Nach jeder anderen
     * Eingabe steht er wieder im Rad.
     */
    private fun placeFocusAfterInput() {
        val current = _state.value
        if (current.rad.t9) return
        val complete = current.query.isNotEmpty() &&
            current.rad.tasten.none { it is RadTaste.Buchstabe || it == RadTaste.Luecke }
        if (complete && current.rows.isNotEmpty()) {
            focus.focus(0)
            _state.update { it.copy(rad = it.rad.copy(aktiv = false), focusIndex = focus.index) }
        } else if (!current.rad.aktiv) {
            _state.update { it.copy(rad = it.rad.copy(aktiv = true)) }
        }
    }

    fun backspaceQuery() {
        val current = _state.value
        if (!current.screen.hatRad) return
        val query = current.query.dropLast(1)
        _state.update {
            it.copy(query = query, rad = it.rad.copy(getippt = it.rad.getippt.dropLast(1), t9 = it.rad.t9 && query.isNotEmpty()))
        }
        refresh()
        placeFocusAfterInput()
    }

    /** Ganzer Text auf einmal (Tests, Einfuegen): Ziffernfolgen sind T9, sonst Rad-Suchform. */
    fun setQuery(text: String) {
        val t9 = T9.istZiffernfolge(text)
        val query = if (t9) text else Radsuche.eingabe(text)
        _state.update { it.copy(query = query, rad = it.rad.copy(getippt = if (t9) text else text.lowercase(), t9 = t9)) }
        refresh()
    }

    fun goHome() {
        channelJob?.cancel()
        backStack.clear()
        _state.update { it.copy(screen = KidScreen.Home, query = "", titelBild = null, rad = RadZustand()) }
        focus.reset()
        refresh()
    }

    /** Auch per Lupe im Kopf erreichbar, nicht nur mit der Taste rechts. */
    fun openSearch() {
        if (_state.value.screen is KidScreen.Search) return
        push()
        channelJob?.cancel()
        _state.update { it.copy(screen = KidScreen.Search, query = "", titelBild = null, rad = RadZustand()) }
        focus.reset()
        refresh()
    }

    /** Bereich der Mediathek wählen – per Finger direkt, per Taste über [KidAction.NextSegment]. */
    fun selectSegment(segment: LibrarySegment) {
        if (_state.value.screen !is KidScreen.Library) return
        _state.update { it.copy(screen = KidScreen.Library(segment)) }
        focus.reset()   // Der Umschalter bleibt ausgewählt: noch einmal drücken schaltet weiter.
        refresh()
    }

    private fun push() {
        val current = _state.value
        if (current.screen is KidScreen.Home && backStack.isNotEmpty()) backStack.clear()
        backStack.addLast(Return(current.screen, focus.index, current.query, current.rad))
    }

    private fun back() {
        // In der Suche loescht Zurueck erst die letzte Ziffer; erst die leere Suche wird verlassen.
        // Am SP-01 gibt es keine Ruecktaste – Zurueck ist der einzige Weg, sich zu vertippen.
        val current = _state.value
        if (current.screen.hatRad && current.query.isNotEmpty()) { backspaceQuery(); return }
        zurueck()
    }

    /** Eine Ebene hoch, ohne vorher zu löschen – auch nach einem abgeschickten Wunsch. */
    private fun zurueck() {
        val current = _state.value
        if (current.screen is KidScreen.Home) return   // Der Kindermodus hat keinen Ausgang.
        val target = backStack.removeLastOrNull() ?: return goHome()
        channelJob?.cancel()
        _state.update { it.copy(screen = target.screen, query = target.query, titelBild = null, rad = target.rad) }
        when (val screen = target.screen) {
            is KidScreen.Channel -> { focus.reset(); loadChannel(screen.channelId, screen.title) }
            is KidScreen.Playlist -> { focus.reset(); loadPlaylist(screen.playlistId, screen.title) }
            else -> { refresh(); focus.focus(target.focusIndex); publishFocus() }
        }
    }

    private fun activate() {
        val row = _state.value.rows.getOrNull(focus.index) ?: return
        when (val action = row.action) {
            is KidAction.OpenChannel -> {
                push()
                _state.update { it.copy(screen = KidScreen.Channel(action.channelId, action.title)) }
                focus.reset()
                loadChannel(action.channelId, action.title)
            }
            is KidAction.OpenPlaylist -> {
                push()
                _state.update { it.copy(screen = KidScreen.Playlist(action.playlistId, action.title)) }
                focus.reset()
                loadPlaylist(action.playlistId, action.title)
            }
            KidAction.OpenLibrary -> {
                push()
                // „Alle Videos" öffnet bei den Videos: Die Startseite zeigt davon nur die neuesten.
                _state.update { it.copy(screen = KidScreen.Library(LibrarySegment.VIDEOS)) }
                focus.reset()
                refresh()
            }
            KidAction.NextSegment -> (_state.value.screen as? KidScreen.Library)?.let { selectSegment(it.segment.next()) }
            is KidAction.Play -> startPlayback(row)
            KidAction.OpenWishes -> oeffne(KidScreen.Wishes)
            KidAction.OpenThemaWunsch -> openThemaWunsch()
            is KidAction.OpenNeueFolge -> neueFolgen.firstOrNull { it.videoId == action.videoId }?.let { oeffne(KidScreen.NeueFolgeAnsicht(it)) }
            KidAction.SendWish -> sendeWunsch()
            KidAction.GoBack -> back()
            KidAction.Info -> Unit
            is KidAction.Wish -> when (val ziel = action.ziel) {
                is KidAction.Play -> startPlayback(row.copy(action = ziel, section = "wish-" + row.id))
                is KidAction.OpenChannel, is KidAction.OpenPlaylist -> activateAction(ziel)
                else -> Unit
            }
        }
    }

    /** Kanal oder Sendung öffnen, wie aus einer Zeile – auch als Ziel eines erfüllten Wunsches. */
    private fun activateAction(action: KidAction) {
        when (action) {
            is KidAction.OpenChannel -> {
                push()
                _state.update { it.copy(screen = KidScreen.Channel(action.channelId, action.title)) }
                focus.reset()
                loadChannel(action.channelId, action.title)
            }
            is KidAction.OpenPlaylist -> {
                push()
                _state.update { it.copy(screen = KidScreen.Playlist(action.playlistId, action.title)) }
                focus.reset()
                loadPlaylist(action.playlistId, action.title)
            }
            else -> Unit
        }
    }

    private fun oeffne(screen: KidScreen) {
        push()
        channelJob?.cancel()
        _state.update { it.copy(screen = screen, query = "", titelBild = null, rad = RadZustand()) }
        focus.reset()
        refresh()
    }

    // ── Wünsche (ADR 0001) ─────────────────────────────────────────────────────────────────

    /** Themenwunsch; aus der Suche mit dem, was dort schon gewählt war. */
    private fun openThemaWunsch() {
        val current = _state.value
        val start = if (current.screen is KidScreen.Search && !current.rad.t9) current.rad.anzeige.trimEnd() else ""
        push()
        channelJob?.cancel()
        _state.update {
            it.copy(screen = KidScreen.ThemaWunsch, query = Radsuche.eingabe(start), titelBild = null,
                rad = RadZustand(getippt = start, aktiv = true))
        }
        focus.reset()
        refresh()
    }

    /**
     * Wie viele Wünsche heute noch gehen – jedes Mal frisch aus den Wünschen und der Uhr, nicht aus
     * einem Zwischenstand: Über Mitternacht gibt es sonst keine neue Room-Emission, und das Kind
     * hinge bis zum nächsten Wunsch am Stand von gestern.
     */
    private fun wuenscheHeute(): Int {
        val frei = WunschRegeln.heuteNoch(meineWuensche, now(), ZoneId.systemDefault())
        if (frei != _state.value.wuenscheHeute) _state.update { it.copy(wuenscheHeute = frei) }
        return frei
    }

    private fun wunschMoeglich(entwurf: WunschEntwurf): WunschMoeglich {
        val schluessel = WunschRegeln.schluessel(entwurf)
        if (WunschRegeln.istDoppelt(meineWuensche.filter { it.dedupeKey == schluessel })) return WunschMoeglich.SCHON_GEWUENSCHT
        if (wuenscheHeute() <= 0) return WunschMoeglich.GRENZE
        return WunschMoeglich.JA
    }

    private fun entwurfFuerBildschirm(): WunschEntwurf? = when (val screen = _state.value.screen) {
        KidScreen.ThemaWunsch -> WunschEntwurf.Thema(_state.value.rad.getippt)
        is KidScreen.NeueFolgeAnsicht -> screen.folge.let { WunschEntwurf.NeueFolge(it.videoId, it.title, it.channelId, it.channelTitle) }
        else -> null
    }

    private fun sendeWunsch() {
        val entwurf = entwurfFuerBildschirm() ?: return
        schicke(entwurf) { zurueck() }
    }

    /** Schickt den Wunsch; die Datenbank entscheidet über Grenze und Dublette, nicht die Anzeige. */
    private fun schicke(entwurf: WunschEntwurf, danach: () -> Unit = {}) {
        val profile = _state.value.profile ?: return
        val bildschirm = _state.value.screen
        viewModelScope.launch {
            // Im Verlauf steht „Kind", nicht der Name: Der Verlauf ist kein Ort fuer Klarnamen (wie iOS).
            val ergebnis = runCatching { wuensche.wuensche(entwurf, profile.id, WunschRepository.ACTOR_KIND) }.getOrNull()
            if (_state.value.profile?.id != profile.id) return@launch
            val hinweis = when (ergebnis) {
                is WunschErgebnis.Geschickt -> "Dein Wunsch ist bei den Eltern. " + KidWunschRows.heuteText(ergebnis.heuteNoch) + "."
                is WunschErgebnis.SchonGewuenscht -> "Das hast du dir schon gewünscht."
                WunschErgebnis.Grenze -> "Heute gehen keine Wünsche mehr. Morgen wieder."
                WunschErgebnis.Leer -> "Erst Buchstaben wählen."
                null -> "Der Wunsch konnte nicht gespeichert werden."
            }
            // Nur ein neuer Wunsch wird gemeldet – keine Dublette, nichts über der Tagesgrenze.
            if (ergebnis is WunschErgebnis.Geschickt) neuerWunschGemeldet()
            _state.update { it.copy(hint = hinweis) }
            // Ist das Kind inzwischen woanders (Speichern dauerte), bleibt es dort.
            if (ergebnis is WunschErgebnis.Geschickt && _state.value.screen == bildschirm && playback == null) danach()
        }
    }

    /** „Mehr davon" zum Video, das gerade läuft oder eben zu Ende ging. */
    fun wuenscheMehrDavon() {
        val video = playback?.state?.current ?: return
        val entwurf = mehrDavonEntwurf(video.videoId, video.title)
        if (wunschMoeglich(entwurf) != WunschMoeglich.JA) { publishMehrDavon(); return }
        schicke(entwurf)
    }

    /** Kanal als Anlass: aus der Freigabe des Videos, sonst aus der Kanalansicht, aus der es kam. */
    private fun mehrDavonEntwurf(videoId: String, title: String): WunschEntwurf.MehrDavon {
        val item = visible.firstOrNull { it.contentId == videoId }
        val kanal = backStack.lastOrNull()?.screen as? KidScreen.Channel ?: _state.value.screen as? KidScreen.Channel
        return WunschEntwurf.MehrDavon(
            videoId = videoId, videoTitle = item?.title ?: title,
            channelId = item?.sourceChannelId ?: kanal?.channelId,
            channelTitle = item?.channelTitle ?: kanal?.title
        )
    }

    private fun publishMehrDavon() {
        val video = playback?.state?.current
        val moeglich = video?.let { wunschMoeglich(mehrDavonEntwurf(it.videoId, it.title)) } ?: WunschMoeglich.JA
        if (_state.value.mehrDavon != moeglich) _state.update { it.copy(mehrDavon = moeglich) }
    }

    /** Player-Menü: hält das Video an; „Weiterschauen" und Zurück lassen es weiterlaufen. */
    fun openPlayerMenu() {
        val model = playback ?: return
        if (model.state.status == PlaybackStatus.Ended) return
        publishMehrDavon()
        menuPausierte = model.state.status == PlaybackStatus.Playing
        if (menuPausierte) onPlaybackCommand?.invoke(PlaybackModel.Command.Pause)
        _state.update { it.copy(playerMenu = if (it.mehrDavon == WunschMoeglich.JA) 0 else 1) }
    }

    fun closePlayerMenu() {
        if (_state.value.playerMenu == null) return
        _state.update { it.copy(playerMenu = null) }
        if (menuPausierte && playback != null && !enforceTimeRules()) onPlaybackCommand?.invoke(PlaybackModel.Command.Resume)
        menuPausierte = false
    }

    /** Ein Eintrag des Player-Menüs, per Finger oder Mitte. */
    fun choosePlayerMenu(index: Int) {
        if (index == 0 && _state.value.mehrDavon == WunschMoeglich.JA) wuenscheMehrDavon()
        if (index == 0 && _state.value.mehrDavon != WunschMoeglich.JA) return
        closePlayerMenu()
    }

    private fun onPlayerMenuKey(action: KeyAction) {
        val menu = _state.value.playerMenu ?: return
        // „Mehr davon" ist nur wählbar, wenn es geht; sonst steht der Fokus auf „Weiterschauen".
        val erstes = if (_state.value.mehrDavon == WunschMoeglich.JA) 0 else 1
        when (action) {
            KeyAction.FocusPrevious, KeyAction.MediaPrevious -> _state.update { it.copy(playerMenu = maxOf(erstes, menu - 1)) }
            KeyAction.FocusNext, KeyAction.MediaNext -> _state.update { it.copy(playerMenu = 1) }
            KeyAction.Select -> choosePlayerMenu(menu)
            KeyAction.Back, KeyAction.ContextMenu -> closePlayerMenu()
            KeyAction.Home -> { closePlayer(); goHome() }
            KeyAction.Search -> { closePlayer(); openSearch() }
            KeyAction.Settings -> Unit
        }
    }

    /** Ladet „Neu bei deinen Kanälen": sofort aus dem Cache, mit [mitNetz] danach fällige Feeds. */
    private fun ladeNeueFolgen(mitNetz: Boolean) {
        val profile = _state.value.profile ?: return
        if (mitNetz) folgenJob?.cancel() else if (folgenJob?.isActive == true) return
        folgenJob = viewModelScope.launch {
            try {
                val kanaele = folgenLader.kanaele(visible)
                zeigeNeueFolgen(profile, folgenLader.auswahl(profile, kanaele, folgenLader.ausCache(kanaele), meineWuensche, now()))
                if (mitNetz && folgenLader.auffrischen(kanaele, now())) {
                    currentCoroutineContext().ensureActive()
                    zeigeNeueFolgen(profile, folgenLader.auswahl(profile, kanaele, folgenLader.ausCache(kanaele), meineWuensche, now()))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Ohne Feed und Cache fehlt nur der Abschnitt; die Startseite bleibt benutzbar.
            }
        }
    }

    private fun zeigeNeueFolgen(profile: KidProfileEntity, folgen: List<NeueFolge>) {
        if (_state.value.profile?.id != profile.id || folgen == neueFolgen) return
        neueFolgen = folgen
        // Eine geöffnete Folge, die inzwischen entschieden ist, bleibt stehen, bis das Kind zurückgeht.
        refreshKeepingFocus()
    }

    private fun refreshKeepingFocus() {
        if (playback != null) return
        when (val screen = _state.value.screen) {
            is KidScreen.Home -> show(homeRows(), _state.value.profile?.name ?: "SideTube", keepFocus = true)
            KidScreen.Wishes -> show(wishRows(), "Meine Wünsche", keepFocus = true)
            KidScreen.ThemaWunsch, is KidScreen.NeueFolgeAnsicht -> show(wunschBildschirmRows(screen), titleFor(screen), keepFocus = true)
            else -> Unit
        }
    }

    private fun homeRows(): List<KidRow> =
        KidRows.home(visible, recentRows, kanalbilder) +
            KidWunschRows.home(neueFolgen, meineWuensche, wuenscheHeute())

    private fun wishRows(): List<KidRow> =
        KidWunschRows.liste(meineWuensche, visible, kanalbilder, wuenscheHeute(), gesperrteKanaele)

    private fun wunschBildschirmRows(screen: KidScreen): List<KidRow> {
        val entwurf = entwurfFuerBildschirm() ?: return emptyList()
        val moeglich = wunschMoeglich(entwurf)
        return when (screen) {
            KidScreen.ThemaWunsch -> KidWunschRows.themaZeilen(_state.value.rad.getippt, moeglich, wuenscheHeute())
            is KidScreen.NeueFolgeAnsicht -> KidWunschRows.folgenZeilen(moeglich, wuenscheHeute())
            else -> emptyList()
        }
    }

    private fun titleFor(screen: KidScreen): String = when (screen) {
        KidScreen.ThemaWunsch -> "Wunsch an die Eltern"
        is KidScreen.NeueFolgeAnsicht -> screen.folge.channelTitle
        else -> _state.value.title
    }

    /**
     * Endkarte: 0 = „Nochmal", 1 = „Mehr davon wünschen", 2 = „Zurück zu den Videos". Hoch/runter
     * wählt (kein Umlauf), Mitte bestätigt, Zurück schließt. Geht „Mehr davon" gerade nicht, wird es
     * übersprungen. Kein automatisches Weiter.
     */
    private fun onEndCardKey(action: KeyAction) {
        val stellen = if (_state.value.mehrDavon == WunschMoeglich.JA) listOf(0, 1, 2) else listOf(0, 2)
        val hier = stellen.indexOf(_state.value.endFocus).coerceAtLeast(0)
        when (action) {
            KeyAction.FocusPrevious, KeyAction.MediaPrevious -> _state.update { it.copy(endFocus = stellen[maxOf(0, hier - 1)]) }
            KeyAction.FocusNext, KeyAction.MediaNext -> _state.update { it.copy(endFocus = stellen[minOf(stellen.lastIndex, hier + 1)]) }
            KeyAction.Select -> when (_state.value.endFocus) {
                0 -> replay()
                1 -> { wuenscheMehrDavon(); _state.update { it.copy(endFocus = 2) } }
                else -> closePlayer()
            }
            KeyAction.Back -> closePlayer()
            KeyAction.Home -> { closePlayer(); goHome() }
            KeyAction.Search -> { closePlayer(); openSearch() }
            KeyAction.ContextMenu, KeyAction.Settings -> Unit
        }
    }

    /** „Nochmal" auf der Endkarte. */
    fun replay() {
        if (enforceTimeRules()) return
        val model = playback ?: return
        run(model.replay())
        publishPlayback()
    }

    /**
     * Die Warteschlange ist der Abschnitt, aus dem gestartet wurde, ab dem gewählten Video – wer
     * unter „Zuletzt geschaut" startet, bekommt nicht plötzlich die Videos der Startseite.
     */
    private fun startPlayback(row: KidRow) {
        val videoId = (row.action as? KidAction.Play)?.videoId ?: return
        closePlayer()
        if (enforceTimeRules()) return
        val profile = _state.value.profile ?: return
        // Ein erfüllter Wunsch spielt nur sein eines Video.
        val sameSection = if (row.action is KidAction.Play && row.section?.startsWith("wish-") == true) listOf(row)
            else _state.value.rows.filter { it.section == row.section }
        val videos = sameSection.mapNotNull { r ->
            (r.action as? KidAction.Play)?.let { PlayableVideo(it.videoId, it.title) }
        }
        val start = sameSection.filter { it.action is KidAction.Play }.indexOfFirst { it.id == row.id }.coerceAtLeast(0)
        startJob = viewModelScope.launch {
            try {
                // A rapid close/reopen must see all watch records from the previous session.
                recordJob?.join()
                check(profile.id !in accountingFailures)
                if (playbackSessions.pending(profile.id) != null) {
                    _state.update { it.copy(hint = "Die letzte Wiedergabe wurde unterbrochen. Bitte die Eltern um Freigabe bitten.") }
                    return@launch
                }
                val remaining = watchTime.remainingSeconds(profile)
                if (_state.value.profile != profile ||
                    _state.value.rows.none { abspielbarIn(it)?.videoId == videoId }) return@launch
                if (enforceTimeRules()) return@launch
                if (remaining != null && remaining <= 0) {
                    _state.update { it.copy(remainingMinutes = 0, hint = "Deine Sehzeit für heute ist aufgebraucht.") }
                    return@launch
                }
                val token = UUID.randomUUID().toString()
                // Admission must be durable before creating/loading a player. A cancelled begin
                // may already have committed; its marker then deliberately requires parent review.
                playbackSessions.begin(PlaybackSessionEntity(profile.id, token, System.currentTimeMillis()))
                currentCoroutineContext().ensureActive()
                if (_state.value.profile != profile || enforceTimeRules()) {
                    playbackSessions.finish(profile.id, token)
                    return@launch
                }
                sessionToken = token
                val model = PlaybackModel(videos, start, autoAdvance = profile.autoplayNext,
                    budgetSeconds = remaining)
                playbackProfile = profile
                playback = model
                run(model.start())
                publishPlayback()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(hint = "Die Sehzeit konnte nicht geprüft werden. Bitte die Eltern fragen.") }
            }
        }
    }

    /** Was eine Zeile abspielt: ein Video oder das Ziel eines erfüllten Wunsches. Nie eine gesperrte Folge. */
    private fun abspielbarIn(row: KidRow): KidAction.Play? =
        row.action as? KidAction.Play ?: (row.action as? KidAction.Wish)?.ziel as? KidAction.Play

    fun onPlayerEvent(event: PlayerEventKind, value: Int) {
        if (enforceTimeRules()) return
        val model = playback ?: return
        val commands = when (event) {
            PlayerEventKind.State -> model.onState(value)
            PlayerEventKind.Error -> model.onError(value)
            PlayerEventKind.Time -> model.onTime(value)
        }
        run(commands)
        publishPlayback()
    }

    /** Die Einbettung wollte selbst ein nicht angefordertes Video starten (Endscreen, Pausen-Vorschlag). */
    fun onForeignVideo(videoId: String) {
        if (enforceTimeRules()) return
        val model = playback ?: return
        run(model.onForeignVideo(videoId))
        publishPlayback()
    }

    /** Pause und Weiter gehen an die Bruecke; den Zustand meldet sie zurueck. */
    fun togglePlayback() {
        if (enforceTimeRules()) return
        val model = playback ?: return
        val limit = model.checkBudget()
        if (limit.isNotEmpty()) { run(limit); publishPlayback(); return }
        onPlaybackCommand?.invoke(
            if (model.state.status == PlaybackStatus.Playing) PlaybackModel.Command.Pause
            else PlaybackModel.Command.Resume
        )
    }

    fun playerNext() { playback?.let { run(it.next()); publishPlayback() } }
    fun playerPrevious() { playback?.let { run(it.previous()); publishPlayback() } }

    fun closePlayer() {
        val closingProfile = playbackProfile
        val closingToken = sessionToken
        bedtimeJob?.cancel()
        bedtimeJob = null
        startJob?.cancel()
        startJob = null
        budgetJob?.cancel()
        budgetJob = null
        playback?.let { run(it.close()) }
        sessionToken = null
        playback = null
        playbackProfile = null
        menuPausierte = false
        _state.update { it.copy(playback = null, endFocus = 0, playerMenu = null) }
        if (closingProfile != null && closingToken != null) {
            val previous = recordJob
            recordJob = viewModelScope.launch {
                try {
                    previous?.join()
                    if (closingProfile.id !in accountingFailures) playbackSessions.finish(closingProfile.id, closingToken)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    accountingFailures += closingProfile.id
                    _state.update { it.copy(hint = "Die Wiedergabe konnte nicht sicher abgeschlossen werden. Bitte die Eltern fragen.") }
                }
            }
            // Nach jeder Wiedergabe steht „Zuletzt geschaut" neu.
            refreshRecent()
        }
    }

    /** UI entry point exists only in the PIN-gated parent area. Never called automatically. */
    fun acknowledgeInterruptedPlaybackByParent(profileId: String) {
        viewModelScope.launch {
            try {
                check(playback == null && sessionToken == null)
                recordJob?.join()
                playbackSessions.acknowledgeByParent(profileId)
                accountingFailures -= profileId
                _state.update { it.copy(hint = "Unterbrochene Wiedergabe freigegeben. Gespeicherte Sehzeit bleibt erhalten.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(hint = "Die Freigabe konnte nicht gespeichert werden.") }
            }
        }
    }

    private fun run(commands: List<PlaybackModel.Command>) {
        commands.forEach { command ->
            when (command) {
                is PlaybackModel.Command.Record -> {
                    val profile = playbackProfile ?: return@forEach
                    val previous = recordJob
                    recordJob = viewModelScope.launch {
                        try {
                            previous?.join()
                            // `at` is only set for a calendar day already completed before this
                            // stretch crossed midnight; the current day still books at real time.
                            val at = command.at
                            if (at != null) watchTime.record(profile.id, command.video.videoId, command.video.title, command.seconds, at = at)
                            else watchTime.record(profile.id, command.video.videoId, command.video.title, command.seconds)
                            val remaining = watchTime.remainingSeconds(profile)
                            _state.update {
                                if (it.profile?.id == profile.id) it.copy(remainingMinutes = remaining?.let { s -> s / 60 }) else it
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            accountingFailures += profile.id
                            closePlayer()
                            _state.update { it.copy(hint = "Die Sehzeit konnte nicht gespeichert werden. Bitte die Eltern fragen.") }
                        }
                    }
                }
                is PlaybackModel.Command.LimitReached -> _state.update {
                    it.copy(remainingMinutes = 0, hint = "Deine Sehzeit für heute ist aufgebraucht.")
                }
                is PlaybackModel.Command.Done -> closePlayer()
                else -> {
                    if ((command is PlaybackModel.Command.Load || command is PlaybackModel.Command.Resume) &&
                        enforceTimeRules()) return
                    onPlaybackCommand?.invoke(command)
                }
            }
        }
    }

    private fun publishPlayback() {
        scheduleBedtime()
        scheduleSleep()
        publishMehrDavon()
        _state.update {
            val status = playback?.state?.status
            it.copy(playback = playback?.state, endFocus = if (status == PlaybackStatus.Ended) it.endFocus else 0)
        }
        budgetJob?.cancel()
        budgetJob = null
        val model = playback ?: return
        val remaining = model.remainingBudgetMillis ?: return
        if (model.state.status != PlaybackStatus.Playing) return
        budgetJob = viewModelScope.launch {
            delay(remaining)
            if (playback === model) { run(model.checkBudget()); publishPlayback() }
        }
    }

    /**
     * Ruhezeit *und* Schlaf-Timer. Beide koennen gelten; die Tore fragen bewusst diese eine
     * Stelle, damit kein Einstiegsweg nur die Haelfte prueft.
     */
    private fun enforceTimeRules(): Boolean = enforceBedtime() || enforceSleep()

    private fun enforceSleep(): Boolean {
        val timer = sleepTimer ?: return false
        if (!SleepTimerPolicy.hasExpired(timer, now())) return false
        closePlayer()
        // Der Timer bleibt abgelaufen stehen: Erst die Eltern heben ihn auf, sonst startet das
        // Kind gleich das naechste Video. Das entspricht dem sperrenden Overlay auf iOS.
        _state.update { it.copy(sleepRemainingSeconds = 0, hint = "Gute Nacht. Der Schlafmodus ist zu Ende.") }
        return true
    }

    /** Eltern starten den Schlaf-Timer; die Dauer klemmt [SleepTimerPolicy]. */
    fun startSleepTimer(minutes: Int) {
        sleepTimer = SleepTimerPolicy.start(minutes, now())
        publishSleepRemaining()
        scheduleSleep()
    }

    fun stopSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepTimer = null
        _state.update { it.copy(sleepRemainingSeconds = null) }
    }

    private fun publishSleepRemaining() = _state.update {
        it.copy(sleepRemainingSeconds = SleepTimerPolicy.remainingSeconds(sleepTimer, now()))
    }

    /**
     * Eine Frist bis zum Beginn der Ausblendung, danach bis zum Ablauf ein Takt je Sekunde. Der
     * Takt ist der einzige wiederkehrende Wecker der App und bleibt deshalb auf die letzte Minute
     * begrenzt. Springt die Uhr zurueck, laeuft die Schleife einfach weiter - die Frage nach dem
     * Ablauf beantwortet immer die aktuelle Zeit, nicht ein gesetzter Merker.
     */
    private fun scheduleSleep() {
        sleepJob?.cancel()
        sleepJob = null
        val timer = sleepTimer ?: return
        sleepJob = viewModelScope.launch {
            val fadeStart = timer.endsAt.minusSeconds(SleepTimerPolicy.FADE_SECONDS.toLong())
            val untilFade = Duration.between(now(), fadeStart).toMillis()
            if (untilFade > 0) delay(untilFade)
            while (sleepTimer === timer && !SleepTimerPolicy.hasExpired(timer, now())) {
                if (playback != null) {
                    SleepTimerPolicy.fadeVolume(timer, now())?.let { percent ->
                        onPlaybackCommand?.invoke(PlaybackModel.Command.SetVolume(percent))
                    }
                }
                publishSleepRemaining()
                delay(1000)
            }
            if (sleepTimer === timer) enforceSleep()
        }
    }

    private fun enforceBedtime(): Boolean {
        val profile = _state.value.profile ?: return false
        if (!BedtimePolicy.isActive(profile, now(), ZoneId.systemDefault())) return false
        closePlayer()
        _state.update { it.copy(hint = "Jetzt ist Ruhezeit. Bitte die Eltern fragen.") }
        return true
    }

    /** Also called for system clock/time-zone changes; never registers a repeating timer. */
    fun onClockChanged() {
        if (playback != null && !enforceTimeRules()) scheduleBedtime()
        scheduleSleep()
    }

    private fun scheduleBedtime() {
        bedtimeJob?.cancel()
        bedtimeJob = null
        val model = playback ?: return
        val profile = playbackProfile ?: return
        val current = now()
        val boundary = BedtimePolicy.nextBoundary(profile, current, ZoneId.systemDefault()) ?: return
        bedtimeJob = viewModelScope.launch {
            delay(Duration.between(current, boundary).toMillis().coerceAtLeast(1))
            if (playback === model && !enforceTimeRules()) { warnBeforeBedtime(); scheduleBedtime() }
        }
    }

    /**
     * Vorwarnung an den Schwellen, damit die Ruhezeit das Kind nicht mitten im Video ueberrascht.
     * Nur beim Erreichen einer Grenze, nicht bei jedem Neuplanen - sonst kaeme sie im
     * Fuenf-Sekunden-Takt der Wiedergabe.
     */
    private fun warnBeforeBedtime() {
        val profile = playbackProfile ?: return
        val state = BedtimePolicy.state(profile, now(), ZoneId.systemDefault())
        if (state is BedtimeState.Warning) {
            _state.update { it.copy(hint = "In ${state.minutesLeft} Minuten beginnt die Ruhezeit.") }
        }
    }

    private fun loadChannel(channelId: String, title: String) {
        channelJob?.cancel()
        val kanal = visible.firstOrNull { it.contentId == channelId && it.type == WhitelistItemType.CHANNEL.name }
        val bild = kanal?.let { Vorschaubilder.adresse(it) } ?: kanalbilder[channelId]
        _state.update { it.copy(isLoading = true, title = title, titelBild = bild, rows = emptyList()) }
        channelJob = viewModelScope.launch {
            val source = curation.source(channelId)
            if (!ContentPolicy.allowsChannelBrowsing(source)) {
                // Nur freigegebene Einzelvideos dieses Kanals zeigen.
                val approved = visible.filter { it.sourceChannelId == channelId && it.type == "VIDEO" }
                show(approved.map(::itemRow), title)
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            val cached = channelCache.videos(channelId)
            if (cached.isNotEmpty()) show(allowedChannelRows(cached.map { KidRows.video(it.videoId, it.title, it.channelTitle) }, channelId), title)

            val fresh = runCatching { channelFeed.latest(channelId) }.getOrNull()
            currentCoroutineContext().ensureActive()
            if (fresh != null) {
                channelCache.store(channelId, fresh)
                show(allowedChannelRows(fresh.map { KidRows.video(it.videoId, it.title, it.channelTitle) }, channelId), title)
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Playlist-Videos: zuerst der Zwischenspeicher (offline, sofort), dann der Feed. Gezeigt wird
     * nur, was [ContentPolicy.canPlayFromPlaylist] durchlässt – die Freigabe der Playlist allein
     * genügt nicht.
     */
    private fun loadPlaylist(playlistId: String, title: String) {
        channelJob?.cancel()
        val playlist = visible.firstOrNull { it.contentId == playlistId && it.type == WhitelistItemType.PLAYLIST.name }
            ?: return goHome()
        _state.update { it.copy(isLoading = true, title = title, titelBild = Vorschaubilder.adresse(playlist), rows = emptyList()) }
        focus.setCount(0)
        channelJob = viewModelScope.launch {
            val cached = abspielbar.cachedPlaylist(playlistId)
            if (cached.isNotEmpty()) show(playlistRows(playlist, cached), title)

            val fresh = runCatching { playlistHolen(playlistId) }.getOrNull()
            currentCoroutineContext().ensureActive()
            if (fresh != null) {
                runCatching { channelCache.storePlaylist(playlistId, fresh) }
                show(playlistRows(playlist, fresh), title)
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun playlistRows(playlist: WhitelistItemEntity, videos: List<ChannelVideo>): List<KidRow> {
        val profile = _state.value.profile ?: return emptyList()
        return abspielbar.playlistVideos(profile, playlist, videos).map { KidRows.video(it.videoId, it.title, it.channelTitle) }
    }

    /**
     * Liest den Verlauf neu: neueste zuerst, jedes Video einmal, nur Erlaubtes. Android speichert
     * keine Abspielposition, deshalb „Zuletzt geschaut" statt „Weiterschauen" mit Fortschritt.
     */
    private fun refreshRecent() {
        val profile = _state.value.profile ?: return
        val previousRecord = recordJob
        recentJob?.cancel()
        recentJob = viewModelScope.launch {
            try {
                previousRecord?.join()
                val rows = abspielbar.zuletztGeschaut(profile, visible, now())
                currentCoroutineContext().ensureActive()
                if (_state.value.profile?.id != profile.id || rows == recentRows) return@launch
                recentRows = rows
                if (_state.value.screen is KidScreen.Home && playback == null) {
                    show(homeRows(), _state.value.profile?.name ?: "SideTube", keepFocus = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Ohne Verlauf bleibt die Startseite benutzbar; der Abschnitt fehlt dann nur.
            }
        }
    }

    private fun refresh() {
        when (val screen = _state.value.screen) {
            is KidScreen.Home -> show(homeRows(), _state.value.profile?.name ?: "SideTube")
            KidScreen.Wishes -> show(wishRows(), "Meine Wünsche")
            KidScreen.ThemaWunsch -> {
                _state.update { it.copy(rad = KidWunschRows.freiesRad(it.rad)) }
                show(wunschBildschirmRows(screen), titleFor(screen))
            }
            is KidScreen.NeueFolgeAnsicht -> show(wunschBildschirmRows(screen), titleFor(screen))
            is KidScreen.Search -> {
                // Der Themenwunsch steht immer da: ohne Treffer als erste Zeile, sonst nach ihnen.
                val treffer = searchRows(_state.value.query)
                _state.update {
                    val rad = radsuche.rad(visible, it.query, it.rad)
                    // Ohne Treffer steht der Fokus im Rad; der Wunsch darunter ist mit ▼ erreichbar.
                    it.copy(rad = if (treffer.isEmpty()) rad.copy(aktiv = true) else rad)
                }
                show(treffer + KidWunschRows.suchZeile(if (_state.value.rad.t9) "" else _state.value.rad.anzeige), "Suche")
            }
            is KidScreen.Library -> show(KidRows.library(visible, screen.segment, kanalbilder), "Alle Videos")
            is KidScreen.Channel, is KidScreen.Playlist -> Unit   // wird beim Oeffnen geladen
        }
    }

    private suspend fun allowedChannelRows(rows: List<KidRow>, channelId: String): List<KidRow> {
        val profile = _state.value.profile ?: return emptyList()
        return abspielbar.channelRows(profile, channelId, rows)
    }

    /**
     * Gesucht wird nur in dem, was ohnehin sichtbar ist – den Freigaben dieses Profils – und
     * ohne Netz. Hauptweg ist das Rad ([KidRadsuche], Wortanfaenge, schon ab einem Buchstaben).
     * Kamen Ziffern, gilt die alte T9-Suche – falls ein Geraet doch Zifferntasten hat.
     */
    private fun searchRows(query: String): List<KidRow> {
        if (_state.value.rad.t9) {
            // Schon eine Ziffer sucht: Das Kind sieht sofort, dass die Taste etwas bewirkt.
            if (!T9.hatInhalt(query)) return emptyList()
            return visible.filter {
                T9.passt(query, it.title) || it.channelTitle?.let { kanal -> T9.passt(query, kanal) } == true
            }.map(::itemRow)
        }
        return radsuche.treffer(visible, query).map(::itemRow)
    }

    private fun itemRow(item: WhitelistItemEntity) = KidRows.item(item, kanalbilder)

    /**
     * [keepFocus]: Die Auswahl bleibt auf derselben Zeile, wenn sich die Liste darüber ändert
     * (neuer Verlauf). Wer ganz oben steht, bleibt oben – dort erscheint das Neueste.
     */
    private fun show(rows: List<KidRow>, title: String, keepFocus: Boolean = false) {
        val previous = if (keepFocus && focus.index > 0) _state.value.rows.getOrNull(focus.index)?.id else null
        focus.setCount(rows.size)
        previous?.let { id -> rows.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(focus::focus) }
        _state.update { it.copy(rows = rows, title = title, focusIndex = focus.index) }
    }

    private fun publishFocus() = _state.update { it.copy(focusIndex = focus.index) }

    fun clearHint() = _state.update { it.copy(hint = null) }
}

/**
 * Kuratierte Ersatzbilder fuer die beiden mitgelieferten Startquellen (NASA, ESA) – dieselben
 * wie auf iOS. Kanalseiten liefern nicht immer ein Bild (Einwilligungsseite, Drosselung, offline).
 */
private val KANALBILD_ERSATZ = mapOf(
    "UCLA_DiR1FfKNvjuUpBHmylQ" to "https://i.ytimg.com/vi/6o3m9Bw67Os/hqdefault.jpg",
    "UCIBaDdAbGlFDeS33shmlD0A" to "https://i.ytimg.com/vi/PqJpgizriFM/hqdefault.jpg",
)

/** Ein Spulschritt im Player mit dem Ring (SideUI ADR 0015): 10 s, wie in SidePlay. */
internal const val SPULSCHRITT_S = 10
