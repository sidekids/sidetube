// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import xyz.steier.sidetube.LocalTexte
import xyz.steier.sidetube.R
import xyz.steier.sidetube.Texte
import androidx.compose.runtime.Composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.steier.sidetube.core.curation.WunschArt
import xyz.steier.sidetube.core.curation.WunschStatus
import xyz.steier.sidetube.core.db.ReviewEventEntity
import xyz.steier.sidetube.core.db.WishEntity
import xyz.steier.sidetube.kid.VorschauKachel
import xyz.steier.sidetube.kid.Vorschaubilder
import java.time.Instant

/** Wie ein Wunsch in der Pruefliste heisst: woher er kommt und was gewuenscht ist. */
internal object WunschLabels {
    private val ZEIT: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm")

    @Composable
    fun herkunft(wunsch: WishEntity): String = when (WunschArt.from(wunsch.kind)) {
        WunschArt.THEMA -> stringResource(R.string.wunsch_herkunft_thema)
        WunschArt.MEHR_DAVON -> wunsch.channelTitle?.let { stringResource(R.string.wunsch_herkunft_mehr_davon_kanal, it) }
            ?: stringResource(R.string.wunsch_herkunft_mehr_davon)
        WunschArt.NEUE_FOLGE -> wunsch.channelTitle?.let { stringResource(R.string.wunsch_herkunft_neue_folge_kanal, it) }
            ?: stringResource(R.string.wunsch_herkunft_neue_folge)
        null -> wunsch.kind
    }

    /** Wie unter „Meine Wünsche": Wortanfänge groß, auch wenn das Rad klein schreibt. */
    fun titel(wunsch: WishEntity): String = when (WunschArt.from(wunsch.kind)) {
        WunschArt.THEMA -> "„${xyz.steier.sidetube.kid.KidWunschRows.wortanfaenge(wunsch.topic.orEmpty())}“"
        else -> wunsch.videoTitle.orEmpty()
    }

    @Composable
    fun zeile(wunsch: WishEntity): String = listOfNotNull(
        herkunft(wunsch),
        ZEIT.format(Instant.ofEpochMilli(wunsch.createdAt).atZone(java.time.ZoneId.systemDefault())),
        stringResource(R.string.wunsch_zeile_besprechen).takeIf { wunsch.status == WunschStatus.BESPRECHEN.id },
        wunsch.resultContentId?.let { stringResource(R.string.wunsch_zeile_link) }
    ).joinToString(" · ")
}

/** Ein Wunsch in der Pruefliste: Vorschaubild bei Videos, sonst eine Gluehbirne. */
@Composable
internal fun WunschListItem(wunsch: WishEntity, onOpen: () -> Unit) {
    ListItem(
        leadingContent = {
            val video = wunsch.videoId
            if (video != null) VorschauKachel(Vorschaubilder.fuerVideo(video), istKanal = false, breite = 64.dp)
            else Box(Modifier.size(width = 64.dp, height = 36.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Lightbulb, contentDescription = null)
            }
        },
        headlineContent = { Text(WunschLabels.titel(wunsch), maxLines = 2) },
        supportingContent = { Text(WunschLabels.zeile(wunsch)) },
        modifier = Modifier.clickable(onClick = onOpen)
    )
}

/**
 * Entscheidung ueber einen Wunsch (ADR 0001). Die Aktionen haengen von der Herkunft ab:
 * - neue Folge: Freigeben, Ablehnen, Besprechen;
 * - „Mehr davon": Kanal pruefen, Video-Link hinzufuegen, Ablehnen, Besprechen, Erledigt;
 * - Thema: Link hinzufuegen, Erledigt, Ablehnen, Besprechen.
 * Die Antwort an das Kind ist freiwillig und erscheint unter „Meine Wuensche".
 * Aufbau wie [ReviewDecisionSheet]: Knoepfe untereinander, damit sie auf 320 dp passen; Antwortfeld
 * und erste Aktion stehen am SidePhone ohne Scrollen im Bild, die Erklaerung folgt darunter.
 */
@Composable
fun WunschSheet(
    wunsch: WishEntity,
    history: List<ReviewEventEntity>,
    onDismiss: () -> Unit,
    onFreigeben: (String?) -> Unit,
    onEntscheiden: (WunschStatus, String?) -> Unit,
    onKanalPruefen: () -> Unit,
    onLink: () -> Unit
) {
    var antwort by remember(wunsch.id) { mutableStateOf("") }
    val art = WunschArt.from(wunsch.kind)
    fun text() = antwort.takeIf { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wunsch_titel)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Bild neben dem Titel: So stehen Antwortfeld und erste Aktion am SidePhone ohne Scrollen im Bild.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    wunsch.videoId?.let {
                        VorschauKachel(Vorschaubilder.fuerVideo(it), istKanal = false, breite = 80.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Column {
                        Text(WunschLabels.titel(wunsch), style = MaterialTheme.typography.titleSmall, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(WunschLabels.herkunft(wunsch), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    colors = xyz.steier.sidetube.sideTextFieldColors(),
                    value = antwort,
                    onValueChange = { if (it.length <= xyz.steier.sidetube.core.curation.WunschRegeln.ANTWORT_MAX) antwort = it },
                    label = { Text(stringResource(R.string.wunsch_antwort_label)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                when (art) {
                    WunschArt.NEUE_FOLGE -> {
                        Button(onClick = { onFreigeben(text()) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.freigeben)) }
                    }
                    WunschArt.MEHR_DAVON -> {
                        Button(onClick = onKanalPruefen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wunsch_kanal_pruefen)) }
                        OutlinedButton(onClick = onLink, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wunsch_video_link_hinzufuegen)) }
                        OutlinedButton(onClick = { onEntscheiden(WunschStatus.ERFUELLT, text()) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.erledigt)) }
                    }
                    WunschArt.THEMA, null -> {
                        Button(onClick = onLink, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.link_hinzufuegen)) }
                        OutlinedButton(onClick = { onEntscheiden(WunschStatus.ERFUELLT, text()) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.erledigt)) }
                    }
                }
                OutlinedButton(onClick = { onEntscheiden(WunschStatus.ABGELEHNT, text()) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wunsch_ablehnen_nicht_jetzt), color = MaterialTheme.colorScheme.error)
                }
                if (wunsch.status != WunschStatus.BESPRECHEN.id) {
                    OutlinedButton(onClick = { onEntscheiden(WunschStatus.BESPRECHEN, text()) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.besprechen))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    when (art) {
                        WunschArt.NEUE_FOLGE -> stringResource(R.string.wunsch_erklaerung_neue_folge)
                        WunschArt.MEHR_DAVON -> stringResource(R.string.wunsch_erklaerung_mehr_davon)
                        WunschArt.THEMA -> stringResource(R.string.wunsch_erklaerung_thema)
                        null -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.verlauf), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                if (history.isEmpty()) {
                    Text(stringResource(R.string.noch_keine_eintraege), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                history.forEach { event ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(ParentLabels.eventHeadline(event, LocalTexte.current), style = MaterialTheme.typography.labelMedium)
                        Text(ParentLabels.eventTime(event), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        event.note?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.schliessen)) } }
    )
}
