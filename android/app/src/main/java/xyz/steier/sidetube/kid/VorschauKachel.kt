// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.
package xyz.steier.sidetube.kid

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Kachel mit Vorschaubild, sonst Platzhalter. Kinderliste, Kanalansicht und die Listen im
 * Elternbereich nutzen dieselbe, damit kein Ort ohne den [Vorschaubilder]-Lader bleibt.
 * Kanaele rund, Videos im Querformat.
 */
@Composable
fun VorschauKachel(url: String?, istKanal: Boolean, breite: Dp, hoehe: Dp = if (istKanal) breite else breite * 9 / 16) {
    val breitePx = with(LocalDensity.current) { breite.roundToPx() }
    val bild by produceState(url?.let(Vorschaubilder::ausDemSpeicher), url) {
        if (url != null && value == null) value = Vorschaubilder.lade(url, breitePx)
    }
    Box(
        Modifier
            .size(width = breite, height = hoehe)
            .clip(if (istKanal) CircleShape else RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        val geladen = bild
        if (geladen != null) {
            Image(
                bitmap = geladen,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(
                if (istKanal) Icons.Default.Search else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
