package com.finsight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.finsight.coach.HealthCheck
import com.finsight.coach.HealthResult
import com.finsight.coach.Profile
import com.finsight.ui.designsystem.*

/** Band colour for a 0..100 health score: green ≥80, yellow ≥60, red otherwise; neutral when unknown. */
fun healthBandColor(score: Int?): Color = when {
    score == null -> FsColors.role.content.secondary
    score >= 80 -> FsColors.role.change.upContent
    score >= 60 -> FsColors.role.status.warningContent
    else -> FsColors.role.status.errorContent
}

/** Financial Health 2.0 detail (plan 6.5): score, profile switch, every check explained, next steps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthSheet(health: HealthResult, profile: Profile, onProfile: (Profile) -> Unit, onClose: () -> Unit) {
    val type = FsTheme.type
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FsColors.role.fill.sheet,
        scrimColor = FsColors.scrim,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.sheetPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.rowGapVertical),
        ) {
            Text("Financial health", style = type.text.titleLarge, color = FsColors.role.content.primary)

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text(health.score?.toString() ?: "—", style = type.numeric.hero, color = healthBandColor(health.score))
                if (health.score != null) Text("/100", style = type.numeric.title, color = FsColors.role.content.tertiary, modifier = Modifier.padding(bottom = Spacing.s1))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                BandDot(healthBandColor(health.score))
                Text("${health.band} · based on ${health.used} of 6 checks", style = type.text.bodyMedium, color = FsColors.role.content.secondary)
            }

            Overline("Your profile")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Profile.entries.forEach { p -> FsChip(p.label, selected = p == profile, onClick = { onProfile(p) }) }
            }

            FsCard {
                SectionHeader("Checks")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                    health.checks.forEach { CheckRow(it) }
                }
            }

            BulletList("Good", health.good)
            BulletList("Needs attention", health.attention)
            BulletList("Next steps", health.actions)

            Text("Education, not financial advice.", style = type.text.caption, color = FsColors.role.content.tertiary)
            Spacer(Modifier.height(Spacing.s6))
        }
    }
}

@Composable
private fun CheckRow(c: HealthCheck) {
    val type = FsTheme.type
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        Column(Modifier.weight(1f)) {
            Text(c.name, style = type.text.labelLarge, color = FsColors.role.content.primary)
            Text(c.detail, style = type.text.caption, color = FsColors.role.content.secondary)
        }
        val s = c.score
        if (s == null) FsTag("unknown", TagTone.Neutral)
        else {
            val pct = (s * 100).toInt()
            val color = healthBandColor(pct)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text("$pct%", style = type.numeric.caption, color = color)
                Box(Modifier.width(Sizes.listRowMin).height(Sizes.barHeight).clip(RoundedCornerShape(Radius.full)).background(FsColors.role.fill.cardInset)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(s.toFloat().coerceIn(0f, 1f)).clip(RoundedCornerShape(Radius.full)).background(color))
                }
            }
        }
    }
}

@Composable
private fun BulletList(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Column {
        SectionHeader(title)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            items.forEach { Text("• $it", style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.secondary) }
        }
    }
}
