package com.finsight.pipeline

import com.finsight.domain.Category
import com.finsight.domain.CategorySource
import com.finsight.domain.TxnKind

data class CategoryDecision(val category: Category, val confidence: Double, val source: CategorySource, val reason: String)

/** Stage 3: memory → exact merchant → keywords → transfer heuristics. */
object RuleCategorizer {
    private val merchants: Map<String, Category> = mapOf(
        "kfc" to Category.FOOD, "mcdonald" to Category.FOOD, "foodpanda" to Category.FOOD, "cheezious" to Category.FOOD,
        "pizza hut" to Category.FOOD, "subway" to Category.FOOD, "starbucks" to Category.FOOD, "hardee" to Category.FOOD,
        "careem" to Category.TRANSPORT, "uber" to Category.TRANSPORT, "indrive" to Category.TRANSPORT, "bykea" to Category.TRANSPORT,
        "yango" to Category.TRANSPORT, "pso" to Category.TRANSPORT, "shell" to Category.TRANSPORT, "total parco" to Category.TRANSPORT,
        "daraz" to Category.SHOPPING, "amazon" to Category.SHOPPING, "khaadi" to Category.SHOPPING, "outfitters" to Category.SHOPPING,
        "imtiaz" to Category.GROCERIES, "carrefour" to Category.GROCERIES, "metro" to Category.GROCERIES, "al-fatah" to Category.GROCERIES,
        "chase up" to Category.GROCERIES, "naheed" to Category.GROCERIES, "tesco" to Category.GROCERIES, "walmart" to Category.GROCERIES,
        "k-electric" to Category.BILLS, "k electric" to Category.BILLS, "lesco" to Category.BILLS, "iesco" to Category.BILLS,
        "mepco" to Category.BILLS, "sngpl" to Category.BILLS, "ssgc" to Category.BILLS, "wasa" to Category.BILLS,
        "ptcl" to Category.MOBILE, "nayatel" to Category.MOBILE, "stormfiber" to Category.MOBILE, "zong" to Category.MOBILE,
        "telenor" to Category.MOBILE, "ufone" to Category.MOBILE, "jazz load" to Category.MOBILE,
        "netflix" to Category.ENTERTAINMENT, "spotify" to Category.ENTERTAINMENT, "cinepax" to Category.ENTERTAINMENT, "youtube" to Category.ENTERTAINMENT,
        "savour" to Category.FOOD, "student biryani" to Category.FOOD, "gloria jeans" to Category.FOOD, "tea junction" to Category.FOOD,
        "chaayé khana" to Category.FOOD, "domino" to Category.FOOD, "ox burger" to Category.FOOD,
        "pesco" to Category.BILLS, "hesco" to Category.BILLS, "fesco" to Category.BILLS, "gepco" to Category.BILLS, "qesco" to Category.BILLS,
        "sui gas" to Category.BILLS, "kwsc" to Category.BILLS, "kwsb" to Category.BILLS,
        "jazz prepaid" to Category.MOBILE, "jazz postpaid" to Category.MOBILE, "warid" to Category.MOBILE, "prepaid" to Category.MOBILE, "postpaid" to Category.MOBILE,
        "go petroleum" to Category.TRANSPORT, "attock petroleum" to Category.TRANSPORT, "hascol" to Category.TRANSPORT, "service station" to Category.TRANSPORT,
        "cng" to Category.TRANSPORT, "petrol pump" to Category.TRANSPORT,
        "hyperstar" to Category.GROCERIES, "springs" to Category.GROCERIES, "esajee" to Category.GROCERIES, "super market" to Category.GROCERIES,
        "sapphire" to Category.SHOPPING, "gul ahmed" to Category.SHOPPING, "bata" to Category.SHOPPING, "temu" to Category.SHOPPING,
        "aga khan" to Category.HEALTH, "chughtai" to Category.HEALTH, "sehat" to Category.HEALTH,
        "dvago" to Category.HEALTH, "pharmacy" to Category.HEALTH, "hospital" to Category.HEALTH, "shaukat khanum" to Category.HEALTH,
    )
    private val keywords: List<Pair<Regex, Category>> = listOf(
        Regex("""\b(restaurant|cafe|food|burger|biryani|chai|lunch|dinner|bakery|dhaba|khana|nihari|karahi|pizza)""", RegexOption.IGNORE_CASE) to Category.FOOD,
        Regex("""\b(grocery|mart\b|super ?store|super ?market|kiryana|karyana|vegetable|sabzi)""", RegexOption.IGNORE_CASE) to Category.GROCERIES,
        Regex("""\b(fuel|petrol|ride|taxi|rickshaw|parking|toll|motorway|m-tag)""", RegexOption.IGNORE_CASE) to Category.TRANSPORT,
        Regex("""\b(electric|bill payment|utility|gas bill|water bill|bill paid|consumer no)""", RegexOption.IGNORE_CASE) to Category.BILLS,
        Regex("""\b(mobile load|top ?up|recharge|internet|broadband|package|easyload|balance load)""", RegexOption.IGNORE_CASE) to Category.MOBILE,
        Regex("""\b(medical|medicine|clinic|lab\b|laboratory|doctor|pharma)""", RegexOption.IGNORE_CASE) to Category.HEALTH,
        Regex("""\b(rent\b|kiraya|maintenance|society)""", RegexOption.IGNORE_CASE) to Category.HOUSING,
        Regex("""\b(school|university|tuition|fee voucher|course|academy|college)""", RegexOption.IGNORE_CASE) to Category.EDUCATION,
        Regex("""\b(committee|bc payment|kameti)""", RegexOption.IGNORE_CASE) to Category.COMMITTEE,
        Regex("""\b(shopping|store|mall|clothing|pos purchase|online purchase)""", RegexOption.IGNORE_CASE) to Category.SHOPPING,
        Regex("""\b(cinema|movie|game|subscription)""", RegexOption.IGNORE_CASE) to Category.ENTERTAINMENT,
    )

