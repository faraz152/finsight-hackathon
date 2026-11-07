package com.finsight

import com.finsight.data.Repository
import com.finsight.domain.Category
import com.finsight.domain.Channel
import com.finsight.domain.RawInput
import com.finsight.domain.TxnKind
import com.finsight.domain.TxnType
import com.finsight.pipeline.BankProfiles
import com.finsight.pipeline.BankType
import com.finsight.pipeline.PipelineResult
import com.finsight.pipeline.RegexExtractor
import com.finsight.pipeline.RuleCategorizer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Category accuracy of the ported old-FinSight rules on realistic Pakistani messages. */
class CategorizationTest {
    private val cases: List<Pair<String, Category>> = listOf(
        "Your HBL Debit Card ending 4432 was used for PKR 2,890.00 at KFC GULBERG LAHORE on 03/11/2026." to Category.FOOD,
        "Rs 1,450.00 paid to Foodpanda via JazzCash QR. TID: 041122334455. Balance: Rs 3,210.00" to Category.FOOD,
        "PKR 650.00 spent at CAREEM RIDE using card **7710. Avl Bal PKR 12,300" to Category.TRANSPORT,
        "UBL Credit Card *-7182 is charged on 09/02/26 19:45 for PKR 500.00 at SANA SERVICE STATION-TOTA. Avail. Limit: PKR 113048.49." to Category.TRANSPORT,
        "Rs 3,000 paid at PSO DHA PHASE 5 using Meezan Debit Card **1234 on 02-11-2026" to Category.TRANSPORT,
        "You have paid Rs 8,450.00 as the K-Electric bill for consumer no 0400012345. TID: 039988776655." to Category.BILLS,
        "Your SNGPL bill of Rs. 2,340 has been paid successfully from A/C **5678. Ref 77889900" to Category.BILLS,
        "PKR 4,120 debited from your account for LESCO electricity bill payment. Ref: LS998877" to Category.BILLS,
        "Payment of Rs 1,999 to PTCL for bill payment successful from your Easypaisa account. TID 0987654321" to Category.MOBILE,
        "You have purchased Mobile Load of Rs 100.00 for JAZZ PREPAID mobile number: 03001234567. Fee: Rs 0.00, Deduction: Rs 100.00, Balance: Rs 14.00. TID: 035919397758." to Category.MOBILE,
        "Nayatel internet bill PKR 2,999 paid via Alfa. Ref 11223344" to Category.MOBILE,
        "PKR 7,850.00 spent at DARAZ.PK online using card **4421 on 01/11/2026" to Category.SHOPPING,
        "Rs 5,200.00 spent at IMTIAZ SUPER MARKET using card **7710." to Category.GROCERIES,
        "MCB Card 1234 used for Rs. 2,500 at AL-FATAH on 20/01/2026. Available: Rs. 15,000." to Category.GROCERIES,
        "Rs. 3,000 paid to Utility Store using NayaPay. Updated balance: Rs. 12,000." to Category.GROCERIES,
        "PKR 1,860 spent at DVAGO PHARMACY using card **7710 on 02/11/2026" to Category.HEALTH,
        "Rs 2,400 paid at Fazal Din Pharma Plus using HBL Debit Card 4432" to Category.HEALTH,
        "Rs 4,500 debited for Chughtai Lab test using card **7710" to Category.HEALTH,
        "Rs 18,500 sent via Raast to BEACONHOUSE SCHOOL fee voucher 2211 from A/C **1234. Ref RS112233" to Category.EDUCATION,
        "Rs 45,000 sent via Raast to Malik Naveed for house rent November from A/C **1234. Ref RS445566" to Category.HOUSING,
        "Rs 5,000 sent to Edhi Foundation JazzCash A/C: 03001112223 zakat. TID: 033050058719." to Category.CHARITY,
        "Rs 2,000 donation paid to Shaukat Khanum Memorial Trust via Easypaisa. TID 0099887766" to Category.CHARITY,
        "PKR 25,000 debited for purchase of units in Al Meezan Mutual Fund. Ref AMI12345" to Category.INVESTMENT,
        "Rs 10,000 transferred to AKD Securities for PSX investment from A/C **5678" to Category.INVESTMENT,
        "Your account **5678 has been credited with PKR 185,000.00 SALARY ACME TECH PVT LTD on 01/11/2026" to Category.INCOME,
        "Rs 12,000 sent via Raast to ALI RAZA from A/C **1234. Ref RS998877" to Category.TRANSFER,
        "Rs 1,020.00 sent to SHEHRYAR JazzCash A/C: 03472015623. Fee: Rs 0.00. Deduction: Rs 1,020.00, Balance: Rs 1,014.00. TID: 033050058719." to Category.TRANSFER,
        "PKR 1,599 charged at NETFLIX.COM on your card **4421" to Category.ENTERTAINMENT,
        "PKR 2,200 spent at CINEPAX PACKAGES MALL using card **4421" to Category.ENTERTAINMENT,
        "Rs 3,500 paid at TONI&GUY SALON using card **7710" to Category.PERSONAL_CARE,
        "PKR 38,400 spent at PIA online booking using card **4421" to Category.TRAVEL,
        "Rs 4,800 paid for token tax e-challan PSID 123456789 via HBL Mobile" to Category.TAXES,
        "Rs 10,000 sent to SAJID committee via Raast from A/C **1234" to Category.COMMITTEE,
        "Rs 1,200.00 spent at Gulshan Biryani Point using card **7710." to Category.FOOD,
        "Rs 3,000 paid at STUDENT BIRYANI using card **7710" to Category.FOOD,
    )

