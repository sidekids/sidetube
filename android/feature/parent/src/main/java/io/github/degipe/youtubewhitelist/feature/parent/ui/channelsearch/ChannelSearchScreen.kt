package io.github.degipe.youtubewhitelist.feature.parent.ui.channelsearch

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.degipe.youtubewhitelist.core.common.R as CommonR
import io.github.degipe.youtubewhitelist.core.common.input.CollectSideInput
import io.github.degipe.youtubewhitelist.core.common.input.SideInputAction
import io.github.degipe.youtubewhitelist.core.common.input.SideInputChannel
import io.github.degipe.youtubewhitelist.core.common.input.rememberSideFocus
import io.github.degipe.youtubewhitelist.core.common.input.sideFocusBorder
import io.github.degipe.youtubewhitelist.core.common.ui.text
import io.github.degipe.youtubewhitelist.core.data.model.YouTubeMetadata
import io.github.degipe.youtubewhitelist.feature.parent.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSearchScreen(
    viewModel: ChannelSearchViewModel,
    sideInput: SideInputChannel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val focus = rememberSideFocus(uiState.results.size, listState)

    CollectSideInput(sideInput) { action ->
        when (action) {
            SideInputAction.Up, SideInputAction.Previous -> focus.moveUp()
            SideInputAction.Down, SideInputAction.Next -> focus.moveDown()
            SideInputAction.Select -> uiState.results.getOrNull(focus.index)?.let(viewModel::approve)
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parent_channel_search_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CommonR.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChanged,
                placeholder = { Text(stringResource(R.string.parent_channel_search_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        viewModel.search()
                    }
                ),
                trailingIcon = {
                    IconButton(onClick = {
                        keyboardController?.hide()
                        viewModel.search()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(CommonR.string.common_search)
                        )
                    }
                }
            )

            uiState.error?.let { error ->
                Text(
                    text = error.text(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when {
                uiState.isSearching -> CenteredBox { CircularProgressIndicator() }

                !uiState.hasSearched -> CenteredBox {
                    Text(
                        text = stringResource(R.string.parent_channel_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.results.isEmpty() -> CenteredBox {
                    Text(
                        text = stringResource(R.string.parent_channel_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.results, key = { _, c -> c.youtubeId }) { index, channel ->
                        ChannelResultCard(
                            channel = channel,
                            focused = focus.isFocused(index),
                            isApproved = channel.youtubeId in uiState.approvedIds,
                            isAdding = uiState.addingId == channel.youtubeId,
                            onClick = {
                                focus.focus(index)
                                viewModel.approve(channel)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelResultCard(
    channel: YouTubeMetadata.Channel,
    focused: Boolean,
    isApproved: Boolean,
    isAdding: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isApproved && !isAdding, onClick = onClick)
            .sideFocusBorder(focused)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = channel.thumbnailUrl,
                contentDescription = channel.title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            when {
                isAdding -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                isApproved -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.parent_channel_search_approved),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
