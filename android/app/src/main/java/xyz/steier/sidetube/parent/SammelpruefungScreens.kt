// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xyz.steier.sidetube.core.curation.Kanaleinstufung
import xyz.steier.sidetube.core.curation.KategorieWahl
import xyz.steier.sidetube.core.curation.Sammelwahl
import xyz.steier.sidetube.core.model.ContentCategory
import xyz.steier.sidetube.core.model.SourceTrust

/**
 * Knopfleiste der Auswahl (ADR 0004). Zwei Knoepfe nebeneinander mit schmalem Innenabstand: So passt
 * „Freigeben (12)" auch auf die 320 dp des SidePhone, und beide bleiben ohne Scrollen sichtbar.
 */
@Composable
internal fun SammelLeiste(anzahl: Int, onFreigeben: () -> Unit, onAblehnen: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            val schmal = PaddingValues(horizontal = 8.dp)
            Button(onClick = onFreigeben, enabled = anzahl > 0, contentPadding = schmal, modifier = Modifier.weight(1f)) {
                Text("Freigeben ($anzahl)", maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onAblehnen, enabled = anzahl > 0, contentPadding = schmal, modifier = Modifier.weight(1f)) {
                Text("Ablehnen ($anzahl)", maxLines = 1,
                    color = if (anzahl > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            }
        }
    }
}

/**
 * Sammel-Maske: einmal fuer alle fragen, was sonst je Eintrag gefragt wird. Voreingestellt ist
 * „wie vorgeschlagen" – jeder Eintrag behaelt seine Vorgabe, solange niemand bewusst etwas waehlt.
 * Kompakt fuer das SidePhone: Schalter statt Erklaertext, Kategorie und Stufe als Aufklappliste,
 * erklaert wird nur die gewaehlte Stufe (Lehre aus ADR 0003).
 */
@Composable
internal fun SammelFreigabeDialog(
    anzahl: Int,
    kanaele: Int,
    onDismiss: () -> Unit,
    onConfirm: (Sammelwahl) -> Unit
) {
    var alterFest by remember { mutableStateOf(false) }
    var alter by remember { mutableStateOf(Kanaleinstufung.STANDARD_ALTER) }
    var kategorie by remember { mutableStateOf<KategorieWahl>(KategorieWahl.WieVorgeschlagen) }
    var stufe by remember { mutableStateOf(SourceTrust.PER_VIDEO_REVIEW) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (anzahl == 1) "1 Eintrag freigeben" else "$anzahl Einträge freigeben") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    Modifier.fillMaxWidth().toggleable(value = alterFest, role = Role.Switch, onValueChange = { alterFest = it }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Mindestalter", style = MaterialTheme.typography.bodyMedium)
                        Text(if (alterFest) "für alle gleich" else "wie vorgeschlagen", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    Switch(checked = alterFest, onCheckedChange = null)
                }
                if (alterFest) {
                    AgeStepper("Ab $alter Jahren", "Mindestalter",
                        onMinus = { if (alter > Kanaleinstufung.MIN_ALTER) alter-- },
                        onPlus = { if (alter < Kanaleinstufung.MAX_ALTER) alter++ })
                }

                Spacer(Modifier.height(8.dp))
                SammelKategoriePicker(kategorie, onSelect = { kategorie = it })
                (kategorie as? KategorieWahl.Gesetzt)?.kategorie
                    ?.takeIf { it.minimumAge > (if (alterFest) alter else Kanaleinstufung.MIN_ALTER) }
                    ?.let {
                        Text("„${ParentLabels.category(it)}“ gilt erst ab ${it.minimumAge} – jüngere Angaben werden angehoben.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }

                if (kanaele > 0) {
                    Spacer(Modifier.height(8.dp))
                    KanalStufePicker(kanaele, stufe, onSelect = { stufe = it })
                    Text(ParentLabels.trustErklaerung(stufe), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(Sammelwahl(alter = alter.takeIf { alterFest }, kategorie = kategorie, kanalStufe = stufe))
            }) { Text("Freigeben") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Rueckfrage vor der Sammelablehnung (ADR 0004). */
@Composable
internal fun SammelAblehnenDialog(anzahl: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (anzahl == 1) "1 Eintrag ablehnen?" else "$anzahl Einträge ablehnen?") },
        text = { Text("Sie erscheinen nicht beim Kind. Jeder Eintrag bekommt einen Vermerk im Verlauf.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Ablehnen", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Kategorie mit „wie vorgeschlagen" vorneweg; „keine" ist eine bewusste Wahl fuer alle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SammelKategoriePicker(wahl: KategorieWahl, onSelect: (KategorieWahl) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = when (wahl) {
        KategorieWahl.WieVorgeschlagen -> "wie vorgeschlagen"
        is KategorieWahl.Gesetzt -> wahl.kategorie?.let { ParentLabels.category(it) } ?: "keine"
    }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            colors = xyz.steier.sidetube.sideTextFieldColors(),
            value = label, onValueChange = {}, readOnly = true,
            label = { Text("Kategorie") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("wie vorgeschlagen") },
                onClick = { onSelect(KategorieWahl.WieVorgeschlagen); open = false })
            DropdownMenuItem(text = { Text("keine") }, onClick = { onSelect(KategorieWahl.Gesetzt(null)); open = false })
            ContentCategory.entries.forEach { entry ->
                DropdownMenuItem(text = { Text(ParentLabels.category(entry)) },
                    onClick = { onSelect(KategorieWahl.Gesetzt(entry)); open = false })
            }
        }
    }
}

/** Stufe fuer die Kanaele der Auswahl – ohne „Gesperrt": Sperren bleibt eine Einzelentscheidung. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KanalStufePicker(kanaele: Int, stufe: SourceTrust, onSelect: (SourceTrust) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            colors = xyz.steier.sidetube.sideTextFieldColors(),
            value = ParentLabels.trust(stufe), onValueChange = {}, readOnly = true,
            label = { Text(if (kanaele == 1) "Stufe für 1 Kanal" else "Stufe für $kanaele Kanäle") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SAMMEL_STUFEN.forEach { entry ->
                DropdownMenuItem(text = { Text(ParentLabels.trust(entry)) }, onClick = { onSelect(entry); open = false })
            }
        }
    }
}

/** Was die Sammel-Maske fuer Kanaele anbietet (ADR 0004: alle Stufen ausser „Gesperrt"). */
internal val SAMMEL_STUFEN: List<SourceTrust> = SourceTrust.entries.filter { it != SourceTrust.BLOCKED }
