package com.finsight.data

import com.finsight.domain.Account
import com.finsight.domain.AccountType
import com.finsight.domain.Channel
import com.finsight.domain.RawInput
import com.finsight.pipeline.Integrity

/**
 * Decides which [Account] a message belongs to (port of old FinSight `account_resolver.dart`).
 *
 * Order: 1. institution (bank / wallet) [+ card type + last 4] → 2. sender → 3. card/account last 4 → 4. auto-create.
 * Pure function over the current account list: returns the matched account, or a new one to add.
 */
object AccountResolver {

    data class Institution(val name: String, val type: AccountType)
    data class Resolution(val account: Account, val created: Boolean, val reason: String)

    private val institutions: List<Pair<Regex, Institution>> = listOf(
        """jazz\s?cash|mobilinkcustomer""" to Institution("JazzCash", AccountType.WALLET),
        """easy\s?paisa|telenor\.phoenix""" to Institution("Easypaisa", AccountType.WALLET),
        """sada\s?pay""" to Institution("SadaPay", AccountType.WALLET),
        """naya\s?pay""" to Institution("NayaPay", AccountType.WALLET),
        """meezan""" to Institution("Meezan Bank", AccountType.BANK),
        """bank\s+al[\s-]?habib|\bbahl\b""" to Institution("Bank AL Habib", AccountType.BANK),
        """\bhbl\b|habib bank|com\.hbl""" to Institution("HBL", AccountType.BANK),
        """\bubl\b|united bank""" to Institution("UBL", AccountType.BANK),
        """\bmcb\b""" to Institution("MCB", AccountType.BANK),
        """alfalah""" to Institution("Bank Alfalah", AccountType.BANK),
        """allied bank|\babl\b""" to Institution("Allied Bank", AccountType.BANK),
        """faysal""" to Institution("Faysal Bank", AccountType.BANK),
        """standard chartered|\bscb\b""" to Institution("Standard Chartered", AccountType.BANK),
    ).map { (p, i) -> Regex(p, RegexOption.IGNORE_CASE) to i }

    private val shortCodes = mapOf("8558" to Institution("JazzCash", AccountType.WALLET), "3737" to Institution("Easypaisa", AccountType.WALLET))
    private val maskedCard = Regex("""(?:credit|debit)?\s*card\s*(?:no\.?|number|ending(?: with)?|#)?\s*[:\-]?\s*[*xX\-]*\s*([0-9]{4})\b""", RegexOption.IGNORE_CASE)
    private val anyLast4 = Regex("""(?:(?:a/?c|acct|account|card)\s*(?:no\.?|number|ending(?: with)?|#)?\s*[:\-]?\s*[*xX\-]*\s*|[*xX]{2,}\s*)([0-9]{4})\b""", RegexOption.IGNORE_CASE)

    /** Small internal fallback until BankProfiles lands: sender short code → sender/app name → earliest mention in text. */
    fun institutionGuess(sender: String?, sourceApp: String?, text: String): Institution? {
        // Primary: the 27 Pakistani bank/wallet profiles ported from the old FinSight app.
        com.finsight.pipeline.BankProfiles.identify(sender, sourceApp ?: "", text)?.let { p ->
            val type = when (p.type) {
                com.finsight.pipeline.BankType.WALLET -> AccountType.WALLET
                com.finsight.pipeline.BankType.CARD -> AccountType.CARD
                com.finsight.pipeline.BankType.BANK -> AccountType.BANK
            }
            return Institution(p.name, type)
        }
        sender?.trim()?.let { s -> shortCodes[s]?.let { return it } }
        for (field in listOf(sender, sourceApp)) {
            if (field.isNullOrBlank()) continue
            institutions.firstOrNull { it.first.containsMatchIn(field) }?.let { return it.second }
        }
        return institutions.mapNotNull { (rx, i) -> rx.find(text)?.let { it.range.first to i } }.minByOrNull { it.first }?.second
    }

    /** The user's own account digits in the message (destination "to ••1234" is excluded). */
    fun sourceLast4(text: String): String? {
        val dest = Integrity.counterpartyLast4(text)
        return anyLast4.findAll(text).map { it.groupValues[1] }.firstOrNull { it != dest }
    }

