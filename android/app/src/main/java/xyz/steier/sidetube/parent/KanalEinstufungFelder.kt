// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.parent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import xyz.steier.sidetube.core.curation.Kanaleinstufung
import xyz.steier.sidetube.core.model.ContentCategory
import xyz.steier.sidetube.core.model.SourceTrust

/**
 * Die drei Entscheidungen beim Hinzufuegen eines Kanals (ADR 0003): Stufe, Mindestalter, Kategorie.
 * Eine Komponente fuer Vorschau-Dialog und Pruefmaske, damit beide Wege dasselbe fragen.
 *
 * Bei „Gesperrt" verschwinden Alter und Kategorie: Fuer einen Kanal, der nicht ins Profil kommt,
 * waeren sie eine Frage ohne Folge.
 */
@Composable
internal fun KanalEinstufungFelder(einstufung: Kanaleinstufung, onChange: (Kanaleinstufung) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Vertrauensstufe", style = MaterialTheme.typography.titleSmall)
        Column(Modifier.selectableGroup()) {
            SourceTrust.entries.forEach { stufe ->
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = einstufung.trust == stufe, role = Role.RadioButton,
                        onClick = { onChange(einstufung.mitStufe(stufe)) }
                    ).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    RadioButton(selected = einstufung.trust == stufe, onClick = null, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
                    Column {
                        Text(ParentLabels.trust(stufe), style = MaterialTheme.typography.bodyMedium)
                        // Nur die gewaehlte Stufe erklaert sich: Alle fuenf Saetze zugleich fuellten das
                        // SidePhone (480×640) – Alter, Kategorie und die Vorauswahl lagen unter dem Falz.
                        if (einstufung.trust == stufe) {
                            Text(ParentLabels.trustErklaerung(stufe), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        if (einstufung.ergebnis == Kanaleinstufung.Ergebnis.AUFNEHMEN_UND_FREIGEBEN) {
            Spacer(Modifier.height(12.dp))
            AgeStepper("Ab ${einstufung.ageMin} Jahren", "Mindestalter",
                onMinus = { onChange(einstufung.juenger()) },
                onPlus = { onChange(einstufung.aelter()) })
            Spacer(Modifier.height(8.dp))
            CategoryPicker(selected = einstufung.category?.id, onSelect = { onChange(einstufung.mitKategorie(ContentCategory.from(it))) })
            einstufung.category?.takeIf { einstufung.kategorieHebtAlterAn }?.let {
                Text("„${ParentLabels.category(it)}“ wird erst ab ${it.minimumAge} gezeigt – es gilt ab ${einstufung.effektivesMindestalter}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text("Der Kanal kommt nicht in die Liste des Kindes. Links aus diesem Kanal werden künftig abgewiesen.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
