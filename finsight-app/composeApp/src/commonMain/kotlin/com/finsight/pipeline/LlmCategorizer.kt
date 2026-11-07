package com.finsight.pipeline

import com.finsight.ai.LocalLlm
import com.finsight.domain.Category
import com.finsight.domain.CategorySource
import com.finsight.domain.Transaction

/** What Gemma said: a valid category, 0.80 on the first try, 0.60 if it needed the repair retry (plan 4, stage 3). */
data class LlmVerdict(val category: Category, val confidence: Double, val repaired: Boolean)

/** Rule + Gemma combined (plan 4, stage 4). needsReview follows the usual < 0.90 rule. */
data class HybridDecision(
    val category: Category,
    val confidence: Double,
    val source: CategorySource,
    val note: String,
) {
    val needsReview: Boolean get() = confidence < 0.90
}

/**
 * Stage 3b: Gemma as a constrained classifier for transactions the rules were unsure about.
 * On-device only (LocalLlm), never the cloud. Returns null on any failure so callers keep the rule answer.
 */
class LlmCategorizer(private val llm: LocalLlm) {

    /** @param corrections user-corrected examples ("merchant -> category"), shown first as few-shot. */
    suspend fun classify(t: Transaction, corrections: List<Pair<String, Category>> = emptyList()): LlmVerdict? {
        if (!llm.available) return null
        val first = runCatching { llm.complete(prompt(t, corrections), maxTokens = 8) }.getOrNull()
        parse(first)?.let { return LlmVerdict(it, 0.80, repaired = false) }
        val retry = runCatching { llm.complete(repairPrompt(t), maxTokens = 8) }.getOrNull()
        parse(retry)?.let { return LlmVerdict(it, 0.60, repaired = true) }
        return null
    }

    companion object {
        val ids: List<String> = Category.entries.map { it.id }

        private val Category.id: String get() = name.lowercase()

        private val staticExamples = listOf(
            "KFC Gulberg | DEBIT | PKR 2,890 -> food",
            "Careem ride | DEBIT | PKR 650 -> transport",
            "K-Electric bill | DEBIT | PKR 8,450 -> bills",
            "Nayatel internet | DEBIT | PKR 2,999 -> mobile",
            "Imtiaz Super Market | DEBIT | PKR 5,200 -> groceries",
            "ACME TECH PVT LTD salary | CREDIT | PKR 185,000 -> income",
        )

        fun line(t: Transaction): String =
            "${t.merchant ?: "unknown"} | ${t.type} | ${t.amount}"

        fun prompt(t: Transaction, corrections: List<Pair<String, Category>> = emptyList()): String {
            val examples = (corrections.take(4).map { (m, c) -> "$m -> ${c.id}" } + staticExamples).take(8)
            return buildString {
                appendLine("Pick exactly one category id for this transaction. Reply with the id only.")
                appendLine("Categories: ${ids.joinToString(", ")}")
                appendLine("Examples:")
                examples.forEach { appendLine(it) }
                appendLine("Transaction: ${line(t)}")
                // A short hint from the raw text helps when the merchant is missing; trimmed to keep the prompt small.
                if (t.merchant == null) appendLine("Message: ${t.rawText.take(160)}")
                append("Category id:")
            }
        }

        fun repairPrompt(t: Transaction): String =
            "Reply with one id from the list: ${ids.joinToString(", ")}\nTransaction: ${line(t)}\nId:"

        private val aliases = mapOf(
            "utilities" to Category.BILLS, "utility" to Category.BILLS, "bill" to Category.BILLS,
            "internet" to Category.MOBILE, "telecom" to Category.MOBILE, "grocery" to Category.GROCERIES,
            "fees" to Category.FINANCIAL, "fee" to Category.FINANCIAL, "rent" to Category.HOUSING,
            "restaurant" to Category.FOOD, "dining" to Category.FOOD, "salary" to Category.INCOME,
            "medical" to Category.HEALTH, "travel" to Category.TRAVEL, "kameti" to Category.COMMITTEE,
            "personal" to Category.PERSONAL_CARE, "charity" to Category.CHARITY, "zakat" to Category.CHARITY,
            "donation" to Category.CHARITY, "tax" to Category.TAXES, "government" to Category.TAXES,
            "investment" to Category.INVESTMENT, "savings" to Category.INVESTMENT,
        )

        /** Defensive: trim, accept a JSON object's "category", else the first word; must be in the allowed list. */
        fun parse(output: String?): Category? {
            val s = output?.trim()?.lowercase() ?: return null
            if (s.isEmpty()) return null
            val token = Regex(""""(?:category|id)"\s*:\s*"([a-z &]+)"""").find(s)?.groupValues?.get(1)?.trim()
                ?: Regex("""[a-z]+""").find(s)?.value
                ?: return null
            lookup(token)?.let { return it }
            // First word wasn't an id (e.g. "Category: food"): accept only if a short reply names exactly one id.
            if (s.length > 40) return null
            val found = Regex("""[a-z]+""").findAll(s).mapNotNull { m -> Category.entries.firstOrNull { it.id == m.value } }.toSet()
            return found.singleOrNull()
        }

        private fun lookup(token: String): Category? =
            Category.entries.firstOrNull { it.id == token || it.label.lowercase() == token } ?: aliases[token]

        private fun pct(d: Double) = "${(d * 100).toInt()}%"

        /**
         * Hybrid score. `ruleConfidence` is the transaction's current (rule-based) confidence.
         * Agree → boost, HYBRID. Disagree → higher one wins, capped at 0.85 (stays in review).
         * No rule answer (source NONE) → Gemma alone, source LLM.
         */
        fun combine(rule: Category, ruleConfidence: Double, ruleSource: CategorySource, g: LlmVerdict): HybridDecision {
            val gl = g.category.id
            val rl = rule.id
            if (ruleSource == CategorySource.NONE) {
                return HybridDecision(g.category, g.confidence, CategorySource.LLM,
                    "Gemma: $gl; rule: none → $gl (${pct(g.confidence)})")
            }
            if (g.category == rule) {
                val c = minOf(1.0, maxOf(ruleConfidence, g.confidence) + 0.15)
                return HybridDecision(rule, c, CategorySource.HYBRID, "Gemma: $gl; rule: $rl → agree, ${pct(c)}")
            }
            val gemmaWins = g.confidence > ruleConfidence
            val c = minOf(0.85, maxOf(g.confidence, ruleConfidence))
            return if (gemmaWins)
                HybridDecision(g.category, c, CategorySource.LLM, "Gemma: $gl; rule: $rl → $gl (disagree, please review)")
            else
                HybridDecision(rule, c, ruleSource, "Gemma: $gl; rule: $rl → $rl (disagree, please review)")
        }
    }
}
