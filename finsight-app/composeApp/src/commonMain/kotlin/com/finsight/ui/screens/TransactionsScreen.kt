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

/** Filter chips on the Transactions screen; each has its own empty state. */
private enum class TxnFilter(val label: String, val emptyTitle: String, val emptyBody: String, val matches: (Transaction) -> Boolean) {
    ALL("All", "No transactions yet", "Import demo SMS on Home, or enable notification access in Privacy.", { true }),
    REVIEW("Review", "Nothing to review", "Everything is confident or confirmed by you.", { it.needsReview }),
    INCOME("Income", "No income yet", "Salary and money you receive show up here.", { it.isIncome }),
    TRANSFERS("Transfers", "No transfers", "Money sent to or received from people and your own accounts shows up here.",
        { it.kind == TxnKind.TRANSFER_OUT || it.kind == TxnKind.TRANSFER_IN }),
    REFUNDS("Refunds", "No refunds or reversals", "Refunds and reversed payments show up here. They never count as income.",
        { it.kind == TxnKind.REFUND || it.kind == TxnKind.REVERSAL }),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(state: AppState) {
    val txns by state.repo.transactions.collectAsState()
    var filter by remember { mutableStateOf(TxnFilter.ALL) }
    var open by remember { mutableStateOf<Long?>(null) }
    val shown = txns.filter(filter.matches).sortedByDescending { it.timestamp }
    val today = localDay(state.now())
    val groups = shown.groupBy { localDay(it.timestamp) }

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter)) {
        ScreenHeader("Transactions", "${txns.size} records · tap one to see why")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), modifier = Modifier.padding(bottom = Spacing.s2)) {
            items(TxnFilter.entries) { f ->
                FsChip("${f.label} (${txns.count(f.matches)})", selected = filter == f, onClick = { filter = f })
            }
        }
        if (shown.isEmpty()) EmptyState(filter.emptyTitle, filter.emptyBody)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            groups.forEach { (day, rows) ->
                item(key = "day-$day") {
                    Text(dayLabel(day, today), style = FsTheme.type.text.labelSmall, color = FsColors.role.content.tertiary,
                        modifier = Modifier.padding(top = Spacing.s3))
                }
                items(rows, key = { it.id }) { t -> TxnRow(t) { open = t.id } }
            }
            item { Spacer(Modifier.height(Spacing.s6)) }
        }
    }
    val sel = txns.firstOrNull { it.id == open }
    if (sel != null) ModalBottomSheet(onDismissRequest = { open = null }, containerColor = FsColors.role.fill.sheet, scrimColor = FsColors.scrim) {
        WhySheet(sel, onCorrect = { c -> state.repo.correct(sel.id, c) }, onDelete = { state.repo.delete(sel.id); open = null })
    }
}

// Day grouping without java.time (commonMain has no time-zone API). FinSight is Pakistan-first, so days are
// counted in PKT (UTC+5, no daylight saving) so "Today" does not flip at 5 am local time.
private const val DAY_MS = 86_400_000L
private const val LOCAL_OFFSET_MS = 5 * 3_600_000L
private val WEEKDAYS = arrayOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed") // 1970-01-01 was a Thursday
private val MONTHS = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** Days since 1970-01-01 in local (PKT) time. */
internal fun localDay(epochMs: Long): Long = (epochMs + LOCAL_OFFSET_MS).floorDiv(DAY_MS)

/** "Today", "Yesterday", "Fri 6 Nov", or "Fri 6 Nov 2025" when the year differs from today's. */
internal fun dayLabel(day: Long, today: Long): String {
    if (day == today) return "Today"
    if (day == today - 1) return "Yesterday"
    val (y, m, d) = civilFromDays(day)
    val base = "${WEEKDAYS[day.mod(7L).toInt()]} $d ${MONTHS[m - 1]}"
    return if (y == civilFromDays(today).first) base else "$base $y"
}

/** Days since epoch → (year, month 1..12, day 1..31). Howard Hinnant's civil_from_days. */
internal fun civilFromDays(days: Long): Triple<Int, Int, Int> {
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
    return Triple(y, m, d)
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
