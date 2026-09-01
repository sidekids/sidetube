package io.github.degipe.youtubewhitelist.feature.parent.ui.exportimport

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.degipe.youtubewhitelist.core.export.ImportStrategy
import androidx.compose.ui.res.stringResource
import io.github.degipe.youtubewhitelist.feature.parent.R
import io.github.degipe.youtubewhitelist.core.common.R as CommonR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportImportScreen(
    viewModel: ExportImportViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showImportConfirmation by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }

    // SAF file picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val json = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                reader.readText()
            }
            if (json != null) {
                pendingImportJson = json
                showImportConfirmation = true
            }
        }
    }

    // SAF file creator for export
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            uiState.exportedJson?.let { json ->
                context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer ->
                    writer.write(json)
                }
                viewModel.clearExportedJson()
            }
        }
    }

    // When export is ready, launch SAF save dialog
    LaunchedEffect(uiState.exportedJson) {
        if (uiState.exportedJson != null) {
            exportLauncher.launch("youtubewhitelist_backup.json")
        }
    }

    // Show import result
    LaunchedEffect(uiState.importResult) {
        uiState.importResult?.let { result ->
            snackbarHostState.showSnackbar(
                context.getString(
                    R.string.parent_backup_imported,
                    result.profilesImported,
                    result.itemsImported
                ) + if (result.itemsSkipped > 0) {
                    " (" + context.getString(
                        R.string.parent_backup_imported_skipped,
                        result.itemsSkipped
                    ) + ")"
                } else {
                    ""
                }
            )
            viewModel.dismissResult()
        }
    }

    // Show error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                context.getString(
                    CommonR.string.common_error_with_message,
                    context.getString(error.resId, *error.args.toTypedArray())
                )
            )
            viewModel.dismissError()
        }
    }

    // Import strategy confirmation dialog
    if (showImportConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmation = false
                pendingImportJson = null
            },
            title = { Text(stringResource(R.string.parent_backup_strategy)) },
            text = { Text(stringResource(R.string.parent_backup_strategy_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportJson?.let { viewModel.importData(it, ImportStrategy.MERGE) }
                    showImportConfirmation = false
                    pendingImportJson = null
                }) {
                    Text(stringResource(R.string.parent_backup_merge))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingImportJson?.let { viewModel.importData(it, ImportStrategy.OVERWRITE) }
                    showImportConfirmation = false
                    pendingImportJson = null
                }) {
                    Text(stringResource(R.string.parent_backup_overwrite))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parent_backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CommonR.string.common_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Export section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.parent_backup_export),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.parent_backup_export_sub),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::export,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isExporting
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Text(stringResource(R.string.parent_backup_export_action))
                            }
                        }
                    }
                }
            }

            // Import section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.parent_backup_import),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.parent_backup_import_sub),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isImporting
                    ) {
                        if (uiState.isImporting) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        } else {
                            Text(stringResource(R.string.parent_backup_import_action))
                        }
                    }
                }
            }
        }
    }
}
