// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.compose.ui.res.stringResource
import xyz.steier.sidetube.R
import xyz.steier.sidetube.Texte

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.steier.sidetube.PinResult
import xyz.steier.sidetube.PinStore

private const val PIN_LENGTH = 4

/**
 * Eingabe der Eltern-PIN. Der Ziffernblock ist gross genug fuer das kleine Display des
 * Sidephone und kommt ohne Tastatur aus – die Systemtastatur wuerde dort den halben
 * Bildschirm einnehmen.
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onComplete: (String) -> Unit
) {
    var entered by remember { mutableStateOf("") }

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            subtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(PIN_LENGTH) { index ->
                    val filled = index < entered.length
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                    )
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(14.dp))
            Keypad(enabled = enabled) { key ->
                when (key) {
                    Key.Backspace -> entered = entered.dropLast(1)
                    is Key.Digit -> {
                        if (entered.length < PIN_LENGTH) entered += key.value
                        if (entered.length == PIN_LENGTH) {
                            val complete = entered
                            entered = ""
                            onComplete(complete)
                        }
                    }
                }
            }
        }
    }
}

private sealed interface Key {
    data class Digit(val value: Char) : Key
    data object Backspace : Key
}

@Composable
private fun Keypad(enabled: Boolean, onKey: (Key) -> Unit) {
    val rows = listOf("123", "456", "789")
    // Auf dem Sidephone stehen 427 dp Hoehe zur Verfuegung; vier Reihen a 72 dp passen nicht
    // neben Titel und Punkte. Die Taste schrumpft deshalb auf schmalen Geraeten mit.
    val keySize = if (LocalConfiguration.current.screenHeightDp < 520) 58.dp else 72.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row {
                row.forEach { digit -> KeyButton(digit.toString(), enabled, size = keySize) { onKey(Key.Digit(digit)) } }
            }
        }
        Row {
            Spacer(Modifier.size(keySize))
            KeyButton("0", enabled, size = keySize) { onKey(Key.Digit('0')) }
            KeyButton("⌫", enabled, size = keySize, description = stringResource(R.string.pin_loeschen)) { onKey(Key.Backspace) }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    enabled: Boolean,
    size: Dp = 72.dp,
    description: String? = null,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size).semantics { description?.let { contentDescription = it } }
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

/** Zustand der PIN-Strecke, damit die Bildschirme selbst zustandslos bleiben. */
class PinFlow(private val store: PinStore, private val texte: Texte) {
    var error by mutableStateOf<String?>(null)
        private set

    // Beobachteter Zustand, nicht nur eine Variable: Sonst erfaehrt die Oberflaeche nichts davon
    // und bleibt bei "PIN festlegen" stehen, obwohl die Wiederholung dran ist.
    private var firstEntry by mutableStateOf<String?>(null)

    val isConfigured: Boolean get() = store.isConfigured

    /** Einrichtung: zweimal eingeben. Gibt `true` zurueck, sobald die PIN gesetzt ist. */
    fun setup(pin: String): Boolean {
        val first = firstEntry
        return if (first == null) {
            firstEntry = pin
            error = null
            false
        } else if (first == pin) {
            store.set(pin)
            firstEntry = null
            error = null
            true
        } else {
            firstEntry = null
            error = texte.get(R.string.pin_eingaben_ungleich)
            false
        }
    }

    val awaitingRepeat: Boolean get() = firstEntry != null

    fun verify(pin: String): Boolean = when (val result = store.verify(pin)) {
        is PinResult.Success -> { error = null; true }
        is PinResult.Wrong -> {
            error = texte.plural(R.plurals.pin_falsch, result.attemptsRemaining)
            false
        }
        is PinResult.LockedOut -> {
            error = texte.plural(R.plurals.pin_gesperrt, result.secondsRemaining.toInt())
            false
        }
    }
}
