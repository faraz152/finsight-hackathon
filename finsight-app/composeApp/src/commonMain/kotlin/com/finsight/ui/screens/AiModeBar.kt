package com.finsight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.finsight.ui.AppState
import com.finsight.ui.designsystem.*

/**
 * Global AI mode, shown at the top of every screen.
 * On-device: nothing ever leaves the phone. Hybrid: on-device first; cloud Gemma only gets redacted facts.
 * Same setting as Privacy → Cloud AI (state.cloudEnabled, persisted).
 */
@Composable
fun AiModeBar(state: AppState, modifier: Modifier = Modifier) {
    val hybrid by state.cloudEnabled.collectAsState()
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("AI mode")
            Spacer(Modifier.weight(1f))
            FsTag(
                when {
                    !hybrid -> "Nothing leaves this phone"
                    state.cloud.configured -> "Cloud gets redacted facts only"
                    else -> "No cloud key: on-device"
                },
                if (hybrid) TagTone.Brand else TagTone.Success,
            )
        }
        Spacer(Modifier.height(Spacing.s1))
        FsSegmented(
            options = listOf("🔒 On-device", "✦ Hybrid"),
            selected = if (hybrid) 1 else 0,
            onSelect = { state.cloudEnabled.value = it == 1 },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
