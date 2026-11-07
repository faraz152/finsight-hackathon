package com.finsight.pipeline

import com.finsight.domain.Category
import com.finsight.domain.CategorySource
import com.finsight.domain.TxnKind

data class CategoryDecision(val category: Category, val confidence: Double, val source: CategorySource, val reason: String)

/**
 * Stage 3, ported from the old FinSight `rule_based_categorizer.dart` + `categorization_orchestrator.dart`:
 * memory (0.98) → kind rules (fee / refund / income / ATM) → known merchant (0.95, or 0.88 when only seen in the text)
 * → keywords (0.75–0.88, per-category base confidence from the old app, +agreement bonus) → transfer heuristic (0.80)
 * → old category_mapping.json keywords (0.75) → person-name heuristic (0.55) → OTHER (0.50, review).
 *
 * All matching is whole-word (the old app used plain `contains`, which made "rent" hit "current").
 */
object RuleCategorizer {
    private class Rule(val category: Category, val base: Double, val merchants: List<String>, val keywords: List<String>)

    /** Per-category rules. Order = tie-break priority. Base confidences follow the old Dart rules. */
    private val rules: List<Rule> = listOf(
        Rule(Category.CHARITY, 0.85,
            merchants = listOf("edhi", "alkhidmat", "al-khidmat", "al khidmat", "saylani", "chhipa", "akhuwat", "jdc foundation",
                "sos village", "the citizens foundation", "tcf", "indus hospital donation", "shaukat khanum donation", "care foundation"),
            keywords = listOf("donation", "donated", "donate", "charity", "zakat", "zakaat", "sadqa", "sadqah", "sadaqah", "sadaqa", "sadka", "fitrana", "fitra",
                "khairat", "welfare", "relief fund", "ngo", "masjid", "mosque", "madrasa", "madrassa", "church", "temple", "gurdwara",
                "qurbani", "foundation", "welfare trust")),
        Rule(Category.TAXES, 0.85,
            merchants = listOf("fbr", "federal board of revenue", "e-challan", "echallan", "excise and taxation", "excise & taxation",
                "nadra", "passport office", "dgip", "safe city", "traffic police", "e-pay punjab", "epay punjab", "pitb"),
            keywords = listOf("income tax", "tax payment", "token tax", "property tax", "professional tax", "challan", "psid",
                "customs duty", "govt fee", "government fee", "passport fee", "tax")),
        Rule(Category.HOUSING, 0.85,
            merchants = listOf("ikea", "interwood", "habitt", "home depot", "zameen", "bahria town", "landlord", "housing society", "chenone home"),
            keywords = listOf("rent", "house rent", "kiraya", "kiraaya", "mortgage", "lease", "apartment", "property", "maintenance", "society",
                "repair", "furniture", "plumber", "electrician", "carpenter", "real estate", "housing", "accommodation", "hostel")),
        Rule(Category.COMMITTEE, 0.85,
            merchants = emptyList(),
            keywords = listOf("committee", "bc payment", "kameti", "kamiti", "bisi", "beesi")),
        Rule(Category.INVESTMENT, 0.82,
            merchants = listOf("psx", "cdc", "nccpl", "al meezan investment", "almeezan", "al meezan", "nbp funds", "nit ", "national investment trust",
                "ubl funds", "hbl asset", "hbl funds", "mcb funds", "mcb arif habib", "arif habib", "faysal funds", "js investments", "abl funds",
                "atlas asset", "akd securities", "topline securities", "national savings", "cdns", "sarmaya", "mahaana", "finqalab"),
            keywords = listOf("investment", "invest", "mutual fund", "unit purchase", "units purchased", "stocks", "shares", "equity", "brokerage",
                "securities", "sukuk", "t-bill", "treasury bill", "prize bond", "savings certificate", "pension fund", "naya pakistan certificate")),
        Rule(Category.BILLS, 0.80,
            merchants = listOf("k-electric", "k electric", "kelectric", "lesco", "iesco", "mepco", "pesco", "hesco", "fesco", "gepco", "qesco", "sepco",
                "sngpl", "ssgc", "sui gas", "sui northern", "sui southern", "wasa", "kwsc", "kwsb", "electric company", "water board"),
            keywords = listOf("electricity", "electric bill", "electric", "gas bill", "water bill", "utility bill", "utility", "bill payment",
                "bill paid", "consumer no", "consumer number", "dues")),
        Rule(Category.MOBILE, 0.80,
            merchants = listOf("ptcl", "nayatel", "stormfiber", "storm fiber", "wateen", "worldcall", "transworld", "cybernet", "fiberlink",
                "zong", "telenor", "ufone", "jazz", "warid", "mobilink", "onic", "etisalat", "mobily"),
            keywords = listOf("mobile load", "balance load", "load", "top up", "topup", "top-up", "recharge", "easyload", "easy load", "internet",
                "broadband", "wifi", "fiber", "data bundle", "internet package", "prepaid", "postpaid", "telecom")),
        Rule(Category.HEALTH, 0.80,
            merchants = listOf("aga khan", "akuh", "chughtai", "shaukat khanum", "dvago", "sehat kahani", "sehat", "dawaai", "marham", "oladoc",
                "excel labs", "islamabad diagnostic", "d-watson", "d watson", "servaid", "fazal din", "clinix", "bin hashim", "shifa international",
                "liaquat national", "indus hospital", "tabba heart", "south city hospital", "hamdard"),
            keywords = listOf("hospital", "clinic", "doctor", "medical", "medicine", "medicines", "pharmacy", "pharma", "chemist", "dental", "dentist",
                "optical", "optician", "lab", "laboratory", "diagnostic", "health", "healthcare", "fitness", "gym", "dawa", "dawai", "hakeem",
                "tabib", "physio", "surgery", "consultation")),
        Rule(Category.EDUCATION, 0.80,
            merchants = listOf("beaconhouse", "the city school", "city school", "lums", "iba", "nust", "fast nu", "comsats", "aga khan university",
                "roots millennium", "headstart", "coursera", "udemy", "edx", "skillshare", "pluralsight", "liberty books", "paramount books"),
            keywords = listOf("tuition", "school", "school fee", "university", "college", "course", "education", "learning", "books", "book shop",
                "bookstore", "stationery", "exam", "exam fee", "semester", "academic", "student", "academy", "institute", "admission",
                "fee voucher", "coaching")),
        Rule(Category.GROCERIES, 0.78,
            merchants = listOf("imtiaz", "carrefour", "metro cash", "metro", "al-fatah", "al fatah", "alfatah", "chase up", "chaseup", "naheed",
                "hyperstar", "springs", "esajee", "pandamart", "panda mart", "green valley", "jalal sons", "euro store", "utility store",
                "krave mart", "grocerapp", "whole foods", "sainsburys", "sainsbury's", "asda", "morrisons", "kroger", "tesco", "walmart", "aldi", "lidl", "costco", "spar"),
            keywords = listOf("grocery", "groceries", "supermarket", "super market", "superstore", "super store", "hypermarket", "mart", "kiryana",
                "karyana", "general store", "sabzi", "vegetable", "vegetables", "fruit", "dairy", "milk", "cash & carry", "cash and carry",
                "departmental store")),
        Rule(Category.PERSONAL_CARE, 0.78,
            merchants = listOf("toni&guy", "toni & guy", "nabila salon", "sabs salon", "depilex", "the body shop", "body shop", "sephora", "conatural",
                "luscious cosmetics"),
            keywords = listOf("salon", "saloon", "barber", "parlour", "parlor", "beauty", "spa", "cosmetics", "grooming", "haircut", "makeup",
                "skincare", "perfume", "fragrance")),
        Rule(Category.FOOD, 0.75,
            merchants = listOf("mcdonalds", "mcdonald's", "mcdonald", "kfc", "burger king", "subway", "dominos", "domino's", "domino", "pizza hut",
                "starbucks", "dunkin", "tim hortons", "costa coffee", "gloria jeans", "chipotle", "taco bell", "wendy's", "five guys",
                "shake shack", "foodpanda", "food panda", "talabat", "zomato", "swiggy", "careem now", "careem food", "uber eats", "hardees",
                "hardee's", "optp", "johnny rockets", "nandos", "nando's", "kolachi", "okra", "monal", "butt karahi", "student biryani",
                "al-rehman", "bundu khan", "bbq tonight", "lal qila", "usmania", "cheezious", "savour", "ox burger", "tea junction",
                "chaaye khana", "chaayé khana", "cafe aylanto", "jammin java", "broadway pizza", "california pizza", "pizza max", "ginsoy",
                "hot n spicy", "kababjees", "xanders", "second cup", "layers bakery", "bake parlor", "hico", "rahat bakers", "tooso",
                "english tea house", "espresso", "burning brownie"),
            keywords = listOf("restaurant", "cafe", "café", "coffee", "food", "foods", "meal", "lunch", "dinner", "breakfast", "pizza", "burger",
                "sushi", "takeaway", "biryani", "nihari", "karahi", "tikka", "kebab", "kabab", "naan", "roti", "paratha", "chai", "lassi",
                "halwa", "mithai", "sweets", "samosa", "chaat", "haleem", "sajji", "chapli", "dhaba", "tandoor", "nashta", "khana", "tawa",
                "handi", "kitchen", "eatery", "grill", "diner", "bistro", "canteen", "buffet", "bakery", "bakers", "confectionery",
                "ice cream", "juice", "shawarma", "falafel", "mandi", "broast", "bbq", "tea", "dosa", "thali", "pakwan")),
        Rule(Category.TRANSPORT, 0.75,
            merchants = listOf("uber", "lyft", "careem", "bolt", "grab", "bykea", "indrive", "indriver", "yango", "pathao", "swvl",
                "pso", "shell", "total parco", "totalenergies", "caltex", "attock petroleum", "hascol", "byco", "go petroleum", "admore",
                "petrol pump", "cng station", "fuel station", "filling station", "service station", "gas station", "fuel mart",
                "m-tag", "mtag", "metro bus", "orange line", "green line", "peshawar brt", "esso", "exxon", "chevron", "adnoc", "enoc",
                "emarat", "petro canada"),
            keywords = listOf("taxi", "cab", "ride", "rides", "fuel", "petrol", "diesel", "cng", "bus", "fare", "parking", "toll", "motorway",
                "rickshaw", "chingchi", "car wash", "tyre", "tyres", "mechanic", "e-tag", "transport", "commute")),
        Rule(Category.TRAVEL, 0.75,
            merchants = listOf("pia", "pakistan international airlines", "airblue", "serene air", "airsial", "fly jinnah", "emirates",
                "qatar airways", "etihad", "saudia", "turkish airlines", "flydubai", "booking.com", "airbnb", "agoda", "expedia",
                "sastaticket", "bookme", "daewoo express", "daewoo", "faisal movers", "pakistan railways", "pearl continental", "pc hotel",
                "serena", "marriott", "avari", "movenpick", "ramada"),
            keywords = listOf("flight", "flights", "airline", "airlines", "airport", "air ticket", "hotel booking", "travel", "trip", "vacation",
                "holiday", "tour", "tours", "umrah", "hajj", "visa", "resort", "guest house")),
        Rule(Category.ENTERTAINMENT, 0.75,
            merchants = listOf("netflix", "spotify", "youtube", "youtube premium", "hbo", "disney", "amazon prime", "prime video", "playstation",
                "xbox", "steam", "epic games", "nintendo", "cinepax", "cinestar", "nueplex", "atrium cinema", "super cinema",
                "universal cinema", "tapmad", "starzplay", "apple tv", "twitch", "pubg", "roblox", "joyland", "google play", "google"),
            keywords = listOf("movie", "movies", "cinema", "theater", "theatre", "game", "games", "gaming", "concert", "event", "subscription",
                "streaming", "music", "amusement", "theme park", "bowling", "arcade")),
        Rule(Category.SHOPPING, 0.72,
            merchants = listOf("daraz", "amazon", "ebay", "aliexpress", "temu", "shein", "noon", "namshi", "styli", "asos", "zara", "h&m", "nike",
                "adidas", "khaadi", "outfitters", "sapphire", "gul ahmed", "bata", "junaid jamshed", "limelight", "alkaram", "nishat linen",
                "bonanza", "breakout", "chen one", "beechtree", "sana safinaz", "maria b", "servis", "stylo", "ndure", "apple store",
                "samsung", "best buy", "mega.pk", "telemart", "ishopping", "priceoye", "shophive", "homeshopping", "dolmen mall",
                "packages mall", "emporium mall", "lucky one", "centaurus", "giga mall", "mall of lahore"),
            keywords = listOf("shopping", "pos purchase", "online purchase", "online order", "clothing", "apparel", "fashion", "shoes", "footwear",
                "electronics", "mall", "store", "retail", "bazaar", "traders", "trading", "wholesale", "boutique", "garments", "tailor",
                "jewellers", "jewelers", "plaza")),
        Rule(Category.FINANCIAL, 0.75,
            merchants = listOf("jubilee life", "state life", "efu", "adamjee insurance", "tpl insurance", "salaam takaful", "microfinance", "mobilink microfinance", "telenor microfinance"),
            keywords = listOf("insurance", "takaful", "premium", "policy", "loan", "installment", "instalment", "emi", "credit card payment",
                "card payment", "interest", "markup", "late payment", "service charges", "processing fee", "annual fee", "bank charges",
                "atm fee", "overdraft", "penalty")),
    )

