package io.github.degipe.youtubewhitelist.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.degipe.youtubewhitelist.core.data.repository.KidProfileRepository
import io.github.degipe.youtubewhitelist.core.data.repository.ParentAccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.github.degipe.youtubewhitelist.core.common.ui.UiMessage
import io.github.degipe.youtubewhitelist.R

data class ProfileCreationUiState(
    val name: String = "",
    val error: UiMessage? = null,
    val createdProfileId: String? = null
)

@HiltViewModel
class ProfileCreationViewModel @Inject constructor(
    private val kidProfileRepository: KidProfileRepository,
    private val parentAccountRepository: ParentAccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileCreationUiState())
    val uiState: StateFlow<ProfileCreationUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onCreateProfile() {
        val name = _uiState.value.name.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(error = UiMessage(R.string.profile_error_name_empty)) }
            return
        }

        viewModelScope.launch {
            val account = parentAccountRepository.getAccount().first()
            if (account == null) {
                _uiState.update { it.copy(error = UiMessage(R.string.profile_error_no_account)) }
                return@launch
            }

            val profile = kidProfileRepository.createProfile(
                parentId = account.id,
                name = name,
                avatarUrl = null
            )
            _uiState.update { it.copy(createdProfileId = profile.id) }
        }
    }
}
