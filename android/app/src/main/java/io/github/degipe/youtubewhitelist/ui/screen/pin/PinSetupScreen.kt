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
fun PinSetupScreen(
    sideInput: SideInputChannel,
    onPinSet: () -> Unit,
    viewModel: PinSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            onPinSet()
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
        title = when (uiState.step) {
            PinSetupStep.ENTER_NEW -> stringResource(R.string.pin_setup_title)
            PinSetupStep.CONFIRM -> stringResource(R.string.pin_confirm_title)
        },
        enteredDigits = uiState.pin.length,
        maxDigits = PinEntryViewModel.MAX_PIN_LENGTH,
        message = uiState.error?.text() ?: when (uiState.step) {
            PinSetupStep.ENTER_NEW -> stringResource(R.string.pin_setup_subtitle)
            PinSetupStep.CONFIRM -> stringResource(R.string.pin_confirm_subtitle)
        },
        shakeTrigger = shakeTrigger,
        canConfirm = uiState.pin.length >= PinEntryViewModel.MIN_PIN_LENGTH,
        onDigit = viewModel::onDigitEntered,
        onBackspace = viewModel::onBackspace,
        onConfirm = viewModel::onSubmit
    )
}