    private fun wordRx(term: String) = Regex("""(?<![\p{L}0-9])${Regex.escape(term.trim())}(?![\p{L}0-9])""", RegexOption.IGNORE_CASE)

    /** (term, regex, rule) for every known merchant, longest first so "careem food" beats "careem" and "aga khan university" beats "aga khan". */
    private val merchantIndex: List<Triple<String, Regex, Rule>> =
        rules.flatMap { r -> r.merchants.map { Triple(it.trim(), wordRx(it), r) } }.sortedByDescending { it.first.length }

    private val keywordIndex: List<Triple<String, Regex, Rule>> =
        rules.flatMap { r -> r.keywords.map { Triple(it, wordRx(it), r) } }

    /** Lowest tier: the old app's category_mapping.json, minus words that appear in almost every bank SMS. */
    private val mappingStop = setOf("credit", "payment", "send", "receive", "deposit", "refund", "other", "uncategorized", "miscellaneous",
        "income", "transfer", "banking", "financial", "gas", "bill", "home", "store", "phone", "interest")
    private val mappingIndex: List<Triple<String, Regex, Category>> =
        CategoryKeywords.keywordCategory.filterKeys { it !in mappingStop }
            .filterValues { it != Category.TRANSFER && it != Category.INCOME && it != Category.OTHER }
            .map { (k, c) -> Triple(k, wordRx(k), c) }

