package io.github.degipe.youtubewhitelist.feature.parent.ui.whitelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.degipe.youtubewhitelist.core.common.model.WhitelistItemType
import io.github.degipe.youtubewhitelist.core.common.result.AppResult
import io.github.degipe.youtubewhitelist.core.export.content.StarterPack
import io.github.degipe.youtubewhitelist.core.export.content.StarterPackImportResult
import io.github.degipe.youtubewhitelist.core.export.content.StarterPackService
import io.github.degipe.youtubewhitelist.core.data.model.WhitelistItem
import io.github.degipe.youtubewhitelist.core.data.repository.WhitelistRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WhitelistManagerUiState(
    val items: List<WhitelistItem> = emptyList(),
    val filterType: WhitelistItemType? = null,
    val isLoading: Boolean = true,
    val isAdding: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val addUrlDialogVisible: Boolean = false,
    val starterPacks: List<StarterPack> = emptyList(),
    val starterPackDialogVisible: Boolean = false,
    val starterPackApplyPreset: Boolean = true,
    val isImportingStarterPack: Boolean = false,
    val starterPackResult: StarterPackImportResult? = null
)

@HiltViewModel(assistedFactory = WhitelistManagerViewModel.Factory::class)
class WhitelistManagerViewModel @AssistedInject constructor(
    private val whitelistRepository: WhitelistRepository,
    private val starterPackService: StarterPackService,
    @Assisted private val profileId: String
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(profileId: String): WhitelistManagerViewModel
    }

    private val _uiState = MutableStateFlow(WhitelistManagerUiState())
    val uiState: StateFlow<WhitelistManagerUiState> = _uiState.asStateFlow()

    private var collectionJob: Job? = null

    init {
        observeItems()
    }

    private fun observeItems() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            val flow = when (val filter = _uiState.value.filterType) {
                null -> whitelistRepository.getItemsByProfile(profileId)
                else -> whitelistRepository.getItemsByProfileAndType(profileId, filter)
            }
            flow.collect { items ->
                _uiState.update { it.copy(items = items, isLoading = false) }
            }
        }
    }

    fun setFilter(type: WhitelistItemType) {
        _uiState.update { it.copy(filterType = type) }
        observeItems()
    }

    fun clearFilter() {
        _uiState.update { it.copy(filterType = null) }
        observeItems()
    }

    fun showAddUrlDialog() {
        _uiState.update { it.copy(addUrlDialogVisible = true) }
    }

    fun dismissAddUrlDialog() {
        _uiState.update { it.copy(addUrlDialogVisible = false) }
    }

    fun addFromUrl(url: String) {
        if (url.isBlank()) return

        _uiState.update { it.copy(isAdding = true, error = null, successMessage = null) }
        viewModelScope.launch {
            when (val result = whitelistRepository.addItemFromUrl(profileId, url)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAdding = false,
                            addUrlDialogVisible = false,
                            successMessage = "${result.data.title} added to whitelist"
                        )
                    }
                }
                is AppResult.Error -> {
                    _uiState.update {
                        it.copy(isAdding = false, error = result.message)
                    }
                }
            }
        }
    }

    // Startpakete: kuratierte Listen aus den gemeinsamen Daten unter assets/content.

    fun showStarterPackDialog() {
        _uiState.update { it.copy(starterPackDialogVisible = true) }
        viewModelScope.launch {
            val packs = starterPackService.availablePacks()
            _uiState.update { it.copy(starterPacks = packs) }
        }
    }

    fun dismissStarterPackDialog() {
        _uiState.update { it.copy(starterPackDialogVisible = false) }
    }

    fun setStarterPackApplyPreset(apply: Boolean) {
        _uiState.update { it.copy(starterPackApplyPreset = apply) }
    }

    fun importStarterPack(pack: StarterPack) {
        _uiState.update { it.copy(isImportingStarterPack = true, error = null) }
        viewModelScope.launch {
            val applyPreset = _uiState.value.starterPackApplyPreset
            when (val result = starterPackService.import(pack.fileName, profileId, applyPreset)) {
                is AppResult.Success -> _uiState.update {
                    it.copy(
                        isImportingStarterPack = false,
                        starterPackDialogVisible = false,
                        starterPackResult = result.data
                    )
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(isImportingStarterPack = false, error = result.message)
                }
            }
        }
    }

    fun dismissStarterPackResult() {
        _uiState.update { it.copy(starterPackResult = null) }
    }

    fun removeItem(item: WhitelistItem) {
        viewModelScope.launch {
            whitelistRepository.removeItem(item)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
