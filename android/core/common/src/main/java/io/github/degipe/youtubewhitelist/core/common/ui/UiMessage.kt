package io.github.degipe.youtubewhitelist.core.common.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A message a ViewModel wants to show, kept as a resource id instead of ready
 * text. The wording is resolved in the UI, so it always follows the language the
 * device is set to — and ViewModels stay free of Context.
 */
data class UiMessage(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList()
) {
    constructor(@StringRes resId: Int, vararg args: Any) : this(resId, args.toList())
}

@Composable
fun UiMessage.text(): String = stringResource(resId, *args.toTypedArray())