    /** Categories that a Raast / IBFT payment is clearly *for* when the message says so ("rent", "zakat", "school fee"). */
    private val transferIntents = setOf(Category.HOUSING, Category.CHARITY, Category.EDUCATION, Category.COMMITTEE, Category.TAXES,
        Category.INVESTMENT, Category.BILLS)

    /** Wallet / rail names: never a merchant category on their own. */
    private val rails = Regex("""\b(raast|ibft|iban|funds transfer|fund transfer|sent to|interbank|inter bank|1link|transferred to|transfer to)""", RegexOption.IGNORE_CASE)
    private val atm = Regex("""(\batm\b|cash withdrawal|cash out)""", RegexOption.IGNORE_CASE)
    private val salary = Regex("""\b(salary|payroll|wages?)\b""", RegexOption.IGNORE_CASE)
    private val businessWords = setOf("store", "shop", "mart", "mall", "plaza", "centre", "center", "restaurant", "cafe", "hotel", "hospital",
        "clinic", "pharmacy", "school", "college", "university", "academy", "institute", "bank", "insurance", "limited", "ltd", "pvt", "inc",
        "corp", "services", "solutions", "enterprises", "industries", "group", "foundation", "trust", "association", "company", "co", "foods",
        "electronics", "motors", "traders", "agencies", "market", "bazaar", "warehouse", "depot", "outlet", "kitchen", "bakery", "grill",
        "dhaba", "darbar", "station", "pump", "filling", "online")

