package com.finsight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.finsight.ui.AppState
import com.finsight.ui.PlatformBackHandler
import com.finsight.ui.designsystem.*

private class OnboardingStep(val overline: String, val title: String, val tone: NoticeTone?, val intro: String, val points: List<String>, val footer: String?)

private val steps = listOf(
    OnboardingStep(
        "What FinSight reads", "Only transaction notifications", null,
        "FinSight builds your money picture from the alerts your bank and wallet apps already show you.",
        listOf(
            "Transaction notifications from your SMS app",
            "Notifications from the wallet and bank apps you allow",
            "Messages you share in or type yourself",
        ),
        "FinSight never opens your SMS inbox and does not ask for the READ_SMS permission.",
    ),
    OnboardingStep(
        "What stays on your phone", "Your details never leave the device", NoticeTone.Success,
        "Parsing, categories and every number are worked out on this phone. These never leave it:",
        listOf(
            "Raw messages and notifications",
            "Merchant names and people you pay",
            "Account and card numbers",
            "Exact balances",
        ),
        "Cloud AI is optional. It only receives redacted facts (rounded totals, categories) and you can switch it off any time in Privacy.",
    ),
    OnboardingStep(
        "Before you start", "Education, not financial advice", NoticeTone.Warning,
        "FinSight helps you understand your spending and plan ahead. It is an educational tool.",
        listOf(
            "It does not give investment, tax or legal advice",
            "Check important decisions with a qualified professional",
            "You stay in control: review or correct any transaction",
        ),
        null,
    ),
)

/** First-launch onboarding with the Google Play prominent disclosure (plan 6.4). */
@Composable
fun OnboardingScreen(state: AppState) {
    var step by remember { mutableStateOf(0) }
    val s = steps[step]
    val last = step == steps.lastIndex
    PlatformBackHandler(enabled = step > 0) { step-- }

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.rowGapVertical),
        ) {
            ScreenHeader("Welcome to FinSight", "Private money insights from your transaction alerts") {
                FsTag("${step + 1} of ${steps.size}", TagTone.Brand)
            }
            FsCard(tone = s.tone) {
                Overline(s.overline)
                Spacer(Modifier.height(Spacing.s2))
                Text(s.title, style = FsTheme.type.text.titleMedium, color = FsColors.role.content.primary)
                Spacer(Modifier.height(Spacing.s2))
                Text(s.intro, style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.secondary)
                Spacer(Modifier.height(Spacing.s3))
                s.points.forEach { point ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s1)) {
                        Text("•", style = FsTheme.type.text.bodyMedium, color = FsColors.brandText)
                        Spacer(Modifier.width(Spacing.s2))
                        Text(point, style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.primary)
                    }
                }
                s.footer?.let {
                    Spacer(Modifier.height(Spacing.s3))
                    Text(it, style = FsTheme.type.text.caption, color = FsColors.role.content.secondary)
                }
            }
            if (last) {
                FsCard {
                    Text(
                        "Allow notification access so FinSight can read transaction alerts as they arrive, or try it first with sample bank and wallet messages.",
                        style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.secondary,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s2))
        }

        Column(
            Modifier.fillMaxWidth().padding(vertical = Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.targetGap),
        ) {
            if (last) {
                FsButton("Allow notification access", {
                    state.openNotificationSettings(); state.onboarded.value = true
                }, Modifier.fillMaxWidth())
                FsButton("Try with demo data", {
                    state.repo.importBatch(state.loadDemo()); state.onboarded.value = true
                }, Modifier.fillMaxWidth(), variant = FsButtonVariant.Secondary)
            } else {
                FsButton("Next", { step++ }, Modifier.fillMaxWidth())
            }
            if (step > 0) FsButton("Back", { step-- }, Modifier.fillMaxWidth(), variant = FsButtonVariant.Text)
        }
    }
}
