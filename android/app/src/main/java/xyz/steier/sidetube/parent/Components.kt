// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import xyz.steier.sidetube.core.curation.Kanaleinstufung
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.ContentProvider
import xyz.steier.sidetube.core.provider.ContentDraft
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.core.repo.StarterPack
import xyz.steier.sidetube.kid.VorschauKachel
import xyz.steier.sidetube.kid.Vorschaubilder

/** Leerzustand mit einem Weg heraus – eine leere Liste ohne Angebot ist eine Sackgasse. */
@Composable
fun EmptyHint(
    title: String,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Eine Eingabe, ein Knopf – fuer Name und Adresse. */
@Composable
fun TextPrompt(
    title: String,
    label: String,
    confirmLabel: String,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                colors = xyz.steier.sidetube.sideTextFieldColors(),
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = keyboard,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Auswahl der mitgelieferten Startpakete. */
@Composable
fun StarterPackDialog(
    packs: List<StarterPack>,
    onDismiss: () -> Unit,
    onSelect: (StarterPack, Boolean) -> Unit
) {
    var applyPreset by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Startpaket laden") },
        text = {
            Column {
                Text(
                    "Von Hand geprüfte Listen. Alles kommt zur Prüfung, nichts wird sofort sichtbar.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                if (packs.isEmpty()) {
                    Text("In dieser Fassung sind keine Startpakete enthalten.")
                } else {
                    packs.forEach { pack ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            onClick = { onSelect(pack, applyPreset) }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(pack.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    listOfNotNull(
                                        pack.videoCount.takeIf { it > 0 }?.let { "$it Videos" },
                                        pack.channelCount.takeIf { it > 0 }?.let { "$it Kanäle" }
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = applyPreset, onCheckedChange = { applyPreset = it })
                        Text("Vorgeschlagene Regeln mit übernehmen", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Zeitangaben im Elternbereich: kurze lokale Uhrzeit, keine Sekunden. */
internal object ParentFormat {
    private val formatter: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("HH:mm")

    fun time(instant: java.time.Instant, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
        formatter.format(instant.atZone(zone))
}

/**
 * Vorschaubild eines Eintrags in den Elternlisten - dieselbe Kachel wie im Kindermodus. Feste
 * Spaltenbreite, damit die Titel von Kanaelen und Videos buendig stehen.
 */
@Composable
internal fun Vorschau(item: WhitelistItemEntity) {
    val kanal = item.type == WhitelistItemType.CHANNEL.name
    Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
        VorschauKachel(Vorschaubilder.adresse(item), kanal, breite = if (kanal) 40.dp else 64.dp)
    }
}


/**
 * Was hinter einer eingegebenen Adresse steckt – gesehen, bevor es aufgenommen wird.
 *
 * Videos und Playlists kommen mit [onConfirm] in die Pruefliste. Ein **Kanal** wird hier gleich
 * eingestuft (ADR 0003): Stufe, Mindestalter, Kategorie, dann [onKanal] – die Eltern haben ihn eben
 * selbst ausgewaehlt, ein zweiter Gang ueber die Pruefliste waere nur ein Umweg. [quelle] liefert die
 * Vorauswahl, wenn der Kanal schon eingestuft ist.
 */
@Composable
internal fun VorschauDialog(
    draft: ContentDraft,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    quelle: CuratedSourceEntity? = null,
    onKanal: (Kanaleinstufung) -> Unit = {}
) {
    val kanal = draft.type == WhitelistItemType.CHANNEL
    val bild = draft.thumbnailUrl.takeIf { it.isNotBlank() }
        ?: Vorschaubilder.fuerVideo(draft.contentId).takeIf { !kanal && draft.provider == ContentProvider.YOUTUBE }
    var einstufung by remember(draft.contentId) { mutableStateOf(Kanaleinstufung.vorauswahl(quelle)) }
    val gesperrt = einstufung.ergebnis == Kanaleinstufung.Ergebnis.NICHT_AUFNEHMEN
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kanal) "Kanal hinzufügen" else "Vorschau") },
        text = {
            // Mit den drei Wahlen ist der Dialog hoeher als das SidePhone; ohne Scrollen ueberlappt er.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                if (kanal) {
                    // Beim Kanal kleines Bild neben dem Namen: Die drei Entscheidungen sollen ins Bild,
                    // nicht das Kanalbild (am SidePhone gesehen, 04.10.2026).
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        VorschauKachel(bild, kanal, breite = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(draft.title, style = MaterialTheme.typography.titleSmall)
                    }
                } else {
                    VorschauKachel(bild, kanal, breite = 224.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(draft.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                    draft.channelTitle?.takeIf { it != draft.title }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
                if (kanal) {
                    Spacer(Modifier.height(12.dp))
                    KanalEinstufungFelder(einstufung, onChange = { einstufung = it })
                }
            }
        },
        confirmButton = {
            if (kanal) TextButton(onClick = { onKanal(einstufung) }) { Text(if (gesperrt) "Sperren" else "Hinzufügen") }
            else TextButton(onClick = onConfirm) { Text("Aufnehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
