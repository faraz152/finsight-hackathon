package com.finsight.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finsight.coach.*
import com.finsight.domain.*
import com.finsight.pipeline.PipelineResult
import kotlinx.coroutines.launch

enum class Tab(val label: String) { HOME("Home"), TXNS("Transactions"), COACH("Coach"), PRIVACY("Privacy") }

@Composable
fun App(state: AppState) {
    FinSightTheme {
        var tab by remember { mutableStateOf(Tab.HOME) }
        Scaffold(
            containerColor = Fs.canvas,
            bottomBar = {
                NavigationBar(containerColor = Fs.surface) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t },
                            icon = { Text(when (t) { Tab.HOME -> "◉"; Tab.TXNS -> "≡"; Tab.COACH -> "✦"; Tab.PRIVACY -> "🔒" }) },
                            label = { Text(t.label, fontSize = 11.sp) },
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize().background(Fs.canvas)) {
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

// ---------------------------------------------------------------- shared bits

@Composable
fun Card(modifier: Modifier = Modifier, color: Color = Fs.surface, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color).padding(16.dp), content = content)
}

@Composable
fun Chip(text: String, fg: Color, bg: Color) {
    Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun Bars(rows: List<Pair<String, Long>>) {
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    rows.forEach { (label, v) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Fs.text2, fontSize = 12.sp, modifier = Modifier.width(110.dp), maxLines = 1)
            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Fs.subtle)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(v.toFloat() / max).clip(RoundedCornerShape(5.dp)).background(Fs.brand))
            }
            Text(formatPkr(v).removePrefix("PKR "), color = Fs.text, fontSize = 12.sp, modifier = Modifier.width(80.dp).padding(start = 8.dp))
        }
    }
}

@Composable
fun SectionTitle(t: String) = Text(t, color = Fs.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(state: AppState, go: (Tab) -> Unit) {
    val txns by state.repo.transactions.collectAsState()
    val last by state.repo.lastImport.collectAsState()
    val scams by state.repo.scams.collectAsState()
    val profile by state.profile.collectAsState()
    val p = state.tools.period("30")
    val coverage = remember(txns) { state.repo.coverage() }
    val health = remember(txns, profile) { HealthScore.compute(state.tools, profile, coverage.percent) }
    val pulse = remember(txns) { Pulse.cards(state.tools) }
    var quick by remember { mutableStateOf("") }
    var quickMsg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FinSight", color = Fs.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Your money. Your intelligence. On your phone.", color = Fs.text3, fontSize = 13.sp)

        Card {
            Text("THIS MONTH'S SPENDING", color = Fs.text3, fontSize = 11.sp)
            Text(formatPkr(state.tools.totalSpend(p)), color = Fs.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Income ${formatPkr(state.tools.totalIncome(p))} · ${txns.size} records", color = Fs.text2, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f).clickable { go(Tab.COACH) }) {
                Text("Financial health", color = Fs.text3, fontSize = 11.sp)
                Text(health.score?.let { "$it/100" } ?: "—", color = when { health.score == null -> Fs.text2; health.score >= 80 -> Fs.gain; health.score >= 60 -> Fs.warn; else -> Fs.loss }, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${health.band} · ${health.used}/6 checks", color = Fs.text2, fontSize = 11.sp)
            }
            Card(Modifier.weight(1f)) {
                Text("Data coverage", color = Fs.text3, fontSize = 11.sp)
                Text(coverage.percent?.let { "$it%" } ?: "—", color = Fs.info, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(if ((coverage.percent ?: 0) < 100) "Partial data" else "Complete", color = Fs.text2, fontSize = 11.sp)
            }
        }
        if (txns.isNotEmpty()) Text(coverage.basis, color = Fs.text3, fontSize = 11.sp)

        pulse.firstOrNull()?.let { c ->
            Card(color = Fs.brandSubtle) {
                Text("FinSight Pulse · ${c.title}", color = Fs.brand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(c.body, color = Fs.text, fontSize = 14.sp)
                Text("Why: ${c.why}", color = Fs.text3, fontSize = 11.sp)
            }
        }
        scams.firstOrNull()?.let { Card(color = Fs.lossSubtle) {
            Text("⚠ Possible scam message blocked", color = Fs.loss, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(it, color = Fs.text2, fontSize = 12.sp, maxLines = 2)
        } }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { state.repo.importBatch(state.loadDemo()) }, colors = ButtonDefaults.buttonColors(containerColor = Fs.brand)) { Text("Import demo SMS") }
            OutlinedButton(onClick = { go(Tab.TXNS) }) { Text("Review inbox") }
        }
        last?.let { Text("Imported: ${it.autoAccepted} auto-accepted by rules · ${it.review} need review · ${it.duplicates} duplicates merged · ${it.dropped} dropped · ${it.scams} scams", color = Fs.text2, fontSize = 12.sp) }

        Card {
            Text("Quick add", color = Fs.text3, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(quick, { quick = it }, placeholder = { Text("chai 150 cash") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val r = state.quickAdd(quick)
                    quickMsg = when (r) { is PipelineResult.Accepted -> "Added: ${r.txn.merchant} ${r.txn.amount} → ${r.txn.category.label}"; is PipelineResult.Dropped -> "Not added: ${r.reason}"; is PipelineResult.ScamWarning -> "Blocked: ${r.reason}" }
                    quick = ""
                }) { Text("Add") }
            }
            quickMsg?.let { Text(it, color = Fs.text2, fontSize = 12.sp) }
        }

        val cats = state.tools.spendByCategory(p)
        if (cats.isNotEmpty()) Card {
            SectionTitle("Where it went (last 30 days)")
            Bars(cats.take(6).map { it.category.label to it.minor })
        }
    }
}

// ---------------------------------------------------------------- Transactions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(state: AppState) {
    val txns by state.repo.transactions.collectAsState()
    var reviewOnly by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Long?>(null) }
    val shown = if (reviewOnly) txns.filter { it.needsReview } else txns

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Transactions", color = Fs.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            FilterChip(reviewOnly, { reviewOnly = !reviewOnly }, label = { Text("Review (${txns.count { it.needsReview }})") })
        }
        if (shown.isEmpty()) Text(if (reviewOnly) "Nothing to review 🎉" else "No transactions yet. Import demo SMS on Home, or enable notification access.", color = Fs.text2)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { t -> TxnRow(t) { open = t.id } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    val sel = txns.firstOrNull { it.id == open }
    if (sel != null) ModalBottomSheet(onDismissRequest = { open = null }, containerColor = Fs.surface) {
        WhySheet(sel, onCorrect = { c -> state.repo.correct(sel.id, c) }, onDelete = { state.repo.delete(sel.id); open = null })
    }
}

