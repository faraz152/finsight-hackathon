package com.finsight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.finsight.domain.*
import com.finsight.ui.AppState
import com.finsight.ui.bandColor
import com.finsight.ui.designsystem.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(state: AppState) {
    val txns by state.repo.transactions.collectAsState()
    var reviewOnly by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Long?>(null) }
    val shown = if (reviewOnly) txns.filter { it.needsReview } else txns

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter)) {
        ScreenHeader("Transactions", "${txns.size} records · tap one to see why") {
            FsChip("Review (${txns.count { it.needsReview }})", selected = reviewOnly, onClick = { reviewOnly = !reviewOnly })
        }
        if (shown.isEmpty()) EmptyState(
            if (reviewOnly) "Nothing to review" else "No transactions yet",
            if (reviewOnly) "Everything is confident or confirmed by you." else "Import demo SMS on Home, or enable notification access in Privacy.",
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            items(shown, key = { it.id }) { t -> TxnRow(t) { open = t.id } }
            item { Spacer(Modifier.height(Spacing.s6)) }
        }
    }
    val sel = txns.firstOrNull { it.id == open }
    if (sel != null) ModalBottomSheet(onDismissRequest = { open = null }, containerColor = FsColors.role.fill.sheet, scrimColor = FsColors.scrim) {
        WhySheet(sel, onCorrect = { c -> state.repo.correct(sel.id, c) }, onDelete = { state.repo.delete(sel.id); open = null })
    }
}

@Composable
fun TxnRow(t: Transaction, onClick: () -> Unit) {
    EdgeSurface(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(Spacing.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BandDot(bandColor(t.band))
            Column(Modifier.weight(1f).padding(horizontal = Spacing.s3)) {
                Text(t.merchant ?: t.category.label, style = FsTheme.type.text.titleSmall, color = FsColors.role.content.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1), modifier = Modifier.padding(top = Spacing.s1)) {
                    FsTag(t.category.label)
                    FsTag(sourceBadge(t), TagTone.Brand)
                    if (t.kind != TxnKind.EXPENSE && t.kind != TxnKind.INCOME) FsTag(t.kind.name.replace('_', ' ').lowercase(), TagTone.Info)
                    if (t.evidenceCount > 1) FsTag("dup ×${t.evidenceCount}", TagTone.Warning)
                }
            }
            AmountText((if (t.type == TxnType.CREDIT) "+" else "−") + t.amount.toString().substringAfter(' '), credit = t.type == TxnType.CREDIT)
        }
    }
}

fun sourceBadge(t: Transaction) = when (t.categorySource) {
    CategorySource.USER_MEMORY, CategorySource.USER -> "You"
    CategorySource.LLM -> "Gemma"
    CategorySource.HYBRID -> "Rule+Gemma"
    else -> if (t.channel == Channel.MANUAL) "You" else "Rule"
}

@Composable
fun WhySheet(t: Transaction, onCorrect: (Category) -> Unit, onDelete: () -> Unit) {
    val type = FsTheme.type
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.sheetPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Text("Why?", style = type.text.titleLarge, color = FsColors.role.content.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            FsTag("${(t.confidence * 100).toInt()}% confidence", when (t.band) { Band.GREEN -> TagTone.Success; Band.YELLOW -> TagTone.Warning; Band.RED -> TagTone.Error })
            FsTag(sourceBadge(t), TagTone.Brand)
            FsTag(t.channel.name.lowercase())
        }
        KeyValueRow("Amount", t.amount.toString(), if (t.type == TxnType.CREDIT) FsColors.role.change.upContent else FsColors.role.content.primary)
        KeyValueRow("Type", "${t.type.name.lowercase()} · ${t.kind.name.lowercase().replace('_', ' ')}")
        t.accountLast4?.let { KeyValueRow("Account", "••$it") }
        t.balance?.let { KeyValueRow("Balance after", it.toString()) }
        SectionHeader("Checks", Modifier.padding(top = Spacing.s2))
        t.explanation.forEach { Text("✓ $it", style = type.text.bodyMedium, color = FsColors.role.content.primary) }
        Overline("Original text (stays on this phone)")
        CodeBox(t.rawText)
        Overline("Correct category (FinSight remembers this merchant)")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            items(Category.entries) { c -> FsChip(c.label, selected = t.category == c, onClick = { onCorrect(c) }) }
        }
        FsButton("Delete this record and its raw text", onDelete, Modifier.fillMaxWidth().padding(top = Spacing.s2), variant = FsButtonVariant.Destructive)
        Spacer(Modifier.height(Spacing.s6))
    }
}
