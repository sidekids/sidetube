// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.ApprovalStatus
import xyz.steier.sidetube.core.model.SourceTrust
import java.time.Instant
import java.time.ZoneId
import xyz.steier.sidetube.core.player.BedtimePolicy
import xyz.steier.sidetube.core.player.BedtimeState
import xyz.steier.sidetube.core.player.SleepTimerPolicy
import xyz.steier.sidetube.core.repo.Approval
import xyz.steier.sidetube.core.repo.StarterPack

/** Übersicht: die Profile der Familie. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    state: ParentState,
    onOpen: (KidProfileEntity) -> Unit,
    onEdit: (KidProfileEntity) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (KidProfileEntity) -> Unit,
    onSources: () -> Unit,
    onChangePin: () -> Unit,
    onElternkanal: () -> Unit,
    onRecoverPlayback: (String) -> Unit,
    /** Restzeit eines laufenden Schlaf-Timers; `null` wenn keiner laeuft. */
    sleepRemainingSeconds: Int?,
    onStartSleep: (Int) -> Unit,
    onStopSleep: () -> Unit,
    onSkipBedtime: (KidProfileEntity) -> Unit,
    onClearBedtimeException: (KidProfileEntity) -> Unit,
    onLock: () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var recoveryProfile by remember { mutableStateOf<KidProfileEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen", maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = onLock) { Text("Sperren") }
                },
                actions = {
                    IconButton(onClick = { showSleep = true }) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = sleepRemainingSeconds
                                ?.let { "Schlafmodus laeuft, noch ${SleepTimerPolicy.format(it)}" }
                                ?: "Schlafmodus"
                        )
                    }
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Neues Profil")
                    }
                    // Wie das ⋯-Menue auf iOS. Selten Gebrauchtes wandert hierher, damit die
                    // Leiste auf dem SidePhone dem Titel noch Platz laesst.
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Weitere")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("PIN ändern") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                onClick = { showMenu = false; onChangePin() }
                            )
                            DropdownMenuItem(
                                text = { Text("Quellen & Sicherheitsstufen") },
                                leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
                                onClick = { showMenu = false; onSources() }
                            )
                            DropdownMenuItem(
                                text = { Text("Eltern benachrichtigen") },
                                leadingIcon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                                onClick = { showMenu = false; onElternkanal() }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.profiles.isEmpty()) {
            EmptyHint(
                title = "Noch kein Profil",
                text = "Lege für jedes Kind ein Profil mit eigener Liste an.",
                actionLabel = "Profil anlegen",
                onAction = { showCreate = true },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.profiles, key = { it.id }) { profile ->
                    ListItem(
                        headlineContent = { Text(profile.name) },
                        supportingContent = {
                            Text(profileSummary(profile))
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onEdit(profile) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Profil bearbeiten")
                                }
                                IconButton(onClick = { onDelete(profile) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Profil entfernen")
                                }
                            }
                        },
                        modifier = Modifier.clickable { onOpen(profile) }
                    )
                    HorizontalDivider()
                    if (profile.id in state.interruptedProfiles) {
                        TextButton(onClick = { recoveryProfile = profile }) {
                            Text("Unterbrochene Wiedergabe prüfen: ${profile.name}")
                        }
                    }
                    BedtimeExceptionRow(profile, onSkipBedtime, onClearBedtimeException)
                }
            }
        }
    }

    recoveryProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { recoveryProfile = null },
            title = { Text("Wiedergabe wieder freigeben?") },
            text = { Text("Die letzte Wiedergabe wurde nicht sicher abgeschlossen. Ein Teil der Sehzeit ist möglicherweise unbekannt. Bereits gespeicherte Sehzeit bleibt erhalten. Diese Freigabe schätzt keine fehlende Zeit nach.") },
            confirmButton = { TextButton(onClick = { recoveryProfile = null; onRecoverPlayback(profile.id) }) { Text("Als Eltern freigeben") } },
            dismissButton = { TextButton(onClick = { recoveryProfile = null }) { Text("Abbrechen") } }
        )
    }

    if (showSleep) {
        SleepModeDialog(
            remainingSeconds = sleepRemainingSeconds,
            onDismiss = { showSleep = false },
            onStart = { minutes -> showSleep = false; onStartSleep(minutes) },
            onStop = { showSleep = false; onStopSleep() }
        )
    }

    if (showCreate) {
        TextPrompt(
            title = "Neues Profil",
            label = "Name",
            confirmLabel = "Anlegen",
            onDismiss = { showCreate = false },
            onConfirm = { name -> showCreate = false; if (name.isNotBlank()) onCreate(name) }
        )
    }
}

/** Kurzfassung unter dem Namen: Tageslimit und Ruhezeit, die zwei Regeln, die Eltern am haeufigsten suchen. */
internal fun profileSummary(profile: KidProfileEntity): String = listOf(
    profile.dailyLimitMinutes?.let { "$it Minuten am Tag" } ?: "ohne Tageslimit",
    if (profile.bedtimeEnabled) "Ruhezeit ab ${ParentLabels.clock(profile.bedtimeStartMinutes)}" else "ohne Ruhezeit"
).joinToString(" · ")

/**
 * Ausnahme von der Ruhezeit. Bewusst nur waehrend einer laufenden Ruhezeit und nur bis zu deren
 * Ende - eine frei waehlbare Dauer waere in der Praxis ein Ausschalter.
 *
 * Der Zustand wird beim Aufbau der Liste einmal bestimmt, nicht getaktet nachgefuehrt: Der
 * Elternbereich soll keinen wiederkehrenden Wecker starten. Nach dem Antippen aendert sich das
 * Profil, und die Zeile wird ohnehin neu aufgebaut.
 */
