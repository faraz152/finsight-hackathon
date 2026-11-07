package com.finsight.pipeline

enum class BankType { BANK, WALLET, CARD }

/**
 * A Pakistani bank or wallet that can send us transaction messages.
 * `senders` = SMS sender ids / short codes; `aliases` = names as they appear in message text or app names / package tokens.
 */
data class BankProfile(
    val id: String,
    val name: String,
    val type: BankType,
    val senders: List<String>,
    val aliases: List<String> = emptyList(),
    val packages: List<String> = emptyList(),
)

/**
 * Ported from the old FinSight `assets/data/bank_profiles/pakistan_banks.json` (8 profiles, sender ids kept verbatim)
 * and extended with the other major PK banks / wallets (name + text aliases only where the short code is not known).
 * Pure function, no I/O.
 */
object BankProfiles {
    val all: List<BankProfile> = listOf(
        // --- from pakistan_banks.json ---
        BankProfile("jazzcash", "JazzCash", BankType.WALLET, listOf("8558", "8657", "JazzCash", "JC"), listOf("jazzcash", "jazz cash"),
            listOf("com.techlogix.mobilinkcustomer")),
        BankProfile("easypaisa", "Easypaisa", BankType.WALLET, listOf("3737", "EasyPaisa", "EP"), listOf("easypaisa", "easy paisa"),
            listOf("pk.com.telenor.phoenix")),
        BankProfile("ubl", "UBL", BankType.BANK, listOf("18258", "UBL", "8258"), listOf("ubl", "united bank", "ubl digital"), listOf("app.com.brd")),
        BankProfile("hbl", "HBL", BankType.BANK, listOf("HBL", "4250"), listOf("hbl", "habib bank limited", "hbl mobile"),
            listOf("com.hbl.android.hblmobilebanking")),
        BankProfile("mcb", "MCB Bank", BankType.BANK, listOf("MCB", "6227"), listOf("mcb", "muslim commercial bank", "mcb live")),
        BankProfile("meezan", "Meezan Bank", BankType.BANK, listOf("MEEZAN", "6329", "MeezanBank"), listOf("meezan bank", "meezan"),
            listOf("invo8.meezan.mb")),
        BankProfile("sadapay", "SadaPay", BankType.WALLET, listOf("SadaPay", "7529"), listOf("sadapay")),
        BankProfile("nayapay", "NayaPay", BankType.WALLET, listOf("NayaPay", "7297"), listOf("nayapay")),
        // --- other major PK banks / wallets ---
        BankProfile("alfalah", "Bank Alfalah", BankType.BANK, listOf("BAFL", "BankAlfalah", "ALFALAH"), listOf("bank alfalah", "alfalah", "alfa")),
        BankProfile("abl", "Allied Bank", BankType.BANK, listOf("ABL", "AlliedBank"), listOf("allied bank", "abl", "myabl")),
        BankProfile("askari", "Askari Bank", BankType.BANK, listOf("AKBL", "AskariBank", "Askari"), listOf("askari bank", "askari")),
        BankProfile("bahl", "Bank AL Habib", BankType.BANK, listOf("BAHL", "BankAlHabib"), listOf("bank al habib", "bank al-habib", "bahl")),
        BankProfile("faysal", "Faysal Bank", BankType.BANK, listOf("FBL", "FaysalBank"), listOf("faysal bank", "faysal")),
        BankProfile("scb", "Standard Chartered", BankType.BANK, listOf("SCB", "StanChart", "SCBPK"), listOf("standard chartered", "stanchart")),
        BankProfile("hmb", "Habib Metro", BankType.BANK, listOf("HMB", "HabibMetro"), listOf("habibmetro", "habib metro", "hmb")),
        BankProfile("nbp", "National Bank", BankType.BANK, listOf("NBP"), listOf("national bank of pakistan", "nbp")),
        BankProfile("bankislami", "BankIslami", BankType.BANK, listOf("BankIslami", "BIPL"), listOf("bankislami", "bank islami")),
        BankProfile("dib", "Dubai Islamic Bank", BankType.BANK, listOf("DIB", "DIBPK"), listOf("dubai islamic", "dib")),
        BankProfile("js", "JS Bank", BankType.BANK, listOf("JSBL", "JSBank"), listOf("js bank", "jsbl")),
        BankProfile("soneri", "Soneri Bank", BankType.BANK, listOf("Soneri", "SoneriBank"), listOf("soneri bank", "soneri")),
        BankProfile("bop", "Bank of Punjab", BankType.BANK, listOf("BOP", "BankofPunjab"), listOf("bank of punjab", "bop")),
        BankProfile("albaraka", "Al Baraka Bank", BankType.BANK, listOf("AlBaraka"), listOf("al baraka", "albaraka")),
        BankProfile("mcbislamic", "MCB Islamic", BankType.BANK, listOf("MCBIslamic"), listOf("mcb islamic")),
        BankProfile("silk", "Silkbank", BankType.BANK, listOf("Silkbank"), listOf("silkbank", "silk bank")),
        BankProfile("zindigi", "Zindigi", BankType.WALLET, listOf("Zindigi"), listOf("zindigi")),
        BankProfile("upaisa", "UPaisa", BankType.WALLET, listOf("UPaisa"), listOf("upaisa", "u paisa")),
        BankProfile("konnect", "HBL Konnect", BankType.WALLET, listOf("Konnect", "HBLKonnect"), listOf("konnect", "hbl konnect")),
    )

