package com.finsight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.finsight.coach.CoachAnswer
import com.finsight.coach.Profile
import com.finsight.coach.Pulse
import com.finsight.coach.Route
import com.finsight.domain.formatPkr
import com.finsight.ui.AppState
import com.finsight.ui.designsystem.*
import kotlinx.coroutines.launch

data class ChatMsg(val fromUser: Boolean, val text: String, val answer: CoachAnswer? = null)

private val suggestions = listOf(
    "Where did my money go this week?", "How healthy are my finances and what should I fix first?",
    "Can I afford a laptop for PKR 150,000?", "Meri spending kahan gayi?", "Make my monthly report",
    "Compare with last month", "What are my recurring bills?", "What is an emergency fund?",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    val msgs = remember { mutableStateListOf(ChatMsg(false, "Assalam o Alaikum! I'm FinSight Coach. Ask about your spending, health, bills, or \"Can I afford…?\". Personal numbers are computed on this phone.")) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf<CoachAnswer?>(null) }
    val profile by state.profile.collectAsState()
    val listState = rememberLazyListState()
    val draft by state.coachDraft.collectAsState()
    val txns by state.repo.transactions.collectAsState()
    val pulse = remember(txns) { Pulse.cards(state.tools) }
    var dismissed by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(draft) { draft?.let { input = it; state.coachDraft.value = null } }

    fun send(q: String) {
        if (q.isBlank() || busy) return
        msgs += ChatMsg(true, q); input = ""; busy = true
        scope.launch {
            val a = state.coach.ask(q)
            msgs += ChatMsg(false, a.text, a); busy = false
            listState.animateScrollToItem(msgs.size - 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = Spacing.gutter)) {
            ScreenHeader("FinSight Coach", "Hybrid AI · on-device first") {
                var open by remember { mutableStateOf(false) }
                Box {
                    FsChip(profile.label, selected = true, onClick = { open = true })
                    DropdownMenu(open, { open = false }, containerColor = FsColors.role.fill.sheet) {
                        Profile.entries.forEach { p -> DropdownMenuItem({ Text(p.label, style = FsTheme.type.text.bodyMedium) }, { state.profile.value = p; open = false }) }
                    }
                }
            }
        }
        val visiblePulse = pulse.filter { it.title !in dismissed }
        if (visiblePulse.isNotEmpty()) LazyRow(
            Modifier.padding(bottom = Spacing.s2),
            contentPadding = PaddingValues(horizontal = Spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            items(visiblePulse, key = { it.title }) { c ->
                FsCard(Modifier.width(Sizes.popoverWidth * 0.8f), tone = NoticeTone.Info) {
                    Text("FinSight Pulse · ${c.title}", style = FsTheme.type.text.labelSmall, color = FsColors.role.status.infoContent)
                    Text(c.body, style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.primary, modifier = Modifier.padding(top = Spacing.s1))
                    Text("Why: ${c.why}", style = FsTheme.type.text.caption, color = FsColors.role.content.tertiary, modifier = Modifier.padding(top = Spacing.s1))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FsButton("Dismiss", { dismissed = dismissed + c.title }, variant = FsButtonVariant.Text, small = true)
                        FsButton("Ask", { send("Tell me more: " + c.body) }, enabled = !busy, variant = FsButtonVariant.Text, small = true)
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = Spacing.gutter), state = listState, verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            items(msgs) { m -> Bubble(m) { shared = it } }
            if (busy) item { Text("Thinking…", style = FsTheme.type.text.caption, color = FsColors.role.content.tertiary) }
        }
        LazyRow(Modifier.padding(vertical = Spacing.s2), contentPadding = PaddingValues(horizontal = Spacing.gutter), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            items(suggestions) { s -> FsChip(s, onClick = { send(s) }) }
        }
        Row(Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.s2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            FsTextField(input, { input = it }, "Ask in English or Roman Urdu…", Modifier.weight(1f), onDone = { send(input) })
            FsButton("Send", { send(input) }, enabled = !busy, small = true)
        }
    }
    shared?.let { a ->
        ModalBottomSheet(onDismissRequest = { shared = null }, containerColor = FsColors.role.fill.sheet, scrimColor = FsColors.scrim) {
            Column(Modifier.padding(Spacing.sheetPadding).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Text("What was shared?", style = FsTheme.type.text.titleLarge, color = FsColors.role.content.primary)
                FsTag(a.route.badge, routeTone(a.route))
                if (a.shared == null) Text("Nothing left this phone. The answer was computed on-device from your records.", style = FsTheme.type.text.bodyMedium, color = FsColors.role.change.upContent)
                else {
                    Text("Exact payload sent to ${state.cloud.modelName} (no merchants, names, account numbers or raw messages):", style = FsTheme.type.text.bodyMedium, color = FsColors.role.content.secondary)
                    CodeBox(a.shared)
                }
                Spacer(Modifier.height(Spacing.s6))
            }
        }
    }
}

fun routeTone(r: Route) = when (r) { Route.LOCAL -> TagTone.Success; Route.HYBRID -> TagTone.Brand; Route.CLOUD -> TagTone.Info }

@Composable
fun Bubble(m: ChatMsg, onBadge: (CoachAnswer) -> Unit) {
    val type = FsTheme.type
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        if (m.fromUser) {
            Text(m.text, style = type.text.bodyMedium, color = FsColors.role.content.primary,
                modifier = Modifier.widthIn(max = Sizes.listRowMin * 5).clip(RoundedCornerShape(Radius.card)).background(FsColors.brandSubtle).padding(Spacing.s3))
        } else EdgeSurface(Modifier.widthIn(max = Sizes.listRowMin * 5), contentPadding = PaddingValues(Spacing.s3)) {
            Text(m.text, style = type.text.bodyMedium, color = FsColors.role.content.primary)
            m.answer?.let { a ->
                if (a.chart.isNotEmpty()) { Spacer(Modifier.height(Spacing.s2)); ShareBars(a.chart.take(5)) { formatPkr(it).removePrefix("PKR ") } }
                a.note?.let { Text(it, style = type.text.caption, color = FsColors.role.status.warningContent, modifier = Modifier.padding(top = Spacing.s1)) }
                Row(Modifier.padding(top = Spacing.s2).clickable { onBadge(a) }, verticalAlignment = Alignment.CenterVertically) {
                    FsTag(a.route.badge, routeTone(a.route))
                    Text("  What was shared? ›", style = type.text.caption, color = FsColors.role.content.link)
                }
                Text("Education, not financial advice.", style = type.text.caption, color = FsColors.role.content.tertiary, modifier = Modifier.padding(top = Spacing.s1))
            }
        }
    }
}
