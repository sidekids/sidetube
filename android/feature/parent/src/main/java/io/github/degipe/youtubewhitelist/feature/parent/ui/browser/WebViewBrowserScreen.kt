package io.github.degipe.youtubewhitelist.feature.parent.ui.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.degipe.youtubewhitelist.core.common.youtube.YouTubeContentType
import androidx.compose.ui.res.stringResource
import io.github.degipe.youtubewhitelist.feature.parent.R
import io.github.degipe.youtubewhitelist.core.common.R as CommonR
import androidx.annotation.StringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewBrowserScreen(
    viewModel: WebViewBrowserViewModel,
    profileId: String,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pageProgress by remember { mutableIntStateOf(100) }
    val context = LocalContext.current
    val activity = context as? Activity
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val exitFullscreen: () -> Unit = {
        fullscreenView?.let { view ->
            (activity?.window?.decorView as? FrameLayout)?.removeView(view)
        }
        fullscreenCallback?.onCustomViewHidden()
        fullscreenView = null
        fullscreenCallback = null
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(act.window, true)
            WindowInsetsControllerCompat(act.window, act.window.decorView).show(
                WindowInsetsCompat.Type.systemBars()
            )
        }
    }

    BackHandler(enabled = fullscreenView != null) {
        exitFullscreen()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (fullscreenView != null) exitFullscreen()
        }
    }

    LaunchedEffect(uiState.addResult) {
        when (val result = uiState.addResult) {
            is AddToWhitelistResult.Success -> {
                snackbarHostState.showSnackbar(context.getString(R.string.parent_whitelist_added, result.itemTitle))
                viewModel.dismissResult()
            }
            is AddToWhitelistResult.Error -> {
                snackbarHostState.showSnackbar(context.getString(CommonR.string.common_error_with_message, result.message))
                viewModel.dismissResult()
            }
            null -> {}
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.parent_browser_title),
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(CommonR.string.common_back)
                            )
                        }
                    }
                )
                if (pageProgress < 100) {
                    LinearProgressIndicator(
                        progress = { pageProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = uiState.detectedContent != null && !uiState.isAdding,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.addToWhitelist(profileId) },
                    icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.parent_whitelist_add_action)) },
                    text = {
                        Text(
                            text = stringResource(
                                R.string.parent_browser_approve,
                                uiState.detectedContent?.type?.let { stringResource(it.labelRes()) } ?: ""
                            )
                        )
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            YouTubeWebView(
                initialUrl = uiState.currentUrl,
                onUrlChanged = viewModel::onUrlChanged,
                onProgressChanged = { pageProgress = it },
                onEnterFullscreen = { view, callback ->
                    fullscreenView = view
                    fullscreenCallback = callback
                    activity?.let { act ->
                        view.setBackgroundColor(android.graphics.Color.BLACK)
                        (act.window.decorView as? FrameLayout)?.addView(
                            view,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        )
                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        WindowCompat.setDecorFitsSystemWindows(act.window, false)
                        WindowInsetsControllerCompat(act.window, act.window.decorView).apply {
                            hide(WindowInsetsCompat.Type.systemBars())
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    }
                },
                onExitFullscreen = exitFullscreen
            )

            if (uiState.isAdding) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebView(
    initialUrl: String,
    onUrlChanged: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onExitFullscreen: () -> Unit
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false

                    // Security hardening
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = true
                    }
                }

                // Enable cookies so YouTube login persists (Premium, ad-free, etc.)
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        request?.url?.toString()?.let { onUrlChanged(it) }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        url?.let { onUrlChanged(it) }
                    }

                    // YouTube ist eine Single-Page-App: Wechsel auf einen Kanal
                    // oder ein Video laufen über pushState. Nur dieser Rückruf
                    // meldet solche Navigationen.
                    override fun doUpdateVisitedHistory(
                        view: WebView?,
                        url: String?,
                        isReload: Boolean
                    ) {
                        url?.let { onUrlChanged(it) }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                        // Rückfallebene, falls eine Navigation keinen der
                        // Verlaufsrückrufe auslöst.
                        view?.url?.let { onUrlChanged(it) }
                    }

                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        if (view != null && callback != null) {
                            onEnterFullscreen(view, callback)
                        }
                    }

                    override fun onHideCustomView() {
                        onExitFullscreen()
                    }
                }

                loadUrl(initialUrl)
                webViewRef.value = this
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            webViewRef.value?.apply {
                loadUrl("about:blank")
                stopLoading()
                webChromeClient = null
                clearHistory()
                destroy()
            }
        }
    }
}

@StringRes
private fun YouTubeContentType.labelRes(): Int = when (this) {
    YouTubeContentType.VIDEO -> R.string.parent_type_video
    YouTubeContentType.CHANNEL -> R.string.parent_type_channel
    YouTubeContentType.CHANNEL_HANDLE -> R.string.parent_type_channel
    YouTubeContentType.CHANNEL_CUSTOM -> R.string.parent_type_channel
    YouTubeContentType.PLAYLIST -> R.string.parent_type_playlist
}
