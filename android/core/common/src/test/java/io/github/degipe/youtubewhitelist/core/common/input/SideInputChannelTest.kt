package io.github.degipe.youtubewhitelist.core.common.input

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SideInputChannelTest {

    @Test
    fun `screen actions are dropped while no screen collects`() {
        val channel = SideInputChannel()

        assertThat(channel.send(SideInputAction.Select)).isFalse()
        assertThat(channel.hasScreenCollector).isFalse()
    }

    @Test
    fun `screen actions are delivered to a collecting screen`() = runTest {
        val channel = SideInputChannel()

        channel.actions.test {
            assertThat(channel.send(SideInputAction.Digit(7))).isTrue()
            assertThat(awaitItem()).isEqualTo(SideInputAction.Digit(7))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigation actions are delivered without a screen collector`() = runTest {
        val channel = SideInputChannel()

        channel.navigationActions.test {
            assertThat(channel.send(SideInputAction.Back)).isTrue()
            assertThat(awaitItem()).isEqualTo(SideInputAction.Back)

            assertThat(channel.send(SideInputAction.Home)).isTrue()
            assertThat(awaitItem()).isEqualTo(SideInputAction.Home)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `screen collectors never see navigation actions`() = runTest {
        val channel = SideInputChannel()

        channel.actions.test {
            channel.send(SideInputAction.Back)
            channel.send(SideInputAction.Select)
            assertThat(awaitItem()).isEqualTo(SideInputAction.Select)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
