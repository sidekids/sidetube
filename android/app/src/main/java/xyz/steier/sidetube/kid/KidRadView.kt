// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.steier.sidetube.core.input.RadTaste

/**
 * Fokus nach SideUI (ADR 0002, 0016): eigene Flaeche plus heller Rahmen, **nie gelb**. Die Werte
 * entsprechen `SideSurfaceFocused` und `SideTextSecondary` aus SidePlay. Die Trefferzeilen
 * darunter tragen noch den alten gelben Rahmen – offen in `konformitaet.md`.
 */
internal object SideFokus {
    val Flaeche = Color(0xFF262E38)
    val Rand = Color(0xFFA8B1BD)
}
private val FokusFlaeche = SideFokus.Flaeche
private val FokusRand = SideFokus.Rand

/** Was schon gewählt ist, groß und mit Wortanfängen in Großbuchstaben. */
@Composable
internal fun RadEingabe(anzeige: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.width(8.dp))
        Text(
            if (anzeige.isEmpty()) "…" else wortanfaengeGross(anzeige).let { if (it.length > 16) "…" + it.takeLast(15) else it } + "…",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { contentDescription = "Gesucht: ${anzeige.ifEmpty { "noch nichts" }}" }
        )
    }
}

/**
 * Das Buchstabenrad: eine Reihe mit nur den Buchstaben, mit denen es weitergeht, dazu Lücke und
 * Löschen. Am Ring wählen links/rechts, die Mitte nimmt; mit dem Finger antippen.
 */
@Composable
internal fun Buchstabenrad(rad: RadZustand, hasHits: Boolean, onTap: (Int) -> Unit, unten: String = "Treffer") {
    val listState = rememberLazyListState()
    // Das gewählte Feld bleibt in der Mitte der Reihe, damit man sieht, was links und rechts kommt.
    LaunchedEffect(rad.fokus, rad.tasten.size) {
        if (rad.tasten.isNotEmpty()) listState.animateScrollToItem((rad.fokus - 2).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            itemsIndexed(rad.tasten) { index, taste ->
                RadFeld(taste, focused = rad.aktiv && index == rad.fokus, onClick = { onTap(index) })
            }
        }
        Text(
            when {
                !rad.aktiv -> "▲ zurück zu den Buchstaben"
                hasHits -> "◀ ▶ wählen · Mitte nimmt · ▼ $unten"
                else -> "◀ ▶ wählen · Mitte nimmt"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            maxLines = 1,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
        )
    }
}

@Composable
private fun RadFeld(taste: RadTaste, focused: Boolean, onClick: () -> Unit) {
    val form = RoundedCornerShape(12.dp)
    val (gross, klein, beschreibung) = when (taste) {
        is RadTaste.Buchstabe -> Triple(taste.zeichen.beschriftung, null, taste.zeichen.beschriftung)
        RadTaste.Luecke -> Triple("␣", "Lücke", "Lücke")
        RadTaste.Loeschen -> Triple("⌫", "Löschen", "Löschen")
    }
    Column(
        Modifier
            .size(width = if (klein == null && gross.length <= 1) 52.dp else 72.dp, height = 64.dp)
            .clip(form)
            .background(if (focused) FokusFlaeche else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .then(if (focused) Modifier.border(3.dp, FokusRand, form) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = beschreibung; selected = focused },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(gross, fontSize = if (klein == null) 28.sp else 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (klein != null) Text(klein, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun wortanfaengeGross(text: String): String =
    text.split(' ').joinToString(" ") { wort -> wort.replaceFirstChar { if (it == 'ß') it else it.titlecaseChar() } }
