package io.github.degipe.youtubewhitelist.ui.screen.pin.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.degipe.youtubewhitelist.R

/**
 * Parent PIN entry, second version.
 *
 * Built for the Sidephone display (320 x 427 dp): the numerals carry the screen,
 * the touch cells are large and invisible, and nothing competes for attention.
 * The layout mirrors the physical 3x4 keypad of the device, so the same digit
 * sits in the same place whether it is pressed on glass or on a key.
 *
 * This component holds no authentication logic — it renders state and reports
 * presses.
 */
@Composable
fun ParentPinPad(
    title: String,
    enteredDigits: Int,
    maxDigits: Int,
    message: String?,
    shakeTrigger: Int,
    canConfirm: Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            // One short nudge, no bounce: enough to notice, not enough to alarm.
            shake.animateTo(1f, tween(90))
            shake.animateTo(-1f, tween(90))
            shake.animateTo(0f, tween(90))
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // The app draws edge to edge; without this the title would hide
            // behind the status bar.
            .safeDrawingPadding()
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        PinDotRow(
            entered = enteredDigits,
            total = maxDigits,
            modifier = Modifier.graphicsLayer { translationX = shake.value * ShakeDistancePx }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // The message keeps its space so the keypad never jumps.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MessageHeight),
            contentAlignment = Alignment.Center
        ) {
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
            KeyRow {
                DigitCell(1, onDigit)
                DigitCell(2, onDigit)
                DigitCell(3, onDigit)
            }
            KeyRow {
                DigitCell(4, onDigit)
                DigitCell(5, onDigit)
                DigitCell(6, onDigit)
            }
            KeyRow {
                DigitCell(7, onDigit)
                DigitCell(8, onDigit)
                DigitCell(9, onDigit)
            }
            KeyRow {
                ActionCell(
                    onClick = onBackspace,
                    enabled = enteredDigits > 0,
                    contentDescription = stringResource(R.string.pin_keypad_backspace)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = null
                    )
                }
                DigitCell(0, onDigit)
                ActionCell(
                    onClick = onConfirm,
                    enabled = canConfirm,
                    contentDescription = stringResource(R.string.pin_keypad_submit)
                ) {
                    if (canConfirm) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PinDotRow(entered: Int, total: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.pin_dots_description, entered, total)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(DotGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val filled = index < entered
            Box(
                modifier = Modifier
                    .size(DotSize)
                    .clip(CircleShape)
                    .then(
                        if (filled) {
                            Modifier.background(MaterialTheme.colorScheme.onSurface)
                        } else {
                            Modifier.border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                        }
                    )
            )
        }
    }
}

@Composable
private fun KeyRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(ColumnGap)) { content() }
}

/**
 * A digit: small numeral, large invisible hit area. The visible glyph is not the
 * target — the whole cell is.
 */
@Composable
private fun DigitCell(digit: Int, onDigit: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = Modifier
            .size(CellWidth, CellHeight)
            .clip(RoundedCornerShape(12.dp))
            // Hardware keys are handled centrally; the cells are touch targets
            // only and must not collect a stray focus frame.
            .focusProperties { canFocus = false }
            .selectable(
                selected = false,
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDigit(digit)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            fontSize = DigitSize,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                val scale = if (pressed) 0.92f else 1f
                scaleX = scale
                scaleY = scale
            }
        )
    }
}

@Composable
private fun ActionCell(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(CellWidth, CellHeight)
            .clip(RoundedCornerShape(12.dp))
            .focusProperties { canFocus = false }
            .selectable(
                selected = false,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .semanticsLabel(contentDescription, enabled),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides
                if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                }
        ) {
            content()
        }
    }
}

private fun Modifier.semanticsLabel(label: String, enabled: Boolean): Modifier =
    if (enabled) {
        this.then(Modifier.clearAndSetSemantics { contentDescription = label })
    } else {
        this
    }

// Measured against the Sidephone display: 320 x 427 dp.
// 3 columns * 88 dp + 2 * 8 dp gaps = 280 dp of 288 dp usable width.
private val ScreenPadding = 16.dp
private val CellWidth = 88.dp
private val CellHeight = 58.dp
private val ColumnGap = 8.dp
private val RowGap = 6.dp
private val DotSize = 12.dp
private val DotGap = 14.dp
private val MessageHeight = 34.dp
private val DigitSize = 26.sp
private const val ShakeDistancePx = 12f
