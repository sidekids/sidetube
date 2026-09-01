package io.github.degipe.youtubewhitelist.feature.parent.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import io.github.degipe.youtubewhitelist.feature.parent.R
import io.github.degipe.youtubewhitelist.core.common.R as CommonR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    viewModel: ProfileEditViewModel,
    onNavigateBack: () -> Unit,
    onProfileDeleted: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onProfileDeleted()
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.parent_profile_delete)) },
            text = { Text(stringResource(R.string.parent_profile_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(CommonR.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text(stringResource(CommonR.string.common_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parent_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CommonR.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChanged,
                    label = { Text(stringResource(R.string.parent_profile_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.error != null
                )

                OutlinedTextField(
                    value = uiState.avatarUrl ?: "",
                    onValueChange = { viewModel.onAvatarUrlChanged(it.ifBlank { null }) },
                    label = { Text(stringResource(R.string.parent_profile_avatar)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Daily limit section
                Text(
                    text = stringResource(R.string.parent_profile_limit),
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.parent_profile_limit_enable))
                    Switch(
                        checked = uiState.dailyLimitMinutes != null,
                        onCheckedChange = { enabled ->
                            viewModel.onDailyLimitChanged(if (enabled) 60 else null)
                        }
                    )
                }

                if (uiState.dailyLimitMinutes != null) {
                    val limit = uiState.dailyLimitMinutes!!
                    Text(
                        text = stringResource(R.string.parent_profile_limit_value, limit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = limit.toFloat(),
                        onValueChange = { viewModel.onDailyLimitChanged(it.toInt()) },
                        valueRange = 15f..180f,
                        steps = 10
                    )
                }

                BedtimeSection(
                    bedtime = uiState.bedtime,
                    onEnabledChange = viewModel::onBedtimeEnabledChanged,
                    onStartChange = viewModel::onBedtimeStartChanged,
                    onEndChange = viewModel::onBedtimeEndChanged,
                    onWeekendOffsetChange = viewModel::onWeekendOffsetChanged,
                    onExtendTonight = viewModel::extendBedtimeTonight,
                    onSkipTonight = viewModel::skipBedtimeTonight,
                    onResume = viewModel::resumeBedtime,
                    isSuspended = uiState.bedtime.skipUntilEpochMillis
                        ?.let { it > System.currentTimeMillis() } == true
                )

                uiState.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = viewModel::saveProfile,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSaving
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Text(stringResource(CommonR.string.common_save))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = viewModel::requestDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(stringResource(R.string.parent_profile_delete))
                }
            }
        }
    }
}