    fun resolve(accounts: List<Account>, input: RawInput, last4Hint: String?, now: Long): Resolution? {
        if (input.channel == Channel.MANUAL) return manual(accounts, input, now)
        val text = input.text
        val last4 = sourceLast4(text) ?: last4Hint?.takeIf { it != Integrity.counterpartyLast4(text) }
        val inst = institutionGuess(input.sender, input.sourceApp, text)
        val isCard = maskedCard.containsMatchIn(text)
        // Wallets are one per provider: digits in wallet messages are usually the other party's phone number.
        val type = when {
            inst?.type == AccountType.WALLET -> AccountType.WALLET
            isCard -> AccountType.CARD
            else -> inst?.type ?: AccountType.BANK
        }
        val digits = if (type == AccountType.WALLET) null else last4
        fun compatible(a: Account) = a.type == type || (a.type != AccountType.CARD && type != AccountType.CARD && a.type != AccountType.CASH)

        // 1. Institution (card-type aware), narrowed by last 4.
        if (inst != null) {
            val same = accounts.filter { it.institution.equals(inst.name, ignoreCase = true) && compatible(it) }
            val hit = when {
                digits != null -> same.firstOrNull { it.last4 == digits } ?: same.firstOrNull { it.last4 == null && it.type == type }
                else -> same.firstOrNull { it.type == type } ?: same.firstOrNull()
            }
            if (hit != null) {
                val adopted = if (hit.last4 == null && digits != null) hit.copy(last4 = digits) else hit
                return Resolution(adopted, false, "Matched ${inst.name} account by institution" + (digits?.let { " and ••$it" } ?: ""))
            }
            return create(accounts, inst.name, type, digits, now, "New ${inst.name} ${type.label.lowercase()} detected")
        }
        // 2. Sender / app name.
        val senderName = (input.sender?.takeIf { it.isNotBlank() && it.any(Char::isLetter) })
        if (senderName != null) {
            accounts.firstOrNull { it.institution.equals(senderName, ignoreCase = true) && (digits == null || it.last4 == null || it.last4 == digits) }
                ?.let { return Resolution(it, false, "Matched account by sender \"$senderName\"") }
        }
        // 3. Card / account last 4 only.
        if (digits != null) {
            (accounts.firstOrNull { it.last4 == digits && it.type == type } ?: accounts.firstOrNull { it.last4 == digits })
                ?.let { return Resolution(it, false, "Matched ${it.displayName} by digits ••$digits") }
        }
        // 4. Auto-create (only when we know something about it).
        if (digits == null && senderName == null) return null
        return create(accounts, senderName ?: "Unknown", type, digits, now, "New account detected from message")
    }

    private fun manual(accounts: List<Account>, input: RawInput, now: Long): Resolution? {
        val t = input.text.lowercase()
        return when {
            "jazzcash" in t || "easypaisa" in t -> {
                val name = if ("jazzcash" in t) "JazzCash" else "Easypaisa"
                accounts.firstOrNull { it.institution == name && it.type == AccountType.WALLET }?.let { Resolution(it, false, "Paid from your $name wallet") }
                    ?: create(accounts, name, AccountType.WALLET, null, now, "New $name wallet")
            }
            Regex("""\b(card|bank)\s*$""").containsMatchIn(t) -> null
            else -> accounts.firstOrNull { it.type == AccountType.CASH }?.let { Resolution(it, false, "Paid in cash") }
                ?: cash(accounts, now).let { Resolution(it, true, "Cash wallet created") }
        }
    }

    fun cash(accounts: List<Account>, now: Long): Account {
        val n = accounts.count { it.type == AccountType.CASH }
        return Account(uniqueId(accounts, "cash"), if (n == 0) "Cash" else "Cash ${n + 1}", AccountType.CASH, null, null, true, null, null, "PKR", now)
    }

    private fun create(accounts: List<Account>, institution: String, type: AccountType, last4: String?, now: Long, reason: String): Resolution {
        val base = listOfNotNull(slug(institution), type.name.lowercase(), last4).joinToString("-")
        return Resolution(Account(uniqueId(accounts, base), institution, type, last4, null, true, null, null, "PKR", now), true, reason)
    }

    private fun uniqueId(accounts: List<Account>, base: String): String {
        val ids = accounts.map { it.id }.toSet()
        if (base !in ids) return base
        var i = 2
        while ("$base-$i" in ids) i++
        return "$base-$i"
    }

    private fun slug(s: String) = s.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-').ifBlank { "acct" }
}
