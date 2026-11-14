package com.reinchat.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared between the content and chrome ComposeViews. */
object ChromeMetrics {
    var composerHeightPx by mutableStateOf(0)
}
