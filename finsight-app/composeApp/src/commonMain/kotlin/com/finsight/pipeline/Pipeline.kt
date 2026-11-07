package com.finsight.pipeline

import com.finsight.domain.*

sealed interface PipelineResult {
    data class Accepted(val txn: Transaction) : PipelineResult
    data class Dropped(val reason: String) : PipelineResult
    data class ScamWarning(val reason: String, val text: String) : PipelineResult
}

/** Gate → Extract → Integrity → Categorize → Score. Rules only; Gemma plugs in later via LocalLlm. */
class Pipeline(
    private val memory: () -> Map<String, Category>,
    private val ownAccounts: () -> Set<String>,
) {
    /** Public so Repository.restore can keep ids unique after a restart. */
    var nextId = 1L
    private val quickAdd = Regex("""^\s*([A-Za-z][A-Za-z .'&-]*?)\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(cash|card|jazzcash|easypaisa|bank)?\s*$""", RegexOption.IGNORE_CASE)

    fun process(input: RawInput): PipelineResult {
        if (input.channel == Channel.MANUAL) quickAddLine(input)?.let { return it }
        when (val g = Gate.check(input)) {
            is Gate.Result.Drop -> return PipelineResult.Dropped(g.reason)
            is Gate.Result.Scam -> return PipelineResult.ScamWarning(g.reason, input.text)
            Gate.Result.Pass -> {}
        }
        val ex = RegexExtractor.extract(input.text)
        val amount = ex.amountMinor ?: return PipelineResult.Dropped("No verifiable amount")
        val type = ex.type ?: TxnType.DEBIT
        val why = mutableListOf<String>()
        why += ex.checks
        if (ex.type == null) why += "Direction unclear — assumed debit"
        val cls = Integrity.classify(input.text, type, ownAccounts(), ex.accountLast4)
        why += cls.reasons
        val key = Integrity.merchantKey(ex.merchant)
        val cat = RuleCategorizer.categorize(input.text, ex.merchant, key, cls.kind, memory())
        why += "Category: ${cat.reason}"
        var conf = minOf(cat.confidence, ex.confidence + 0.2)
        if (ex.type == null) conf = minOf(conf, 0.7)
        why += "Parse confidence ${pct(ex.confidence)}, category confidence ${pct(cat.confidence)} → final ${pct(conf)}"
        return PipelineResult.Accepted(
            Transaction(
                id = nextId++, amount = Money(amount, ex.currency), type = type, kind = cls.kind, status = cls.status,
                merchant = ex.merchant, merchantKey = key, accountLast4 = ex.accountLast4,
                balance = ex.balanceMinor?.let { Money(it, ex.currency) }, category = cat.category, confidence = conf,
                categorySource = cat.source, parseSource = ParseSource.REGEX, channel = input.channel, sourceApp = input.sourceApp,
                needsReview = conf < 0.90, rawText = input.text, timestamp = input.receivedAt,
                fingerprint = Integrity.fingerprint(key, amount, ex.accountLast4, ex.reference, input.receivedAt),
                explanation = why,
            ),
        )
    }

    /** "lunch 850 card" / "chai 150 cash" → same pipeline, typed by the user. */
    private fun quickAddLine(input: RawInput): PipelineResult? {
        val m = quickAdd.find(input.text) ?: return null
        val what = m.groupValues[1].trim()
        val amount = RegexExtractor.parseMinor(m.groupValues[2]) ?: return null
        val key = Integrity.merchantKey(what)
        val cat = RuleCategorizer.categorize(what, what, key, TxnKind.EXPENSE, memory())
        val conf = maxOf(cat.confidence, 0.75)
        return PipelineResult.Accepted(
            Transaction(
                id = nextId++, amount = Money(amount), type = TxnType.DEBIT, kind = TxnKind.EXPENSE, status = TxnStatus.POSTED,
                merchant = what, merchantKey = key, accountLast4 = null, balance = null, category = cat.category,
                confidence = conf, categorySource = cat.source, parseSource = ParseSource.USER, channel = Channel.MANUAL,
                sourceApp = "You", needsReview = conf < 0.90, rawText = input.text, timestamp = input.receivedAt,
                fingerprint = "manual:${input.receivedAt}",
                explanation = listOf("Quick add by you (${m.groupValues.getOrNull(3)?.ifBlank { "cash" } ?: "cash"})", "Category: ${cat.reason}"),
            ),
        )
    }

    private fun pct(d: Double) = "${(d * 100).toInt()}%"
}
