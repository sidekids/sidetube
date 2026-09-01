package io.github.degipe.youtubewhitelist.ui.screen.pin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.degipe.youtubewhitelist.R
import io.github.degipe.youtubewhitelist.core.common.input.CollectSideInput
import io.github.degipe.youtubewhitelist.core.common.input.SideInputAction
import io.github.degipe.youtubewhitelist.core.common.input.SideInputChannel
import io.github.degipe.youtubewhitelist.core.common.ui.text
import io.github.degipe.youtubewhitelist.ui.screen.pin.v2.ParentPinPad

@Composable
fun PinChangeScreen(
    sideInput: SideInputChannel,
    onPinChanged: () -> Unit,
    onCancel: () -> Unit,
    viewModel: PinChangeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The change screen had no key handling at all — on a phone whose keypad is
    // the main input that made it unusable.
    CollectSideInput(sideInput) { action ->
        when (action) {
            is SideInputAction.Digit -> viewModel.onDigitEntered(action.value)
            SideInputAction.Backspace -> viewModel.onBackspace()
            SideInputAction.Select -> viewModel.onSubmit()
            else -> Unit
        }
    }

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            onPinChanged()
        }
    }

    LaunchedEffect(uiState.pin.length) {
        if (uiState.pin.length == PinEntryViewModel.MAX_PIN_LENGTH) {
            viewModel.onSubmit()
        }
    }

    var shakeTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) shakeTrigger++
    }

    ParentPinPad(
        title = stringResource(R.string.pin_change_title),
        enteredDigits = uiState.pin.length,
        maxDigits = PinEntryViewModel.MAX_PIN_LENGTH,
        message = uiState.error?.text() ?: when (uiState.step) {
            PinChangeStep.VERIFY_OLD -> stringResource(R.string.pin_change_verify_subtitle)
            PinChangeStep.ENTER_NEW -> stringResource(R.string.pin_change_new_subtitle)
            PinChangeStep.CONFIRM_NEW -> stringResource(R.string.pin_confirm_subtitle)
        },
        shakeTrigger = shakeTrigger,
        canConfirm = uiState.pin.length >= PinEntryViewModel.MIN_PIN_LENGTH && !uiState.isLockedOut,
        onDigit = viewModel::onDigitEntered,
        onBackspace = viewModel::onBackspace,
        onConfirm = viewModel::onSubmit
    )
}
