// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Texte der Oberflaeche fuer Code ausserhalb von Compose (ViewModels, Zeilenaufbau). Die
 * Composables nehmen `stringResource` direkt; alles andere fragt hier – so haengt kein ViewModel
 * an einem `Context`, und die Tests lesen dieselben `strings.xml` (siehe `TestTexte`).
 */
interface Texte {
    fun get(id: Int, vararg args: Any): String
    /** Mengenform: `<plurals>` mit `one`/`other`; [menge] ist zugleich das erste Argument, wenn keins uebergeben wird. */
    fun plural(id: Int, menge: Int, vararg args: Any): String
}

/**
 * Fuer Helfer, die aus Composables heraus Texte zusammensetzen, aber selbst nicht composable sind
 * (Zusammenfassungen, Zeilen). Die Activity stellt sie an der Wurzel bereit.
 */
val LocalTexte = staticCompositionLocalOf<Texte> { error("LocalTexte ist nicht bereitgestellt") }

class AndroidTexte(private val context: Context) : Texte {
    override fun get(id: Int, vararg args: Any): String = context.getString(id, *args)
    override fun plural(id: Int, menge: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, menge, *(if (args.isEmpty()) arrayOf<Any>(menge) else args))
}