    private data class Hit(val rule: Rule, val term: String, val conf: Double, val inMerchant: Boolean)

    fun categorize(text: String, merchant: String?, merchantKey: String?, kind: TxnKind, memory: Map<String, Category>): CategoryDecision {
        if (merchantKey != null) memory[merchantKey]?.let {
            return CategoryDecision(it, 0.98, CategorySource.USER_MEMORY, "You categorized \"$merchantKey\" before")
        }
        when (kind) {
            TxnKind.FEE -> return CategoryDecision(Category.FINANCIAL, 0.92, CategorySource.RULE, "Fee / tax wording")
            TxnKind.REFUND, TxnKind.REVERSAL -> return CategoryDecision(Category.OTHER, 0.90, CategorySource.RULE, "Refund or reversal")
            else -> {}
        }
        if (kind == TxnKind.INCOME) {
            return if (salary.containsMatchIn(text)) CategoryDecision(Category.INCOME, 0.95, CategorySource.RULE, "Salary credit (\"${salary.find(text)!!.value}\")")
            else CategoryDecision(Category.INCOME, 0.90, CategorySource.RULE, "Money received")
        }
        if (atm.containsMatchIn(text)) return CategoryDecision(Category.OTHER, 0.90, CategorySource.RULE, "Cash withdrawal (ATM / agent cash out)")

        val m = normalize(merchant.orEmpty())
        val t = normalize(text)
        val hits = keywordHits(m, t)

        // 1. Known merchant in the extracted merchant name (0.95), else anywhere in the text (0.88). Keyword agreement adds a bonus.
        val inMerchant = if (m.isNotBlank()) firstBrand(m) else null
        val brand = inMerchant ?: firstBrand(t)
        if (brand != null) {
            val cat = brand.third.category
            // A clear intent keyword ("zakat", "donation") beats the place it was paid at ("Shaukat Khanum").
            val intent = hits.firstOrNull { it.rule.category in setOf(Category.CHARITY, Category.TAXES) && it.rule.category != cat }
            if (intent != null) return keywordDecision(intent, hits)
            val agree = hits.any { it.rule.category == cat && it.term != brand.first }
            val base = if (inMerchant != null) 0.95 else 0.88
            val conf = if (agree) minOf(base + 0.03, 0.97) else base
            val where = if (inMerchant != null) "Known merchant" else "Known merchant in text"
            return CategoryDecision(cat, conf, CategorySource.RULE, "$where \"${brand.first}\"" + if (agree) " (confirmed by keyword)" else "")
        }

        // 2. Raast / IBFT / wallet transfer: stays a transfer unless the payee or a clear purpose says otherwise.
        if (kind == TxnKind.TRANSFER_OUT && rails.containsMatchIn(text)) {
            val purpose = hits.firstOrNull { it.inMerchant || it.rule.category in transferIntents }
            if (purpose != null) return keywordDecision(purpose, hits)
            return CategoryDecision(Category.TRANSFER, 0.90, CategorySource.RULE, "Raast / IBFT / wallet transfer (\"${rails.find(text)!!.value}\")")
        }

        // 3. Keywords (old per-category base confidence, +0.05 when in the merchant name, +0.03 when two keywords agree).
        hits.firstOrNull()?.let { return keywordDecision(it, hits) }

        // 4. Transfer heuristic.
        if (kind == TxnKind.TRANSFER_OUT || kind == TxnKind.TRANSFER_IN) return CategoryDecision(Category.TRANSFER, 0.80, CategorySource.RULE, "Transfer to a person or wallet")

        // 5. Old category_mapping.json keywords.
        val hay = "$m $t"
        mappingIndex.firstOrNull { it.second.containsMatchIn(hay) }?.let {
            return CategoryDecision(it.third, 0.75, CategorySource.RULE, "Keyword \"${it.first}\" (FinSight category map)")
        }

        // 6. Person-name heuristic from the old app: "Ali Raza" with no business words → probably a P2P payment.
        if (looksLikePerson(merchant) && rails.containsMatchIn(text)) return CategoryDecision(Category.TRANSFER, 0.85, CategorySource.RULE, "Transfer wording (\"${rails.find(text)!!.value}\") to a person")
        if (looksLikePerson(merchant)) return CategoryDecision(Category.TRANSFER, 0.55, CategorySource.RULE, "Looks like a person's name — probably a transfer")
        return CategoryDecision(Category.OTHER, 0.50, CategorySource.NONE, "No rule matched — needs your review")
    }