    @Test
    fun categoryAccuracy() {
        val repo = Repository()
        var ok = 0
        val misses = mutableListOf<String>()
        cases.forEachIndexed { i, (msg, want) ->
            val r = repo.pipeline.process(RawInput(Channel.DEMO, "test", null, msg, 1_700_000_000_000L + i * 3_600_000L))
            val t = (r as? PipelineResult.Accepted)?.txn
            if (t?.category == want) ok++ else misses += "$want <- ${t?.category} [${t?.merchant}] ${t?.explanation?.firstOrNull { it.startsWith("Category") }} :: $msg"
        }
        val pct = Math.round(ok * 1000.0 / cases.size) / 10.0
        println("CATEGORY accuracy ${pct}% ($ok/${cases.size})")
        misses.forEach { println("CMISS $it") }
        assertTrue(ok * 100 / cases.size >= 90, "category accuracy too low: $ok/${cases.size}")
    }

    @Test
    fun quickAddAndDirectRules() {
        val m = emptyMap<String, Category>()
        assertEquals(Category.FOOD, RuleCategorizer.categorize("chai 150", "chai", "chai", TxnKind.EXPENSE, m).category)
        assertEquals(Category.HOUSING, RuleCategorizer.categorize("rent 45000", "rent", "rent", TxnKind.EXPENSE, m).category)
        // Whole-word matching: "current" must not look like "rent".
        assertEquals(Category.OTHER, RuleCategorizer.categorize("current account xyz", "Qwerty", "qwerty", TxnKind.EXPENSE, m).category)
        // Memory still wins.
        val d = RuleCategorizer.categorize("PKR 500 at KFC", "KFC", "kfc", TxnKind.EXPENSE, mapOf("kfc" to Category.ENTERTAINMENT))
        assertEquals(Category.ENTERTAINMENT, d.category); assertEquals(0.98, d.confidence)
        // Exact merchant 0.95; "Jazz Cash" wallet is not the Jazz telco.
        assertEquals(0.95, RuleCategorizer.categorize("PKR 500 at KFC", "KFC", "kfc", TxnKind.EXPENSE, m).confidence, 0.021)
        assertEquals(Category.TRANSFER, RuleCategorizer.categorize("Rs 500 sent to Ahmed via Jazz Cash", "Ahmed", "ahmed", TxnKind.TRANSFER_OUT, m).category)
        // Person-name heuristic from the old app.
        val p = RuleCategorizer.categorize("Rs 900 paid to Ali Raza", "Ali Raza", "ali raza", TxnKind.EXPENSE, m)
        assertEquals(Category.TRANSFER, p.category); assertEquals(0.55, p.confidence)
    }

    @Test
    fun bankProfiles() {
        assertEquals("jazzcash", BankProfiles.identify("8558", "", "Rs 10,000.00 sent to EasyPaisa Account: 0343")?.id)
        assertEquals("easypaisa", BankProfiles.identify(null, "pk.com.telenor.phoenix", "Rs 500 received")?.id)
        assertEquals(BankType.WALLET, BankProfiles.identify("3737", "", "x")?.type)
        assertEquals("hbl", BankProfiles.identify(null, "HBL", "anything")?.id)
        assertEquals("meezan", BankProfiles.identify(null, "invo8.meezan.mb", "debited")?.id)
        assertEquals("meezan", BankProfiles.identify("MEEZANBANK", "", "debited")?.id)
        val card = BankProfiles.identify(null, "com.android.messaging", "UBL Credit Card *-7182 is charged on 09/02/26 for PKR 500.00 at X. Avail. Limit: PKR 1.")
        assertEquals("ubl", card?.id); assertEquals(BankType.CARD, card?.type)
        // Counterparty name in text is not the sender: JazzCash message paying to an Easypaisa account, no sender info.
        assertEquals("jazzcash", BankProfiles.identify(null, "", "Rs 100 sent to EasyPaisa Account 0343 from your JazzCash account")?.id)
        assertEquals("konnect", BankProfiles.identify(null, "", "HBL Konnect: Rs 500 received")?.id)
        assertEquals("alfalah", BankProfiles.identify(null, "", "Bank Alfalah: PKR 2,000 debited")?.id)
        assertNull(BankProfiles.identify(null, "", "hello world"))
    }

