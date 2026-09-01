package io.github.degipe.youtubewhitelist.feature.kid.ui.bedtime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.degipe.youtubewhitelist.core.data.bedtime.BedtimeEvaluator
import io.github.degipe.youtubewhitelist.core.data.bedtime.BedtimeState
import io.github.degipe.youtubewhitelist.feature.kid.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

private val NightTop = Color(0xFF0A0E1A)
private val NightBottom = Color(0xFF141B2D)
private val NightText = Color(0xFFE6E9F0)
private val NightAccent = Color(0xFF8FA3C8)

/**
 * Quiet reminder that bedtime is close. It appears at the announced steps, stays
 * for a few seconds and fades out — nothing to confirm, nothing to dismiss.
 */
@Composable
fun BoxScope.BedtimeWarning(state: BedtimeState) {
    val step = when {
        state !is BedtimeState.Warning -> null
        state.minutesLeft <= BedtimeEvaluator.WARNING_LAST_MINUTES ->
            BedtimeEvaluator.WARNING_LAST_MINUTES
        else -> BedtimeEvaluator.WARNING_LEAD_MINUTES
    }
    val minutesLeft = (state as? BedtimeState.Warning)?.minutesLeft ?: 0

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(step) {
        if (step == null) {
            visible = false
        } else {
            visible = true
            delay(VISIBLE_MILLIS)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.kid_bedtime_warning, minutesLeft),
            style = MaterialTheme.typography.labelLarge,
            color = NightText,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(NightBottom.copy(alpha = 0.92f))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )
    }
}

/**
 * Bedtime has started: one icon, one line, one quiet promise for tomorrow.
 * The parent access stays reachable underneath as the only way out.
 */
@Composable
fun BedtimeGoodNight(
    profileName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightTop, NightBottom))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = NightAccent
            )
            Text(
                text = if (profileName.isNotEmpty()) {
                    stringResource(R.string.kid_bedtime_good_night_name, profileName)
                } else {
                    stringResource(R.string.kid_good_night_title)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = NightText,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.kid_bedtime_tomorrow),
                style = MaterialTheme.typography.bodyMedium,
                color = NightAccent,
                textAlign = TextAlign.Center
            )
        }
    }
}

private const val VISIBLE_MILLIS = 6_000L
