package io.github.degipe.youtubewhitelist.feature.kid.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import io.github.degipe.youtubewhitelist.core.common.input.CollectSideInput
import io.github.degipe.youtubewhitelist.core.common.input.SideFocusState
import io.github.degipe.youtubewhitelist.core.common.input.SideInputAction
import io.github.degipe.youtubewhitelist.core.common.input.SideInputChannel
import io.github.degipe.youtubewhitelist.core.common.input.rememberSideFocus
import io.github.degipe.youtubewhitelist.core.common.input.sideFocusBorder
import io.github.degipe.youtubewhitelist.core.common.webview.WebViewWarmup
import io.github.degipe.youtubewhitelist.core.data.model.WhitelistItem
import androidx.compose.ui.res.stringResource
import io.github.degipe.youtubewhitelist.feature.kid.R
import io.github.degipe.youtubewhitelist.core.common.R as CommonR
import io.github.degipe.youtubewhitelist.feature.kid.ui.bedtime.BedtimeGoodNight
import io.github.degipe.youtubewhitelist.feature.kid.ui.bedtime.BedtimeWarning

@Composable
fun KidHomeScreen(
    viewModel: KidHomeViewModel,
    sideInput: SideInputChannel,
    onParentAccess: () -> Unit,
    onSearchClick: () -> Unit,
    onChannelClick: (youtubeId: String, channelTitle: String, thumbnailUrl: String) -> Unit,
    onVideoClick: (videoId: String, videoTitle: String, channelTitle: String?) -> Unit,
    onPlaylistClick: (youtubeId: String, title: String, thumbnailUrl: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusMap = KidHomeFocusMap(uiState)
    val focus = rememberSideFocus(focusMap.count)

    // The most important safe action gets the focus: continue watching if there
    // is something to continue, otherwise the first collection.
    var startFocusApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading, focusMap.count) {
        if (!startFocusApplied && !uiState.isLoading && focusMap.count > 1) {
            focus.focus(FIRST_CONTENT_INDEX)
            startFocusApplied = true
        }
    }

    // Pay for the WebView start while the kid is still browsing, not when a
    // video is opened.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        delay(WEBVIEW_WARMUP_DELAY_MS)
        WebViewWarmup.warmUp(context)
    }

    CollectSideInput(sideInput) { action ->
        when (action) {
            SideInputAction.Up, SideInputAction.Previous -> focus.moveUp()
            SideInputAction.Down, SideInputAction.Next -> focus.moveDown()
            SideInputAction.Select -> when (val target = focusMap.targetAt(focus.index)) {
                KidHomeTarget.Search -> onSearchClick()
                is KidHomeTarget.ContinueWatching ->
                    onVideoClick(target.item.videoId, target.item.title, target.item.channelTitle)
                is KidHomeTarget.Channel ->
                    onChannelClick(target.item.youtubeId, target.item.title, target.item.thumbnailUrl)
                is KidHomeTarget.Video ->
                    onVideoClick(target.item.youtubeId, target.item.title, target.item.channelTitle)
                is KidHomeTarget.Playlist ->
                    onPlaylistClick(target.item.youtubeId, target.item.title, target.item.thumbnailUrl)
                null -> Unit
            }
            SideInputAction.Search -> onSearchClick()
            else -> Unit
        }
    }

    // Block back button in kid mode — only parent can exit via PIN
    BackHandler { /* Intentionally empty — prevents exiting kid mode */ }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onParentAccess,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(CommonR.string.common_parent_area)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                uiState.isEmpty -> {
                    EmptyContent(
                        profileName = uiState.profileName,
                        onSearchClick = {
                            focus.focus(0)
                            onSearchClick()
                        },
                        modifier = Modifier.padding(padding)
                    )
                }
                else -> {
                    KidHomeContent(
                        uiState = uiState,
                        focusMap = focusMap,
                        focus = focus,
                        onSearchClick = onSearchClick,
                        onChannelClick = onChannelClick,
                        onVideoClick = onVideoClick,
                        onPlaylistClick = onPlaylistClick,
                        modifier = Modifier.padding(padding)
                    )
                }
            }

            // Time's Up overlay
            if (uiState.isTimeLimitReached) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.kid_time_up_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.kid_time_up_body),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        FloatingActionButton(
                            onClick = onParentAccess,
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = stringResource(CommonR.string.common_parent_area))
                        }
                    }
                }
            }

            // Bedtime: quiet warning while browsing, good night screen at the start
            BedtimeWarning(uiState.bedtime)

            if (uiState.bedtime.isActive) {
                Box(modifier = Modifier.fillMaxSize()) {
                    BedtimeGoodNight(profileName = uiState.profileName)
                    FloatingActionButton(
                        onClick = onParentAccess,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(CommonR.string.common_parent_area)
                        )
                    }
                }
            }

            // Good Night overlay (sleep timer expired)
            if (uiState.isSleepTimerExpired) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A1A).copy(alpha = 0.98f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = Color(0xFF7B68EE)
                        )
                        Text(
                            text = stringResource(R.string.kid_good_night_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color(0xFFB0B0D0)
                        )
                        Text(
                            text = stringResource(R.string.kid_good_night_body),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = Color(0xFFB0B0D0).copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        FloatingActionButton(
                            onClick = onParentAccess,
                            containerColor = Color(0xFF7B68EE)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = stringResource(CommonR.string.common_parent_area),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContent(
    profileName: String,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (profileName.isNotEmpty()) stringResource(R.string.kid_greeting, profileName)
            else stringResource(R.string.kid_home_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.kid_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSearchClick,
            modifier = Modifier.border(
                BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                MaterialTheme.shapes.extraLarge,
            ),
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(CommonR.string.common_search))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KidHomeContent(
    uiState: KidHomeUiState,
    focusMap: KidHomeFocusMap,
    focus: SideFocusState,
    onSearchClick: () -> Unit,
    onChannelClick: (youtubeId: String, channelTitle: String, thumbnailUrl: String) -> Unit,
    onVideoClick: (videoId: String, videoTitle: String, channelTitle: String?) -> Unit,
    onPlaylistClick: (youtubeId: String, title: String, thumbnailUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchBringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(focus.index) {
        if (focus.index == focusMap.searchIndex) searchBringIntoViewRequester.bringIntoView()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Greeting + Search
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (uiState.profileName.isNotEmpty()) stringResource(R.string.kid_greeting, uiState.profileName)
                else stringResource(R.string.kid_home_title),
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(
                onClick = {
                    focus.focus(focusMap.searchIndex)
                    onSearchClick()
                },
                modifier = Modifier
                    .bringIntoViewRequester(searchBringIntoViewRequester)
                    .sideFocusBorder(focus.isFocused(focusMap.searchIndex), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(CommonR.string.common_search)
                )
            }
        }

        // Remaining time chip
        uiState.remainingTimeFormatted?.let { remaining ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.kid_time_remaining, remaining),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Continue watching section
        if (uiState.continueWatching.isNotEmpty()) {
            Text(
                text = stringResource(R.string.kid_section_continue),
                style = MaterialTheme.typography.titleMedium
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                itemsIndexed(
                    uiState.continueWatching,
                    key = { _, item -> item.videoId }
                ) { index, item ->
                    val itemIndex = focusMap.continueStart + index
                    VideoCard(
                        title = item.title,
                        thumbnailUrl = item.thumbnailUrl,
                        channelTitle = item.channelTitle,
                        focused = focus.isFocused(itemIndex),
                        onClick = {
                            focus.focus(itemIndex)
                            onVideoClick(item.videoId, item.title, item.channelTitle)
                        }
                    )
                }
            }
        }

        // Channels section
        if (uiState.channels.isNotEmpty()) {
            Text(
                text = stringResource(R.string.kid_section_channels),
                style = MaterialTheme.typography.titleMedium
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                uiState.channels.chunked(2).forEachIndexed { rowIndex, rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowItems.forEachIndexed { columnIndex, channel ->
                            val itemIndex = focusMap.channelsStart + rowIndex * 2 + columnIndex
                            ChannelCard(
                                channel = channel,
                                onClick = {
                                    focus.focus(itemIndex)
                                    onChannelClick(channel.youtubeId, channel.title, channel.thumbnailUrl)
                                },
                                focused = focus.isFocused(itemIndex),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill empty space if odd number
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Videos section
        if (uiState.recentVideos.isNotEmpty()) {
            Text(
                text = stringResource(R.string.kid_section_videos),
                style = MaterialTheme.typography.titleMedium
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                itemsIndexed(uiState.recentVideos, key = { _, video -> video.id }) { index, video ->
                    val itemIndex = focusMap.videosStart + index
                    VideoCard(
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        channelTitle = video.channelTitle,
                        focused = focus.isFocused(itemIndex),
                        onClick = {
                            focus.focus(itemIndex)
                            onVideoClick(video.youtubeId, video.title, video.channelTitle)
                        }
                    )
                }
            }
        }

        // Playlists section
        if (uiState.playlists.isNotEmpty()) {
            Text(
                text = stringResource(R.string.kid_section_playlists),
                style = MaterialTheme.typography.titleMedium
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                itemsIndexed(uiState.playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    val itemIndex = focusMap.playlistsStart + index
                    VideoCard(
                        title = playlist.title,
                        thumbnailUrl = playlist.thumbnailUrl,
                        channelTitle = playlist.channelTitle,
                        focused = focus.isFocused(itemIndex),
                        onClick = {
                            focus.focus(itemIndex)
                            onPlaylistClick(playlist.youtubeId, playlist.title, playlist.thumbnailUrl)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(
    channel: WhitelistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) bringIntoViewRequester.bringIntoView()
    }

    Card(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .clickable(onClick = onClick),
        border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = channel.thumbnailUrl,
                contentDescription = channel.title,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = channel.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCard(
    title: String,
    thumbnailUrl: String,
    channelTitle: String?,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) bringIntoViewRequester.bringIntoView()
    }

    Card(
        modifier = Modifier
            .width(200.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
            .clickable(onClick = onClick),
        border = if (focused) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                channelTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Focus order of the kid start screen: search, continue watching, channels,
 * videos, shows — the same order the sections appear on screen.
 */
private class KidHomeFocusMap(private val state: KidHomeUiState) {
    val searchIndex = 0
    val continueStart = 1
    val channelsStart = continueStart + state.continueWatching.size
    val videosStart = channelsStart + state.channels.size
    val playlistsStart = videosStart + state.recentVideos.size
    val count = playlistsStart + state.playlists.size

    fun targetAt(index: Int): KidHomeTarget? = when {
        index == searchIndex -> KidHomeTarget.Search
        index < channelsStart ->
            state.continueWatching.getOrNull(index - continueStart)?.let(KidHomeTarget::ContinueWatching)
        index < videosStart ->
            state.channels.getOrNull(index - channelsStart)?.let(KidHomeTarget::Channel)
        index < playlistsStart ->
            state.recentVideos.getOrNull(index - videosStart)?.let(KidHomeTarget::Video)
        else ->
            state.playlists.getOrNull(index - playlistsStart)?.let(KidHomeTarget::Playlist)
    }
}

private sealed interface KidHomeTarget {
    data object Search : KidHomeTarget
    data class ContinueWatching(val item: ContinueWatchingItem) : KidHomeTarget
    data class Channel(val item: WhitelistItem) : KidHomeTarget
    data class Video(val item: WhitelistItem) : KidHomeTarget
    data class Playlist(val item: WhitelistItem) : KidHomeTarget
}

private const val FIRST_CONTENT_INDEX = 1

/** Wait for the start screen to be drawn before warming up the WebView. */
private const val WEBVIEW_WARMUP_DELAY_MS = 600L
