// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.Instant
import xyz.steier.sidetube.core.db.KidProfileEntity
import xyz.steier.sidetube.core.model.AgeBand

/**
 * Profil bearbeiten wie auf iOS: Name, Altersprofil, Inhalte, Ruhezeit, Tageslimit.
 *
 * Fuer das SidePhone (rund 320 × 427 dp) eine einzige scrollbare Spalte mit Schaltern und
 * „−“/„+“ statt Zahlenfeldern - Tippen genuegt, eine Tastatur braucht nur der Name. Die
 * Schlaf-Playlist der iOS-Maske fehlt bewusst: Android liest das Feld nirgends, ein Eingabefeld
 * dafuer verspraeche eine Wirkung, die es nicht gibt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    profile: KidProfileEntity,
    onCancel: () -> Unit,
    onSave: (ProfileDraft) -> Unit
) {
    var draft by remember(profile.id) { mutableStateOf(ProfileDraft.from(profile)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil bearbeiten", maxLines = 1) },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
                actions = {
                    TextButton(onClick = { onSave(draft) }, enabled = draft.canSave) { Text("Sichern") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                colors = xyz.steier.sidetube.sideTextFieldColors(),
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("Altersprofil")
            AgeBandPicker(draft.ageBand) { draft = draft.copy(ageBand = it) }
            Footnote("Inhalte mit höherem Mindestalter bleiben unsichtbar. Manga zeichnen ab 8, Anime & Manga ab 12.")

            SectionTitle("Inhalte")
            SwitchRow("Nachrichten (logo!)", draft.allowNews) { draft = draft.copy(allowNews = it) }
            SwitchRow("Manga zeichnen", draft.allowManga) { draft = draft.copy(allowManga = it) }
            SwitchRow(
                "Anime & Manga (ab 12)",
                draft.allowMangaEntertainment && draft.mangaEntertainmentSelectable,
                enabled = draft.mangaEntertainmentSelectable
            ) { draft = draft.copy(allowMangaEntertainment = it) }
            SwitchRow("Shorts erlauben", draft.allowShorts) { draft = draft.copy(allowShorts = it) }
            SwitchRow("Nächstes Video automatisch", draft.autoplayNext) { draft = draft.copy(autoplayNext = it) }

            SectionTitle("Ruhezeit")
            SwitchRow("Ruhezeit", draft.bedtimeEnabled) { draft = draft.copy(bedtimeEnabled = it) }
            if (draft.bedtimeEnabled) {
                StepperRow("Beginn ${ParentLabels.clock(draft.bedtimeStartMinutes)}", "Beginn") {
                    draft = draft.stepBedtimeStart(it)
                }
                StepperRow("Ende ${ParentLabels.clock(draft.bedtimeEndMinutes)}", "Ende") {
                    draft = draft.stepBedtimeEnd(it)
                }
                StepperRow("Fr/Sa ${draft.bedtimeWeekendOffsetMinutes} min später", "Wochenende") {
                    draft = draft.stepWeekendOffset(it)
                }
                Text("Vorschlag", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProfileDraft.BEDTIME_SUGGESTIONS.forEach { (label, start) ->
                        OutlinedButton(
                            onClick = { draft = draft.copy(bedtimeStartMinutes = start) },
                            contentPadding = PaddingValuesCompact
                        ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                draft.bedtimeSkipUntil?.let(Instant::ofEpochMilli)?.takeIf { it.isAfter(Instant.now()) }?.let { until ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ausnahme bis ${ParentFormat.time(until)}",
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { draft = draft.clearBedtimeSkip() }) { Text("Aufheben") }
                    }
                }
            }
            Footnote("In der Ruhezeit ist der Kindermodus gesperrt; 15 und 5 Minuten vorher gibt es einen Hinweis. Die PIN hebt die Sperre bis zum Ende der Ruhezeit auf.")

            SectionTitle("Tageslimit")
            SwitchRow("Tageslimit", draft.limitEnabled) { draft = draft.copy(limitEnabled = it) }
            if (draft.limitEnabled) {
                StepperRow("${draft.limitMinutes} Minuten pro Tag", "Tageslimit") { draft = draft.stepLimit(it) }
            }
            Footnote("Ohne Limit darf unbegrenzt geschaut werden. Die Zeit wird um Mitternacht zurückgesetzt.")
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val PaddingValuesCompact = PaddingValues(horizontal = 8.dp, vertical = 0.dp)

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun Footnote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 4.dp))
}

/** Die ganze Zeile ist der Schalter: Auf dem kleinen Display ist der Schalter allein ein kleines Ziel. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleableRow(checked, enabled, onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f),
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private fun Modifier.toggleableRow(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit): Modifier =
    toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(vertical = 4.dp)

/** Wert mit „−“ und „+“ - wie der iOS-Stepper, ohne Zahlentastatur. */
@Composable
private fun StepperRow(label: String, what: String, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onStep(-1) },
            modifier = Modifier.semantics { contentDescription = "$what weniger" }) { Text("−") }
        FilledTonalIconButton(onClick = { onStep(1) },
            modifier = Modifier.semantics { contentDescription = "$what mehr" }) { Text("+") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgeBandPicker(selected: AgeBand, onSelect: (AgeBand) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            colors = xyz.steier.sidetube.sideTextFieldColors(),
            value = ParentLabels.ageBand(selected),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AgeBand.entries.forEach { band ->
                DropdownMenuItem(
                    text = { Text(ParentLabels.ageBand(band)) },
                    onClick = { onSelect(band); open = false }
                )
            }
        }
    }
}
