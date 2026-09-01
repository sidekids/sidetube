package io.github.degipe.youtubewhitelist.feature.parent.ui.channelsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.degipe.youtubewhitelist.core.common.R as CommonR
import io.github.degipe.youtubewhitelist.core.common.result.AppResult
import io.github.degipe.youtubewhitelist.core.common.ui.UiMessage
import io.github.degipe.youtubewhitelist.core.data.model.YouTubeMetadata
import io.github.degipe.youtubewhitelist.core.data.repository.WhitelistRepository
import io.github.degipe.youtubewhitelist.core.data.repository.YouTubeApiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChannelSearchUiState(
    val query: String = "",
    val results: List<YouTubeMetadata.Channel> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val approvedIds: Set<String> = emptySet(),
    val addingId: String? = null,
    val error: UiMessage? = null
)

/**
 * Approving a subscription without the YouTube website: the parent types a name,
 * the app searches channels through the YouTube API and adds the chosen one to
 * the whitelist. This is the only path that works on devices whose WebView is
 * too old to render youtube.com.
 */
@HiltViewModel(assistedFactory = ChannelSearchViewModel.Factory::class)
class ChannelSearchViewModel @AssistedInject constructor(
    private val youTubeApiRepository: YouTubeApiRepository,
    private val whitelistRepository: WhitelistRepository,
    @Assisted private val profileId: String
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(profileId: String): ChannelSearchViewModel
    }

    private val _uiState = MutableStateFlow(ChannelSearchUiState())
    val uiState: StateFlow<ChannelSearchUiState> = _uiState.asStateFlow()

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query, error = null)
    }

    /** Search runs on demand only — every query costs API quota. */
    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank() || _uiState.value.isSearching) return

        _uiState.value = _uiState.value.copy(isSearching = true, error = null)
        viewModelScope.launch {
            when (val result = youTubeApiRepository.searchChannels(query)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        hasSearched = true,
                        results = result.data
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        hasSearched = true,
                        results = emptyList(),
                        error = result.uiMessage ?: UiMessage(CommonR.string.error_search_failed)
                    )
                }
            }
        }
    }

    fun approve(channel: YouTubeMetadata.Channel) {
        if (_uiState.value.addingId != null) return

        _uiState.value = _uiState.value.copy(addingId = channel.youtubeId, error = null)
        viewModelScope.launch {
            val url = "https://www.youtube.com/channel/${channel.youtubeId}"
            when (val result = whitelistRepository.addItemFromUrl(profileId, url)) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        addingId = null,
                        approvedIds = _uiState.value.approvedIds + channel.youtubeId
                    )
                }
                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        addingId = null,
                        error = result.uiMessage ?: UiMessage(CommonR.string.error_unexpected)
                    )
                }
            }
        }
    }
}