    /** The old app's sample SMS from pakistan_banks.json: amount + direction must parse. */
    @Test
    fun oldBankSamplesParse() {
        val samples = listOf(
            "Rs 50.00 received from naveed online traders, JazzCash Mobile Account: 0335****448. Fee: Rs 0.00. Balance: Rs 50.00. TID: 029462847601." to (5000L to TxnType.CREDIT),
            "Rs 10,000.00 sent to EasyPaisa Account: 03431237891. Fee: Rs 0.00, Deduction: Rs 10,000.00, Balance: Rs 100.00. TID: 030035934987." to (1000000L to TxnType.DEBIT),
            "TID: 029462887962. Cash In of Rs 50.00 by Business Center successful. Fee:Rs 0.00, Pay: Rs 50.00" to (5000L to TxnType.CREDIT),
            "UBL MasterCard *-5859 is charged on 11/02/26 at 11:29 PM for PKR 1,400.00 at Google Rob." to (140000L to TxnType.DEBIT),
            "credited with PKR 35 on 08/02/26. For info: 111825888." to (3500L to TxnType.CREDIT),
            "You have received Rs. 500 from 03451234567. Your new balance is Rs. 1,500. TID: EP1234567890" to (50000L to TxnType.CREDIT),
            "HBL Account XXXX1234 debited with Rs. 5,000 on 15/01/2026. Available Balance: Rs. 25,000. Ref: HBL123456" to (500000L to TxnType.DEBIT),
            "MCB Card 1234 used for Rs. 2,500 at SUPERSTORE on 20/01/2026. Available: Rs. 15,000. Ref: 987654321" to (250000L to TxnType.DEBIT),
            "Your Meezan Account XXXX5678 has been debited with Rs. 10,000 on 25/01/2026. Balance: Rs. 50,000. Ref: MB123456789" to (1000000L to TxnType.DEBIT),
            "You spent Rs. 1,200 at Coffee Shop with your SadaPay card. Current balance: Rs. 8,500." to (120000L to TxnType.DEBIT),
            "You received Rs. 5,000 from Ahmad Ali. Current balance: Rs. 13,500." to (500000L to TxnType.CREDIT),
            "Rs. 1,500 sent to 03109876543 using NayaPay. Updated balance: Rs. 15,500." to (150000L to TxnType.DEBIT),
        )
        val bad = samples.filter { (msg, exp) -> RegexExtractor.extract(msg).let { it.amountMinor != exp.first || it.type != exp.second } }
            .map { (msg, exp) -> "$exp <- ${RegexExtractor.extract(msg).let { it.amountMinor to it.type }} :: $msg" }
        println("OLD SAMPLES ${samples.size - bad.size}/${samples.size}")
        bad.forEach { println("SMISS $it") }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }

    /** Fixtures have no labelled category; report the distribution so regressions are visible. */
    @Test
    fun templateCategoryDistribution() {
        for (path in listOf("src/androidMain/assets/sms_test_templates.json", "src/androidMain/assets/pk_extra_templates.json")) {
            val text = File(path).readText()
            val labelled = Regex(""""expected":\s*\{[^}]*"category"""").findAll(text).count()
            val msgs = Regex(""""message":\s*"((?:[^"\\]|\\.)*)"""").findAll(text).map { it.groupValues[1].replace("\\\"", "\"").replace("\\n", "\n") }.toList()
            val repo = Repository()
            val dist = msgs.mapIndexedNotNull { i, msg ->
                (repo.pipeline.process(RawInput(Channel.DEMO, "test", null, msg, 1_700_000_000_000L + i * 3_600_000L)) as? PipelineResult.Accepted)?.txn?.category
            }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
            msgs.forEachIndexed { i, msg ->
                val t = (repo.pipeline.process(RawInput(Channel.DEMO, "test", null, msg, 1_800_000_000_000L + i * 3_600_000L)) as? PipelineResult.Accepted)?.txn
                if (t != null && (t.category == Category.OTHER || t.needsReview)) println("TCAT ${t.category} ${(t.confidence * 100).toInt()}% [${t.merchant}] ${t.kind} :: ${msg.take(110)}")
            }
            println("TEMPLATE-CATEGORIES ${File(path).name} labelled=$labelled n=${msgs.size} $dist")
        }
    }
}
