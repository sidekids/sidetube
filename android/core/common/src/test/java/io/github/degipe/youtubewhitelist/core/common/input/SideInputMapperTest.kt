package io.github.degipe.youtubewhitelist.core.common.input

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SideInputMapperTest {
    @Test
    fun `maps sidephone navigation keys`() {
        assertThat(SideInputMapper.map(KeyEvent.KEYCODE_DPAD_UP))
            .isEqualTo(SideInputAction.Up)
        assertThat(SideInputMapper.map(KeyEvent.KEYCODE_DPAD_DOWN))
            .isEqualTo(SideInputAction.Down)
        assertThat(SideInputMapper.map(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            .isEqualTo(SideInputAction.Previous)
        assertThat(SideInputMapper.map(KeyEvent.KEYCODE_MEDIA_NEXT))
            .isEqualTo(SideInputAction.Next)
        assertThat(SideInputMapper.map(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            .isEqualTo(SideInputAction.Select)
        assertThat(SideInputMapper.map(KeyEvent.KEYCODE_DPAD_CENTER))
            .isEqualTo(SideInputAction.Select)
        // Die OK-Taste des SP-01 meldet ENTER
        assertThat(SideInputMapper.map(KeyEvent.KEYCODE_ENTER))
            .isEqualTo(SideInputAction.Select)
        assertThat(SideInputMapper.map(KeyEvent.KEYCODE_NUMPAD_ENTER))
            .isEqualTo(SideInputAction.Select)
        assertThat(SideInputMapper.map(KeyEvent.KEYCODE_DEL))
            .isEqualTo(SideInputAction.Backspace)
        assertThat(SideInputMapper.map(KeyEvent.KEYCODE_7))
            .isEqualTo(SideInputAction.Digit(7))
        assertThat(SideInputMapper.map(KeyEvent.KEYCODE_NUMPAD_7))
            .isEqualTo(SideInputAction.Digit(7))
    }

    @Test
    fun `recognizes every sidephone back key`() {
        assertThat(SideInputMapper.isBack(KeyEvent.KEYCODE_BACK)).isTrue()
        assertThat(SideInputMapper.isBack(KeyEvent.KEYCODE_TAB)).isTrue()
        assertThat(SideInputMapper.isBack(KeyEvent.KEYCODE_ESCAPE)).isTrue()
        assertThat(SideInputMapper.isBack(KeyEvent.KEYCODE_DPAD_LEFT)).isTrue()
        assertThat(SideInputMapper.isBack(KeyEvent.KEYCODE_STAR)).isTrue()
        assertThat(SideInputMapper.isBack(KeyEvent.KEYCODE_DPAD_RIGHT)).isFalse()
    }
}