@Composable
fun TxnRow(t: Transaction, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Fs.surface).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Fs.band(t.band)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.merchant ?: t.category.label, color = Fs.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                Chip(t.category.label, Fs.text2, Fs.subtle)
                Chip(sourceBadge(t), Fs.brand, Fs.brandSubtle)
                if (t.kind != TxnKind.EXPENSE && t.kind != TxnKind.INCOME) Chip(t.kind.name.replace('_', ' ').lowercase(), Fs.info, Fs.infoSubtle)
                if (t.evidenceCount > 1) Chip("dup ×${t.evidenceCount}", Fs.warn, Fs.warnSubtle)
            }
        }
        Text((if (t.type == TxnType.CREDIT) "+" else "−") + t.amount.toString().substringAfter(' '), color = if (t.type == TxnType.CREDIT) Fs.gain else Fs.text, fontWeight = FontWeight.Bold)
    }
}

fun sourceBadge(t: Transaction) = when (t.categorySource) {
    CategorySource.USER_MEMORY, CategorySource.USER -> "You"
    CategorySource.LLM -> "Gemma"; CategorySource.HYBRID -> "Rule+Gemma"
    else -> if (t.channel == Channel.MANUAL) "You" else "Rule"
}

@Composable
fun WhySheet(t: Transaction, onCorrect: (Category) -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Why?", color = Fs.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("${(t.confidence * 100).toInt()}% confidence", Fs.band(t.band), Fs.bandSubtle(t.band))
            Chip(sourceBadge(t), Fs.brand, Fs.brandSubtle)
            Chip(t.channel.name.lowercase(), Fs.text2, Fs.subtle)
        }
        Text("${t.amount} · ${t.type.name.lowercase()} · ${t.kind.name.lowercase()}" + (t.accountLast4?.let { " · ••$it" } ?: "") + (t.balance?.let { " · bal $it" } ?: ""), color = Fs.text2, fontSize = 13.sp)
        t.explanation.forEach { Text("✓ $it", color = Fs.text, fontSize = 13.sp) }
        Text("Original text (stays on this phone)", color = Fs.text3, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        Text(t.rawText, color = Fs.text2, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Fs.subtle).padding(10.dp))
        Text("Correct category (FinSight remembers this merchant)", color = Fs.text3, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Category.entries) { c -> FilterChip(t.category == c, { onCorrect(c) }, label = { Text(c.label, fontSize = 12.sp) }) }
        }
        TextButton(onClick = onDelete) { Text("Delete this record and its raw text", color = Fs.loss) }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- Coach

