package com.finsight.coach

import com.finsight.domain.Category
import com.finsight.domain.formatPkr

enum class Profile(val label: String, val savingsTarget: Int, val stabilityMatters: Boolean) {
    SALARIED("Salaried", 20, true), FREELANCER("Freelancer", 25, false), STUDENT("Student", 10, false),
    SELF_EMPLOYED("Self-employed", 25, false), HOUSEHOLD("Household manager", 15, true),
}

data class HealthCheck(val name: String, val score: Double?, val detail: String)
data class HealthResult(
    val score: Int?, val band: String, val checks: List<HealthCheck>, val used: Int,
    val good: List<String>, val attention: List<String>, val actions: List<String>, val facts: List<SafeFact>,
)

/** Financial Health 2.0 (plan 6.5): profile-aware, missing ≠ poor, explained. Deterministic Kotlin, no model. */
object HealthScore {
    fun compute(tools: Tools, profile: Profile, coveragePct: Int?): HealthResult {
        val p = tools.period("30")
        val income = tools.totalIncome(p)
        val spend = tools.totalSpend(p)
        val checks = mutableListOf<HealthCheck>()
        val weights = mutableMapOf<String, Int>()

        // 1 Savings rate
        if (income > 0) {
            val rate = ((income - spend) * 100 / income).toInt()
            checks += HealthCheck("Savings rate", lin(rate.toDouble(), 0.0, profile.savingsTarget.toDouble()), "$rate% of income kept (target ${profile.savingsTarget}% for ${profile.label})")
        } else checks += HealthCheck("Savings rate", null, if (profile == Profile.STUDENT) "No income seen — normal for students, not scored" else "No income captured yet — unknown, not zero")
        weights["Savings rate"] = 25

        // 2 Emergency runway
        val essentials = tools.spendByCategory(p).filter { it.category.essential }.sumOf { it.minor }
        val bal = tools.latestBalance()
        if (bal != null && essentials > 0) {
            val months = bal.toDouble() / essentials
            checks += HealthCheck("Emergency runway", lin(months, 0.0, 3.0), "${(months * 10).toInt() / 10.0} months of essentials in the latest known balance")
        } else checks += HealthCheck("Emergency runway", null, "No balance seen in messages — unknown, not zero")
        weights["Emergency runway"] = 20

        // 3 Debt / fees load
        val fin = tools.spendByCategory(p).firstOrNull { it.category == Category.FINANCIAL }?.minor ?: 0
        if (income > 0) {
            val pct = (fin * 100 / income).toInt()
            checks += HealthCheck("Debt & fees load", 1 - lin(pct.toDouble(), 10.0, 40.0), "$pct% of income to loans, fees and charges")
        } else checks += HealthCheck("Debt & fees load", null, "Needs income data")
        weights["Debt & fees load"] = 15

        // 4 Essentials share (assumption, tuned per profile)
        if (spend > 0) {
            val share = (essentials * 100 / spend).toInt()
            val hi = if (profile == Profile.HOUSEHOLD || profile == Profile.STUDENT) 85 else 70
            val s = when { share in 40..hi -> 1.0; share < 40 -> 0.7; else -> lin((100 - share).toDouble(), 0.0, (100 - hi).toDouble()) }
            checks += HealthCheck("Essentials share", s, "$share% of spending is essentials (healthy band 40–$hi% for ${profile.label})")
        } else checks += HealthCheck("Essentials share", null, "No spending yet")
        weights["Essentials share"] = 15

        // 5 Stability (only if the profile has steady income)
        if (profile.stabilityMatters && spend > 0) {
            val w = tools.inPeriod(p).filter { it.isSpend }.groupBy { (it.timestamp / (7 * 86_400_000L)) }.values.map { l -> l.sumOf { it.amount.minor }.toDouble() }
            if (w.size >= 2) {
                val mean = w.average(); val cv = kotlin.math.sqrt(w.sumOf { (it - mean) * (it - mean) } / w.size) / mean
                checks += HealthCheck("Spending stability", 1 - lin(cv, 0.3, 1.2), "Weekly spending varies by ${(cv * 100).toInt()}%")
            } else checks += HealthCheck("Spending stability", null, "Need 2+ weeks of data")
        } else checks += HealthCheck("Spending stability", null, "Not scored for ${profile.label} (irregular income is normal)")
        weights["Spending stability"] = 15

        // 6 Data coverage (trust)
        checks += if (coveragePct != null) HealthCheck("Data coverage", coveragePct / 100.0, "FinSight sees about $coveragePct% of your money") else HealthCheck("Data coverage", null, "Unknown")
        weights["Data coverage"] = 10

        val used = checks.filter { it.score != null }
        val enough = tools.dataDays() >= 7 && used.size >= 3
        val score = if (enough) (used.sumOf { it.score!! * weights[it.name]!! } / used.sumOf { weights[it.name]!! } * 100).toInt() else null
        val band = when { score == null -> "Not enough data"; score >= 80 -> "Healthy"; score >= 60 -> "Fair"; else -> "Needs attention" }
        val good = used.filter { it.score!! >= 0.75 }.map { "${it.name}: ${it.detail}" }
        val att = used.filter { it.score!! < 0.5 }.map { "${it.name}: ${it.detail}" }
        val actions = buildList {
            if (att.any { it.startsWith("Emergency") }) add("Build a small emergency fund: start with one month of essentials")
            if (att.any { it.startsWith("Savings") }) add("Move a fixed amount to savings on salary day")
            if (att.any { it.startsWith("Debt") }) add("List fees and loan payments; clear the most expensive first")
            add("Plan upcoming recurring bills before discretionary spending")
            if (size < 3) add("Keep capturing for a few more weeks for a sharper score")
        }.take(3)
        val facts = buildList<SafeFact> {
            add(SafeFact.Label("profile", profile.label))
            score?.let { add(SafeFact.Count("health_score", it)) }
            if (income > 0) add(SafeFact.Percentage("savings_rate", ((income - spend) * 100 / income).toInt()))
            if (bal != null && essentials > 0) add(SafeFact.MonthsRatio("runway_months", bal.toDouble() / essentials))
            if (spend > 0) add(SafeFact.Percentage("essentials_share", (essentials * 100 / spend).toInt()))
            coveragePct?.let { add(SafeFact.Percentage("data_coverage", it)) }
            tools.spendByCategory(p).take(3).forEach { add(SafeFact.CategoryShare("top_category_${it.category.name.lowercase()}", it.category.label, it.percent)) }
        }
        return HealthResult(score, band, checks, used.size, good, att, actions, facts)
    }

