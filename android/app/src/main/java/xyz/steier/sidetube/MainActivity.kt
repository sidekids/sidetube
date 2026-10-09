// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube

import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.input.HoldKey
import xyz.steier.sidetube.core.input.KeyMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.DisposableEffect
import xyz.steier.sidetube.core.player.PlaybackModel
import xyz.steier.sidetube.kid.KidSperre
import xyz.steier.sidetube.kid.KidSperreText
import xyz.steier.sidetube.kid.KidSperreView
import xyz.steier.sidetube.kid.KidView
import xyz.steier.sidetube.kid.KidViewModel
import xyz.steier.sidetube.kid.PlayerEventKind
import xyz.steier.sidetube.player.PlayerBridge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import xyz.steier.sidetube.player.PlayerEvent
import xyz.steier.sidetube.player.PlayerView
import xyz.steier.sidetube.parent.*

/** Wo die App gerade steht. Ein Aufzaehlungstyp statt einer Navigationsbibliothek. */
private sealed interface Screen {
    data object Kid : Screen
    data object PinSetup : Screen
    /** [sperre]: Die PIN kommt von der Vollbild-Sperre; nur dann hebt sie die Sperre auf. */
    data class PinEntry(val sperre: KidSperre? = null) : Screen
    data object Profiles : Screen
    data class Whitelist(val profile: KidProfileEntity) : Screen
    data class Review(val profile: KidProfileEntity) : Screen
    data object Sources : Screen
    data class ProfileEdit(val profile: KidProfileEntity) : Screen
    /** Nutzung (Sehstatistik) eines Profils, wie `WatchStatsView` auf iOS. */
    data class Stats(val profile: KidProfileEntity) : Screen
    data object ChangePin : Screen
    data object Elternkanal : Screen
}

class MainActivity : ComponentActivity() {

    /**
     * Tasten des Sidephone; wer gerade hoert, meldet sich hier an. Der zweite Wert sagt, ob der
     * Druck lang war (nur bei [KeyMap.isHoldKey]-Tasten).
     */
    private var keyHandler: ((KeyEvent, Boolean) -> Boolean)? = null

