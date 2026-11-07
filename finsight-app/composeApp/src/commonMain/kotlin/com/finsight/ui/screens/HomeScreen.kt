package com.finsight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.finsight.coach.HealthScore
import com.finsight.coach.Pulse
import com.finsight.domain.formatPkr
import com.finsight.pipeline.PipelineResult
import com.finsight.ui.AppState
import com.finsight.ui.Tab
import com.finsight.ui.designsystem.*

@Composable
fun HomeScreen(state: AppState, go: (Tab) -> Unit) {
    val txns by state.repo.transactions.collectAsState()
    val last by state.repo.lastImport.collectAsState()
    val scams by state.repo.scams.collectAsState()
    val profile by state.profile.collectAsState()
    val p = state.tools.period("30")
    val coverage = remember(txns) { state.repo.coverage() }
    val health = remember(txns, profile) { HealthScore.compute(state.tools, profile, coverage.percent) }
    val pulse = remember(txns) { Pulse.cards(state.tools) }
    var quick by remember { mutableStateOf("") }
    var quickMsg by remember { mutableStateOf<String?>(null) }
    val type = FsTheme.type

    fun addQuick() {
        if (quick.isBlank()) return
        quickMsg = when (val r = state.quickAdd(quick)) {
            is PipelineResult.Accepted -> "Added: ${r.txn.merchant} ${r.txn.amount} → ${r.txn.category.label}"
            is PipelineResult.Dropped -> "Not added: ${r.reason}"
            is PipelineResult.ScamWarning -> "Blocked: ${r.reason}"
        }
        quick = ""
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.s2),
        verticalArrangement = Arrangement.spacedBy(Spacing.rowGapVertical),
    ) {
        ScreenHeader("FinSight", "Your money. Your intelligence. On your phone.")

        FsCard {
            Overline("This month's spending")
            AmountText(formatPkr(state.tools.totalSpend(p)), style = TextStyleSize.Hero)
            Text("Income ${formatPkr(state.tools.totalIncome(p))} · ${txns.size} records", style = type.text.bodyMedium, color = FsColors.role.content.secondary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            val hColor = when { health.score == null -> FsColors.role.content.secondary; health.score >= 80 -> FsColors.role.change.upContent; health.score >= 60 -> FsColors.role.status.warningContent; else -> FsColors.role.status.errorContent }
            MetricTile("Financial health", health.score?.let { "$it/100" } ?: "—", "${health.band} · ${health.used}/6 checks", hColor, Modifier.weight(1f)) { go(Tab.COACH) }
            MetricTile("Data coverage", coverage.percent?.let { "$it%" } ?: "—", if ((coverage.percent ?: 0) < 100) "Partial data" else "Complete", FsColors.role.status.infoContent, Modifier.weight(1f))
        }
        if (txns.isNotEmpty()) Text(coverage.basis, style = type.text.caption, color = FsColors.role.content.tertiary)

        pulse.firstOrNull()?.let { c ->
            FsCard(tone = NoticeTone.Info) {
                Text("FinSight Pulse · ${c.title}", style = type.text.labelSmall, color = FsColors.role.status.infoContent)
                Text(c.body, style = type.text.bodyLarge, color = FsColors.role.content.primary)
                Text("Why: ${c.why}", style = type.text.caption, color = FsColors.role.content.tertiary)
            }
        }
        scams.firstOrNull()?.let {
            FsCard(tone = NoticeTone.Error) {
                Text("Possible scam message blocked", style = type.text.labelMedium, color = FsColors.role.status.errorContent)
                Text(it, style = type.text.caption, color = FsColors.role.content.secondary, maxLines = 2)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            FsButton("Import demo SMS", { state.repo.importBatch(state.loadDemo()) }, Modifier.weight(1f))
            FsButton("Review inbox", { go(Tab.TXNS) }, Modifier.weight(1f), variant = FsButtonVariant.Secondary)
        }
        last?.let {
            Text("Imported: ${it.autoAccepted} auto-accepted by rules · ${it.review} need review · ${it.duplicates} duplicates merged · ${it.dropped} dropped · ${it.scams} scams",
                style = type.text.caption, color = FsColors.role.content.secondary)
        }

        FsCard {
            Overline("Quick add")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                FsTextField(quick, { quick = it }, "chai 150 cash", Modifier.weight(1f), onDone = ::addQuick)
                FsButton("Add", ::addQuick, small = true)
            }
            quickMsg?.let { Text(it, style = type.text.caption, color = FsColors.role.content.secondary) }
        }

        val cats = state.tools.spendByCategory(p)
        if (cats.isNotEmpty()) FsCard {
            SectionHeader("Where it went (last 30 days)")
            ShareBars(cats.take(6).map { it.category.label to it.minor }) { formatPkr(it).removePrefix("PKR ") }
        }
        Spacer(Modifier.height(Spacing.s6))
    }
}
