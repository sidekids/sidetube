package io.github.degipe.youtubewhitelist.feature.kid.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.degipe.youtubewhitelist.core.common.input.CollectSideInput
import io.github.degipe.youtubewhitelist.core.common.input.SideInputAction
import io.github.degipe.youtubewhitelist.core.common.input.SideInputChannel
import io.github.degipe.youtubewhitelist.core.common.input.rememberSideFocus
import io.github.degipe.youtubewhitelist.core.common.input.sideFocusBorder
import io.github.degipe.youtubewhitelist.core.common.model.WhitelistItemType
import io.github.degipe.youtubewhitelist.core.data.model.WhitelistItem
import androidx.compose.ui.res.stringResource
import io.github.degipe.youtubewhitelist.feature.kid.R
import io.github.degipe.youtubewhitelist.core.common.R as CommonR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidSearchScreen(
    viewModel: KidSearchViewModel,
    sideInput: SideInputChannel,
    onNavigateBack: () -> Unit,
    onVideoClick: (videoId: String, videoTitle: String, channelTitle: String?) -> Unit,
    onChannelClick: (youtubeId: String, channelTitle: String, thumbnailUrl: String) -> Unit,
    onPlaylistClick: (youtubeId: String, title: String, thumbnailUrl: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val resultListState = rememberLazyListState()
    val focus = rememberSideFocus(uiState.results.size, resultListState)

    CollectSideInput(sideInput) { action ->
        when (action) {
            SideInputAction.Up, SideInputAction.Previous -> focus.moveUp()
            SideInputAction.Down, SideInputAction.Next -> focus.moveDown()
            is SideInputAction.Digit -> viewModel.onDigitEntered(action.value)
            SideInputAction.Backspace -> viewModel.onBackspace()
            SideInputAction.Select -> uiState.results.getOrNull(focus.index)?.let { item ->
                when (item.type) {
                    WhitelistItemType.VIDEO -> onVideoClick(item.youtubeId, item.title, item.channelTitle)
                    WhitelistItemType.CHANNEL -> onChannelClick(item.youtubeId, item.title, item.thumbnailUrl)
                    WhitelistItemType.PLAYLIST -> onPlaylistClick(item.youtubeId, item.title, item.thumbnailUrl)
                }
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQueryChanged,
                        placeholder = { Text(stringResource(R.string.kid_search_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = viewModel::onClearQuery) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(CommonR.string.common_clear_search)
                                    )
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        }
                    )
                },
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
        when {
            query.isBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.kid_search_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            uiState.results.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.kid_search_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.kid_search_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = resultListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.results, key = { _, item -> item.id }) { index, item ->
                        SearchResultCard(
                            item = item,
                            focused = focus.isFocused(index),
                            onClick = {
                                focus.focus(index)
                                when (item.type) {
                                    WhitelistItemType.VIDEO -> onVideoClick(item.youtubeId, item.title, item.channelTitle)
                                    WhitelistItemType.CHANNEL -> onChannelClick(item.youtubeId, item.title, item.thumbnailUrl)
                                    WhitelistItemType.PLAYLIST -> onPlaylistClick(item.youtubeId, item.title, item.thumbnailUrl)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: WhitelistItem,
    focused: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .sideFocusBorder(focused)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (item.type) {
                WhitelistItemType.CHANNEL -> {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .width(100.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when (item.type) {
                        WhitelistItemType.CHANNEL -> stringResource(R.string.kid_search_type_channel)
                        WhitelistItemType.VIDEO -> item.channelTitle ?: stringResource(R.string.kid_search_type_video)
                        WhitelistItemType.PLAYLIST -> stringResource(R.string.kid_search_type_playlist)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
