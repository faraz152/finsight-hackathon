package com.finsight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.finsight.domain.Band
import com.finsight.ui.designsystem.*
import com.finsight.ui.screens.*

/** Bottom navigation tabs (plan 6.1). The centre slot is the hexagon "smart button", not a tab. */
enum class Tab(val label: String, val glyph: String) { HOME("Home", "◉"), TXNS("Transactions", "≡"), COACH("Coach", "✦"), PRIVACY("Privacy", "◈") }

@Composable
fun App(state: AppState) {
    FsTheme {
        var tab by remember { mutableStateOf(Tab.HOME) }
        var actionsOpen by remember { mutableStateOf(false) }
        var quickAddOpen by remember { mutableStateOf(false) }

        // FinSight functionality behind the InvesTerra hexagon.
        val actions = listOf(
            QuickAction("Add transaction", "Type it: \"chai 150 cash\"", "+", QuickActionTone.Gain) { actionsOpen = false; quickAddOpen = true },
            QuickAction("Ask Coach", "Chat in English or Roman Urdu", "✦", QuickActionTone.Brand) { actionsOpen = false; tab = Tab.COACH },
            QuickAction("Can I afford it?", "What If? simulator", "?", QuickActionTone.Warning) {
                actionsOpen = false; state.coachDraft.value = "Can I afford PKR "; tab = Tab.COACH
            },
            QuickAction("Import messages", "Replay bank & wallet SMS", "↓", QuickActionTone.Info) {
                actionsOpen = false; state.repo.importBatch(state.loadDemo()); tab = Tab.TXNS
            },
            QuickAction("Scan receipt", "Roadmap: on-device multimodal Gemma", "◎", QuickActionTone.Neutral, locked = true) {},
        )

        Box(Modifier.fillMaxSize().background(FsColors.role.fill.canvas)) {
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = Sizes.navBarHeight + Edges.outline)) {
                when (tab) {
                    Tab.HOME -> HomeScreen(state) { tab = it }
                    Tab.TXNS -> TransactionsScreen(state)
                    Tab.COACH -> CoachScreen(state)
                    Tab.PRIVACY -> PrivacyScreen(state)
                }
            }
            QuickActionsOverlay(actionsOpen, actions) { actionsOpen = false }
            FsBottomBar(
                items = Tab.entries.map { NavItem(it.label, it.glyph) },
                selected = Tab.entries.indexOf(tab),
                onSelect = { actionsOpen = false; tab = Tab.entries[it] },
                hexagonOpen = actionsOpen,
                onHexagon = { actionsOpen = !actionsOpen },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (quickAddOpen) QuickAddSheet(state) { quickAddOpen = false }
    }
}

/** Confidence band → colour role. */
fun bandColor(b: Band) = when (b) { Band.GREEN -> FsColors.role.change.upContent; Band.YELLOW -> FsColors.role.status.warningContent; Band.RED -> FsColors.role.status.errorContent }
