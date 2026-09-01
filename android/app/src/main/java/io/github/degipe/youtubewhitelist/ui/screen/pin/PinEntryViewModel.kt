package io.github.degipe.youtubewhitelist.ui.screen.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.degipe.youtubewhitelist.core.data.model.PinVerificationResult
import io.github.degipe.youtubewhitelist.core.data.repository.PinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.github.degipe.youtubewhitelist.core.common.ui.UiMessage
import io.github.degipe.youtubewhitelist.R

data class PinEntryUiState(
    val pin: String = "",
    val error: UiMessage? = null,
    val isVerified: Boolean = false,
    val isLockedOut: Boolean = false,
    val lockoutSeconds: Int = 0,
    val attemptsRemaining: Int? = null
)

@HiltViewModel
class PinEntryViewModel @Inject constructor(
    private val pinRepository: PinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinEntryUiState())
    val uiState: StateFlow<PinEntryUiState> = _uiState.asStateFlow()

    fun onDigitEntered(digit: Int) {
        _uiState.update { state ->
            if (state.pin.length < MAX_PIN_LENGTH) {
                state.copy(pin = state.pin + digit, error = null)
            } else {
                state
            }
        }
    }

    fun onBackspace() {
        _uiState.update { state ->
            state.copy(pin = state.pin.dropLast(1), error = null)
        }
    }

    fun onSubmit() {
        val pin = _uiState.value.pin
        if (pin.length < MIN_PIN_LENGTH) return

        viewModelScope.launch {
            when (val result = pinRepository.verifyPin(pin)) {
                is PinVerificationResult.Success -> {
                    _uiState.update { it.copy(isVerified = true, error = null) }
                }
                is PinVerificationResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            pin = "",
                            error = UiMessage(R.string.pin_error_wrong),
                            attemptsRemaining = result.attemptsRemaining
                        )
                    }
                }
                is PinVerificationResult.LockedOut -> {
                    _uiState.update {
                        it.copy(
                            pin = "",
                            isLockedOut = true,
                            lockoutSeconds = result.remainingSeconds,
                            error = UiMessage(R.string.pin_error_locked, result.remainingSeconds)
                        )
                    }
                }
            }
        }
    }

    companion object {
        /** Shared with the PIN screen so the dot row matches the accepted length. */
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 6
    }
}
