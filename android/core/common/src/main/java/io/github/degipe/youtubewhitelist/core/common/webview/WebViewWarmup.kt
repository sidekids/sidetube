package io.github.degipe.youtubewhitelist.core.common.webview

import android.content.Context
import android.webkit.WebView

/**
 * Loads the WebView implementation into the process before the player needs it.
 *
 * The first WebView of a process pays for the Chromium provider start — on a
 * low-end device that is a few hundred milliseconds, and it would otherwise land
 * exactly when a video is opened. Warming up while the kid is still browsing
 * moves that cost out of the playback path.
 */
object WebViewWarmup {

    @Volatile
    private var warmedUp = false

    fun warmUp(context: Context) {
        if (warmedUp) return
        warmedUp = true
        runCatching { WebView(context.applicationContext).destroy() }
    }
}
