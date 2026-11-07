package com.finsight.ui

import androidx.compose.runtime.Composable

/** System back gesture/button handler. Android: androidx.activity.compose.BackHandler. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
