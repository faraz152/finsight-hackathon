package com.finsight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.finsight.ui.AppState
import com.finsight.ui.designsystem.*

// Pipeline accuracy, rules only (from docs/ACCURACY.md; update both together).
private const val ACC_TEMPLATES_GLOBAL = 59      // sms_test_templates.json (PK, IN, AE, GB, US, CA)
private const val ACC_TEMPLATES_PK = 38          // pk_extra_templates.json (Pakistan only)
private const val ACC_AMOUNT_PCT = 100
private const val ACC_DIRECTION_PCT = 100
private const val ACC_MERCHANT_PCT = 100
private const val ACC_ADVERSARIAL_PASSING = 12   // AdversarialTest

@Composable
fun PrivacyScreen(state: AppState) {
    val cloudOn by state.cloudEnabled.collectAsState()
    val log by state.log.entries.collectAsState()
    val txns by state.repo.transactions.collectAsState()
    var access by remember { mutableStateOf(state.hasNotificationAccess()) }
    val type = FsTheme.type

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.rowGapVertical)) {
        ScreenHeader("Privacy Center", "See and control exactly what FinSight uses")
        FsCard {
            SectionHeader("What FinSight reads")
            Text("Only transaction notifications from SMS apps and the wallet apps you allow (JazzCash, Easypaisa). Everything else is ignored in memory and never stored. FinSight never reads your SMS inbox (no READ_SMS permission).",
                style = type.text.bodyMedium, color = FsColors.role.content.secondary)
            Spacer(Modifier.height(Spacing.s3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FsTag(if (access) "Capture: ON" else "Capture: OFF", if (access) TagTone.Success else TagTone.Warning)
                Spacer(Modifier.weight(1f))
                FsButton(if (access) "Manage" else "Enable", { state.openNotificationSettings() }, small = true)
            }
            FsButton("Refresh status", { access = state.hasNotificationAccess() }, variant = FsButtonVariant.Text)
        }
        FsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionHeader("Cloud AI (hybrid Coach)")
                    Text(if (cloudOn) "Automatic: on-device first; only redacted facts go to Gemini when knowledge is needed." else "Off: Coach answers on this phone only.",
                        style = type.text.bodyMedium, color = FsColors.role.content.secondary)
                    Text(if (state.cloud.configured) "Model: ${state.cloud.modelName}" else "No cloud key configured — on-device only", style = type.text.caption, color = FsColors.role.content.tertiary)
                }
                Switch(cloudOn, { state.cloudEnabled.value = it }, colors = SwitchDefaults.colors(
                    checkedThumbColor = FsColors.brandOnFill, checkedTrackColor = FsColors.brandFill,
                    uncheckedThumbColor = FsColors.textTertiary, uncheckedTrackColor = FsColors.subtle, uncheckedBorderColor = FsColors.borderStrong,
                ))
            }
        }
        FsCard(tone = NoticeTone.Success) {
            SectionHeader("Never leaves this phone")
            Text("Raw messages · merchant names · people's names · account and card numbers · phone numbers · exact balances and dates · your transaction list",
                style = type.text.bodyMedium, color = FsColors.role.content.secondary)
        }
        FsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Cloud log (${log.size})", Modifier.weight(1f))
                FsButton("Clear", { state.log.clear() }, variant = FsButtonVariant.Text)
            }
            if (log.isEmpty()) Text("No cloud requests yet.", style = type.text.bodyMedium, color = FsColors.role.content.secondary)
            log.take(10).forEach { e ->
                Text("${e.route} · ${e.model} · ${e.latencyMs} ms · ${e.status}", style = type.text.caption, color = FsColors.role.content.secondary, modifier = Modifier.padding(top = Spacing.s2, bottom = Spacing.s1))
                CodeBox(e.payload)
            }
        }
        FsCard {
            SectionHeader("How accurate is FinSight?")
            KeyValueRow("Labelled test messages", "$ACC_TEMPLATES_GLOBAL + $ACC_TEMPLATES_PK Pakistani")
            KeyValueRow("Amount correct", "$ACC_AMOUNT_PCT%")
            KeyValueRow("Direction (in / out) correct", "$ACC_DIRECTION_PCT%")
            KeyValueRow("Merchant correct", "$ACC_MERCHANT_PCT%")
            KeyValueRow("Adversarial tests", "$ACC_ADVERSARIAL_PASSING passing", FsColors.role.change.upContent)
            val review = txns.count { it.needsReview }
            KeyValueRow("Sent to review on this phone",
                if (txns.isEmpty()) "0/0" else "$review/${txns.size} · ${review * 100 / txns.size}%")
            Text("Measured on labelled test messages; real inboxes vary.", style = type.text.caption, color = FsColors.role.content.tertiary,
                modifier = Modifier.padding(top = Spacing.s2))
        }
        FsCard {
            SectionHeader("Your data")
            Text("${txns.size} records stored only in this app's private storage. Backup to cloud is disabled.", style = type.text.bodyMedium, color = FsColors.role.content.secondary)
            Spacer(Modifier.height(Spacing.s3))
            FsButton("Delete all local data", { state.repo.clearAll(); state.log.clear() }, Modifier.fillMaxWidth(), variant = FsButtonVariant.Destructive)
        }
        Spacer(Modifier.height(Spacing.s6))
    }
}
