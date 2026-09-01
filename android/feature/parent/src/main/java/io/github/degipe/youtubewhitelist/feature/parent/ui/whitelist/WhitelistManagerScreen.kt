package io.github.degipe.youtubewhitelist.feature.parent.ui.whitelist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import io.github.degipe.youtubewhitelist.core.export.content.StarterPack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.degipe.youtubewhitelist.core.common.model.WhitelistItemType
import io.github.degipe.youtubewhitelist.core.data.model.WhitelistItem
import androidx.compose.ui.res.stringResource
import io.github.degipe.youtubewhitelist.feature.parent.R
import io.github.degipe.youtubewhitelist.core.common.R as CommonR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistManagerScreen(
    viewModel: WhitelistManagerViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSuccessMessage()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    val starterPackResult = uiState.starterPackResult
    val starterPackMessage = starterPackResult?.let {
        stringResource(R.string.parent_starterpack_result, it.added, it.skipped)
    }
    LaunchedEffect(starterPackResult) {
        if (starterPackMessage != null) {
            snackbarHostState.showSnackbar(starterPackMessage)
            viewModel.dismissStarterPackResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parent_whitelist_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CommonR.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::showStarterPackDialog) {
                        Icon(
                            imageVector = Icons.Default.LibraryAdd,
                            contentDescription = stringResource(R.string.parent_starterpack_action)
                        )
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
        ) {
            FilterChipRow(
                selectedType = uiState.filterType,
                onFilterSelected = viewModel::setFilter,
                onFilterCleared = viewModel::clearFilter,
                onAdd = viewModel::showAddUrlDialog
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.items.isEmpty() -> {
                    EmptyWhitelistMessage(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                }
                else -> {
                    WhitelistItemList(
                        items = uiState.items,
                        onRemoveItem = viewModel::removeItem
                    )
                }
            }
        }

        if (uiState.addUrlDialogVisible) {
            AddUrlDialog(
                isAdding = uiState.isAdding,
                onDismiss = viewModel::dismissAddUrlDialog,
                onConfirm = viewModel::addFromUrl
            )
        }

        if (uiState.starterPackDialogVisible) {
            StarterPackDialog(
                packs = uiState.starterPacks,
                applyPreset = uiState.starterPackApplyPreset,
                isImporting = uiState.isImportingStarterPack,
                onApplyPresetChange = viewModel::setStarterPackApplyPreset,
                onSelect = viewModel::importStarterPack,
                onDismiss = viewModel::dismissStarterPackDialog
            )
        }
    }
}

@Composable
private fun FilterChipRow(
    selectedType: WhitelistItemType?,
    onFilterSelected: (WhitelistItemType) -> Unit,
    onFilterCleared: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedType == null,
            onClick = onFilterCleared,
            label = { Text(stringResource(R.string.parent_whitelist_filter_all)) }
        )
        WhitelistItemType.entries.forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onFilterSelected(type) },
                label = { Text(type.displayName()) }
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onAdd) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.parent_whitelist_add_action),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun WhitelistItemList(
    items: List<WhitelistItem>,
    onRemoveItem: (WhitelistItem) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            WhitelistItemCard(item = item, onRemove = { onRemoveItem(item) })
        }
    }
}

@Composable
private fun WhitelistItemCard(
    item: WhitelistItem,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                item.channelTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = item.type.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(CommonR.string.common_remove),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun EmptyWhitelistMessage(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.parent_whitelist_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.parent_whitelist_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddUrlDialog(
    isAdding: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isAdding) onDismiss() },
        title = { Text(stringResource(R.string.parent_whitelist_add_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.parent_whitelist_url_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text(stringResource(R.string.parent_whitelist_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAdding
                )
                if (isAdding) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.parent_whitelist_adding), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(urlText) },
                enabled = urlText.isNotBlank() && !isAdding
            ) {
                Text(stringResource(CommonR.string.common_add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isAdding
            ) {
                Text(stringResource(CommonR.string.common_cancel))
            }
        }
    )
}

@Composable
private fun WhitelistItemType.displayName(): String = stringResource(
    when (this) {
        WhitelistItemType.CHANNEL -> R.string.parent_whitelist_filter_channels
        WhitelistItemType.VIDEO -> R.string.parent_whitelist_filter_videos
        WhitelistItemType.PLAYLIST -> R.string.parent_whitelist_filter_playlists
    }
)

/**
 * Auswahl der kuratierten Startpakete. Die Listen liegen als gemeinsame Daten unter
 * `assets/content/libraries` und stammen aus demselben Bestand wie die iOS-Fassung.
 */
@Composable
private fun StarterPackDialog(
    packs: List<StarterPack>,
    applyPreset: Boolean,
    isImporting: Boolean,
    onApplyPresetChange: (Boolean) -> Unit,
    onSelect: (StarterPack) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parent_starterpack_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.parent_starterpack_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (packs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.parent_starterpack_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    packs.forEach { pack ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = !isImporting) { onSelect(pack) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(pack.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = stringResource(
                                        R.string.parent_starterpack_count,
                                        pack.videoCount
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = applyPreset, onCheckedChange = onApplyPresetChange)
                        Text(
                            text = stringResource(R.string.parent_starterpack_apply_preset),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (isImporting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.parent_starterpack_importing),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(CommonR.string.common_cancel)) }
        }
    )
}
