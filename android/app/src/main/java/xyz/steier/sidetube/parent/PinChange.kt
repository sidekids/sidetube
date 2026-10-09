// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.compose.ui.res.stringResource
import xyz.steier.sidetube.LocalTexte
import xyz.steier.sidetube.R
import xyz.steier.sidetube.Texte

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import xyz.steier.sidetube.PinResult
import xyz.steier.sidetube.PinStore

/**
 * PIN aendern in drei Schritten wie auf iOS: aktuelle PIN, neue PIN, Wiederholung.
 *
 * Die aktuelle PIN wird erst am Ende geprueft, mit derselben Fehlversuchssperre wie beim
 * Entsperren - sonst liesse sich ueber diese Maske die Sperre umgehen. Pruefen und Setzen sind
 * hereingereicht, damit der Ablauf ohne verschluesselten Speicher testbar bleibt.
 */
class PinChangeFlow(
    private val texte: Texte,
    private val verify: (String) -> PinResult,
    private val set: (String) -> Unit
) {
    enum class Step { OLD, NEW, CONFIRM }

    var step by mutableStateOf(Step.OLD)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var oldPin = ""
    private var newPin = ""

    val title: String get() = when (step) {
        Step.OLD -> texte.get(R.string.pin_aktuelle)
        Step.NEW -> texte.get(R.string.pin_neue)
        Step.CONFIRM -> texte.get(R.string.pin_neue_wiederholen)
    }

    /** Gibt `true` zurueck, sobald die neue PIN gespeichert ist. */
    fun handle(pin: String): Boolean {
        when (step) {
            Step.OLD -> { oldPin = pin; message = null; step = Step.NEW }
            Step.NEW -> { newPin = pin; step = Step.CONFIRM }
            Step.CONFIRM -> {
                if (pin != newPin) {
                    message = texte.get(R.string.pin_neue_ungleich)
                    step = Step.NEW
                    return false
                }
                return when (val result = verify(oldPin)) {
                    PinResult.Success -> {
                        set(newPin)
                        oldPin = ""; newPin = ""
                        true
                    }
                    is PinResult.Wrong -> {
                        message = texte.plural(R.plurals.pin_aktuelle_falsch, result.attemptsRemaining)
                        step = Step.OLD
                        false
                    }
                    is PinResult.LockedOut -> {
                        message = texte.get(R.string.pin_gesperrt_kurz, result.secondsRemaining)
                        step = Step.OLD
                        false
                    }
                }
            }
        }
        return false
    }
}

/** Eigene Seite statt Dialog: Der Ziffernblock braucht auf dem SidePhone die ganze Hoehe. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePinScreen(store: PinStore, onCancel: () -> Unit, onDone: () -> Unit) {
    val texte = LocalTexte.current
    val flow = remember { PinChangeFlow(texte, store::verify, store::set) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pin_aendern)) },
                navigationIcon = { TextButton(onClick = onCancel) { Text(stringResource(R.string.abbrechen)) } }
            )
        }
    ) { padding ->
        PinPad(
            title = flow.title,
            subtitle = stringResource(R.string.pin_vier_ziffern),
            error = flow.message,
            modifier = Modifier.padding(padding),
            onComplete = { pin -> if (flow.handle(pin)) onDone() }
        )
    }
}
