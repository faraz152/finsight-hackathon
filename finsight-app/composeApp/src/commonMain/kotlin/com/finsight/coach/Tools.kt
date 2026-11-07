package com.finsight.coach

import com.finsight.domain.*

private const val DAY = 86_400_000L

data class CategorySpend(val category: Category, val minor: Long, val percent: Int)
data class Period(val label: String, val from: Long, val to: Long)

/** Fixed Kotlin "SQL" tools: the only source of numbers for Coach. */
class Tools(private val txns: () -> List<Transaction>, private val now: () -> Long) {

    fun period(key: String): Period {
        val n = now()
        return when (key) {
            "week" -> Period("last 7 days", n - 7 * DAY, n)
            "last_month" -> Period("previous 30 days", n - 60 * DAY, n - 30 * DAY)
            "90" -> Period("last 90 days", n - 90 * DAY, n)
            else -> Period("last 30 days", n - 30 * DAY, n)
        }
    }

    fun inPeriod(p: Period) = txns().filter { it.timestamp in p.from..p.to && it.amount.currency == "PKR" }

    fun totalSpend(p: Period) = inPeriod(p).filter { it.isSpend }.sumOf { it.amount.minor }
    fun totalIncome(p: Period) = inPeriod(p).filter { it.isIncome }.sumOf { it.amount.minor }

    fun spendByCategory(p: Period): List<CategorySpend> {
        val spend = inPeriod(p).filter { it.isSpend }
        val total = spend.sumOf { it.amount.minor }.coerceAtLeast(1)
        return spend.groupBy { it.category }.map { (c, l) -> l.sumOf { it.amount.minor }.let { CategorySpend(c, it, (it * 100 / total).toInt()) } }
            .sortedByDescending { it.minor }
    }

    fun topMerchants(p: Period, n: Int = 5): List<Pair<String, Long>> =
        inPeriod(p).filter { it.isSpend && it.merchant != null }.groupBy { it.merchant!! }
            .map { (m, l) -> m to l.sumOf { it.amount.minor } }.sortedByDescending { it.second }.take(n)

    data class Compare(val category: Category, val now: Long, val before: Long) {
        val changePct: Int? get() = if (before > 0) ((now - before) * 100 / before).toInt() else null
    }

    fun compareMonths(): List<Compare> {
        val a = spendByCategory(period("30")).associate { it.category to it.minor }
        val b = spendByCategory(period("last_month")).associate { it.category to it.minor }
        return (a.keys + b.keys).map { Compare(it, a[it] ?: 0, b[it] ?: 0) }.sortedByDescending { kotlin.math.abs(it.now - it.before) }
    }

    data class Recurring(val name: String, val category: Category, val avgMinor: Long, val dayOfMonth: Int, val count: Int)

    /** Same merchant (or bill category) on ≥2 different months → recurring. */
    fun recurringPayments(): List<Recurring> =
        txns().filter { it.isSpend && (it.merchantKey != null) }
            .groupBy { it.merchantKey!! }
            .filter { (_, l) -> l.map { it.timestamp / (30 * DAY) }.toSet().size >= 2 || (l.size >= 2 && l.first().category in setOf(Category.BILLS, Category.MOBILE, Category.COMMITTEE)) }
            .map { (k, l) -> Recurring(l.first().merchant ?: k, l.first().category, l.sumOf { it.amount.minor } / l.size, dayOfMonth(l.first().timestamp), l.size) }
            .sortedByDescending { it.avgMinor }

    /** Sum of the latest known balance per account (accounts without a balance in messages are unknown, not zero). */
    fun latestBalance(): Long? = txns().filter { it.balance != null && it.balance.currency == "PKR" }
        .groupBy { it.accountLast4 ?: it.sourceApp }.values.map { l -> l.maxByOrNull { it.timestamp }!!.balance!!.minor }
        .takeIf { it.isNotEmpty() }?.sum()

    fun spendAt(name: String): Pair<Long, Int> {
        val l = txns().filter { it.isSpend && it.merchant?.lowercase()?.contains(name.lowercase()) == true }
        return l.sumOf { it.amount.minor } to l.size
    }

    fun dataDays(): Int {
        val l = txns(); if (l.isEmpty()) return 0
        return ((l.maxOf { it.timestamp } - l.minOf { it.timestamp }) / DAY + 1).toInt()
    }

    private fun dayOfMonth(ts: Long): Int = (((ts / DAY) % 30) + 1).toInt()
}
