package io.github.degipe.youtubewhitelist.core.common.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Collects side input while this screen is resumed.
 *
 * During a navigation transition two screens are composed at the same time, but
 * only the incoming one reaches [Lifecycle.State.RESUMED] — this keeps a single
 * key press from being handled twice.
 */
@Composable
fun CollectSideInput(
    channel: SideInputChannel,
    onAction: (SideInputAction) -> Unit,
) {
    val currentOnAction by rememberUpdatedState(onAction)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(channel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            channel.actions.collect { action -> currentOnAction(action) }
        }
    }
}
