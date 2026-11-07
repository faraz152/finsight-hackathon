package com.finsight.pipeline

import com.finsight.domain.TxnType

data class Extracted(
    val amountMinor: Long?,
    val currency: String,
    val type: TxnType?,
    val merchant: String?,
    val accountLast4: String?,
    val balanceMinor: Long?,
    val reference: String?,
    val confidence: Double,
    val checks: List<String>,
)

/** Stage 2: regex extraction with the old Flutter weights (amount .40, merchant .20, date .15, type .15, balance .10). */
object RegexExtractor {
    private const val CUR = """(?:PKR|Rs\.?|RS\.?|Rs|INR|₹|USD|US\$|C\$|CAD|AED|SAR|GBP|£|\$)"""
    private val amountRx = Regex("""$CUR\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val amountAfterRx = Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:PKR|Rs)\b""", RegexOption.IGNORE_CASE)
    private val balanceRx = Regex("""(?:avail(?:able)?\.?\s*bal(?:ance)?|available|bal(?:ance)?|new balance|current balance)\s*(?:is|:)?\s*$CUR?\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val accountRx = Regex("""(?:a/?c|acct|account|card|wallet)\s*(?:no\.?|number|ending(?: with)?|#)?\s*[:\-]?\s*[*xX\-]*\s*([0-9]{4})\b""", RegexOption.IGNORE_CASE)
    private val maskedRx = Regex("""[*xX]{2,}\s*([0-9]{4})\b""")
    private val refRx = Regex("""(?:ref(?:erence)?|trx|tid|txn|transaction)\s*(?:id|no\.?|#)?\s*[:\-]?\s*([A-Z0-9]{6,})""", RegexOption.IGNORE_CASE)
    private val merchantRx = Regex(
        """(?:\bat\b|\bto\b|\bfrom\b|\bby\b|\bfor\b)\s+([A-Za-z@][A-Za-z0-9&.'_\- ]{1,40}?)(?=\s+(?:on|at|to|via|from|ref|trx|tid|with|using|for|dated|is|has|into|a/c|account|iban|bill|successful|\d)|\s*[(*]|[.,;:\n]|$)""",
        RegexOption.IGNORE_CASE,
    )
    private const val NAME = """([A-Za-z][A-Za-z0-9&.'\- ]{1,40}?)"""
    /** Pakistan-specific shapes, tried before the generic "at/to/from" rule. */
    private val specificMerchantRx: List<Regex> = listOf(
        Regex("""(?:QR Code|QR|QuickPay ID:?\s*\d*)\s+at\s+$NAME(?=\s*,|\.|\s+on\b)""", RegexOption.IGNORE_CASE),
        Regex("""(?:load|top ?up|recharge)\s+(?:of\s+\S+\s*[0-9][0-9,.]*\s+)?for\s+([A-Za-z]+(?:\s+(?:PREPAID|POSTPAID))?)""", RegexOption.IGNORE_CASE),
        Regex("""\b([A-Z][A-Za-z\-]+(?:\s[A-Z][A-Za-z\-]+)?)\s+[Bb]ill\b"""),
        Regex("""\bsent to\s+$NAME\s*(?:\(|JazzCash|Easypaisa|A/C|Account|IBAN|\*|\d)""", RegexOption.IGNORE_CASE),
    )
    private val rails = setOf("raast", "ibft", "easypaisa", "jazzcash", "qr", "consumer", "transfer", "your", "you", "the", "paid", "credit card", "cash", "debit card")
    private val debitWords = Regex("""(debited|sent|paid|transferred to|withdrawn|withdrawal|purchase|spent|charged|payment of|deducted|bill paid|used for|used at|cash out|transfer of|transfer to|top up|mobile load)""", RegexOption.IGNORE_CASE)
    private val creditWords = Regex("""(credited|received|deposited|refund|reversed|cashback received|salary|cash in)""", RegexOption.IGNORE_CASE)
    private val dateRx = Regex("""\b(\d{1,2}[/\-.](\d{1,2}|[A-Za-z]{3})[/\-.]\d{2,4})\b""")
    private val stopMerchant = setOf("your", "you", "account", "a/c", "ac", "the", "rs", "pkr", "mobile", "wallet", "card", "checking", "savings", "transfer")

    fun parseMinor(s: String): Long? {
        val clean = s.replace(",", "")
        val parts = clean.split(".")
        val whole = parts[0].toLongOrNull() ?: return null
        val frac = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0
        return whole * 100 + frac
    }

    fun findAmount(text: String): Long? {
        val balRanges = balanceRx.findAll(text).map { it.range }.toList()
        val m = amountRx.findAll(text).firstOrNull { a -> balRanges.none { it.first <= a.range.first && a.range.last <= it.last } }
            ?: amountAfterRx.find(text)
        return m?.groupValues?.get(1)?.let(::parseMinor)?.takeIf { it > 0 }
    }

    private fun cleanName(raw: String, allowWallet: Boolean = false): String? {
        var words = raw.trim().trimEnd('.', '*', ',').split(Regex("""\s+""")).filter { it.isNotBlank() }
        while (words.isNotEmpty() && words.first().lowercase() in stopMerchant + setOf("your", "paid", "the", "my")) words = words.drop(1)
        val name = words.joinToString(" ")
        return name.takeIf { it.length >= 2 && (it.lowercase() !in rails || (allowWallet && it.lowercase() in setOf("easypaisa", "jazzcash"))) && it.none(Char::isDigit) }
    }

    fun findMerchant(text: String): String? {
        specificMerchantRx.forEachIndexed { i, rx ->
            rx.findAll(text).forEach { m -> cleanName(m.groupValues[1], allowWallet = i == specificMerchantRx.lastIndex)?.let { return it } }
        }
        return merchantRx.findAll(text).mapNotNull { m ->
            val cand = m.groupValues[1].trim().trimEnd('.')
            if (cand.lowercase().split(" ").first() in stopMerchant) null else cleanName(cand)
        }.firstOrNull()
    }

    /** Distinct transaction-looking amounts (not balance / limit / fee / deduction). More than one = contradictory. */
    fun distinctAmounts(text: String): Set<Long> {
        val balRanges = balanceRx.findAll(text).map { it.range }.toList()
        return amountRx.findAll(text).filter { a ->
            balRanges.none { it.first <= a.range.first && a.range.last <= it.last } &&
                !labelBefore.containsMatchIn(text.substring(maxOf(0, a.range.first - 14), a.range.first))
        }.mapNotNull { parseMinor(it.groupValues[1])?.takeIf { v -> v > 0 } }.toSet()
    }
    private val labelBefore = Regex("""(limit|fee|charges?|deduction|pay|bal(ance)?|tax|wht)\W*$""", RegexOption.IGNORE_CASE)

    fun extract(text: String): Extracted {
        val checks = mutableListOf<String>()
        var conf = 0.0
        val amount = findAmount(text)
        if (amount != null) { conf += 0.40; checks += "Amount found in text" }
        val currency = when {
            Regex("""CAD|C\$""").containsMatchIn(text) -> "CAD"
            Regex("""GBP|£""").containsMatchIn(text) -> "GBP"
            Regex("""\bUSD|\$""").containsMatchIn(text) -> "USD"
            Regex("""\bAED\b""").containsMatchIn(text) -> "AED"
            Regex("""\bINR\b|₹""").containsMatchIn(text) -> "INR"
            else -> "PKR"
        }
        val d = debitWords.find(text)
        val c = creditWords.find(text)
        val type = when {
            d != null && c != null -> if (d.range.first < c.range.first) TxnType.DEBIT else TxnType.CREDIT
            d != null -> TxnType.DEBIT
            c != null -> TxnType.CREDIT
            else -> null
        }
        if (type != null) { conf += 0.15; checks += "Direction from keyword \"${(if (type == TxnType.DEBIT) d else c)!!.value}\"" }
        val merchant = findMerchant(text)
        if (merchant != null) { conf += 0.20; checks += "Merchant \"$merchant\" appears in text" }
        if (dateRx.containsMatchIn(text)) conf += 0.15 else conf += 0.05 // receivedAt is a fine fallback
        if (amount != null && distinctAmounts(text).size > 1) { conf -= 0.25; checks += "Message has conflicting amounts — please confirm" }
        val balance = balanceRx.find(text)?.groupValues?.get(1)?.let(::parseMinor)
        if (balance != null) { conf += 0.10; checks += "Balance found" }
        val acct = (accountRx.find(text) ?: maskedRx.find(text))?.groupValues?.get(1)
        val ref = refRx.find(text)?.groupValues?.get(1)
        return Extracted(amount, currency, type, merchant, acct, balance, ref, conf.coerceAtMost(1.0), checks)
    }
}
