package io.github.degipe.youtubewhitelist

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.degipe.youtubewhitelist.core.common.input.SideInputAction
import io.github.degipe.youtubewhitelist.core.common.input.SideInputChannel
import io.github.degipe.youtubewhitelist.core.common.input.SideInputMapper
import io.github.degipe.youtubewhitelist.core.data.sleep.SleepTimerManager
import io.github.degipe.youtubewhitelist.navigation.AppNavigation
import io.github.degipe.youtubewhitelist.ui.theme.YouTubeWhitelistTheme
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sleepTimerManager: SleepTimerManager

    @Inject
    lateinit var sideInput: SideInputChannel

    private var longPressJob: Job? = null
    private var longPressHandled = false
    private var keyIsDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!longPressHandled) {
                        sideInput.send(SideInputAction.Back)
                    }
                    longPressHandled = false
                    keyIsDown = false
                    longPressJob?.cancel()
                    longPressJob = null
                }
            },
        )
        setContent {
            YouTubeWhitelistTheme {
                AppNavigation(
                    sleepTimerManager = sleepTimerManager,
                    sideInput = sideInput,
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return handleSystemBackKey(event)
        }
        if (SideInputMapper.isBack(event.keyCode)) {
            return handleBackKey(event)
        }
        if (event.action != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event)
        }

        val action = SideInputMapper.map(event.keyCode) ?: return super.dispatchKeyEvent(event)
        if (action == SideInputAction.Search && event.repeatCount > 0) return true

        // Screens without key handling (parent browser, text entry) keep the
        // default Android behaviour of the physical keys.
        return sideInput.send(action) || super.dispatchKeyEvent(event)
    }

    private fun handleSystemBackKey(event: KeyEvent): Boolean {
        if (
            event.action == KeyEvent.ACTION_DOWN &&
            (event.repeatCount > 0 || event.isLongPress)
        ) {
            if (!longPressHandled) {
                longPressHandled = true
                sideInput.send(SideInputAction.Home)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleBackKey(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0 && !event.isLongPress) {
                    keyIsDown = true
                    longPressHandled = false
                    longPressJob?.cancel()
                    longPressJob = lifecycleScope.launch {
                        delay(LONG_PRESS_MS)
                        longPressHandled = true
                        sideInput.send(SideInputAction.Home)
                    }
                } else if (!longPressHandled) {
                    longPressHandled = true
                    longPressJob?.cancel()
                    sideInput.send(SideInputAction.Home)
                }
            }
            KeyEvent.ACTION_UP -> {
                longPressJob?.cancel()
                longPressJob = null
                if (keyIsDown && !longPressHandled) {
                    sideInput.send(SideInputAction.Back)
                }
                keyIsDown = false
                longPressHandled = false
            }
        }
        return true
    }

    private companion object {
        const val LONG_PRESS_MS = 500L
    }
}
