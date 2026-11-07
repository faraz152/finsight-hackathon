package com.finsight.pipeline

import com.finsight.domain.RawInput

/** Stage 1: is this a real completed transaction? Rules only, no LLM. */
object Gate {
    private val strongOtp = Regex("""\b(otp|one[- ]time (password|pin|code)|verification code|security code)\b""", RegexOption.IGNORE_CASE)
    private val otp = Regex("""\b(otp|one[- ]time|verification code|password|pin\b|do not share)""", RegexOption.IGNORE_CASE)
    private val postedVerb = Regex("""(debited|credited|received|sent|paid|transferred|withdrawn|withdrawal|deposited|charged|refunded|reversed|used for|used at|cash in|cash out|purchased)""", RegexOption.IGNORE_CASE)
    private val injection = Regex("""(ignore (all |the )?(previous|prior|above) (instructions|prompts?)|disregard (previous|prior|above)|system prompt|you are (now )?an? (ai|assistant|model)|mark (this )?as (income|credit|salary))""", RegexOption.IGNORE_CASE)
    private val link = Regex("""(https?://|www\.|\b[a-z0-9-]+\.(xyz|top|info|click|link|online|site|live|cc)\b)""", RegexOption.IGNORE_CASE)
    private val urgency = Regex("""(verify|blocked|suspend|update|expire|claim|prize|winner|reward|lucky|inaam|jeeto|confirm your|re-?activate|unlock|bisp|benazir)""", RegexOption.IGNORE_CASE)
    private val prizeScam = Regex("""((inaam|prize|lottery|jeet(a|e)|lucky draw|bisp|benazir).{0,80}(call|contact|send|bhej|rabta))""", RegexOption.IGNORE_CASE)
    private val promo = Regex("""(\boffer\b|% ?off|\bwin\b|cashback upto|discount|promo|lucky draw|subscribe now)""", RegexOption.IGNORE_CASE)
    private val completedVerb = Regex(
        """(debited|credited|received|sent|paid|transferred|withdrawn|purchase|spent|deposited|refund|reversed|charged|payment of|trx|transaction|cash in|cash out|used for|transfer of|successful|withdrawal|used at|mobile load|top up)""",
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
        if (link.containsMatchIn(t) && urgency.containsMatchIn(t)) return Result.Scam("Link plus urgent / prize wording — typical phishing")
        if (prizeScam.containsMatchIn(t)) return Result.Scam("Prize / BISP style message asking you to call or send money")
        if (injection.containsMatchIn(t)) return Result.Scam("Message contains instructions aimed at the app, not a bank alert")
        if (strongOtp.containsMatchIn(t)) return Result.Drop("OTP / security code")
        if (otp.containsMatchIn(t) && !completedVerb.containsMatchIn(t.replace(otp, ""))) return Result.Drop("OTP / security code")
        if (promo.containsMatchIn(t) && !postedVerb.containsMatchIn(t)) return Result.Drop("Promotion")
        if (RegexExtractor.findAmount(t) == null) return Result.Drop("No amount")
        if (!completedVerb.containsMatchIn(t)) return Result.Drop("No completed-payment verb")
        return Result.Pass
    }
}
