package com.finsight.coach

import com.finsight.ai.CloudLlm
import com.finsight.data.Repository
import com.finsight.domain.formatPkr
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

enum class Route(val badge: String) { LOCAL("On-device"), HYBRID("Hybrid"), CLOUD("Cloud") }

data class CoachAnswer(
    val text: String,
    val route: Route,
    val shared: String?,               // exact payload that left the phone, or null
    val note: String? = null,          // e.g. "Cloud unavailable — answered on-device"
    val chart: List<Pair<String, Long>> = emptyList(),
)

enum class Intent { SPEND, MERCHANTS, COMPARE, HEALTH, PULSE, WHAT_IF, REPORT, RECURRING, GENERAL }

/** Privacy Router (plan 5.3). Automatic; the user only has a global cloud switch. */
object PrivacyRouter {
    fun route(intent: Intent, cloudAllowed: Boolean, online: Boolean, scrubbedOk: Boolean): Route {
        if (!cloudAllowed || !online || !scrubbedOk) return Route.LOCAL
        return when (intent) {
            Intent.HEALTH, Intent.REPORT, Intent.WHAT_IF -> Route.HYBRID
            Intent.GENERAL -> Route.CLOUD
            else -> Route.LOCAL // pure personal numbers never need the cloud
        }
    }
}

