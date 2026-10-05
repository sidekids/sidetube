// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Feste dunkle Farbwelt. Bewusst kein dynamisches Farbschema: Die Oberflaeche muss auf dem
 * kleinen Sidephone-Display berechenbar kontrastreich bleiben.
 *
 * **Gelb bedeutet ACTIVE, genau ein Wert: `#FFB74D`** (SideUI ADR 0001, 0016). Das Markengelb
 * `#FBBB1B` gehoert Logo, App-Symbol und Store-Material, nicht der Oberflaeche. Der Fokus ist
 * nie gelb, sondern eine eigene Flaeche mit hellem Rahmen: [xyz.steier.sidetube.kid.SideFokus].
 */
internal val SideActive = Color(0xFFFFB74D)
private val brandDark = Color(0xFF090A0C)
private val brandLight = Color(0xFFF6F4EF)

@Composable
fun SideTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SideActive,
            onPrimary = brandDark,
            background = brandDark,
            onBackground = brandLight,
            surface = brandDark,
            onSurface = brandLight
        ),
        content = content
    )
}

/**
 * Eingabefelder nach SideUI: Der Fokus ist ein heller Rahmen, nie gelb (ADR 0002). Ohne das nimmt
 * Material fuer Rahmen, Beschriftung und Schreibmarke `primary` – das ACTIVE-Gelb.
 */
@Composable
fun sideTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = xyz.steier.sidetube.kid.SideFokus.Rand,
    focusedLabelColor = brandLight,
    cursorColor = brandLight
)