    private val atm = Regex("""(\batm\b|cash withdrawal|cash out)""", RegexOption.IGNORE_CASE)
    private val rail = Regex("""\b(raast|ibft|iban|funds transfer|sent to)""", RegexOption.IGNORE_CASE)

    fun categorize(text: String, merchant: String?, merchantKey: String?, kind: TxnKind, memory: Map<String, Category>): CategoryDecision {
        if (merchantKey != null) memory[merchantKey]?.let {
            return CategoryDecision(it, 0.98, CategorySource.USER_MEMORY, "You categorized \"$merchantKey\" before")
        }
        when (kind) {
            TxnKind.FEE -> return CategoryDecision(Category.FINANCIAL, 0.92, CategorySource.RULE, "Fee / tax wording")
            TxnKind.REFUND, TxnKind.REVERSAL -> return CategoryDecision(Category.OTHER, 0.90, CategorySource.RULE, "Refund or reversal")
            else -> {}
        }
        if (kind == TxnKind.INCOME) return CategoryDecision(Category.INCOME, 0.90, CategorySource.RULE, "Money received")
        if (atm.containsMatchIn(text)) return CategoryDecision(Category.OTHER, 0.90, CategorySource.RULE, "Cash withdrawal (ATM / agent cash out)")
        val m = merchant?.lowercase().orEmpty()
        merchants.entries.firstOrNull { m.contains(it.key) || (m.isBlank() && text.lowercase().contains(it.key)) }?.let {
            return CategoryDecision(it.value, 0.95, CategorySource.RULE, "Known merchant \"${it.key}\"")
        }
        if (kind == TxnKind.TRANSFER_OUT && rail.containsMatchIn(text)) return CategoryDecision(Category.TRANSFER, 0.90, CategorySource.RULE, "Raast / IBFT / wallet transfer (\"${rail.find(text)!!.value}\")")
        val haystack = "$m ${text.lowercase()}"
        keywords.firstOrNull { it.first.containsMatchIn(haystack) }?.let { (rx, cat) ->
            return CategoryDecision(cat, if (m.isNotBlank() && rx.containsMatchIn(m)) 0.85 else 0.78, CategorySource.RULE, "Keyword \"${rx.find(haystack)!!.value}\"")
        }
        if (kind == TxnKind.TRANSFER_OUT || kind == TxnKind.TRANSFER_IN) return CategoryDecision(Category.TRANSFER, 0.80, CategorySource.RULE, "Transfer to a person or wallet")
        return CategoryDecision(Category.OTHER, 0.50, CategorySource.NONE, "No rule matched — needs your review")
    }
}
