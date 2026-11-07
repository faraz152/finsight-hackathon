package com.finsight.pipeline

import com.finsight.domain.TxnKind
import com.finsight.domain.TxnStatus
import com.finsight.domain.TxnType

/** Transaction Integrity Layer (plan 6.2): kind, status, fingerprint. Duplicate merge happens in the repository. */
object Integrity {
    private val reversal = Regex("""(reversed|reversal|reverted)""", RegexOption.IGNORE_CASE)
    private val refund = Regex("""(refund|refunded|cashback)""", RegexOption.IGNORE_CASE)
    private val pending = Regex("""(pending|on hold|authori[sz]ation hold|is being processed)""", RegexOption.IGNORE_CASE)
    private val fee = Regex("""(service charges?|annual fee|sms charges|excise duty|withholding tax|wht\b|late payment charge)""", RegexOption.IGNORE_CASE)
    private val transferOut = Regex("""(sent to|transferred to|funds transfer|ibft|raast|to .*account|to .*a/c|wallet to)""", RegexOption.IGNORE_CASE)
    private val transferIn = Regex("""(received from|transfer(red)? from|ibft from|raast from|via (raast|ibft)|received via)""", RegexOption.IGNORE_CASE)
    private val salary = Regex("""(salary|payroll)""", RegexOption.IGNORE_CASE)

    data class Classification(val kind: TxnKind, val status: TxnStatus, val reasons: List<String>, val ownTransfer: Boolean = false)

    /** Destination account digits: a masked number after "to"/"into" (never the "from" source account). */
    private val counterpartyRx = Regex("""\b(?:to|into)\s+(?:(?!\bfrom\b|\busing\b)[^.\n]){0,50}?[*xX]{2,}\s*([0-9]{4})\b""", RegexOption.IGNORE_CASE)

    fun counterpartyLast4(text: String): String? = counterpartyRx.find(text)?.groupValues?.get(1)

    fun classify(text: String, type: TxnType, ownAccounts: Set<String>, counterpartyLast4: String?): Classification {
        val r = mutableListOf<String>()
        val status = when {
            reversal.containsMatchIn(text) -> TxnStatus.REVERSED
            pending.containsMatchIn(text) -> TxnStatus.PENDING.also { r += "Marked pending (\"${pending.find(text)!!.value}\")" }
            else -> TxnStatus.POSTED
        }
        var own = false
        val kind = when {
            reversal.containsMatchIn(text) -> TxnKind.REVERSAL.also { r += "Reversal wording: excluded from spend and income" }
            refund.containsMatchIn(text) && type == TxnType.CREDIT -> TxnKind.REFUND.also { r += "Refund: not counted as income" }
            fee.containsMatchIn(text) && type == TxnType.DEBIT -> TxnKind.FEE.also { r += "Bank fee / tax" }
            type == TxnType.DEBIT && counterpartyLast4 != null && counterpartyLast4 in ownAccounts ->
                TxnKind.TRANSFER_OUT.also { own = true; r += "Transfer to your own account ••$counterpartyLast4: not spending" }
            type == TxnType.CREDIT && salary.containsMatchIn(text) -> TxnKind.INCOME.also { r += "Salary credit" }
            type == TxnType.CREDIT && transferIn.containsMatchIn(text) -> TxnKind.INCOME.also { r += "Incoming transfer (Raast / IBFT / wallet) counted as income" }
            type == TxnType.DEBIT && transferOut.containsMatchIn(text) -> TxnKind.TRANSFER_OUT.also { r += "Outgoing transfer" }
            type == TxnType.CREDIT -> TxnKind.INCOME
            else -> TxnKind.EXPENSE
        }
        return Classification(kind, status, r, own)
    }

    /** Same payment seen twice (SMS + app notification) must produce the same fingerprint. */
    fun fingerprint(merchantKey: String?, amountMinor: Long, last4: String?, ref: String?, @Suppress("UNUSED_PARAMETER") timestamp: Long): String {
        if (!ref.isNullOrBlank()) return "ref:$ref"
        return "fp:${merchantKey ?: "-"}|$amountMinor|${last4 ?: "-"}"
    }

    fun merchantKey(merchant: String?): String? =
        merchant?.lowercase()?.replace(Regex("""[^a-z0-9 ]"""), " ")?.split(" ")?.filter { it.isNotBlank() }
            ?.filterNot { it in setOf("pvt", "ltd", "the", "account", "a", "c", "mobile", "jazzcash", "easypaisa") }
            ?.take(3)?.joinToString(" ")?.ifBlank { null }
}