    private fun lin(v: Double, zero: Double, full: Double) = ((v - zero) / (full - zero)).coerceIn(0.0, 1.0)
}

data class PulseCard(val title: String, val body: String, val why: String)

/** FinSight Pulse (plan 6.6): proactive, explained, dismissible. Local only. */
object Pulse {
    fun cards(tools: Tools): List<PulseCard> = buildList {
        tools.compareMonths().firstOrNull { it.before > 0 && (it.changePct ?: 0) >= 20 && it.now - it.before >= 50_000 }?.let {
            add(PulseCard("Spending pattern", "${it.category.label} spending is up ${it.changePct}% against the previous 30 days.",
                "Last 30 days ${formatPkr(it.now)} vs previous ${formatPkr(it.before)}"))
        }
        tools.recurringPayments().firstOrNull { it.category in setOf(Category.BILLS, Category.MOBILE, Category.ENTERTAINMENT, Category.COMMITTEE) }?.let {
            add(PulseCard("Upcoming payment", "${it.name} looks recurring, around day ${it.dayOfMonth} of the month (~${formatPkr(it.avgMinor)}).",
                "Seen ${it.count} times with similar amounts"))
        }
        val fees = tools.spendByCategory(tools.period("30")).firstOrNull { it.category == Category.FINANCIAL }
        if (fees != null && fees.minor >= 50_000) add(PulseCard("Fees", "You paid ${formatPkr(fees.minor)} in bank fees and charges this month.", "Sum of records classified as fee/tax"))
        if (isEmpty()) tools.spendByCategory(tools.period("30")).firstOrNull()?.let {
            add(PulseCard("Top category", "${it.category.label} is ${it.percent}% of your spending this month.", "Largest category in the last 30 days"))
        }
    }
}

data class WhatIfResult(val verdict: String, val lines: List<String>, val facts: List<SafeFact>)

/** What If? simulator (plan 6.7): uses captured income, spend, balance and an emergency reserve. */
object WhatIf {
    fun afford(tools: Tools, priceMinor: Long): WhatIfResult {
        val p = tools.period("30")
        val income = tools.totalIncome(p); val spend = tools.totalSpend(p)
        val surplus = income - spend
        val bal = tools.latestBalance()
        val essentials = tools.spendByCategory(p).filter { it.category.essential }.sumOf { it.minor }
        val reserve = essentials // keep one month of essentials untouched
        val usable = ((bal ?: 0) - reserve).coerceAtLeast(0)
        val lines = mutableListOf<String>()
        lines += "Monthly surplus (income − spending, last 30 days): ${formatPkr(surplus)}"
        lines += if (bal != null) "Latest known balance: ${formatPkr(bal)}; keeping ${formatPkr(reserve)} as a 1-month emergency reserve" else "No balance seen — using surplus only"
        val verdict = when {
            usable >= priceMinor -> "Yes — you can pay now and still keep a 1-month reserve."
            surplus > 0 -> {
                val months = (priceMinor - usable).toDouble() / surplus
                "Not yet — saving your full surplus, you reach it in about ${(months * 10).toInt() / 10.0} months."
            }
            else -> "Not right now — you spent more than you earned this month. Cut spending first."
        }
        lines += "Assumes income and spending stay the same; upcoming bills and goals not yet included."
        val facts = listOf<SafeFact>(
            SafeFact.RoundedAmount("purchase_price", priceMinor), SafeFact.RoundedAmount("monthly_surplus", surplus.coerceAtLeast(0)),
            SafeFact.Label("can_pay_now", if (usable >= priceMinor) "yes" else "no"),
        )
        return WhatIfResult(verdict, lines, facts)
    }
}
