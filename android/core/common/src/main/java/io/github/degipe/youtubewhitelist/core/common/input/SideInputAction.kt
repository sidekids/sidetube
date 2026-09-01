package io.github.degipe.youtubewhitelist.core.common.input

import android.view.KeyEvent

sealed interface SideInputAction {
    data object Up : SideInputAction
    data object Down : SideInputAction
    data object Previous : SideInputAction
    data object Next : SideInputAction
    data object Select : SideInputAction
    data object Backspace : SideInputAction
    data object Back : SideInputAction
    data object Home : SideInputAction
    data object Search : SideInputAction
    data class Digit(val value: Int) : SideInputAction
}

object SideInputMapper {
    fun map(keyCode: Int): SideInputAction? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> SideInputAction.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> SideInputAction.Down
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> SideInputAction.Previous
        KeyEvent.KEYCODE_MEDIA_NEXT -> SideInputAction.Next
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_DPAD_CENTER,
        // Die OK-Taste des SP-01 meldet ENTER, nicht DPAD_CENTER.
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> SideInputAction.Select
        KeyEvent.KEYCODE_DEL,
        KeyEvent.KEYCODE_FORWARD_DEL -> SideInputAction.Backspace
        KeyEvent.KEYCODE_DPAD_RIGHT -> SideInputAction.Search
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
            SideInputAction.Digit(keyCode - KeyEvent.KEYCODE_0)
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
            SideInputAction.Digit(keyCode - KeyEvent.KEYCODE_NUMPAD_0)
        else -> null
    }

    fun isBack(keyCode: Int): Boolean = keyCode in setOf(
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_TAB,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_STAR,
    )
}
