package com.reinchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The web client's flat paper canvas; kept as a composable for call-site compatibility. */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}
