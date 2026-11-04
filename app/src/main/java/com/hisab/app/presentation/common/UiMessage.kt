package com.hisab.app.presentation.common

import androidx.annotation.StringRes

/** A user-facing message as a string resource plus formatting args, so ViewModels never touch Context. */
data class UiMessage(@StringRes val res: Int, val args: List<Any> = emptyList())