    /** The brand that appears first wins ("CINEPAX PACKAGES MALL" is Cinepax, not the mall); same start → longest. */
    private fun firstBrand(s: String): Triple<String, Regex, Rule>? =
        merchantIndex.mapNotNull { e -> e.second.find(s)?.let { it.range.first to e } }
            .minWithOrNull(compareBy<Pair<Int, Triple<String, Regex, Rule>>> { it.first }.thenByDescending { it.second.first.length })?.second

    private fun normalize(s: String) = s.lowercase().replace("jazz cash", "jazzcash").replace("easy paisa", "easypaisa")

    /** All keyword hits, best first (confidence, then rule priority). */
    private fun keywordHits(m: String, t: String): List<Hit> {
        val out = mutableListOf<Hit>()
        for ((term, rx, rule) in keywordIndex) {
            val inM = m.isNotBlank() && rx.containsMatchIn(m)
            if (inM || rx.containsMatchIn(t)) out += Hit(rule, term, rule.base + if (inM) 0.05 else 0.0, inM)
        }
        val order = rules.withIndex().associate { it.value to it.index }
        return out.sortedWith(compareByDescending<Hit> { it.conf }.thenBy { order[it.rule] })
    }

    private fun keywordDecision(best: Hit, hits: List<Hit>): CategoryDecision {
        val cat = best.rule.category
        val agree = hits.count { it.rule.category == cat } >= 2
        val rival = hits.firstOrNull { it.rule.category != cat }
        var conf = best.conf + if (agree) 0.03 else 0.0
        if (rival != null && rival.conf >= conf - 0.02) conf -= 0.03 // close call between two categories → keep it yellow
        conf = conf.coerceIn(0.75, 0.88)
        val note = buildString {
            append("Keyword \"${best.term}\"")
            if (best.inMerchant) append(" in merchant name")
            if (agree) append(", confirmed by \"${hits.first { it.rule.category == cat && it !== best }.term}\"")
            if (rival != null) append(" (also looks like ${rival.rule.category.label})")
        }
        return CategoryDecision(cat, conf, CategorySource.RULE, note)
    }

    private fun looksLikePerson(merchant: String?): Boolean {
        val words = merchant?.trim()?.lowercase()?.split(Regex("""\s+"""))?.filter { it.isNotBlank() } ?: return false
        if (words.size !in 2..4) return false
        if (!words.all { Regex("""^[a-z][a-z.]*$""").matches(it) }) return false
        return words.none { it in businessWords }
    }
}
