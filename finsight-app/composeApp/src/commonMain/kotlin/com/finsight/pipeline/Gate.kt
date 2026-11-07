package com.finsight.pipeline

import com.finsight.domain.RawInput

/** Stage 1: is this a real completed transaction? Rules only, no LLM. */
object Gate {
    private val otp = Regex("""\b(otp|one[- ]time|verification code|password|pin\b|do not share)""", RegexOption.IGNORE_CASE)
    private val promo = Regex("""(\boffer\b|% ?off|\bwin\b|cashback upto|discount|promo|lucky draw|subscribe now)""", RegexOption.IGNORE_CASE)
    private val completedVerb = Regex(
        """(debited|credited|received|sent|paid|transferred|withdrawn|purchase|spent|deposited|refund|reversed|charged|payment of|trx|transaction|cash in|cash out|used for|transfer of|successful)""",
        RegexOption.IGNORE_CASE,
    )
    private val scam = Regex("""(account (will be )?(blocked|suspended)|verify your account|click (the )?link|update your kyc|bit\.ly|tinyurl)""", RegexOption.IGNORE_CASE)

    sealed interface Result {
        data object Pass : Result
        data class Drop(val reason: String) : Result
        data class Scam(val reason: String) : Result
    }

    fun check(input: RawInput): Result {
        val t = input.text
        if (scam.containsMatchIn(t)) return Result.Scam("Looks like a phishing message (link / account-blocked wording)")
        if (otp.containsMatchIn(t) && !completedVerb.containsMatchIn(t.replace(otp, ""))) return Result.Drop("OTP / security code")
        if (promo.containsMatchIn(t) && !completedVerb.containsMatchIn(t)) return Result.Drop("Promotion")
        if (RegexExtractor.findAmount(t) == null) return Result.Drop("No amount")
        if (!completedVerb.containsMatchIn(t)) return Result.Drop("No completed-payment verb")
        return Result.Pass
    }
}