    /** Kurz oder lang fuer Zurueck, oben rechts und ENTER; siehe [HoldKey]. */
    private val hold = HoldKey()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var holdTimer: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SideTubeApp).container
        setContent {
            SideTubeTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalTexte provides container.texte) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        SideTubeApp(container) { handler -> keyHandler = handler }
                    }
                }
            }
        }
    }

    /**
     * Tasten werden **vor** der Oberflaeche abgefangen: Compose verarbeitet Richtungstasten und
     * die Mitteltaste selbst fuer seine eigene Fokuswanderung, sodass `onKeyDown` sie nie sieht.
     * Am Geraet gepruefte Falle – ohne das bliebe die Tastenbedienung wirkungslos.
     *
     * Es meldet sich nur der Kindermodus an; im Elternbereich bleibt alles Standard, damit
     * Texteingaben bedienbar bleiben.
     */
    // Android's public Activity hook; the inherited AndroidX core annotation also flags super.
    // Keep interception before Compose so the Sidephone DPAD continues to work.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keys = keyHandler
        // Tasten mit zwei Bedeutungen: erst beim Loslassen (kurz) oder beim Halten (lang)
        // auswerten. Vorher galt nur der erste Druck, und der ist nie lang (SideUI ADR 0007).
        if (keys != null && KeyMap.isHoldKey(event.keyCode)) {
            val press = when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0 && !event.isLongPress) startHoldTimer(event, keys)
                    hold.down(event.repeatCount, event.isLongPress)
                }
                KeyEvent.ACTION_UP -> hold.up()
                else -> null
            }
            if (press != null || event.action == KeyEvent.ACTION_UP) stopHoldTimer()
            when (press) {
                HoldKey.Press.Short -> keys(event, false)
                HoldKey.Press.Long -> keys(event, true)
                null -> Unit
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (keys?.invoke(event, false) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Die eigene Uhr: Manche Geraete schicken beim Halten keine Wiederholung. */
    private fun startHoldTimer(event: KeyEvent, keys: (KeyEvent, Boolean) -> Boolean) {
        stopHoldTimer()
        val timer = Runnable { if (hold.timeout() == HoldKey.Press.Long) keys(event, true) }
        holdTimer = timer
        mainHandler.postDelayed(timer, HoldKey.LONG_MS)
    }

    private fun stopHoldTimer() {
        holdTimer?.let(mainHandler::removeCallbacks)
        holdTimer = null
    }
}

@Composable
private fun SideTubeApp(container: AppContainer, registerKeys: (((KeyEvent, Boolean) -> Boolean)?) -> Unit) {
    val viewModel: ParentViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            ParentViewModel(container) as T
    })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val kidViewModel: KidViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            KidViewModel(container) as T
    })
    val kidState by kidViewModel.state.collectAsStateWithLifecycle()

    // Eine WebView nur während einer aktiven Wiedergabesitzung.
    val context = LocalContext.current
    val bridge = remember(kidState.playback != null) {
        if (kidState.playback == null) null else PlayerBridge(context) { event ->
            when (event) {
                is PlayerEvent.State -> kidViewModel.onPlayerEvent(PlayerEventKind.State, event.value)
                is PlayerEvent.Error -> kidViewModel.onPlayerEvent(PlayerEventKind.Error, event.code)
                is PlayerEvent.Time -> kidViewModel.onPlayerEvent(PlayerEventKind.Time, event.seconds)
                is PlayerEvent.ForeignVideo -> kidViewModel.onForeignVideo(event.videoId)
                PlayerEvent.Ready -> Unit
                PlayerEvent.ApiFailed -> kidViewModel.onPlayerEvent(PlayerEventKind.Error, -1)
            }
        }
    }
    // Die Seite laedt, sobald die WebView im Fenster haengt: siehe PlayerBridge.
    DisposableEffect(bridge) {
        if (bridge != null) {
        kidViewModel.onPlaybackCommand = { command ->
            when (command) {
                is PlaybackModel.Command.Load -> bridge.play(command.videoId)
                PlaybackModel.Command.Pause -> bridge.pause()
                PlaybackModel.Command.Resume -> bridge.resume()
                PlaybackModel.Command.Stop -> bridge.stop()
                is PlaybackModel.Command.SeekBy -> bridge.seekBy(command.seconds)
                is PlaybackModel.Command.SetVolume -> bridge.setVolume(command.percent)
                else -> Unit
            }
        }
        kidState.playback?.current?.let { bridge.play(it.videoId) }
        }
        onDispose {
            kidViewModel.onPlaybackCommand = null
            bridge?.destroy()
        }
    }

    // Die WebView haengt im Fenster der Activity, nicht in der Compose-Einbettung; ihre Lage
    // kommt aus dem Platzhalter der Player-Ansicht. Grund siehe PlayerView.
    var videoBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val activity = context as android.app.Activity
    DisposableEffect(kidState.playback != null) {
        if (bridge != null) {
            activity.addContentView(
                bridge.webView,
                android.widget.FrameLayout.LayoutParams(1, 1)
            )
        }
        onDispose {
            (bridge?.webView?.parent as? android.view.ViewGroup)?.removeView(bridge?.webView)
        }
    }
    LaunchedEffect(videoBounds, kidState.playback != null) {
        if (bridge == null || videoBounds == androidx.compose.ui.geometry.Rect.Zero) {
            return@LaunchedEffect
        }
        val params = android.widget.FrameLayout.LayoutParams(
            videoBounds.width.toInt(),
            videoBounds.height.toInt()
        )
        params.leftMargin = videoBounds.left.toInt()
        params.topMargin = videoBounds.top.toInt()
        bridge.webView.layoutParams = params
    }

    val flow = remember { PinFlow(container.pinStore, container.texte) }

    // Ohne PIN zuerst die Einrichtung, danach ist der Kindermodus der Normalzustand.
    var screen by remember { mutableStateOf<Screen>(if (flow.isConfigured) Screen.Kid else Screen.PinSetup) }
    var editing by remember { mutableStateOf<WhitelistItemEntity?>(null) }
    // Wuensche (ADR 0001): offener Wunsch-Dialog und Link-Eingabe zu einem Wunsch.
    var wunsch by remember { mutableStateOf<xyz.steier.sidetube.core.db.WishEntity?>(null) }
    var linkFuer by remember { mutableStateOf<xyz.steier.sidetube.core.db.WishEntity?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Uhr- und Zeitzonenwechsel: frueher nur waehrend einer Wiedergabe gehoert. Seit die Sperre
    // (ADR 0007) auch beim Stoebern an Wanduhr-Grenzen haengt, hoert der Kindermodus immer zu.
    DisposableEffect(lifecycle, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                kidViewModel.onClockChanged()
            }
        }
        var registered = false
        fun unregister() {
            if (registered) { context.unregisterReceiver(receiver); registered = false }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && !registered) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                }
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                registered = true
                kidViewModel.onClockChanged()
            }
            if (event == Lifecycle.Event.ON_STOP) unregister()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); unregister() }
    }
    DisposableEffect(lifecycle, bridge) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                kidViewModel.closePlayer()
                bridge?.destroy()
                editing = null
                wunsch = null
                linkFuer = null
                screen = if (flow.isConfigured) Screen.Kid else Screen.PinSetup
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Tasten hoert nur der Kindermodus; im Elternbereich bleibt die Bedienung Standard.
    val isKid = screen is Screen.Kid
    // Suche und Themenwunsch nehmen Zeichen einer Tastatur direkt an (beide haben ein Rad).
    val searching = kidState.screen is xyz.steier.sidetube.kid.KidScreen.Search ||
        kidState.screen is xyz.steier.sidetube.kid.KidScreen.ThemaWunsch
    val sperre = kidState.sperre
    DisposableEffect(isKid, searching, sperre) {
        registerKeys(if (!isKid) null else { event, longPress ->
            val action = KeyMap.action(event.keyCode, longPress, typing = searching)
            when {
                // Unter der Sperre: Mitte fuehrt zur PIN (wie der Knopf), Einstellungen lang in den
                // Elternbereich – die Sperre bleibt dabei stehen. Alles andere verhallt.
                sperre != null && action == xyz.steier.sidetube.core.input.KeyAction.Select -> {
                    screen = Screen.PinEntry(sperre); true
                }
                sperre != null && action != xyz.steier.sidetube.core.input.KeyAction.Settings -> true
                // Aussen oben rechts lang: die Einstellungen, also der Elternbereich hinter der PIN –
                // wie das Schloss im Kopf. Ein laufendes Video wird ordentlich geschlossen.
                action == xyz.steier.sidetube.core.input.KeyAction.Settings -> {
                    kidViewModel.closePlayer(); screen = Screen.PinEntry(); true
                }
                action != null -> { kidViewModel.onKey(action); true }
                // In der Suche nimmt das Geraet Zeichen direkt entgegen - ohne fokussiertes
                // Feld und damit ohne Bildschirmtastatur, die hier den halben Schirm faellt.
                searching && KeyMap.isBackspace(event.keyCode) -> { kidViewModel.backspaceQuery(); true }
                // Zifferntasten (auch Ziffernblock ohne NumLock) sind T9 – siehe core/input/T9.
                searching && KeyMap.digit(event.keyCode) != null -> {
                    kidViewModel.appendToQuery(KeyMap.digit(event.keyCode)!!); true
                }
                searching && event.unicodeChar != 0 -> {
                    val char = event.unicodeChar.toChar()
                    if (char.isLetterOrDigit() || char == ' ') { kidViewModel.appendToQuery(char); true } else false
                }
                else -> false
            }
        })
        onDispose { registerKeys(null) }
    }
    val snackbar = remember { SnackbarHostState() }
    // Meldungen des Elternbereichs (Titel, Antworten an das Kind) bleiben nicht im Kindermodus stehen.
    LaunchedEffect(isKid) {
        if (isKid) { snackbar.currentSnackbarData?.dismiss(); kidViewModel.pruefeSperre() }
    }

    LaunchedEffect(kidState.hint) {
        kidState.hint?.let {
            snackbar.showSnackbar(message = it, withDismissAction = true)
            kidViewModel.clearHint()
        }
    }

    LaunchedEffect(state.message) {
        // Lang stehen lassen und wegtippbar: Eine Fehlermeldung, die nach zwei Sekunden weg ist,
        // hilft niemandem - und genau daran ist heute ein Fehler beinahe unbemerkt geblieben.
        state.message?.let {
            snackbar.showSnackbar(message = it, withDismissAction = true, duration = SnackbarDuration.Long)
            viewModel.clearMessage()
        }
    }

    // Von der PIN-Abfrage eine Ebene zurueck heisst: zurueck zum Kind, nicht aus der App heraus.
    // Seit aussen oben rechts lang dorthin fuehrt (SideUI ADR 0013), ist sie mit dem Ring erreichbar.
    BackHandler(enabled = screen is Screen.PinEntry) { screen = Screen.Kid }

    // Die Systemtaste "Zurueck" fuehrt eine Ebene hoch, nie aus der App heraus.
    BackHandler(enabled = screen !is Screen.Kid && screen !is Screen.PinEntry && screen !is Screen.PinSetup) {
        screen = when (val current = screen) {
            is Screen.Review -> Screen.Whitelist(current.profile)
            else -> Screen.Profiles
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.padding(padding)) {
        when (val current = screen) {
            Screen.Kid -> {
                kidState.playback?.let { playback ->
                PlayerView(
                    state = playback,
                    onVideoBounds = { videoBounds = it },
                    onPlayPause = kidViewModel::togglePlayback,
                    onPrevious = kidViewModel::playerPrevious,
                    onNext = kidViewModel::playerNext,
                    onClose = kidViewModel::closePlayer,
                    onReplay = kidViewModel::replay,
                    endFocus = kidState.endFocus,
                    mehrDavon = kidState.mehrDavon,
                    onMehrDavon = kidViewModel::wuenscheMehrDavon,
                    menu = kidState.playerMenu,
                    onOpenMenu = kidViewModel::openPlayerMenu,
                    onChooseMenu = kidViewModel::choosePlayerMenu
                )
            } ?: KidView(
                state = kidState,
                onFocus = kidViewModel::focusRow,
                onActivate = kidViewModel::activateRow,
                onQuery = kidViewModel::setQuery,
                onHome = kidViewModel::goHome,
                onParent = { kidViewModel.closePlayer(); screen = Screen.PinEntry() },
                onSelectProfile = kidViewModel::selectProfile,
                onSearch = kidViewModel::openSearch,
                onSelectSegment = kidViewModel::selectSegment,
                onWheelKey = kidViewModel::tapWheelKey
            )
            // Die Sperre liegt ueber Liste und Player; nur die PIN der Eltern fuehrt heraus.
            kidState.sperre?.let { aktiv ->
                KidSperreView(aktiv, KidSperreText.weiterAb(kidState.profile)) { screen = Screen.PinEntry(aktiv) }
            }
            }

            Screen.PinSetup -> PinPad(
                title = stringResource(if (flow.awaitingRepeat) R.string.pin_wiederholen else R.string.pin_festlegen),
                subtitle = stringResource(R.string.pin_festlegen_untertitel),
                error = flow.error,
                onComplete = { pin -> if (flow.setup(pin)) screen = Screen.Kid }
            )

            is Screen.PinEntry -> PinPad(
                title = stringResource(R.string.pin_fuer_einstellungen),
                error = flow.error,
                onComplete = { pin ->
                    if (flow.verify(pin)) {
                        val vonSperre = current.sperre
                        if (vonSperre != null) kidViewModel.elternHebenSperreAuf()
                        // Schlaf-Timer und Ruhezeit: zurueck zum Kind. Sonst dorthin, wo die Eltern
                        // etwas tun koennen – Limit anpassen, Wiedergabe freigeben.
                        screen = if (vonSperre?.zurueckZumKind == true) Screen.Kid else Screen.Profiles
                    }
                }
            )

            Screen.Profiles -> ProfileListScreen(
                state = state,
                onOpen = { profile -> viewModel.openProfile(profile); screen = Screen.Whitelist(profile) },
                onEdit = { profile -> screen = Screen.ProfileEdit(profile) },
                onStats = { profile -> screen = Screen.Stats(profile) },
                onCreate = viewModel::createProfile,
                onDelete = viewModel::deleteProfile,
                onSources = { screen = Screen.Sources },
                onChangePin = { screen = Screen.ChangePin },
                onElternkanal = { screen = Screen.Elternkanal },
                onRecoverPlayback = kidViewModel::acknowledgeInterruptedPlaybackByParent,
                sleepRemainingSeconds = kidState.sleepRemainingSeconds,
                // Wie auf iOS: starten und gleich zurueck in den Kindermodus.
                onStartSleep = { minutes ->
                    kidViewModel.startSleepTimer(minutes); kidViewModel.goHome(); screen = Screen.Kid
                },
                onStopSleep = kidViewModel::stopSleepTimer,
                onSkipBedtime = viewModel::skipBedtimeTonight,
                onClearBedtimeException = viewModel::clearBedtimeException,
                onLock = { kidViewModel.goHome(); screen = Screen.Kid }
            )

            is Screen.Whitelist -> WhitelistScreen(
                profile = current.profile,
                state = state,
                onBack = { screen = Screen.Profiles },
                onAddUrl = viewModel::previewFromUrl,
                onConfirmAdd = { viewModel.nimmVorschauAuf(current.profile.id) },
                onAddKanal = { viewModel.nimmKanalAuf(current.profile.id, it) },
                onCancelAdd = viewModel::verwirfVorschau,
                onReview = { screen = Screen.Review(current.profile) },
                onEdit = { editing = it },
                onRemove = viewModel::remove,
                onImportPack = { pack, preset -> viewModel.importStarterPack(pack, current.profile, preset) }
            )

            is Screen.Review -> {
                ReviewQueueScreen(
                    pending = state.pending,
                    onBack = { screen = Screen.Whitelist(current.profile) },
                    onOpen = { editing = it },
                    onDiscardAll = viewModel::discardPending,
                    wuensche = state.wuensche,
                    onOpenWish = { wunsch = it },
                    sammelGrund = viewModel::sammelGrund,
                    onSammelFreigeben = viewModel::sammelFreigeben,
                    onSammelAblehnen = viewModel::sammelAblehnen
                )
                // Link zu einem Wunsch: dieselbe Vorschau wie unter „Link hinzufügen".
                state.vorschau?.let { draft ->
                    VorschauDialog(draft, onConfirm = { viewModel.nimmVorschauAuf(current.profile.id) }, onDismiss = viewModel::verwirfVorschau,
                        quelle = state.quelleZu(draft), onKanal = { viewModel.nimmKanalAuf(current.profile.id, it) })
                }
            }

            is Screen.ProfileEdit -> ProfileEditorScreen(
                profile = current.profile,
                onCancel = { screen = Screen.Profiles },
                onSave = { draft -> viewModel.saveProfile(current.profile.id, draft); screen = Screen.Profiles }
            )

            Screen.ChangePin -> ChangePinScreen(
                store = container.pinStore,
                onCancel = { screen = Screen.Profiles },
                onDone = { viewModel.pinChanged(); screen = Screen.Profiles }
            )

            Screen.Elternkanal -> ElternkanalScreen(
                einrichtung = remember { ElternkanalEinrichtung(container.elternkanal, container.elternmelder, container.texte) },
                onBack = { screen = Screen.Profiles }
            )

            is Screen.Stats -> WatchStatsScreen(
                profile = current.profile,
                laden = container.watchTime::since,
                onBack = { screen = Screen.Profiles }
            )

            Screen.Sources -> SourceTrustScreen(
                sources = state.sources,
                onBack = { screen = Screen.Profiles },
                onSetTrust = viewModel::setTrust
            )
        }
    }

    }

    wunsch?.let { offen ->
        LaunchedEffect(offen.id) { viewModel.loadWunschVerlauf(offen) }
        WunschSheet(
            wunsch = offen,
            history = state.verlauf,
            onDismiss = { wunsch = null },
            onFreigeben = { antwort -> wunsch = null; viewModel.wunschFreigeben(offen, antwort) },
            onEntscheiden = { status, antwort -> wunsch = null; viewModel.wunschEntscheiden(offen, status, antwort) },
            onKanalPruefen = { wunsch = null; viewModel.wunschKanalPruefen(offen) { screen = Screen.Sources } },
            onLink = { wunsch = null; linkFuer = offen }
        )
    }
    linkFuer?.let { ziel ->
        TextPrompt(
            title = stringResource(if (ziel.kind == "thema") R.string.wunsch_link_titel else R.string.wunsch_video_link_titel),
            label = stringResource(R.string.youtube_adresse),
            confirmLabel = stringResource(R.string.pruefen),
            onDismiss = { linkFuer = null },
            onConfirm = { url -> linkFuer = null; if (url.isNotBlank()) viewModel.wunschLink(ziel, url) }
        )
    }

    editing?.let { item ->
        LaunchedEffect(item.id) { viewModel.loadHistory(item) }
        ReviewDecisionSheet(
            item = item,
            history = state.verlauf,
            onDismiss = { editing = null },
            onApprove = { approval -> editing = null; viewModel.approve(item, approval) },
            onReject = { editing = null; viewModel.reject(item) },
            onLater = { editing = null; viewModel.later(item) },
            onBackToReview = { editing = null; viewModel.backToReview(item) },
            quelle = state.quelleZu(item),
            onApproveKanal = { einstufung, ageMax, notes -> editing = null; viewModel.stufeKanalEin(item, einstufung, ageMax, notes) }
        )
    }
}
