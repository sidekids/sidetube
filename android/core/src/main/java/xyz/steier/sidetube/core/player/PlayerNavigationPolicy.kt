// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.core.player

/**
 * Entscheidet, welche Navigation innerhalb der Player-WebView weiterlaufen darf. Rein und
 * geraeteunabhaengig, damit sie ohne Robolectric/Instrumentierung testbar ist; `PlayerBridge`
 * verdrahtet sie nur mit `WebViewClient.shouldOverrideUrlLoading`.
 */
object PlayerNavigationPolicy {
    private val embedHosts = setOf(
        "www.youtube.com", "youtube.com", "www.youtube-nocookie.com", "youtube-nocookie.com"
    )

    /** Hauptdokument: nur die eigene Herkunft der App (oder die anfaengliche Leerseite) darf dort laden. */
    fun allowMainFrame(url: String, appOrigin: String): Boolean {
        if (url == "about:blank") return true
        val target = parse(url) ?: return false
        val origin = parse(appOrigin) ?: return false
        return target.scheme == origin.scheme && target.host == origin.host &&
            target.port == origin.port && target.userInfo == null
    }

    /**
     * Nur das Einbettungsdokument der YouTube-IFrame-API, sonst nichts. Ein Host-Vergleich allein
     * würde nicht reichen: youtube.com/watch ist immer noch youtube.com. Ohne den Pfadvergleich
     * könnte das YouTube-Logo, ein Endbildschirm-Vorschlag oder ein anderer Link in der Einbettung
     * die Flaeche des Kindes auf die volle youtube.com-Seite umleiten (Suche, Anmeldung, Werbung,
     * Kommentare, verwandte Videos) - genau die Lücke, die dieser Pfadvergleich schliesst.
     */
    fun allowSubFrame(url: String): Boolean {
        if (url == "about:blank") return true
        val target = parse(url) ?: return false
        val host = target.host?.lowercase() ?: return false
        return host in embedHosts && (target.path ?: "").startsWith("/embed/")
    }

    private fun parse(url: String) = runCatching { java.net.URI(url) }.getOrNull()
}
