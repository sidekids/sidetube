package io.github.degipe.youtubewhitelist.feature.kid.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.degipe.youtubewhitelist.core.data.model.WhitelistItem
import io.github.degipe.youtubewhitelist.core.data.repository.WatchHistoryRepository
import io.github.degipe.youtubewhitelist.core.data.repository.WhitelistRepository
import io.github.degipe.youtubewhitelist.core.data.sleep.SleepTimerManager
import io.github.degipe.youtubewhitelist.core.data.sleep.SleepTimerStatus
import io.github.degipe.youtubewhitelist.core.data.timelimit.TimeLimitChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.github.degipe.youtubewhitelist.core.data.bedtime.BedtimeState
import io.github.degipe.youtubewhitelist.core.data.bedtime.BedtimeStateProvider
import io.github.degipe.youtubewhitelist.core.data.repository.KidProfileRepository

data class VideoPlayerUiState(
    val videoId: String = "",
    val videoTitle: String = "",
    val youtubeId: String = "",
    val siblingVideos: List<WhitelistItem> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isEmbedBlocked: Boolean = false,
    val playerReloadToken: Int = 0,
    val remainingTimeFormatted: String? = null,
    val isTimeLimitReached: Boolean = false,
    val isSleepTimerExpired: Boolean = false,
    val bedtime: BedtimeState = BedtimeState.Off,
    val profileName: String = ""
)

@HiltViewModel(assistedFactory = VideoPlayerViewModel.Factory::class)
class VideoPlayerViewModel @AssistedInject constructor(
    private val whitelistRepository: WhitelistRepository,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val timeLimitChecker: TimeLimitChecker,
    private val bedtimeStateProvider: BedtimeStateProvider,
    private val kidProfileRepository: KidProfileRepository,
    private val sleepTimerManager: SleepTimerManager,
    @Assisted("profileId") private val profileId: String,
    @Assisted("videoId") private val videoId: String,
    @Assisted("videoTitle") private val initialVideoTitle: String,
    @Assisted("channelTitle") private val channelTitle: String?
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("profileId") profileId: String,
            @Assisted("videoId") videoId: String,
            @Assisted("videoTitle") videoTitle: String,
            @Assisted("channelTitle") channelTitle: String?
        ): VideoPlayerViewModel
    }

    private val _uiState = MutableStateFlow(VideoPlayerUiState(videoId = videoId))
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private var siblingsJob: Job? = null

    /** Seconds of the current video already written to the watch history. */
    private var recordedSeconds = 0

    init {
        loadVideo()
        loadSiblings()
        observeTimeLimit()
        observeSleepTimer()
        observeBedtime()
    }

    private fun loadVideo() {
        _uiState.value = _uiState.value.copy(
            videoTitle = initialVideoTitle,
            youtubeId = videoId,
            isLoading = false
        )
    }

    private fun loadSiblings() {
        if (channelTitle == null) return

        siblingsJob?.cancel()
        siblingsJob = viewModelScope.launch {
            whitelistRepository.getVideosByChannelTitle(profileId, channelTitle)
                .collect { siblings ->
                    val currentIdx = siblings.indexOfFirst { it.youtubeId == _uiState.value.youtubeId }
                    _uiState.value = _uiState.value.copy(
                        siblingVideos = siblings,
                        currentIndex = currentIdx,
                        hasNext = currentIdx >= 0 && currentIdx < siblings.size - 1,
                        hasPrevious = currentIdx > 0
                    )
                }
        }
    }

    private fun observeTimeLimit() {
        viewModelScope.launch {
            timeLimitChecker.getTimeLimitStatus(profileId).collect { status ->
                _uiState.value = _uiState.value.copy(
                    remainingTimeFormatted = status.remainingSeconds?.let { formatRemaining(it) },
                    isTimeLimitReached = status.isLimitReached
                )
            }
        }
    }

    private fun observeBedtime() {
        viewModelScope.launch {
            bedtimeStateProvider.observe(profileId).collect { bedtime ->
                _uiState.value = _uiState.value.copy(bedtime = bedtime)
            }
        }
        viewModelScope.launch {
            kidProfileRepository.getProfileById(profileId).collect { profile ->
                _uiState.value = _uiState.value.copy(profileName = profile?.name.orEmpty())
            }
        }
    }

    private fun observeSleepTimer() {
        viewModelScope.launch {
            sleepTimerManager.state.collect { sleepState ->
                _uiState.value = _uiState.value.copy(
                    isSleepTimerExpired = sleepState.status == SleepTimerStatus.EXPIRED
                            && sleepState.profileId == profileId
                )
            }
        }
    }

    fun onVideoEnded(watchedSeconds: Int) {
        recordProgress(watchedSeconds)
    }

    /**
     * Position report from the player (pause, heartbeat, end). Only the part
     * that has not been recorded yet is added, so pausing and resuming does not
     * count the same seconds twice.
     */
    fun onPlaybackProgress(positionSeconds: Int) {
        recordProgress(positionSeconds)
    }

    /** The video cannot be embedded — no silent jump to unapproved content. */
    fun onEmbedBlocked() {
        _uiState.value = _uiState.value.copy(isEmbedBlocked = true)
    }

    fun retryPlayback() {
        recordedSeconds = 0
        _uiState.value = _uiState.value.copy(
            isEmbedBlocked = false,
            playerReloadToken = _uiState.value.playerReloadToken + 1
        )
    }

    private fun recordProgress(positionSeconds: Int) {
        val newSeconds = positionSeconds - recordedSeconds
        if (newSeconds <= 0) return
        recordedSeconds = positionSeconds

        viewModelScope.launch {
            val state = _uiState.value
            watchHistoryRepository.recordWatch(
                profileId = profileId,
                videoId = state.youtubeId,
                videoTitle = state.videoTitle,
                watchedSeconds = newSeconds
            )
        }
    }

    fun playNext() {
        val state = _uiState.value
        if (state.hasNext) {
            navigateToIndex(state.currentIndex + 1)
        }
    }

    fun playPrevious() {
        val state = _uiState.value
        if (state.hasPrevious) {
            navigateToIndex(state.currentIndex - 1)
        }
    }

    fun playVideoAt(index: Int) {
        navigateToIndex(index)
    }

    private fun navigateToIndex(index: Int) {
        val siblings = _uiState.value.siblingVideos
        if (index < 0 || index >= siblings.size) return

        val nextItem = siblings[index]
        recordedSeconds = 0
        _uiState.value = _uiState.value.copy(
            videoId = nextItem.youtubeId,
            videoTitle = nextItem.title,
            youtubeId = nextItem.youtubeId,
            currentIndex = index,
            hasNext = index < siblings.size - 1,
            hasPrevious = index > 0,
            isEmbedBlocked = false
        )
    }

    private fun formatRemaining(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
