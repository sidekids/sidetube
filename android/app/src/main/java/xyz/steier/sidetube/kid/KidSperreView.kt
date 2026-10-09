// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.kid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import xyz.steier.sidetube.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Dunkles Vollbild über dem Kindermodus; nur die PIN der Eltern führt heraus (iOS: `KidOverlayView`).
 * Der eine Knopf steht immer im Fokus: Mitte am Ring tut dasselbe wie der Finger. Berührungen
 * darunter kommen nicht durch.
 */
@Composable
fun KidSperreView(sperre: KidSperre, weiterAb: String?, onElternPin: () -> Unit) {
    val titel = stringResource(KidSperreText.titelRes(sperre))
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            // Schluckt jede Berührung, damit nichts darunter reagiert; ohne Hervorhebung.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .semantics { testTag = "sperre." + sperre.name; contentDescription = titel },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(symbol(sperre), contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(64.dp))
            Text(titel, style = MaterialTheme.typography.headlineMedium, color = Color.White,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(stringResource(KidSperreText.textRes(sperre, weiterAb), weiterAb ?: ""), style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            // Wie die Endkarte: Fokus als eigene Fläche mit hellem Rahmen, nie gelb (SideUI ADR 0002).
            OutlinedButton(
                onClick = onElternPin,
                modifier = Modifier.heightIn(min = 48.dp).semantics { testTag = "sperre.elternPin" },
                border = BorderStroke(3.dp, SideFokus.Rand),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = SideFokus.Flaeche, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sperre_fuer_eltern), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private fun symbol(sperre: KidSperre) = when (sperre) {
    KidSperre.GUTE_NACHT -> Icons.Default.NightsStay
    KidSperre.ZEIT_UM -> Icons.Default.HourglassBottom
    KidSperre.RUHEZEIT -> Icons.Default.Bedtime
    KidSperre.SPEICHERFEHLER, KidSperre.UNTERBROCHEN -> Icons.Default.Warning
}