@Composable
private fun BedtimeExceptionRow(
    profile: KidProfileEntity,
    onSkip: (KidProfileEntity) -> Unit,
    onClear: (KidProfileEntity) -> Unit
) {
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val skipUntil = profile.bedtimeSkipUntil?.let(Instant::ofEpochMilli)?.takeIf { it.isAfter(now) }
    when {
        skipUntil != null -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Ruhezeit ausgesetzt bis ${ParentFormat.time(skipUntil, zone)}",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { onClear(profile) }) { Text("Aufheben") }
        }
        BedtimePolicy.state(profile, now, zone) == BedtimeState.Active ->
            TextButton(onClick = { onSkip(profile) }) {
                Text("Ruhezeit für ${profile.name} bis zum Ende aussetzen")
            }
    }
}

/**
 * Schlafmodus: eine Dauer, danach endet die Wiedergabe von selbst. Anders als die Ruhezeit gilt
 * er einmalig ab jetzt. Die letzte Minute blendet die Lautstaerke aus, damit das Ende nicht
 * abrupt kommt; [SleepTimerPolicy] klemmt die Dauer.
 */
@Composable
private fun SleepModeDialog(
    remainingSeconds: Int?,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
    onStop: () -> Unit
) {
    var minutes by remember { mutableStateOf(SleepTimerPolicy.DEFAULT_MINUTES.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schlafmodus") },
        text = {
            if (remainingSeconds != null) {
                Text("Der Schlafmodus laeuft noch ${SleepTimerPolicy.format(remainingSeconds)}. " +
                    "Danach endet die Wiedergabe und es laesst sich kein neues Video starten.")
            } else {
                Column {
                    Text("Nach ${minutes.toInt()} Minuten endet die Wiedergabe von selbst.")
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = minutes,
                        onValueChange = { minutes = it },
                        valueRange = 5f..SleepTimerPolicy.MINUTES.last.toFloat(),
                        steps = (SleepTimerPolicy.MINUTES.last - 5) / 5 - 1
                    )
                    Text("Die letzte Minute wird leiser ausgeblendet.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (remainingSeconds != null) TextButton(onClick = onStop) { Text("Schlafmodus beenden") }
            else TextButton(onClick = { onStart(minutes.toInt()) }) { Text("Starten") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Die Liste eines Kindes: aufnehmen, prüfen, nachbessern. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(
    profile: KidProfileEntity,
    state: ParentState,
    onBack: () -> Unit,
    onAddUrl: (String) -> Unit,
    onConfirmAdd: () -> Unit,
    /** Kanal aus der Vorschau mit Stufe, Alter und Kategorie (ADR 0003). */
    onAddKanal: (xyz.steier.sidetube.core.curation.Kanaleinstufung) -> Unit,
    onCancelAdd: () -> Unit,
    onReview: () -> Unit,
    onEdit: (WhitelistItemEntity) -> Unit,
    onRemove: (WhitelistItemEntity) -> Unit,
    onImportPack: (StarterPack, Boolean) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var showPacks by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück") }
                },
                actions = {
                    BadgedBox(badge = {
                        // Zaehler fuer Inhalte und Wuensche – der einzige Hinweis, keine Benachrichtigung.
                        val offen = state.pending.size + state.wuensche.size
                        if (offen > 0) Badge { Text("$offen") }
                    }) {
                        IconButton(onClick = onReview) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Freigaben prüfen")
                        }
                    }
                    IconButton(onClick = { showPacks = true }) {
                        Icon(Icons.Default.Download, contentDescription = "Startpaket laden")
                    }
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
                    }
                }
            )
        }
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyHint(
                title = "Liste ist leer",
                text = "Füge Kanäle, Videos oder Playlists über ihren Link hinzu – oder lade ein Startpaket.",
                actionLabel = "Link hinzufügen",
                onAction = { showAdd = true },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.items, key = { it.id }) { item ->
                    ListItem(
                        leadingContent = { Vorschau(item) },
                        headlineContent = { Text(item.title, maxLines = 2) },
                        supportingContent = { Text(describe(item)) },
                        trailingContent = {
                            IconButton(onClick = { onRemove(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Entfernen")
                            }
                        },
                        modifier = Modifier.clickable { onEdit(item) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAdd) {
        TextPrompt(
            title = "Link hinzufügen",
            label = "YouTube-Adresse",
            confirmLabel = "Prüfen",
            onDismiss = { showAdd = false },
            onConfirm = { url -> showAdd = false; if (url.isNotBlank()) onAddUrl(url) }
        )
    }

    state.vorschau?.let { draft ->
        VorschauDialog(draft, onConfirm = onConfirmAdd, onDismiss = onCancelAdd,
            quelle = state.quelleZu(draft), onKanal = onAddKanal)
    }

    if (showPacks) {
        StarterPackDialog(
            packs = state.starterPacks,
            onDismiss = { showPacks = false },
            onSelect = { pack, preset -> showPacks = false; onImportPack(pack, preset) }
        )
    }
}

private fun describe(item: WhitelistItemEntity): String {
    val status = when (ApprovalStatus.from(item.approvalStatus)) {
        ApprovalStatus.APPROVED -> "freigegeben"
        ApprovalStatus.REJECTED -> "abgelehnt"
        else -> "zu prüfen"
    }
    return listOfNotNull(item.type.lowercase(), status, item.channelTitle).joinToString(" · ")
}
