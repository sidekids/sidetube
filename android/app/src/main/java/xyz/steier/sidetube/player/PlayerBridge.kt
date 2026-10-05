// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package xyz.steier.sidetube.player

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import xyz.steier.sidetube.core.player.CookieStore
import xyz.steier.sidetube.core.player.PlayerCookiePolicy
import xyz.steier.sidetube.core.player.PlayerNavigationPolicy

/** Was der Player meldet. */
sealed interface PlayerEvent {
    data object Ready : PlayerEvent
    data class State(val value: Int) : PlayerEvent
    data class Error(val code: Int) : PlayerEvent
    data class Time(val seconds: Int) : PlayerEvent
    data object ApiFailed : PlayerEvent
    /** Der IFrame wollte ein nicht angefordertes Video spielen (Endscreen, Pausen-Vorschlag). */
    data class ForeignVideo(val videoId: String) : PlayerEvent
}

/**
 * Wiedergabe über die YouTube-IFrame-API in einer WebView.
 *
 * **Herkunft:** `http://<paketname>` – **nicht** youtube.com. Damit antwortet die IFrame-API
 * sofort mit Fehler 152 und spielt nichts ab; in der iOS-Fassung dieselbe Falle.
 *
 * Es gibt genau eine WebView je Sitzung. Sie ist der teuerste Baustein der App; sie je Video
 * neu anzulegen kostet auf einem kleinen Gerät spürbar Zeit und Strom.
 */
class PlayerBridge(private val host: Context, private val onEvent: (PlayerEvent) -> Unit) {

    private val origin = "https://xyz.steier.sidetube"
    private var destroyed = false
    private val retry = Runnable {
        if (!destroyed && !ready) { loaded = false; load() }
    }

