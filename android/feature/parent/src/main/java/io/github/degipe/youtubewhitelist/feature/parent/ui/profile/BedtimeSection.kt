package io.github.degipe.youtubewhitelist.feature.parent.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.degipe.youtubewhitelist.core.common.R as CommonR
import io.github.degipe.youtubewhitelist.core.data.bedtime.BedtimeSettings
import io.github.degipe.youtubewhitelist.feature.parent.R

/**
 * Quiet hours as one calm block: a switch, two times, one weekend rule — values
 * on the right, no icons, no colour beyond the theme.
 */
@Composable
fun BedtimeSection(
    bedtime: BedtimeSettings,
    onEnabledChange: (Boolean) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    onWeekendOffsetChange: (Int) -> Unit,
    onExtendTonight: () -> Unit,
    onSkipTonight: () -> Unit,
    onResume: () -> Unit,
    isSuspended: Boolean,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<TimeField?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.parent_bedtime_title),
            style = MaterialTheme.typography.titleMedium
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            SettingRow(
                label = stringResource(R.string.parent_bedtime_enabled),
                trailing = {
                    Switch(checked = bedtime.enabled, onCheckedChange = onEnabledChange)
                }
            )

            if (bedtime.enabled) {
                HorizontalDivider()
                SettingRow(
                    label = stringResource(R.string.parent_bedtime_from),
                    value = formatTime(bedtime.startMinutes),
                    onClick = { editing = TimeField.START }
                )
                HorizontalDivider()
                SettingRow(
                    label = stringResource(R.string.parent_bedtime_to),
                    value = formatTime(bedtime.endMinutes),
                    onClick = { editing = TimeField.END }
                )
                HorizontalDivider()
                SettingRow(
                    label = stringResource(R.string.parent_bedtime_weekend),
                    trailing = {
                        Switch(
                            checked = bedtime.weekendOffsetMinutes > 0,
                            onCheckedChange = { later ->
                                onWeekendOffsetChange(
                                    if (later) BedtimeSettings.DEFAULT_WEEKEND_OFFSET_MINUTES else 0
                                )
                            }
                        )
                    }
                )
            }
        }

        Text(
            text = bedtimeSummary(bedtime),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (bedtime.enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isSuspended) {
                    TextButton(onClick = onResume) {
                        Text(stringResource(R.string.parent_bedtime_resume))
                    }
                } else {
                    TextButton(onClick = onExtendTonight) {
                        Text(stringResource(R.string.parent_bedtime_extend))
                    }
                    TextButton(onClick = onSkipTonight) {
                        Text(stringResource(R.string.parent_bedtime_skip_today))
                    }
                }
            }
        }
    }

    editing?.let { field ->
        TimePickerDialog(
            initialMinutes = when (field) {
                TimeField.START -> bedtime.startMinutes
                TimeField.END -> bedtime.endMinutes
            },
            onDismiss = { editing = null },
            onConfirm = { minutes ->
                when (field) {
                    TimeField.START -> onStartChange(minutes)
                    TimeField.END -> onEndChange(minutes)
                }
                editing = null
            }
        )
    }
}

@Composable
private fun bedtimeSummary(bedtime: BedtimeSettings): String = when {
    !bedtime.enabled -> stringResource(R.string.parent_bedtime_summary_off)
    bedtime.weekendOffsetMinutes > 0 -> stringResource(
        R.string.parent_bedtime_summary_weekend,
        formatTime(bedtime.startMinutes),
        formatTime(bedtime.endMinutes),
        formatTime(bedtime.startMinutes + bedtime.weekendOffsetMinutes)
    )
    else -> stringResource(
        R.string.parent_bedtime_summary,
        formatTime(bedtime.startMinutes),
        formatTime(bedtime.endMinutes)
    )
}

@Composable
private fun SettingRow(
    label: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        when {
            trailing != null -> trailing()
            value != null -> Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(CommonR.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CommonR.string.common_cancel))
            }
        },
        text = { TimePicker(state = state) }
    )
}

private enum class TimeField { START, END }

private fun formatTime(minutesSinceMidnight: Int): String {
    val minutes = ((minutesSinceMidnight % (24 * 60)) + 24 * 60) % (24 * 60)
    return "%02d:%02d".format(minutes / 60, minutes % 60)
}
