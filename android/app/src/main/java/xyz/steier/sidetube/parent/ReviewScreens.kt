// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import xyz.steier.sidetube.LocalTexte
import xyz.steier.sidetube.R
import xyz.steier.sidetube.Texte

import androidx.compose.foundation.layout.Box
import xyz.steier.sidetube.core.model.WhitelistItemType
import xyz.steier.sidetube.kid.Vorschaubilder
import xyz.steier.sidetube.kid.VorschauKachel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import xyz.steier.sidetube.core.db.ReviewEventEntity
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.foundation.layout.width
import androidx.compose.ui.state.ToggleableState
import xyz.steier.sidetube.core.curation.Einzelpruefungsgrund
import xyz.steier.sidetube.core.curation.Sammelpruefung
import xyz.steier.sidetube.core.curation.Sammelwahl
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.steier.sidetube.core.curation.Kanaleinstufung
import xyz.steier.sidetube.core.db.CuratedSourceEntity
import xyz.steier.sidetube.core.db.WhitelistItemEntity
import xyz.steier.sidetube.core.model.ApprovalStatus
import xyz.steier.sidetube.core.model.ContentCategory
import xyz.steier.sidetube.core.model.SourceTrust
import xyz.steier.sidetube.core.repo.Approval

/** Die Prüfliste: was noch auf eine Entscheidung wartet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewQueueScreen(
    pending: List<WhitelistItemEntity>,
    onBack: () -> Unit,
    onOpen: (WhitelistItemEntity) -> Unit,
    onDiscardAll: () -> Unit,
    /** Wuensche des Kindes stehen zuerst (ADR 0001). */
    wuensche: List<xyz.steier.sidetube.core.db.WishEntity> = emptyList(),
    onOpenWish: (xyz.steier.sidetube.core.db.WishEntity) -> Unit = {},
    /** Sammelpruefung (ADR 0004): warum ein Eintrag einzeln geprueft werden muss, `null` = sammelbar. */
    sammelGrund: (WhitelistItemEntity) -> Einzelpruefungsgrund? = { null },
    onSammelFreigeben: (List<WhitelistItemEntity>, Sammelwahl) -> Unit = { _, _ -> },
    onSammelAblehnen: (List<WhitelistItemEntity>) -> Unit = {}
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    var auswahlModus by remember { mutableStateOf(false) }
    var ausgewaehlt by remember { mutableStateOf(setOf<String>()) }
    var sammelFreigabe by remember { mutableStateOf(false) }
    var sammelAblehnen by remember { mutableStateOf(false) }

    val gruende = pending.associate { it.id to sammelGrund(it) }
    val sammelbar = pending.filter { gruende[it.id] == null }
    // Nur, was noch offen und sammelbar ist: Die Liste kann sich unter der Auswahl aendern.
    val auswahl = sammelbar.filter { it.id in ausgewaehlt }
    fun beende() { auswahlModus = false; ausgewaehlt = emptySet() }
    LaunchedEffect(pending.isEmpty()) { if (pending.isEmpty()) beende() }

    Scaffold(
        topBar = {
            if (auswahlModus) TopAppBar(
                title = { Text(stringResource(R.string.review_ausgewaehlt, auswahl.size)) },
                navigationIcon = {
                    IconButton(onClick = ::beende) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.review_auswahl_beenden)) }
                }
            ) else TopAppBar(
                title = { Text(stringResource(R.string.review_pruefen_anzahl, pending.size + wuensche.size)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.zurueck)) }
                },
                actions = {
                    if (pending.isNotEmpty()) {
                        TextButton(onClick = { auswahlModus = true }) { Text(stringResource(R.string.review_auswaehlen)) }
                        IconButton(onClick = { confirmDiscard = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.review_alle_verwerfen))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (auswahlModus) SammelLeiste(auswahl.size,
                onFreigeben = { sammelFreigabe = true }, onAblehnen = { sammelAblehnen = true })
        }
    ) { padding ->
        if (pending.isEmpty() && wuensche.isEmpty()) {
            EmptyHint(
                title = stringResource(R.string.review_nichts),
                text = stringResource(R.string.review_nichts_text),
                modifier = Modifier.padding(padding)
            )
        } else if (auswahlModus) {
            // „Alle auswählen" und der Hinweis stehen fest ueber der Liste, nicht als erste Zeile:
            // So bleiben sie beim Scrollen sichtbar, und die Liste springt beim Umschalten nicht.
            Column(Modifier.fillMaxSize().padding(padding)) {
                AlleAuswaehlen(
                    gewaehlt = auswahl.size, moeglich = sammelbar.size,
                    hinweis = ParentLabels.einzelpruefungHinweis(gruende.values.filterNotNull(), LocalTexte.current),
                    onChange = { alle -> ausgewaehlt = if (alle) sammelbar.map { it.id }.toSet() else emptySet() }
                )
                HorizontalDivider()
                // Wuensche sind keine Eintraege der Liste; sie werden weiter einzeln beantwortet.
                LazyColumn(Modifier.weight(1f)) {
                    items(pending, key = { it.id }) { item ->
                        val grund = gruende[item.id]
                        val an = item.id in ausgewaehlt && grund == null
                        ListItem(
                            leadingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = an, onCheckedChange = null, enabled = grund == null)
                                    Vorschau(item)
                                }
                            },
                            headlineContent = { Text(item.title, maxLines = 2) },
                            supportingContent = {
                                if (grund == null) Text(hints(item), maxLines = 1)
                                else Text(stringResource(R.string.review_einzeln, ParentLabels.einzelpruefung(grund)), maxLines = 1,
                                    color = MaterialTheme.colorScheme.tertiary)
                            },
                            // Nicht Sammelbares oeffnet die Einzelpruefung – der Weg, den es ohnehin braucht.
                            modifier = if (grund == null) Modifier.toggleable(value = an, role = Role.Checkbox,
                                onValueChange = { ausgewaehlt = if (it) ausgewaehlt + item.id else ausgewaehlt - item.id })
                            else Modifier.clickable { onOpen(item) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                if (wuensche.isNotEmpty()) {
                    item(key = "wuensche-kopf") { ListenKopf(stringResource(R.string.review_wuensche_anzahl, wuensche.size)) }
                    items(wuensche, key = { "wunsch-" + it.id }) { wunsch ->
                        WunschListItem(wunsch, onOpen = { onOpenWish(wunsch) })
                        HorizontalDivider()
                    }
                    if (pending.isNotEmpty()) item(key = "inhalte-kopf") { ListenKopf(stringResource(R.string.review_inhalte_anzahl, pending.size)) }
                }
                items(pending, key = { it.id }) { item ->
                    ListItem(
                        leadingContent = { Vorschau(item) },
                        headlineContent = { Text(item.title, maxLines = 2) },
                        supportingContent = { Text(hints(item)) },
                        modifier = Modifier.clickable { onOpen(item) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.review_verwerfen_titel)) },
            text = { Text(pluralStringResource(R.plurals.review_verwerfen_text, pending.size, pending.size)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onDiscardAll() }) {
                    Text(stringResource(R.string.review_verwerfen_anzahl, pending.size))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.abbrechen)) } }
        )
    }

    if (sammelFreigabe) {
        val gewaehlt = auswahl
        SammelFreigabeDialog(
            anzahl = gewaehlt.size,
            kanaele = gewaehlt.count { Sammelpruefung.istKanal(it) },
            onDismiss = { sammelFreigabe = false },
            onConfirm = { wahl -> sammelFreigabe = false; onSammelFreigeben(gewaehlt, wahl); beende() }
        )
    }
    if (sammelAblehnen) {
        val gewaehlt = auswahl
        SammelAblehnenDialog(
            anzahl = gewaehlt.size,
            onDismiss = { sammelAblehnen = false },
            onConfirm = { sammelAblehnen = false; onSammelAblehnen(gewaehlt); beende() }
        )
    }
}

