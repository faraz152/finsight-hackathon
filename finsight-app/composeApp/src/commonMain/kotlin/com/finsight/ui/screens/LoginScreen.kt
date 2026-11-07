package com.finsight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.finsight.ui.AppState
import com.finsight.ui.designsystem.*

/** Demo sign-in for the hackathon. Real Google Sign-In (Credential Manager) is on the roadmap; this skips it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(state: AppState) {
    var picker by remember { mutableStateOf(false) }
    val accounts = listOf("Faraz Ahmed" to "faraz@finsight.demo", "Afza Touqeer" to "afza@finsight.demo")
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.rowGapVertical),
    ) {
        Spacer(Modifier.height(Spacing.s16))
        Text("FinSight", style = FsTheme.type.numeric.hero, color = FsColors.role.content.primary)
        Text("Your money. Your intelligence. On your phone.", style = FsTheme.type.text.bodyLarge, color = FsColors.role.content.secondary)
        Spacer(Modifier.weight(1f))
        FsCard {
            Overline("Sign in")
            Text("Signing in only separates your data on this phone. FinSight has no server and your email is never sent to the AI.",
                style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.secondary)
        }
        FsButton("G   Continue with Google", { picker = true }, Modifier.fillMaxWidth())
        FsButton("Continue without account", { state.demoUser.value = "Guest" }, Modifier.fillMaxWidth(), variant = FsButtonVariant.Secondary)
        Text("Demo build: Google sign-in is simulated.", style = FsTheme.type.text.caption, color = FsColors.role.content.tertiary)
        Spacer(Modifier.height(Spacing.s6))
    }
    if (picker) ModalBottomSheet(onDismissRequest = { picker = false }, containerColor = FsColors.role.fill.sheet, scrimColor = FsColors.scrim) {
        Column(Modifier.fillMaxWidth().padding(Spacing.sheetPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            Text("Choose an account", style = FsTheme.type.text.titleLarge, color = FsColors.role.content.primary)
            Text("to continue to FinSight (demo)", style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.secondary)
            accounts.forEach { (name, email) ->
                EdgeSurface(Modifier.fillMaxWidth(), onClick = { picker = false; state.demoUser.value = name }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(Sizes.avatar).clip(CircleShape).background(FsColors.brandSubtle), contentAlignment = Alignment.Center) {
                            Text(name.take(1), style = FsTheme.type.text.titleMedium, color = FsColors.brandText)
                        }
                        Column(Modifier.padding(start = Spacing.s3)) {
                            Text(name, style = FsTheme.type.text.titleSmall, color = FsColors.role.content.primary)
                            Text(email, style = FsTheme.type.text.caption, color = FsColors.role.content.secondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.s6))
        }
    }
}
