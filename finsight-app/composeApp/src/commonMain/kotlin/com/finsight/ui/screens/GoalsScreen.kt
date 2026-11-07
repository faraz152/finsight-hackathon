package com.finsight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.finsight.coach.WhatIf
import com.finsight.domain.formatPkr
import com.finsight.ui.AppState
import com.finsight.ui.designsystem.*

@Composable
fun GoalsScreen(state: AppState, onBack: () -> Unit) {
    var price by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<com.finsight.coach.WhatIfResult?>(null) }
    val recurring = state.tools.recurringPayments()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.rowGapVertical),
    ) {
        ScreenHeader("Goals & bills", "Recurring payments and purchase planning") {
            FsButton("Back", onBack, variant = FsButtonVariant.Text, small = true)
        }

        SectionHeader("Recurring payments")
        if (recurring.isEmpty()) {
            EmptyState("No recurring payments yet", "Import more transactions across different months to spot regular bills.")
        } else {
            recurring.forEach { payment ->
                FsCard {
                    Text(payment.name, style = FsTheme.type.text.titleSmall, color = FsColors.role.content.primary)
                    Spacer(Modifier.height(Spacing.s1))
                    AmountText(formatPkr(payment.avgMinor), style = TextStyleSize.Title)
                    Text("around day ${payment.dayOfMonth}", style = FsTheme.type.numeric.caption, color = FsColors.role.content.secondary)
                }
            }
        }

        FsCard(tone = NoticeTone.Info) {
            SectionHeader("What If?")
            Text("Check how a purchase fits your recent income, spending and balance.", style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.secondary)
            Spacer(Modifier.height(Spacing.s2))
            FsTextField(price, { price = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, "Price in PKR", Modifier.fillMaxWidth())
            FsButton("Check", {
                parseMinor(price)?.takeIf { it > 0 }?.let { result = WhatIf.afford(state.tools, it) }
            }, Modifier.fillMaxWidth())
            result?.let { answer ->
                Text(answer.verdict, style = FsTheme.type.text.titleSmall, color = FsColors.role.content.primary)
                answer.lines.forEach { line ->
                    Text(line, style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.secondary)
                }
            }
        }
        Spacer(Modifier.height(Spacing.s6))
    }
}

/** Parses a user-entered PKR amount into minor units without floating-point rounding. */
private fun parseMinor(text: String): Long? {
    val parts = text.trim().split('.')
    if (parts.isEmpty() || parts.size > 2 || parts[0].isEmpty()) return null
    if ((parts.getOrNull(1)?.length ?: 0) > 2) return null
    if (parts.any { it.isNotEmpty() && it.any { c -> !c.isDigit() } }) return null
    val whole = parts[0].toLongOrNull() ?: return null
    val fraction = (parts.getOrNull(1) ?: "").padEnd(2, '0').take(2).toLongOrNull() ?: 0L
    return whole * 100 + fraction
}
