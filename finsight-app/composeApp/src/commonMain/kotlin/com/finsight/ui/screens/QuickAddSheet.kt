package com.finsight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.finsight.pipeline.PipelineResult
import com.finsight.ui.AppState
import com.finsight.ui.designsystem.*

/** Opened from the centre hexagon → "Add transaction". Same pipeline as SMS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(state: AppState, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    fun add() {
        if (text.isBlank()) return
        result = when (val r = state.quickAdd(text)) {
            is PipelineResult.Accepted -> "Added ${r.txn.merchant} ${r.txn.amount} → ${r.txn.category.label} (${(r.txn.confidence * 100).toInt()}%)"
            is PipelineResult.Dropped -> "Not added: ${r.reason}"
            is PipelineResult.ScamWarning -> "Blocked: ${r.reason}"
        }
        text = ""
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = FsColors.role.fill.sheet, scrimColor = FsColors.scrim) {
        Column(Modifier.fillMaxWidth().padding(Spacing.sheetPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Text("Add transaction", style = FsTheme.type.text.titleLarge, color = FsColors.role.content.primary)
            Text("Type what you paid, the amount and how. FinSight categorizes it with the same rules as your bank messages.",
                style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.secondary)
            FsTextField(text, { text = it }, "lunch 850 card", Modifier.fillMaxWidth(), onDone = ::add)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                listOf("chai 150 cash", "careem 650 card", "petrol 5000 cash").forEach { s -> FsChip(s, onClick = { text = s }) }
            }
            FsButton("Add", ::add, Modifier.fillMaxWidth())
            result?.let { FsCard(tone = if (it.startsWith("Added")) NoticeTone.Success else NoticeTone.Warning) {
                Text(it, style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.primary)
            } }
            Spacer(Modifier.height(Spacing.s6))
        }
    }
}
