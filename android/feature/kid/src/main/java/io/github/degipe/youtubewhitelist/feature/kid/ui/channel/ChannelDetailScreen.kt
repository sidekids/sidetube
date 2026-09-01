package io.github.degipe.youtubewhitelist.feature.kid.ui.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.degipe.youtubewhitelist.core.common.input.CollectSideInput
import io.github.degipe.youtubewhitelist.core.common.input.SideInputAction
import io.github.degipe.youtubewhitelist.core.common.input.SideInputChannel
import io.github.degipe.youtubewhitelist.core.common.input.rememberSideFocus
import io.github.degipe.youtubewhitelist.core.common.input.sideFocusBorder
import io.github.degipe.youtubewhitelist.core.common.ui.text
import io.github.degipe.youtubewhitelist.core.data.model.PlaylistVideo
import androidx.compose.ui.res.stringResource
import io.github.degipe.youtubewhitelist.feature.kid.R
import io.github.degipe.youtubewhitelist.core.common.R as CommonR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(
    viewModel: ChannelDetailViewModel,
    sideInput: SideInputChannel,
    onNavigateBack: () -> Unit,
    onVideoClick: (videoId: String, videoTitle: String, channelTitle: String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var isSearchActive by remember { mutableStateOf(false) }
    // Only touch users get the on-screen keyboard; key users type with T9.
    var isTypingByTouch by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val focus = rememberSideFocus(uiState.videos.size, listState)

    CollectSideInput(sideInput) { action ->
        when (action) {
            SideInputAction.Up, SideInputAction.Previous -> focus.moveUp()
            SideInputAction.Down, SideInputAction.Next -> {
                focus.moveDown()
                if (uiState.hasMorePages && focus.index >= uiState.videos.lastIndex) {
                    viewModel.loadMore()
                }
            }
            SideInputAction.Select -> uiState.videos.getOrNull(focus.index)?.let { video ->
                onVideoClick(video.videoId, video.title, video.channelTitle)
            }
            SideInputAction.Search -> {
                isTypingByTouch = false
                isSearchActive = true
            }
            is SideInputAction.Digit -> {
                isTypingByTouch = false
                isSearchActive = true
                viewModel.onDigitEntered(action.value)
            }
            SideInputAction.Backspace -> if (isSearchActive) viewModel.onBackspace()
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = viewModel::onSearchQueryChanged,
                            placeholder = { Text(stringResource(R.string.kid_channel_search_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { keyboardController?.hide() }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                        LaunchedEffect(isTypingByTouch) {
                            if (isTypingByTouch) focusRequester.requestFocus()
                        }
                    } else {
                        Text(
                            text = uiState.channelTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            isTypingByTouch = false
                            viewModel.onClearSearch()
                            keyboardController?.hide()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CommonR.string.common_back)
                        )
                    }
                },
                actions = {
                    if (isSearchActive) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onClearSearch() }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(CommonR.string.common_clear_search)
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = {
                            isTypingByTouch = true
                            isSearchActive = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(CommonR.string.common_search)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.videos.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error?.text() ?: stringResource(CommonR.string.common_unknown_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = viewModel::retry) {
                            Text(stringResource(CommonR.string.common_retry))
                        }
                    }
                }
            }
            uiState.videos.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) stringResource(R.string.kid_channel_no_videos_found)
                        else stringResource(R.string.kid_channel_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    itemsIndexed(uiState.videos, key = { _, video -> video.videoId }) { index, video ->
                        ChannelVideoCard(
                            video = video,
                            focused = focus.isFocused(index),
                            onClick = {
                                focus.focus(index)
                                onVideoClick(video.videoId, video.title, video.channelTitle)
                            }
                        )
                    }
                    if (uiState.hasMorePages) {
                        item(key = "load_more") {
                            LaunchedEffect(Unit) {
                                viewModel.loadMore()
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelVideoCard(
    video: PlaylistVideo,
    focused: Boolean,
    onClick: () -> Unit
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
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .width(160.dp)
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.channelTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
