// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube

import kotlinx.coroutines.launch

import android.app.Application

/**
 * Einstiegspunkt der Anwendung. Haelt den [AppContainer], der die Bausteine von Hand
 * verdrahtet – bei dieser Groesse traegt eine Annotationsbibliothek ihren Aufwand nicht.
 */
class SideTubeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        datenPflegeEinmalAmTag()
    }

    /**
     * ADR 0010: einmal am Tag Freigaben auffrischen und Altes loeschen. Im Hintergrund, ohne die
     * Oberflaeche aufzuhalten; ein Fehler (offline) laesst den Tag offen, der naechste Start versucht es wieder.
     */
    private fun datenPflegeEinmalAmTag() {
        val prefs = getSharedPreferences("datenpflege", MODE_PRIVATE)
        val heute = java.time.LocalDate.now().toString()
        if (prefs.getString("letzterLauf", null) == heute) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { container.datenPflege.lauf() }
                .onSuccess { bericht ->
                    if (bericht.spaeter == 0) prefs.edit().putString("letzterLauf", heute).apply()
                    android.util.Log.i("SideTube", "Datenpflege: $bericht")
                }
        }
    }
}
