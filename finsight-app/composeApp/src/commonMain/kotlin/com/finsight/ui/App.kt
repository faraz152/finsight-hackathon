package com.finsight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.finsight.domain.Band
import com.finsight.ui.designsystem.FsColors
import com.finsight.ui.designsystem.FsTheme
import com.finsight.ui.screens.CoachScreen
import com.finsight.ui.screens.HomeScreen
import com.finsight.ui.screens.PrivacyScreen
import com.finsight.ui.screens.TransactionsScreen

/** Bottom navigation tabs (plan 6.1). Goals tab is Phase 3 work. */
enum class Tab(val label: String, val glyph: String) { HOME("Home", "◉"), TXNS("Transactions", "≡"), COACH("Coach", "✦"), PRIVACY("Privacy", "◈") }

@Composable
fun App(state: AppState) {
    FsTheme {
        var tab by remember { mutableStateOf(Tab.HOME) }
        Scaffold(
            containerColor = FsColors.role.fill.canvas,
            bottomBar = {
                NavigationBar(containerColor = FsColors.role.fill.navBar) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t },
                            icon = { Text(t.glyph, style = FsTheme.type.text.titleSmall) },
                            label = { Text(t.label, style = FsTheme.type.text.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FsColors.brandText, selectedTextColor = FsColors.brandText,
                                indicatorColor = FsColors.brandSubtle,
                                unselectedIconColor = FsColors.role.content.tertiary, unselectedTextColor = FsColors.role.content.tertiary,
                            ),
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize().background(FsColors.role.fill.canvas)) {
                when (tab) {
                    Tab.HOME -> HomeScreen(state) { tab = it }
                    Tab.TXNS -> TransactionsScreen(state)
                    Tab.COACH -> CoachScreen(state)
                    Tab.PRIVACY -> PrivacyScreen(state)
                }
            }
        }
    }
}

/** Confidence band → colour role. */
fun bandColor(b: Band) = when (b) { Band.GREEN -> FsColors.role.change.upContent; Band.YELLOW -> FsColors.role.status.warningContent; Band.RED -> FsColors.role.status.errorContent }