/** Kopf der Auswahl: „Alle auswählen" und, wenn noetig, warum manche einzeln bleiben (ADR 0004). */
@Composable
private fun AlleAuswaehlen(gewaehlt: Int, moeglich: Int, hinweis: String?, onChange: (Boolean) -> Unit) {
    val alle = moeglich > 0 && gewaehlt == moeglich
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = alle, enabled = moeglich > 0, role = Role.Checkbox, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TriStateCheckbox(
                state = when { alle -> ToggleableState.On; gewaehlt > 0 -> ToggleableState.Indeterminate; else -> ToggleableState.Off },
                onClick = null, enabled = moeglich > 0
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.review_alle_auswaehlen, moeglich), style = MaterialTheme.typography.bodyMedium)
        }
        hinweis?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun ListenKopf(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun hints(item: WhitelistItemEntity): String = listOfNotNull(
    item.channelTitle,
    stringResource(R.string.review_ab_alter, item.ageMin),
    item.sensitiveTopics.takeIf { it.isNotBlank() }?.let { stringResource(R.string.review_hinweise, it) },
    stringResource(R.string.review_short).takeIf { item.isShort },
    stringResource(R.string.review_live).takeIf { item.isLive }
).joinToString(" · ")

/**
 * Entscheidung über einen Eintrag. Dieselbe Maske dient dem Prüfen neuer Kandidaten und dem
 * Nachbessern bereits freigegebener – wer sich anders entscheidet, soll das ohne Umweg können.
 *
 * Aufbau wie auf iOS: Einordnung, dann die Entscheidungen, darunter der Verlauf. Die Knöpfe
 * stehen im scrollbaren Inhalt untereinander statt in der Knopfleiste des Dialogs: Drei
 * nebeneinander passen auf die 320 dp des SidePhone nicht und wurden dort abgeschnitten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDecisionSheet(
    item: WhitelistItemEntity,
    history: List<ReviewEventEntity>,
    onDismiss: () -> Unit,
    onApprove: (Approval) -> Unit,
    onReject: () -> Unit,
    onLater: () -> Unit,
    onBackToReview: () -> Unit,
    /** Quelle eines Kanal-Eintrags; traegt die Vorauswahl der Stufe (ADR 0003). */
    quelle: CuratedSourceEntity? = null,
    /** Kanaele werden mit Stufe freigegeben statt mit [onApprove]: Einstufung, Hoechstalter, Anmerkung. */
    onApproveKanal: (Kanaleinstufung, Int?, String?) -> Unit = { _, _, _ -> }
) {
    val approved = ApprovalStatus.from(item.approvalStatus) == ApprovalStatus.APPROVED
    val istKanal = item.type == WhitelistItemType.CHANNEL.name
    var einstufung by remember(item.id) { mutableStateOf(Kanaleinstufung.vorauswahl(quelle, item)) }
    var ageMin by remember { mutableStateOf(item.ageMin) }
    var ageMaxEnabled by remember { mutableStateOf(item.ageMax != null) }
    var ageMax by remember { mutableStateOf(item.ageMax ?: 12) }
    var category by remember { mutableStateOf(item.category) }
    var notes by remember { mutableStateOf(item.parentNotes.orEmpty()) }

    fun approval() = ReviewInput(ageMin, ageMaxEnabled, ageMax, category, notes).toApproval()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (approved) R.string.review_bearbeiten else R.string.pruefen)) },
        text = {
            // Auf 427 dp Hoehe passt der Inhalt nicht immer; ohne Scrollen ueberlappen die
            // Abschnitte einander - genau das war am Geraet zu sehen.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Wer freigibt, soll sehen, was er freigibt – wie auf iOS.
                val kanal = item.type == WhitelistItemType.CHANNEL.name
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    VorschauKachel(Vorschaubilder.adresse(item), kanal, breite = if (kanal) 56.dp else 144.dp)
                }
                Spacer(Modifier.height(8.dp))
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                item.channelTitle?.takeIf { it != item.title }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                if (item.sensitiveTopics.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.review_hinweise_filter, item.sensitiveTopics),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.height(12.dp))

                // Ein Kanal wird mit Stufe, Alter und Kategorie freigegeben (ADR 0003) – dieselben
                // Felder wie in der Vorschau, damit beide Wege dasselbe fragen.
                val gesperrt = istKanal && einstufung.ergebnis == Kanaleinstufung.Ergebnis.NICHT_AUFNEHMEN
                if (istKanal) {
                    KanalEinstufungFelder(einstufung, onChange = {
                        einstufung = it
                        if (ageMax < it.effektivesMindestalter) ageMax = it.effektivesMindestalter
                    })
                } else {
                    AgeStepper(stringResource(R.string.ab_jahren, ageMin), stringResource(R.string.mindestalter),
                        onMinus = { if (ageMin > 3) ageMin-- },
                        onPlus = { if (ageMin < 16) { ageMin++; if (ageMax < ageMin) ageMax = ageMin } })
                }

                // Hoechstalter wie auf iOS: frei waehlbar, aber nie unter dem Mindestalter.
                val untergrenze = if (istKanal) einstufung.effektivesMindestalter else ageMin
                if (!gesperrt) Row(
                    Modifier.fillMaxWidth().toggleable(
                        value = ageMaxEnabled, role = Role.Switch,
                        onValueChange = { ageMaxEnabled = it; if (ageMax < untergrenze) ageMax = untergrenze }
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.hoechstalter), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = ageMaxEnabled, onCheckedChange = null)
                }
                if (ageMaxEnabled && !gesperrt) {
                    AgeStepper(stringResource(R.string.bis_jahre, ageMax), stringResource(R.string.hoechstalter),
                        onMinus = { if (ageMax > untergrenze) ageMax-- },
                        onPlus = { if (ageMax < 17) ageMax++ })
                }

                if (!istKanal) {
                    Spacer(Modifier.height(8.dp))
                    // Aufklappliste statt zehn Auswahlknoepfe: Die passen auf dem kleinen Display nicht.
                    CategoryPicker(selected = category, onSelect = { category = it })
                    ContentCategory.from(category)?.takeIf { it.minimumAge > ageMin }?.let {
                        Text(stringResource(R.string.review_kategorie_hebt_an, title(it), it.minimumAge),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    colors = xyz.steier.sidetube.sideTextFieldColors(),
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.anmerkung_freiwillig)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (approved) R.string.review_verlauf_hinweis else R.string.review_filter_hinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (istKanal) onApproveKanal(einstufung, ageMax.takeIf { ageMaxEnabled }, notes)
                        else onApprove(approval())
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(when {
                        gesperrt -> stringResource(R.string.kanal_sperren)
                        approved -> stringResource(R.string.aenderungen_sichern)
                        else -> stringResource(R.string.freigeben)
                    })
                }
                if (approved) {
                    OutlinedButton(onClick = onBackToReview, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.zurueck_zur_pruefung))
                    }
                    OutlinedButton(onClick = onReject, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ablehnen), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    OutlinedButton(onClick = onReject, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ablehnen), color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.spaeter))
                    }
                }

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

