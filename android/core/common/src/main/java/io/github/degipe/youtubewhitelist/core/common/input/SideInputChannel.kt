package io.github.degipe.youtubewhitelist.core.common.input

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central hardware key bus for the Sidephone.
 *
 * Navigation actions (Back, Home) are always delivered — the navigation host is
 * a permanent collector. Screen actions are only delivered while a screen is
 * actually collecting them, so screens without key handling (parent browser,
 * text entry) keep the default Android behaviour of the physical keys.
 */
@Singleton
class SideInputChannel @Inject constructor() {

    private val navigationEvents = MutableSharedFlow<SideInputAction>(extraBufferCapacity = 16)
    private val screenEvents = MutableSharedFlow<SideInputAction>(extraBufferCapacity = 16)

    val navigationActions: SharedFlow<SideInputAction> = navigationEvents.asSharedFlow()

    val actions: SharedFlow<SideInputAction> = screenEvents.asSharedFlow()

    val hasScreenCollector: Boolean
        get() = screenEvents.subscriptionCount.value > 0

    /**
     * @return true when the action was delivered and the key event is consumed.
     */
    fun send(action: SideInputAction): Boolean = when (action) {
        SideInputAction.Back, SideInputAction.Home -> navigationEvents.tryEmit(action)
        else -> hasScreenCollector && screenEvents.tryEmit(action)
    }
}
