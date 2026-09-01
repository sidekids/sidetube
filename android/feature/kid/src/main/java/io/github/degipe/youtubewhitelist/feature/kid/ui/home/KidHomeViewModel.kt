package io.github.degipe.youtubewhitelist.feature.kid.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.degipe.youtubewhitelist.core.data.model.WatchHistory
import io.github.degipe.youtubewhitelist.core.data.model.WhitelistItem
import io.github.degipe.youtubewhitelist.core.data.repository.ChannelVideoCacheRepository
import io.github.degipe.youtubewhitelist.core.data.repository.KidProfileRepository
import io.github.degipe.youtubewhitelist.core.data.repository.WatchHistoryRepository
import io.github.degipe.youtubewhitelist.core.data.repository.WhitelistRepository
import io.github.degipe.youtubewhitelist.core.data.sleep.SleepTimerManager
import io.github.degipe.youtubewhitelist.core.data.sleep.SleepTimerStatus
import io.github.degipe.youtubewhitelist.core.data.timelimit.TimeLimitChecker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import io.github.degipe.youtubewhitelist.core.data.bedtime.BedtimeState
import io.github.degipe.youtubewhitelist.core.data.bedtime.BedtimeStateProvider
import io.github.degipe.youtubewhitelist.core.data.sleep.SleepTimerState
import io.github.degipe.youtubewhitelist.core.data.timelimit.TimeLimitStatus

/**
 * A video the kid watched before. Playback restarts from the beginning — the
 * exact position is not persisted yet.
 */
data class ContinueWatchingItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelTitle: String?
)

data class KidHomeUiState(
    val profileName: String = "",
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val channels: List<WhitelistItem> = emptyList(),
    val recentVideos: List<WhitelistItem> = emptyList(),
    val playlists: List<WhitelistItem> = emptyList(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val remainingTimeFormatted: String? = null,
    val isTimeLimitReached: Boolean = false,
    val isSleepTimerExpired: Boolean = false,
    val bedtime: BedtimeState = BedtimeState.Off
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = KidHomeViewModel.Factory::class)
class KidHomeViewModel @AssistedInject constructor(
    whitelistRepository: WhitelistRepository,
    kidProfileRepository: KidProfileRepository,
    watchHistoryRepository: WatchHistoryRepository,
    channelVideoCacheRepository: ChannelVideoCacheRepository,
    bedtimeStateProvider: BedtimeStateProvider,
    timeLimitChecker: TimeLimitChecker,
    sleepTimerManager: SleepTimerManager,
    @Assisted private val profileId: String
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(profileId: String): KidHomeViewModel
    }

    private data class Ambient(
        val timeLimit: TimeLimitStatus,
        val sleepState: SleepTimerState,
        val continueWatching: List<ContinueWatchingItem>,
        val bedtime: BedtimeState
    )

    private data class WatchedInput(
        val history: List<WatchHistory>,
        val whitelistedVideos: List<WhitelistItem>,
        val whitelistedChannels: List<WhitelistItem>
    )

    /**
     * Recently watched content, resolved against the whitelist: a video only
     * shows up while it is still whitelisted itself or still belongs to a
     * whitelisted channel.
     */
    private val continueWatchingFlow: Flow<List<ContinueWatchingItem>> = combine(
        watchHistoryRepository.getRecentHistory(profileId, HISTORY_LOOKBACK),
        whitelistRepository.getVideosByProfile(profileId),
        whitelistRepository.getChannelsByProfile(profileId)
    ) { history, videos, channels ->
        WatchedInput(history, videos, channels)
    }.flatMapLatest { input ->
        val recent = input.history
            .distinctBy { it.videoId }
            .take(CONTINUE_WATCHING_LIMIT)
        if (recent.isEmpty()) return@flatMapLatest flowOf(emptyList())

        val whitelistedById = input.whitelistedVideos.associateBy { it.youtubeId }
        channelVideoCacheRepository.getVideosByIds(
            videoIds = recent.map { it.videoId },
            channelIds = input.whitelistedChannels.map { it.youtubeId }
        ).map { cachedVideos ->
            val cachedById = cachedVideos.associateBy { it.videoId }
            recent.mapNotNull { entry ->
                whitelistedById[entry.videoId]?.let { item ->
                    ContinueWatchingItem(
                        videoId = item.youtubeId,
                        title = item.title,
                        thumbnailUrl = item.thumbnailUrl,
                        channelTitle = item.channelTitle
                    )
                } ?: cachedById[entry.videoId]?.let { video ->
                    ContinueWatchingItem(
                        videoId = video.videoId,
                        title = video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        channelTitle = video.channelTitle
                    )
                }
            }
        }
    }

    val uiState: StateFlow<KidHomeUiState> = combine(
        kidProfileRepository.getProfileById(profileId),
        whitelistRepository.getChannelsByProfile(profileId),
        whitelistRepository.getVideosByProfile(profileId),
        whitelistRepository.getPlaylistsByProfile(profileId),
        combine(
            timeLimitChecker.getTimeLimitStatus(profileId),
            sleepTimerManager.state,
            continueWatchingFlow,
            bedtimeStateProvider.observe(profileId)
        ) { timeLimit, sleepState, continueWatching, bedtime ->
            Ambient(timeLimit, sleepState, continueWatching, bedtime)
        }
    ) { profile, channels, videos, playlists, ambient ->
        KidHomeUiState(
            profileName = profile?.name ?: "",
            continueWatching = ambient.continueWatching,
            channels = channels,
            recentVideos = videos,
            playlists = playlists,
            isLoading = false,
            isEmpty = channels.isEmpty() && videos.isEmpty() && playlists.isEmpty(),
            remainingTimeFormatted = ambient.timeLimit.remainingSeconds?.let { formatRemaining(it) },
            isTimeLimitReached = ambient.timeLimit.isLimitReached,
            isSleepTimerExpired = ambient.sleepState.status == SleepTimerStatus.EXPIRED
                    && ambient.sleepState.profileId == profileId,
            bedtime = ambient.bedtime
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = KidHomeUiState()
    )

    private fun formatRemaining(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private companion object {
        const val HISTORY_LOOKBACK = 50
        const val CONTINUE_WATCHING_LIMIT = 10
    }
}