@Composable
internal fun AgeStepper(label: String, what: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val senken = stringResource(R.string.stepper_senken, what)
    val erhoehen = stringResource(R.string.stepper_erhoehen, what)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onMinus, modifier = Modifier.semantics { contentDescription = senken }) { Text("−") }
        TextButton(onClick = onPlus, modifier = Modifier.semantics { contentDescription = erhoehen }) { Text("+") }
    }
}

/**
 * Eingaben des Dialogs, getrennt pruefbar: Das Mindestalter steigt auf das der Kategorie, ein
 * Hoechstalter gilt nur eingeschaltet und nie unter dem Mindestalter.
 */
internal data class ReviewInput(
    val ageMin: Int,
    val ageMaxEnabled: Boolean,
    val ageMax: Int,
    val category: String?,
    val notes: String
) {
    fun toApproval(): Approval {
        val effectiveMin = maxOf(ageMin, ContentCategory.from(category)?.minimumAge ?: 0)
        return Approval(
            ageMin = effectiveMin,
            ageMax = if (ageMaxEnabled) maxOf(ageMax, effectiveMin) else null,
            category = category,
            parentNotes = notes.takeIf { it.isNotBlank() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryPicker(selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = selected?.let { id -> ContentCategory.from(id)?.let { title(it) } } ?: stringResource(R.string.keine)

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            colors = xyz.steier.sidetube.sideTextFieldColors(),
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.kategorie)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.keine)) }, onClick = { onSelect(null); open = false })
            ContentCategory.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(title(entry)) },
                    onClick = { onSelect(entry.id); open = false }
                )
            }
        }
    }
}

@Composable
private fun title(category: ContentCategory): String = ParentLabels.category(category)

/** Quellen und ihre Sicherheitsstufen – auch die, die das Register nicht kennt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceTrustScreen(
    sources: List<CuratedSourceEntity>,
    onBack: () -> Unit,
    onSetTrust: (CuratedSourceEntity, SourceTrust) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quellen)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.zurueck)) }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.quellen_erklaerung),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(sources, key = { it.channelId }) { source ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(source.title, style = MaterialTheme.typography.titleSmall)
                    source.notes?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.height(4.dp))
                    SourceTrust.entries.forEach { trust ->
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onSetTrust(source, trust) }) {
                            RadioButton(
                                selected = source.trust == trust.id,
                                onClick = { onSetTrust(source, trust) }
                            )
                            Text(ParentLabels.trust(trust), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