    private fun norm(s: String) = s.lowercase().replace(Regex("""[^a-z0-9]"""), "")
    private fun wordRx(a: String) = Regex("""(?<![a-z0-9])${Regex.escape(a)}(?![a-z0-9])""", RegexOption.IGNORE_CASE)
    private val creditCard = Regex("""\b(credit card|mastercard|avail\.? ?limit|available limit)""", RegexOption.IGNORE_CASE)
    private val counterpartyBefore = Regex("""\b(to|from)\s+(the\s+)?$""", RegexOption.IGNORE_CASE)

    /** Longest alias first so "HBL Konnect" beats "HBL" and "MCB Islamic" beats "MCB". */
    private val aliasIndex: List<Pair<Regex, BankProfile>> =
        all.flatMap { p -> p.aliases.map { it to p } }.sortedByDescending { it.first.length }.map { wordRx(it.first) to it.second }

    /**
     * Which bank / wallet sent this message. Order: SMS sender id → notification app (package or label) → bank name in the text
     * (ignoring names right after "to"/"from", which are usually the counterparty). Returns `type = CARD` when the message is
     * about a credit card of that bank. Null when nothing matches.
     */
    fun identify(sender: String?, sourceApp: String, text: String): BankProfile? {
        val base = bySender(sender) ?: byApp(sourceApp) ?: byText(text) ?: return null
        return if (base.type == BankType.BANK && creditCard.containsMatchIn(text)) base.copy(type = BankType.CARD) else base
    }

    fun byId(id: String): BankProfile? = all.firstOrNull { it.id == id }

    private fun bySender(sender: String?): BankProfile? {
        val s = sender?.let(::norm)?.takeIf { it.isNotBlank() } ?: return null
        all.firstOrNull { p -> p.senders.any { norm(it) == s } }?.let { return it }
        // "AD-HBLBNK", "MEEZANBANK", "UBL-ALERT": alphabetic ids of 3+ chars contained in the sender.
        return all.flatMap { p -> p.senders.filter { it.length >= 3 && it.any(Char::isLetter) }.map { norm(it) to p } }
            .sortedByDescending { it.first.length }
            .firstOrNull { s.contains(it.first) }?.second
    }

    private fun byApp(app: String): BankProfile? {
        val a = app.trim().lowercase()
        if (a.isBlank()) return null
        all.firstOrNull { p -> p.packages.any { it == a } }?.let { return it }
        val spaced = a.replace(Regex("""[._\-]+"""), " ")
        aliasIndex.firstOrNull { it.first.containsMatchIn(spaced) }?.let { return it.second }
        return all.firstOrNull { p -> p.senders.any { it.length >= 3 && norm(it) == norm(a) } }
    }

    private fun byText(text: String): BankProfile? {
        val t = text.replace("Jazz Cash", "JazzCash", ignoreCase = true)
        return aliasIndex.mapNotNull { (rx, p) ->
            rx.findAll(t).firstOrNull { m -> !counterpartyBefore.containsMatchIn(t.substring(maxOf(0, m.range.first - 10), m.range.first)) }
                ?.let { it.range.first to p }
        }.minByOrNull { it.first }?.second
    }
}
