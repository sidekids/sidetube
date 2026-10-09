// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import xyz.steier.sidetube.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import xyz.steier.sidetube.core.player.PlaybackState
import xyz.steier.sidetube.core.player.PlaybackStatus
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import xyz.steier.sidetube.kid.SideFokus
import xyz.steier.sidetube.kid.WunschMoeglich

/**
 * Die Wiedergabe. Oben das Video im Seitenverhaeltnis 16:9, darunter Titel und Steuerung –
 * dieselben Aktionen, die auch die Hardwaretasten ausloesen.
 */
@Composable
fun PlayerView(
    state: PlaybackState,
    onVideoBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onReplay: () -> Unit = {},
    /** Auswahl auf der Endkarte (0 = Nochmal, 1 = Mehr davon, 2 = Zurück), gesteuert von den Tasten. */
    endFocus: Int = 0,
    /** Ob „Mehr davon" zum Video gerade geht (ADR 0001). */
    mehrDavon: WunschMoeglich = WunschMoeglich.JA,
    onMehrDavon: () -> Unit = {},
    /** Player-Menü: Auswahl oder `null`, wenn zu. */
    menu: Int? = null,
    onOpenMenu: () -> Unit = {},
    onChooseMenu: (Int) -> Unit = {}
) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Die Videoflaeche ist ein Platzhalter: Die WebView haengt im Fenster der Activity,
        // nicht in der Compose-Einbettung - dort fuehrt sie die Seite zwar aus, zeichnet sie auf
        // dem SP-01 aber nie ins Fenster. Hier wird nur ihre Lage gemeldet.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onGloballyPositioned { onVideoBounds(it.boundsInWindow()) }
        ) {
            if (state.status == PlaybackStatus.Loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            }
        }

        if (state.status == PlaybackStatus.Ended) {
            EndCard(title = state.current?.title.orEmpty(), endFocus = endFocus, onReplay = onReplay, onClose = onClose,
                mehrDavon = mehrDavon, onMehrDavon = onMehrDavon)
            return@Column
        }
        if (menu != null) {
            PlayerMenu(state.current?.title.orEmpty(), menu, mehrDavon, onChooseMenu)
            return@Column
        }

        Column(Modifier.padding(12.dp)) {
            Text(
                state.current?.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(state.progressLabel.takeIf { it.isNotEmpty() }, stringResource(statusLabel(state)), state.positionLabel)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            state.skippedTitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.player_uebersprungen, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Control(Icons.Default.SkipPrevious, stringResource(R.string.player_voriges), onPrevious)
            Control(
                if (state.status == PlaybackStatus.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                stringResource(if (state.status == PlaybackStatus.Playing) R.string.player_pause else R.string.player_abspielen),
                onPlayPause,
                large = true
            )
            Control(Icons.Default.SkipNext, stringResource(R.string.player_naechstes), onNext)
            // Das Menü mit „Mehr davon wünschen"; am Gerät außen unten rechts lang (ADR 0013).
            Control(Icons.Default.ThumbUp, stringResource(R.string.player_mehr_davon), onOpenMenu)
            Control(Icons.Default.Close, stringResource(R.string.player_schliessen), onClose)
        }
    }
}

private fun statusLabel(state: PlaybackState): Int = when (state.status) {
    PlaybackStatus.Loading -> R.string.player_status_laedt
    PlaybackStatus.Playing -> R.string.player_status_spielt
    PlaybackStatus.Paused -> R.string.player_status_pause
    PlaybackStatus.Ended -> R.string.player_status_fertig
    PlaybackStatus.Skipped -> R.string.player_status_uebersprungen
    PlaybackStatus.Failed -> R.string.player_status_nicht_abspielbar
}

@Composable
private fun Control(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    large: Boolean = false
) {
    IconButton(onClick = onClick, modifier = Modifier.size(if (large) 64.dp else 48.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (large) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(if (large) 40.dp else 28.dp)
        )
    }
}

/**
 * Endkarte nach dem Video wie auf iOS (`PlayerEndedView`): „Fertig 🎉", dann zwei klare Wege –
 * Nochmal oder zurück zur Liste. Kein Autoplay, keine Vorschläge. Hoch/runter wählt, Mitte
 * bestätigt; der Finger tippt direkt.
 */
@Composable
private fun EndCard(
    title: String, endFocus: Int, onReplay: () -> Unit, onClose: () -> Unit,
    mehrDavon: WunschMoeglich, onMehrDavon: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.player_fertig), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        EndButton(stringResource(R.string.player_nochmal), Icons.Default.Replay, focused = endFocus == 0, onClick = onReplay)
        WunschKnopf(stringResource(mehrDavonText(mehrDavon)), focused = endFocus == 1, aktiv = mehrDavon == WunschMoeglich.JA, onClick = onMehrDavon)
        EndButton(stringResource(R.string.player_zurueck_zu_videos), Icons.AutoMirrored.Filled.List, focused = endFocus == 2, onClick = onClose)
    }
}

private fun mehrDavonText(moeglich: WunschMoeglich): Int = when (moeglich) {
    WunschMoeglich.JA -> R.string.player_mehr_davon_wuenschen
    WunschMoeglich.SCHON_GEWUENSCHT -> R.string.player_mehr_davon_gewuenscht
    WunschMoeglich.GRENZE -> R.string.player_heute_keine_wuensche
}

/**
 * Das Player-Menü (ADR 0001: „Mehr davon" auch hier). Das Video steht still, solange es offen ist;
 * hoch/runter wählt, Mitte bestätigt, Zurück schließt.
 */
@Composable
private fun PlayerMenu(title: String, focus: Int, mehrDavon: WunschMoeglich, onChoose: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        WunschKnopf(stringResource(mehrDavonText(mehrDavon)), focused = focus == 0, aktiv = mehrDavon == WunschMoeglich.JA, onClick = { onChoose(0) })
        WunschKnopf(stringResource(R.string.player_weiterschauen), focused = focus == 1, aktiv = true, onClick = { onChoose(1) }, icon = Icons.Default.PlayArrow)
    }
}

/** Neuer Knopf nach SideUI: Fokus als eigene Fläche mit hellem Rahmen, nie gelb. */
@Composable
private fun WunschKnopf(
    label: String, focused: Boolean, aktiv: Boolean, onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.ThumbUp
) {
    val form = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(form)
            .background(if (focused) SideFokus.Flaeche else Color.Transparent)
            .border(if (focused) 3.dp else 1.dp, if (focused) SideFokus.Rand else Color.White.copy(alpha = 0.5f), form)
            .clickable(enabled = aktiv, onClick = onClick)
            .semantics { selected = focused }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = if (aktiv) 1f else 0.5f))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = if (aktiv) 1f else 0.6f))
    }
}

@Composable
private fun EndButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, focused: Boolean, onClick: () -> Unit) {
    // Wie in der Liste: Fokus durch eigene Flaeche und hellen Rahmen, nie gelb (SideUI ADR 0002).
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        border = androidx.compose.foundation.BorderStroke(if (focused) 3.dp else 1.dp, if (focused) SideFokus.Rand else Color.White.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (focused) SideFokus.Flaeche else Color.Transparent,
            contentColor = Color.White
        )
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}