class CoachAgent(
    private val repo: Repository,
    private val tools: Tools,
    private val cloud: CloudLlm,
    private val log: CoachLog,
    private val isOnline: () -> Boolean,
    private val cloudEnabled: () -> Boolean,
    private val profile: () -> Profile,
    private val now: () -> Long,
) {
    fun intentOf(q: String): Intent {
        val s = q.lowercase()
        return when {
            Regex("""afford|khareed|buy|purchase|what if|agar main""").containsMatchIn(s) -> Intent.WHAT_IF
            Regex("""health|healthy|score|kaisi hai|theek|financial condition|fix first""").containsMatchIn(s) -> Intent.HEALTH
            Regex("""report|summary|khulasa""").containsMatchIn(s) -> Intent.REPORT
            Regex("""compare|last month|pichle|increase|barh""").containsMatchIn(s) -> Intent.COMPARE
            Regex("""merchant|where.*most|kis ko|top shops|kahan sab se""").containsMatchIn(s) -> Intent.MERCHANTS
            Regex("""recurring|subscription|bill|committee|bills""").containsMatchIn(s) -> Intent.RECURRING
            Regex("""insight|pulse|tips|kya dekhna""").containsMatchIn(s) -> Intent.PULSE
            Regex("""spend|spent|kharch|kharcha|money go|paise|kahan gay|category""").containsMatchIn(s) -> Intent.SPEND
            else -> Intent.GENERAL
        }
    }

    private fun isRomanUrdu(q: String) = Regex("""\b(kya|kahan|meri|mera|kitna|kitni|hai|hain|gayi|gaya|kaisi|kharch|paise|main)\b""", RegexOption.IGNORE_CASE).containsMatchIn(q)

    suspend fun ask(question: String): CoachAnswer {
        val intent = intentOf(question)
        val names = repo.transactions.value.mapNotNull { it.merchant }.toSet()
        val scrubbed = Redactor.scrub(question, names)
        val route = PrivacyRouter.route(intent, cloudEnabled(), isOnline() && cloud.configured, scrubbed != null)
        val ur = isRomanUrdu(question)
        val local = localAnswer(intent, question, ur)
        if (scrubbed == null) {
            names.filter { it.length >= 3 && question.lowercase().contains(it.lowercase()) }.maxByOrNull { it.length }?.let { m ->
                val (sum, n) = tools.spendAt(m)
                return CoachAnswer(if (ur) "$m par aap ne $n dafa mein ${formatPkr(sum)} kharch kiye." else "You spent ${formatPkr(sum)} at $m across $n payment(s).",
                    Route.LOCAL, null, "Your question names a merchant, so it stayed on this phone.")
            }
        }
        if (route == Route.LOCAL) {
            val note = when {
                scrubbed == null -> "Your question names a person, merchant or number, so it stayed on this phone."
                !cloudEnabled() -> "Cloud AI is off (Privacy tab)."
                (intent == Intent.HEALTH || intent == Intent.REPORT || intent == Intent.WHAT_IF || intent == Intent.GENERAL) && !(isOnline() && cloud.configured) ->
                    "Offline or no cloud key — answered on-device."
                else -> null
            }
            return local.copy(note = note)
        }
        val facts = factsFor(intent, question)
        val prompt = Redactor.build(scrubbed!!, facts)
        val t0 = now()
        return try {
            val raw = withTimeout(15_000) { cloud.complete(prompt) }
            val (verified, dropped) = NumberVerifier.verify(raw, facts)
            log.add(CloudLogEntry(t0, route.badge, cloud.modelName, prompt.payloadJson(), now() - t0, if (dropped > 0) "ok, $dropped sentence(s) removed by verifier" else "ok"))
            val text = if (intent == Intent.GENERAL) verified else local.text + "\n\n" + verified
            CoachAnswer(text, route, prompt.payloadJson(), if (dropped > 0) "Verifier removed $dropped sentence(s) with unverified figures." else null, local.chart)
        } catch (e: TimeoutCancellationException) {
            log.add(CloudLogEntry(t0, route.badge, cloud.modelName, prompt.payloadJson(), now() - t0, "timeout → on-device"))
            local.copy(note = "Cloud timed out — answered on-device.")
        } catch (e: Exception) {
            log.add(CloudLogEntry(t0, route.badge, cloud.modelName, prompt.payloadJson(), now() - t0, "error: ${e.message?.take(80)} → on-device"))
            local.copy(note = "Cloud unavailable — answered on-device.")
        }
    }

    private fun factsFor(intent: Intent, q: String): List<SafeFact> = when (intent) {
        Intent.HEALTH, Intent.REPORT -> HealthScore.compute(tools, profile(), repo.coverage().percent).facts
        Intent.WHAT_IF -> WhatIf.afford(tools, priceOf(q) ?: 0).facts
        else -> emptyList()
    }

    private fun priceOf(q: String): Long? {
        val m = Regex("""(?i)(?:rs\.?|pkr)?\s*([0-9][0-9,]*)\s*(k|lakh|lac)?""").findAll(q).maxByOrNull { it.groupValues[1].replace(",", "").length } ?: return null
        var v = m.groupValues[1].replace(",", "").toLongOrNull() ?: return null
        when (m.groupValues[2].lowercase()) { "k" -> v *= 1_000; "lakh", "lac" -> v *= 100_000 }
        return v * 100
    }

    fun localAnswer(intent: Intent, q: String, ur: Boolean): CoachAnswer {
        val p = tools.period(if (Regex("""week|hafte""").containsMatchIn(q.lowercase())) "week" else "30")
        return when (intent) {
            Intent.SPEND, Intent.GENERAL -> {
                val cats = tools.spendByCategory(p)
                if (cats.isEmpty()) return CoachAnswer(if (ur) "Abhi koi kharch record nahi hua. Home par 'Import demo SMS' dabayein." else "No spending captured yet. Tap 'Import demo SMS' on Home.", Route.LOCAL, null)
                val total = cats.sumOf { it.minor }
                val top = cats.first()
                val text = if (intent == Intent.GENERAL)
                    "I can answer this best with cloud AI (off, offline or no key right now). Meanwhile, from your data: you spent ${formatPkr(total)} in the ${p.label}; ${top.category.label} is the biggest at ${top.percent}%."
                else if (ur) "${p.label} mein aap ne ${formatPkr(total)} kharch kiye. Sab se zyada ${top.category.label} par: ${formatPkr(top.minor)} (${top.percent}%)."
                else "You spent ${formatPkr(total)} in the ${p.label}. The biggest category is ${top.category.label} at ${formatPkr(top.minor)} (${top.percent}%)."
                CoachAnswer(text, Route.LOCAL, null, chart = cats.take(6).map { it.category.label to it.minor })
            }
            Intent.MERCHANTS -> {
                val m = tools.topMerchants(p)
                CoachAnswer(if (m.isEmpty()) "No merchants yet." else "Top places by spend (${p.label}): " + m.joinToString("; ") { "${it.first} ${formatPkr(it.second)}" }, Route.LOCAL, null, chart = m)
            }
            Intent.COMPARE -> {
                val c = tools.compareMonths().take(4)
                CoachAnswer(if (c.isEmpty()) "Not enough data to compare yet." else "Last 30 days vs the previous 30:\n" + c.joinToString("\n") {
                    "• ${it.category.label}: ${formatPkr(it.now)} vs ${formatPkr(it.before)}" + (it.changePct?.let { p -> " (${if (p >= 0) "+" else ""}$p%)" } ?: "")
                }, Route.LOCAL, null, chart = c.map { it.category.label to it.now })
            }
            Intent.RECURRING -> {
                val r = tools.recurringPayments()
                CoachAnswer(if (r.isEmpty()) "No recurring payments detected yet (needs the same payee in 2+ months)." else "Recurring payments:\n" + r.take(5).joinToString("\n") { "• ${it.name}: ~${formatPkr(it.avgMinor)}, around day ${it.dayOfMonth}" }, Route.LOCAL, null)
            }
            Intent.PULSE -> {
                val c = Pulse.cards(tools)
                CoachAnswer(if (c.isEmpty()) "No insights yet." else c.joinToString("\n") { "• ${it.body}  (why: ${it.why})" }, Route.LOCAL, null)
            }
            Intent.HEALTH, Intent.REPORT -> {
                val h = HealthScore.compute(tools, profile(), repo.coverage().percent)
                val head = if (intent == Intent.REPORT) "Monthly report (${p.label}): income ${formatPkr(tools.totalIncome(p))}, spending ${formatPkr(tools.totalSpend(p))}.\n" else ""
                val s = buildString {
                    append(head)
                    append(if (h.score != null) "Financial Health: ${h.score}/100 — ${h.band} (based on ${h.used} of 6 checks, profile ${profile().label})." else "Financial Health: not enough data yet (${h.used} of 6 checks).")
                    if (h.good.isNotEmpty()) append("\nGood: " + h.good.first())
                    if (h.attention.isNotEmpty()) append("\nNeeds attention: " + h.attention.first())
                    append("\nNext steps:\n" + h.actions.joinToString("\n") { "• $it" })
                }
                CoachAnswer(s, Route.LOCAL, null, chart = tools.spendByCategory(p).take(5).map { it.category.label to it.minor })
            }
            Intent.WHAT_IF -> {
                val price = priceOf(q) ?: return CoachAnswer("Tell me a price, e.g. \"Can I afford PKR 150,000?\"", Route.LOCAL, null)
                val w = WhatIf.afford(tools, price)
                CoachAnswer("${formatPkr(price)}: ${w.verdict}\n" + w.lines.joinToString("\n") { "• $it" }, Route.LOCAL, null)
            }
        }
    }
}
