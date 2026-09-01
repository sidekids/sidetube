package io.github.degipe.youtubewhitelist.core.common.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Key focus for a single screen: exactly one item is focused, the focus never
 * wraps around and never leaves the visible list.
 */
class SideFocusState internal constructor(
    private val state: MutableIntState,
    private val itemCount: () -> Int,
) {
    val index: Int
        get() = state.intValue

    private val lastIndex: Int
        get() = (itemCount() - 1).coerceAtLeast(0)

    fun moveUp() {
        state.intValue = (index - 1).coerceAtLeast(0)
    }

    fun moveDown() {
        state.intValue = (index + 1).coerceAtMost(lastIndex)
    }

    fun focus(index: Int) {
        state.intValue = index.coerceIn(0, lastIndex)
    }

    fun isFocused(index: Int): Boolean = index == this.index
}

/**
 * Remembers the focused index across configuration changes and back navigation.
 * When [listState] is given, the focused item is always scrolled into view.
 */
@Composable
fun rememberSideFocus(itemCount: Int, listState: LazyListState? = null): SideFocusState {
    val indexState = rememberSaveable { mutableIntStateOf(0) }
    val currentItemCount by rememberUpdatedState(itemCount)
    val focus = remember(indexState) { SideFocusState(indexState) { currentItemCount } }

    LaunchedEffect(itemCount) {
        focus.focus(focus.index)
    }

    if (listState != null) {
        LaunchedEffect(focus.index, itemCount) {
            if (itemCount > 0) {
                listState.animateScrollToItem(focus.index.coerceAtMost(itemCount - 1))
            }
        }
    }

    return focus
}

/** Yellow focus ring — the only meaning the accent colour carries in kid mode. */
@Composable
fun Modifier.sideFocusBorder(
    focused: Boolean,
    shape: Shape = MaterialTheme.shapes.medium,
): Modifier = if (focused) {
    border(BorderStroke(FOCUS_BORDER_WIDTH, MaterialTheme.colorScheme.primary), shape)
} else {
    this
}

private val FOCUS_BORDER_WIDTH = 3.dp