    val webView: WebView = createWebView()

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(host).apply {
        settings.javaScriptEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.domStorageEnabled = true
        // Kein Zugriff aufs Dateisystem: Die Seite kommt aus den Assets und braucht nichts davon.
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        setBackgroundColor(android.graphics.Color.BLACK)

        addJavascriptInterface(Bridge(), "SideTube")

        // Eine nicht eingehaengte WebView laedt nicht: Der Aufruf verpufft, und die Seite kommt
        // nie an. Deshalb genau dann laden, wenn sie im Fenster haengt.
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = load()
            override fun onViewDetachedFromWindow(v: View) = Unit
        })

        // Cookies gelten nur fuer diese Sitzung: Was der Player anlegt, raeumt destroy() wieder
        // ab. Begruendung und der offene Drittanbieter-Schalter stehen in PlayerCookiePolicy.
        PlayerCookiePolicy.beginSession(cookieStore(this))

        webChromeClient = object : WebChromeClient() {
            /**
             * Ohne Ersatzposter bleibt die Videoflaeche auf manchen Geraeten schwarz, weil die
             * WebView auf ein Vorschaubild wartet, das nie kommt. Ein Punkt genuegt.
             */
            override fun getDefaultVideoPoster(): android.graphics.Bitmap? =
                super.getDefaultVideoPoster()
                    ?: android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.RGB_565)
        }

        webViewClient = object : WebViewClient() {
            /**
             * Aus dem Player führt kein Weg hinaus. Die Einbettung selbst darf laden, jeder
             * Versuch, ein anderes Ziel im Hauptdokument zu öffnen, wird verworfen – sonst
             * landet ein Kind mit einem Fehlgriff im offenen YouTube.
             *
             * Der Unterrahmen (das eingebettete YouTube-IFrame) bekam bislang gar keine solche
             * Prüfung: jede Navigation dort lief ungehindert durch. Ein Tipp auf das YouTube-Logo,
             * einen Endbildschirm-Vorschlag oder eine Werbe-Weiterleitung hätte die Flaeche des
             * Kindes auf die volle youtube.com-Seite umgeleitet (Suche, Anmeldung, Werbung,
             * verwandte Videos) - eine App-interne Navigation, kein System-Browser-Wechsel, also
             * unsichtbar fuer jede Ueberwachung ausserhalb der WebView selbst.
             */
            /**
             * Stirbt der Renderer-Prozess – auf kleinen Geraeten kommt das vor –, ist diese
             * WebView endgueltig unbrauchbar. `true` verhindert, dass das System die ganze App
             * mitnimmt; die Oberflaeche erfaehrt den Ausfall und zeigt ihn an, statt stumm zu
             * warten.
             */
            override fun onRenderProcessGone(
                view: WebView,
                detail: android.webkit.RenderProcessGoneDetail
            ): Boolean {
                ready = false
                onEvent(PlayerEvent.ApiFailed)
                destroy()
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val allowed = if (request.isForMainFrame) PlayerNavigationPolicy.allowMainFrame(url, origin)
                    else PlayerNavigationPolicy.allowSubFrame(url)
                return !allowed
            }
        }
    }

    private var loaded = false
    private var retried = false
    private var ready = false
    private var pending: String? = null

    fun load() {
        if (loaded || destroyed) return
        val html = context.assets.open("player.html").bufferedReader().use { it.readText() }
            .replace("__ORIGIN__", origin)
        webView.loadDataWithBaseURL(origin, html, "text/html", "utf-8", null)
        loaded = true

        // Der Renderer-Prozess der WebView laesst sich auf dem SP-01 Zeit; faellt der erste
        // Ladeversuch in dieses Fenster, kommt die Seite nie an und der Player bleibt leer.
        // Deshalb einmal nachfassen, wenn nach zwoelf Sekunden keine Bereitmeldung da ist.
        if (!retried) {
            retried = true
            webView.postDelayed(retry, RETRY_AFTER_MS)
        }
    }

    private val context = host.applicationContext

    /**
     * Der erste Abspielwunsch laedt die Seite. Bewusst nicht beim Anwendungsstart: Dort ist der
     * Hauptfaden mit Datenbank und Oberflaeche belegt, und die WebView bekommt ihren
     * Renderer-Prozess dann nicht rechtzeitig gebunden – auf dem SP-01 nachgestellt, die
     * Bindung lief nach zehn Sekunden in einen Zeitfehler und der Player blieb stumm und leer.
     */
    fun play(videoId: String) {
        if (destroyed) return
        if (!videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) {
            onEvent(PlayerEvent.Error(2))
            return
        }
        load()
        if (ready) call("loadVideo(${JSONObject.quote(videoId)})") else pending = videoId
    }
    fun pause() = call("pauseVideo()")
    fun resume() = call("playVideo()")
    fun stop() = call("stopVideo()")
    /** Relativ spulen; `seekBy` steht in `player.html` (eine globale `seekTo` gibt es dort nicht). */
    fun seekBy(seconds: Int) = call("seekBy($seconds)")
    fun setVolume(percent: Int) = call("setVolume(${'$'}{percent.coerceIn(0, 100)})")

    fun destroy() {
        if (destroyed) return
        destroyed = true
        pending = null
        ready = false
        webView.removeCallbacks(retry)
        webView.stopLoading()
        PlayerCookiePolicy.endSession(cookieStore(webView))
        webView.removeJavascriptInterface("SideTube")
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.loadUrl("about:blank")
        webView.destroy()
    }

    /** Bindet die reine Policy an den prozessweiten CookieManager. */
    private fun cookieStore(view: WebView) = object : CookieStore {
        private val cookies = CookieManager.getInstance()
        override fun setAcceptCookie(accept: Boolean) = cookies.setAcceptCookie(accept)
        override fun setAcceptThirdPartyCookies(accept: Boolean) =
            cookies.setAcceptThirdPartyCookies(view, accept)
        override fun removeAll() = cookies.removeAllCookies(null)
        override fun flush() = cookies.flush()
    }

    private companion object { const val RETRY_AFTER_MS = 12_000L }

    private fun call(script: String) {
        if (!destroyed) webView.post { if (!destroyed) webView.evaluateJavascript(script, null) }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onPlayerEvent(json: String) {
            val message = runCatching { JSONObject(json) }.getOrNull() ?: return
            // Zustandswerte kommen teils als Text ("-1"), teils als Zahl.
            val value = message.opt("value")?.toString()?.toIntOrNull()
            val event = when (message.optString("event")) {
                "ready" -> {
                    webView.post {
                        if (destroyed) return@post
                        ready = true
                        webView.removeCallbacks(retry)
                        pending?.let { id -> pending = null; call("loadVideo(${JSONObject.quote(id)})") }
                    }
                    PlayerEvent.Ready
                }
                "state" -> value?.let { PlayerEvent.State(it) }
                "error" -> value?.let { PlayerEvent.Error(it) }
                "time" -> value?.let { PlayerEvent.Time(it) }
                "apiFailed" -> PlayerEvent.ApiFailed
                "foreign" -> message.optString("value").takeIf { it.isNotEmpty() }?.let { PlayerEvent.ForeignVideo(it) }
                else -> null
            } ?: return
            webView.post { if (!destroyed) onEvent(event) }
        }
    }
}
