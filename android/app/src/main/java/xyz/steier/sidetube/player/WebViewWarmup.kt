// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.player

import android.content.Context
import android.webkit.WebView

/**
 * Zieht die Chromium-Laufzeit in den Prozess, bevor der Player sie braucht.
 *
 * Die erste WebView eines Prozesses bezahlt den Start der WebView-Umgebung samt Renderer-Prozess.
 * Auf dem SP-01 gelingt die Bindung an den Renderer-Prozess nur, wenn der Hauptfaden gerade frei
 * ist. Waehrend des Anwendungsstarts ist er es nicht – dort laeuft die Bindung nach zehn Sekunden
 * in einen Zeitfehler (`cr_ChildProcessConn: Fallback to SandboxedProcessService1`), es entsteht
 * nie ein `sandboxed_process`, und der Player bleibt danach leer und stumm.
 *
 * Deshalb wird bewusst spaeter vorgewaermt: nach dem Start, waehrend das Kind noch stoebert. Das
 * nimmt die Kosten zugleich aus dem Abspielweg heraus.
 *
 * Nachpruefen laesst sich das mit `adb shell "ps -A -o NAME | grep sandboxed_process"` – ohne
 * Treffer spielt kein Video.
 */
object WebViewWarmup {
    @Volatile private var done = false

    fun warmUp(context: Context) {
        if (done) return
        done = true
        val app = context.applicationContext
        runCatching { WebView(app).destroy() }
    }
}
