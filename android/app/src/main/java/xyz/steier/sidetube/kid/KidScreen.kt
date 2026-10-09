// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import xyz.steier.sidetube.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import xyz.steier.sidetube.core.input.T9
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/**
 * Der Kindermodus. Eine Liste, ein Fokus, keine verborgenen Wege: Was mit den Tasten
 * erreichbar ist, ist es auch mit dem Finger – und umgekehrt.
 */
@Composable
fun KidView(
    state: KidState,
    onFocus: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onQuery: (String) -> Unit,
    onHome: () -> Unit,
    onParent: () -> Unit,
    onSelectProfile: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onSelectSegment: (LibrarySegment) -> Unit = {},
    onWheelKey: (Int) -> Unit = {}
) {
    val listState = rememberLazyListState()

    // Ohne Freigaben steht auf der Startseite nur „Meine Wünsche“; der Hinweis darüber ist ein Listeneintrag.
    val leerHinweis = state.screen is KidScreen.Home && state.rows.isNotEmpty() && state.rows.all { it.action.istWunschZeile }

    // Die Auswahl bleibt sichtbar, auch wenn sie mit Tasten aus dem Bild wandert.
    LaunchedEffect(state.focusIndex, state.rows.size) {
        if (state.rows.isNotEmpty()) {
            listState.animateScrollToItem(state.focusIndex.coerceIn(0, state.rows.lastIndex) + if (leerHinweis) 1 else 0)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header(state = state, onHome = onHome, onParent = onParent, onSelectProfile = onSelectProfile, onSearch = onSearch)

        val wheelFocused = state.screen.hatRad && state.rad.aktiv
        if (state.screen is KidScreen.Search) {
            // Anzeige statt Eingabefeld: Ein fokussiertes Feld holt die Bildschirmtastatur, und
            // die verdeckt auf diesem Display rund 60 Prozent. Buchstaben kommen vom Rad (Ring
            // oder Finger); Zeichen einer Tastatur kommen ueber den Tastenkanal.
            if (state.rad.t9) SearchDisplay(state.query)
            else {
                val treffer = state.rows.any { !it.action.istWunschZeile }
                RadEingabe(state.rad.anzeige)
                Buchstabenrad(state.rad, hasHits = state.rows.isNotEmpty(), onTap = onWheelKey,
                    unten = stringResource(if (treffer) R.string.kid_rad_treffer else R.string.kid_rad_wunsch))
            }
        }
        if (state.screen is KidScreen.ThemaWunsch) {
            // Das freie Rad: alle Buchstaben, auch wenn es dazu noch nichts gibt.
            RadEingabe(state.rad.anzeige)
            Buchstabenrad(state.rad, hasHits = true, onTap = onWheelKey, unten = stringResource(R.string.kid_rad_schicken))
        }
        (state.screen as? KidScreen.NeueFolgeAnsicht)?.let { FolgenKopf(it.folge) }

        when {
            state.isLoading && state.rows.isEmpty() ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            state.rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text(
                    stringResource(emptyText(state)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (leerHinweis) {
                    item(key = "home-empty") {
                        Text(
                            stringResource(R.string.kid_leer_startseite_wunsch),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                // Die Abschnittsueberschrift gehoert zum Eintrag der ersten Zeile: So bleibt der
                // Listenplatz gleich dem Fokusplatz, und das Mitscrollen trifft die richtige Zeile.
                itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                    Column {
                        val section = row.section
                        if (section != null && state.rows.getOrNull(index - 1)?.section != section) {
                            SectionHeader(section)
                        }
                        when (row.action) {
                            KidAction.NextSegment -> SegmentRow(
                                selected = (state.screen as? KidScreen.Library)?.segment ?: LibrarySegment.VIDEOS,
                                focused = index == state.focusIndex,
                                onSelect = { segment -> onFocus(index); onSelectSegment(segment) }
                            )
                            else -> if (row.action.istWunschZeile) WunschZeile(
                                row = row,
                                focused = index == state.focusIndex && !wheelFocused,
                                onClick = { onActivate(index) },
                                onFocus = { onFocus(index) }
                            ) else KidRowView(
                                row = row,
                                focused = index == state.focusIndex && !wheelFocused,
                                onClick = { onActivate(index) },
                                onFocus = { onFocus(index) }
                            )
                        }
                    }
                }
                if (state.screen is KidScreen.Library && state.rows.size == 1) {
                    item(key = "library-empty") {
                        Text(
                            stringResource(R.string.kid_leer_mediathek),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun emptyText(state: KidState): Int = when {
    state.screen is KidScreen.Search && T9.istZiffernfolge(state.query) && !T9.hatInhalt(state.query) ->
        R.string.kid_leer_t9_anleitung
    state.screen is KidScreen.Search && T9.istZiffernfolge(state.query) -> R.string.kid_leer_nichts_gefunden
    state.screen is KidScreen.Search && state.query.isEmpty() -> R.string.kid_leer_rad_anleitung
    state.screen is KidScreen.Search -> R.string.kid_leer_nichts_gefunden
    state.screen is KidScreen.Playlist -> R.string.kid_leer_playlist_offline
    else -> R.string.kid_leer_startseite
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Header(
    state: KidState,
    onHome: () -> Unit,
    onParent: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onSearch: () -> Unit
) {
    var showProfiles by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // In der Kanal- und Playlistansicht steht das Bild vor dem Namen – wie auf iOS.
        if (state.screen is KidScreen.Channel) {
            VorschauKachel(state.titelBild, istKanal = true, breite = 28.dp)
            Spacer(Modifier.width(8.dp))
        }
        if (state.screen is KidScreen.Playlist) {
            VorschauKachel(state.titelBild, istKanal = false, breite = 40.dp)
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f)) {
            Text(
                state.title.ifBlank { stringResource(R.string.app_name) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Ein Antippen geht zur Startseite; nur bei mehreren Profilen oeffnet ein
                // laengeres Antippen den Umschalter, damit ein Kind ihn nicht aus Versehen trifft.
                modifier = Modifier.combinedClickable(
                    onClick = onHome,
                    onLongClick = { if (state.profiles.size > 1) showProfiles = true }
                )
            )
            if (state.profiles.size > 1) {
                DropdownMenu(expanded = showProfiles, onDismissRequest = { showProfiles = false }) {
                    for (profile in state.profiles) {
                        DropdownMenuItem(
                            text = { Text(profile.name) },
                            onClick = { showProfiles = false; onSelectProfile(profile.id) },
                            leadingIcon = if (profile.id == state.profile?.id) {
                                { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
        }
        state.remainingMinutes?.let {
            Text(stringResource(R.string.kid_restminuten, it), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            Spacer(Modifier.width(8.dp))
        }
        // Die Lupe ist der Weg fuer den Finger; am SP-01 fuehrt die Taste rechts dorthin.
        if (state.screen !is KidScreen.Search) {
            IconButton(onClick = onSearch, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.kid_suchen), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        IconButton(onClick = onParent, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.kid_einstellungen), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun KidRowView(row: KidRow, focused: Boolean, onClick: () -> Unit, onFocus: () -> Unit) {
    val form = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(form)
            // Fokus nach SideUI (ADR 0002): eigene Flaeche plus heller Rahmen, nie gelb –
            // Gelb heisst ACTIVE. Nicht nur Farbe: Rahmen und Flaeche tragen ihn.
            .then(if (focused) Modifier.background(SideFokus.Flaeche).border(3.dp, SideFokus.Rand, form) else Modifier)
            .clickable { onFocus(); onClick() }
            .semantics { selected = focused }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(row)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            row.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1)
            }
        }
    }
}

/**
 * Zeile der Wünsche (ADR 0001). Fokus nach SideUI: eigene Fläche plus heller Rahmen, **nie gelb**
 * – wie alle Zeilen seit 02.10.2026. Antworten der Eltern dürfen mehrere Zeilen haben; reine
 * Hinweise sind gedämpft.
 */
@Composable
private fun WunschZeile(row: KidRow, focused: Boolean, onClick: () -> Unit, onFocus: () -> Unit) {
    val form = RoundedCornerShape(10.dp)
    val gedaempft = row.action == KidAction.Info
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(form)
            .background(if (focused) SideFokus.Flaeche else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .then(if (focused) Modifier.border(3.dp, SideFokus.Rand, form) else Modifier)
            .clickable { onFocus(); onClick() }
            .semantics { selected = focused }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.thumbnailUrl != null) {
            Box {
                VorschauKachel(row.thumbnailUrl, istKanal = false, breite = 64.dp, hoehe = 36.dp)
                if (row.action is KidAction.OpenNeueFolge) {
                    // Gesperrt: sichtbar, aber nicht abspielbar.
                    Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.kid_gesperrt),
                        tint = Color.White, modifier = Modifier.align(Alignment.Center).size(18.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp)).padding(2.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (gedaempft) 0.7f else 1f))
            row.subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            row.detail?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Kopf einer gesperrten neuen Folge: Bild, Titel, warum sie nicht abspielbar ist. */
@Composable
private fun FolgenKopf(folge: xyz.steier.sidetube.core.curation.NeueFolge) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            VorschauKachel(Vorschaubilder.fuerVideo(folge.videoId), istKanal = false, breite = 208.dp)
            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.kid_gesperrt), tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp)).padding(6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(folge.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center)
        Text(stringResource(R.string.kid_neue_folge_hinweis, folge.channelTitle),
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun Thumb(row: KidRow) {
    if (row.action == KidAction.OpenLibrary) {
        Box(
            Modifier.size(width = 64.dp, height = 36.dp).clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        return
    }
    VorschauKachel(row.thumbnailUrl, row.isChannel, breite = if (row.isChannel) 44.dp else 64.dp,
        hoehe = if (row.isChannel) 44.dp else 36.dp)
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp)
    )
}

/**
 * Umschalter der Mediathek. Fuer die Tasten ist er **eine** Fokusstelle: Mitte schaltet zum
 * naechsten Bereich weiter (Kanaele → Videos → Sendungen). Mit dem Finger wird direkt gewaehlt.
 */
@Composable
private fun SegmentRow(selected: LibrarySegment, focused: Boolean, onSelect: (LibrarySegment) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            // Fokus neutral (ADR 0002); der gewählte Bereich ist ACTIVE und deshalb gelb.
            .then(if (focused) Modifier.background(SideFokus.Flaeche).border(3.dp, SideFokus.Rand, RoundedCornerShape(10.dp)) else Modifier)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (segment in LibrarySegment.entries) {
            val active = segment == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .clickable { onSelect(segment) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(segment.titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Zeigt die Sucheingabe an, ohne Fokus zu nehmen.
 *
 * Getippte Ziffern erscheinen als kleine Tasten mit ihren Buchstaben darunter – so sieht ein
 * Kind, dass „2" fuer a, b, c steht und was es schon getippt hat. Leer steht dort, wie es geht.
 */
@Composable
private fun SearchDisplay(query: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            // Ein Anzeigefeld, kein Fokus und nichts Aktives: neutral, nicht gelb (ADR 0001).
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            Spacer(Modifier.width(8.dp))
            when {
                query.isEmpty() -> Text(
                    stringResource(R.string.kid_t9_hinweis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2
                )
                T9.istZiffernfolge(query) -> DigitKeys(query)
                else -> Text(query, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (T9.istZiffernfolge(query)) {
            Text(
                stringResource(R.string.kid_t9_luecke_hinweis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 32.dp, top = 2.dp)
            )
        }
    }
}

/** Die getippten Ziffern als Tasten; bei langen Folgen nur das Ende, damit es in eine Zeile passt. */
@Composable
private fun DigitKeys(digits: String) {
    val shown = digits.takeLast(MAX_KEYS)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (digits.length > shown.length) Text("…", style = MaterialTheme.typography.bodyLarge)
        for (digit in shown) {
            Column(
                Modifier
                    .width(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(digit.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (digit == '0') "–" else T9.buchstaben(digit),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

private const val MAX_KEYS = 8
