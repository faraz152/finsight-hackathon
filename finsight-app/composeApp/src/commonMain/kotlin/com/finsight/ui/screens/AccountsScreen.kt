package com.finsight.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.finsight.domain.Account
import com.finsight.domain.AccountType
import com.finsight.domain.formatPkr
import com.finsight.ui.AppState
import com.finsight.ui.designsystem.*

private const val DAY = 86_400_000L

/** Accounts, wallets, cards and cash detected from messages (old FinSight account management). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(state: AppState, onBack: () -> Unit) {
    val accounts by state.repo.accounts.collectAsState()
    val txns by state.repo.transactions.collectAsState()
    val counts = remember(txns) { state.repo.transactionCounts() }
    val total = remember(accounts, txns) { state.tools.latestBalance() }
    var open by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.rowGapVertical),
    ) {
        ScreenHeader("Accounts", "Banks, wallets, cards and cash") {
            FsButton("Back", onBack, variant = FsButtonVariant.Text, small = true)
        }
        FsCard {
            Overline("Total known balance")
            AmountText(total?.let { formatPkr(it) } ?: "Unknown", style = TextStyleSize.Hero)
            Text("Latest balance from messages of accounts marked Mine. Accounts without a balance count as unknown, not zero.",
                style = FsTheme.type.text.caption, color = FsColors.role.content.tertiary)
        }
        if (accounts.isEmpty()) {
            EmptyState("No accounts yet", "Import messages or add a cash wallet. FinSight detects banks, wallets and cards from your SMS.") {
                FsButton("Add cash wallet", { state.repo.addCashAccount(state.now()) }, small = true)
            }
        } else {
            SectionHeader("Your accounts")
            accounts.forEach { a -> AccountCard(a, counts[a.id] ?: 0, state.now(), onMine = { state.repo.setMine(a.id, it) }) { open = a.id } }
            FsButton("Add cash wallet", { state.repo.addCashAccount(state.now()) }, Modifier.fillMaxWidth(), variant = FsButtonVariant.Secondary)
        }
        Spacer(Modifier.height(Spacing.s6))
    }

    val sel = accounts.firstOrNull { it.id == open }
    if (sel != null) ModalBottomSheet(onDismissRequest = { open = null }, containerColor = FsColors.role.fill.sheet, scrimColor = FsColors.scrim) {
        AccountSheet(state, sel, accounts.filter { it.id != sel.id }) { open = null }
    }
}

@Composable
private fun AccountCard(a: Account, count: Int, now: Long, onMine: (Boolean) -> Unit, onClick: () -> Unit) {
    val type = FsTheme.type
    FsCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text(a.displayName, style = type.text.titleSmall, color = FsColors.role.content.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
                    FsTag(a.type.label, typeTone(a.type))
                    if (a.nickname != null) Text(a.institution, style = type.text.caption, color = FsColors.role.content.secondary)
                    a.last4?.let { Text("••$it", style = type.numeric.caption, color = FsColors.role.content.secondary) }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Mine", style = type.text.caption, color = FsColors.role.content.tertiary)
                MineSwitch(a.isMine, onMine)
            }
        }
        Spacer(Modifier.height(Spacing.s2))
        AmountText(a.balanceMinor?.let { formatPkr(it, a.currency) } ?: "Balance unknown", style = TextStyleSize.Title)
        Text(
            listOfNotNull(a.balanceAt?.let { "as of ${asOf(it, now)}" }, "$count transaction" + if (count == 1) "" else "s").joinToString(" · "),
            style = type.numeric.caption, color = FsColors.role.content.tertiary,
        )
    }
}

@Composable
private fun MineSwitch(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(checked, onChange, colors = SwitchDefaults.colors(
    checkedThumbColor = FsColors.brandOnFill, checkedTrackColor = FsColors.brandFill,
    uncheckedThumbColor = FsColors.textTertiary, uncheckedTrackColor = FsColors.subtle, uncheckedBorderColor = FsColors.borderStrong,
))

@Composable
private fun AccountSheet(state: AppState, a: Account, others: List<Account>, onClose: () -> Unit) {
    val type = FsTheme.type
    var name by remember(a.id) { mutableStateOf(a.nickname ?: "") }
    var confirmDelete by remember(a.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.sheetPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Text(a.displayName, style = type.text.titleLarge, color = FsColors.role.content.primary)
        KeyValueRow("Institution", a.institution)
        KeyValueRow("Type", a.type.label)
        a.last4?.let { KeyValueRow("Digits", "••$it") }

        Overline("Nickname")
        FsTextField(name, { name = it.take(32) }, "e.g. Salary account", Modifier.fillMaxWidth(), onDone = { state.repo.renameAccount(a.id, name) })
        FsButton("Save name", { state.repo.renameAccount(a.id, name) }, Modifier.fillMaxWidth(), variant = FsButtonVariant.Secondary)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("This is my account", style = type.text.titleSmall, color = FsColors.role.content.primary)
                Text("Counts in your total balance; money moved between your accounts is a transfer, not spending.",
                    style = type.text.caption, color = FsColors.role.content.secondary)
            }
            MineSwitch(a.isMine) { state.repo.setMine(a.id, it) }
        }

        if (others.isNotEmpty()) {
            Overline("Same account twice? Merge into")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                others.forEach { o -> FsChip(o.displayName, onClick = { state.repo.mergeAccounts(a.id, o.id); onClose() }) }
            }
        }

        if (!confirmDelete) FsButton("Delete account (transactions stay)", { confirmDelete = true }, Modifier.fillMaxWidth(), variant = FsButtonVariant.Destructive)
        else FsCard(tone = NoticeTone.Warning) {
            Text("Remove ${a.displayName}? Its transactions are kept but no longer linked to an account.",
                style = type.text.bodyMedium, color = FsColors.role.content.primary)
            Spacer(Modifier.height(Spacing.s2))
            FsButton("Delete", { state.repo.deleteAccount(a.id); onClose() }, Modifier.fillMaxWidth(), variant = FsButtonVariant.Destructive)
        }
        Spacer(Modifier.height(Spacing.s6))
    }
}

private fun typeTone(t: AccountType) = when (t) {
    AccountType.BANK -> TagTone.Info
    AccountType.WALLET -> TagTone.Brand
    AccountType.CARD -> TagTone.Warning
    AccountType.CASH -> TagTone.Success
}

private fun asOf(at: Long, now: Long): String = when (val d = ((now - at) / DAY).coerceAtLeast(0)) {
    0L -> "today"
    1L -> "yesterday"
    else -> "$d days ago"
}