data class ChatMsg(val fromUser: Boolean, val text: String, val answer: CoachAnswer? = null)

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
    val suggestions = listOf("Where did my money go this week?", "How healthy are my finances and what should I fix first?", "Can I afford a laptop for PKR 150,000?",
        "Meri spending kahan gayi?", "Make my monthly report", "Compare with last month", "What are my recurring bills?", "What is an emergency fund?")

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
        Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("FinSight Coach", color = Fs.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            var open by remember { mutableStateOf(false) }
            Box {
                AssistChip(onClick = { open = true }, label = { Text(profile.label, fontSize = 12.sp) })
                DropdownMenu(open, { open = false }) { Profile.entries.forEach { p -> DropdownMenuItem({ Text(p.label) }, { state.profile.value = p; open = false }) } }
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { m -> Bubble(m) { shared = it } }
            if (busy) item { Text("Thinking…", color = Fs.text3, fontSize = 12.sp) }
        }
        LazyRow(Modifier.padding(vertical = 6.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(suggestions) { s -> SuggestionChip(onClick = { send(s) }, label = { Text(s, fontSize = 12.sp) }) }
        }
        Row(Modifier.padding(16.dp, 4.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, placeholder = { Text("Ask in English or Roman Urdu…") }, modifier = Modifier.weight(1f), maxLines = 3)
            TextButton(onClick = { send(input) }, enabled = !busy) { Text("Send") }
        }
    }
    shared?.let { a ->
        ModalBottomSheet(onDismissRequest = { shared = null }, containerColor = Fs.surface) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What was shared?", color = Fs.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Chip(a.route.badge, Fs.brand, Fs.brandSubtle)
                if (a.shared == null) Text("Nothing left this phone. The answer was computed on-device from your records.", color = Fs.gain)
                else {
                    Text("Exact payload sent to ${state.cloud.modelName} (no merchants, names, account numbers or raw messages):", color = Fs.text2, fontSize = 13.sp)
                    Text(a.shared, color = Fs.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Fs.subtle).padding(10.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun Bubble(m: ChatMsg, onBadge: (CoachAnswer) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(14.dp)).background(if (m.fromUser) Fs.brandSubtle else Fs.surface).padding(12.dp)) {
            Text(m.text, color = Fs.text, fontSize = 14.sp)
            m.answer?.let { a ->
                if (a.chart.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Bars(a.chart.take(5)) }
                a.note?.let { Text(it, color = Fs.warn, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
                Row(Modifier.padding(top = 8.dp).clickable { onBadge(a) }, verticalAlignment = Alignment.CenterVertically) {
                    Chip(a.route.badge, when (a.route) { Route.LOCAL -> Fs.gain; Route.HYBRID -> Fs.brand; Route.CLOUD -> Fs.info }, Fs.subtle)
                    Text("  What was shared? ›", color = Fs.text3, fontSize = 11.sp)
                }
                Text("Education, not financial advice.", color = Fs.text3, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Privacy Center

@Composable
fun PrivacyScreen(state: AppState) {
    val cloudOn by state.cloudEnabled.collectAsState()
    val log by state.log.entries.collectAsState()
    val txns by state.repo.transactions.collectAsState()
    var access by remember { mutableStateOf(state.hasNotificationAccess()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Privacy Center", color = Fs.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Card {
            Text("What FinSight reads", color = Fs.text, fontWeight = FontWeight.Bold)
            Text("Only transaction notifications from SMS apps and the wallet apps you allow (JazzCash, Easypaisa). Everything else is ignored in memory and never stored. FinSight never reads your SMS inbox (no READ_SMS permission).", color = Fs.text2, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (access) "Notification access: ON" else "Notification access: OFF", color = if (access) Fs.gain else Fs.warn, modifier = Modifier.weight(1f))
                Button(onClick = { state.openNotificationSettings() }) { Text(if (access) "Manage" else "Enable") }
            }
            TextButton(onClick = { access = state.hasNotificationAccess() }) { Text("Refresh status") }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cloud AI (hybrid Coach)", color = Fs.text, fontWeight = FontWeight.Bold)
                    Text(if (cloudOn) "Automatic: on-device first; only redacted facts go to Gemini when knowledge is needed." else "Off: Coach answers on this phone only.", color = Fs.text2, fontSize = 13.sp)
                    Text(if (state.cloud.configured) "Model: ${state.cloud.modelName}" else "No cloud key configured — on-device only", color = Fs.text3, fontSize = 11.sp)
                }
                Switch(cloudOn, { state.cloudEnabled.value = it })
            }
        }
        Card {
            Text("Never leaves this phone", color = Fs.text, fontWeight = FontWeight.Bold)
            Text("Raw messages · merchant names · people's names · account and card numbers · phone numbers · exact balances and dates · your transaction list", color = Fs.text2, fontSize = 13.sp)
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cloud log (${log.size})", color = Fs.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { state.log.clear() }) { Text("Clear") }
            }
            if (log.isEmpty()) Text("No cloud requests yet.", color = Fs.text2, fontSize = 13.sp)
            log.take(10).forEach { e ->
                Text("${e.route} · ${e.model} · ${e.latencyMs} ms · ${e.status}", color = Fs.text2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Text(e.payload, color = Fs.text, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Fs.subtle).padding(8.dp))
            }
        }
        Card {
            Text("Your data", color = Fs.text, fontWeight = FontWeight.Bold)
            Text("${txns.size} records stored only in this app's private storage. Backup to cloud is disabled.", color = Fs.text2, fontSize = 13.sp)
            TextButton(onClick = { state.repo.clearAll(); state.log.clear() }) { Text("Delete all local data", color = Fs.loss) }
        }
    }
}
