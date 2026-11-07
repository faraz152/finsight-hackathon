package com.finsight.data

import com.finsight.domain.Category
import com.finsight.domain.TxnKind
import kotlin.random.Random

/** What the pipeline should do with a Test Lab sample. */
enum class ExpectedOutcome { ACCEPTED, DROPPED, SCAM, DUPLICATE }

/**
 * One bank-style alert for the Test Lab (in-app real notifications, `scripts/send_test_sms.sh`, TestSamplesTest).
 * [template] holds `{TID}`, filled with fresh random digits at send time so repeated sends are not merged.
 * [copies] > 1 posts the SAME text (same TID) that many times: integrity must merge it into one record.
 */
data class TestSample(
    val id: String,
    val name: String,
    val sender: String,
    val template: String,
    val outcome: ExpectedOutcome,
    val category: Category? = null,
    val kind: TxnKind? = null,
    val amountMinor: Long? = null,
    val copies: Int = 1,
) {
    fun render(tid: String): String = template.replace(TestSamples.TID, tid)
}

object TestSamples {
    const val TID = "{TID}"

    /** 12 random digits: looks like a JazzCash / bank TID and is a unique fingerprint (ref:...). */
    fun randomTid(random: Random = Random.Default): String = (1..12).joinToString("") { random.nextInt(10).toString() }

    fun byId(id: String): TestSample? = all.firstOrNull { it.id == id }

    val all: List<TestSample> = listOf(
        TestSample("jazzcash_send", "JazzCash send", "8558",
            "Rs 1,250.00 sent to Ali Raza JazzCash 0300****567 from your JazzCash account. Fee: Rs 0.00. Balance: Rs 8,750.00. TID: {TID}.",
            ExpectedOutcome.ACCEPTED, Category.TRANSFER, TxnKind.TRANSFER_OUT, 125_000),
        TestSample("jazzcash_receive", "JazzCash receive", "8558",
            "You have received Rs 3,000.00 from Hamza Khan 0321****890 in your JazzCash account. TID: {TID}. Balance: Rs 11,750.00",
            ExpectedOutcome.ACCEPTED, Category.INCOME, TxnKind.INCOME, 300_000),
        TestSample("easypaisa_bykea", "Easypaisa Bykea", "Easypaisa",
            "Rs 850.00 paid to Bykea via Easypaisa. Trx ID {TID}. Balance Rs 4,150.00",
            ExpectedOutcome.ACCEPTED, Category.TRANSPORT, TxnKind.EXPENSE, 85_000),
        TestSample("raast_in", "Raast in", "HBL",
            "Rs 25,000.00 received via Raast from SARA AHMED into your A/C **4821. Ref: {TID}",
            ExpectedOutcome.ACCEPTED, Category.INCOME, TxnKind.INCOME, 2_500_000),
        TestSample("raast_out", "Raast out", "HBL",
            "Rs 5,000.00 sent to USMAN TARIQ via Raast from A/C **4821. Ref: {TID}",
            ExpectedOutcome.ACCEPTED, Category.TRANSFER, TxnKind.TRANSFER_OUT, 500_000),
        TestSample("hbl_kfc", "HBL card KFC", "HBL",
            "Rs 2,890.00 spent at KFC Gulberg using HBL Debit Card **7710. Ref: {TID}. Avail Bal: Rs 65,200.00",
            ExpectedOutcome.ACCEPTED, Category.FOOD, TxnKind.EXPENSE, 289_000),
        TestSample("hbl_foodpanda", "HBL card Foodpanda", "HBL",
            "Rs 1,850.00 spent at Foodpanda using card **7710. Ref: {TID}. Avail Bal: Rs 71,640.00",
            ExpectedOutcome.ACCEPTED, Category.FOOD, TxnKind.EXPENSE, 185_000),
        TestSample("hbl_daraz", "HBL card Daraz", "HBL",
            "Rs 4,499.00 spent at DARAZ PK online using card **7710. Ref: {TID}. Avail Bal: Rs 67,141.00",
            ExpectedOutcome.ACCEPTED, Category.SHOPPING, TxnKind.EXPENSE, 449_900),
        TestSample("meezan_salary", "Meezan salary", "Meezan",
            "Salary of Rs 185,000.00 credited to your A/C **4821 by ACME TECH PVT LTD. Ref: {TID}. Avail Bal: Rs 212,450.00",
            ExpectedOutcome.ACCEPTED, Category.INCOME, TxnKind.INCOME, 18_500_000),
        TestSample("ubl_atm", "UBL ATM cash", "UBL",
            "Cash withdrawal of Rs 10,000.00 from UBL ATM Clifton using card **3390. Ref: {TID}. Avail Bal: Rs 40,120.00",
            ExpectedOutcome.ACCEPTED, Category.OTHER, TxnKind.EXPENSE, 1_000_000),
        TestSample("kelectric_bill", "K-Electric bill", "HBL",
            "Rs 8,450.00 paid to K-Electric bill via HBL Mobile. Consumer no 0400012345. Ref: {TID}",
            ExpectedOutcome.ACCEPTED, Category.BILLS, TxnKind.EXPENSE, 845_000),
        TestSample("sngpl_bill", "SNGPL gas bill", "8558",
            "Rs 2,340.00 paid for SNGPL Bill via JazzCash. TID: {TID}. Balance: Rs 6,410.00",
            ExpectedOutcome.ACCEPTED, Category.BILLS, TxnKind.EXPENSE, 234_000),
        TestSample("ptcl_bill", "PTCL bill", "Easypaisa",
            "Rs 3,199.00 paid for PTCL Bill via Easypaisa. Trx ID {TID}. Balance Rs 951.00",
            ExpectedOutcome.ACCEPTED, Category.MOBILE, TxnKind.EXPENSE, 319_900),
        TestSample("jazz_topup", "Jazz top-up", "Jazz",
            "Mobile load of Rs 500.00 for JAZZ PREPAID 0301****222 successful via JazzCash. TID: {TID}",
            ExpectedOutcome.ACCEPTED, Category.MOBILE, TxnKind.EXPENSE, 50_000),
        TestSample("daraz_refund", "Daraz refund", "HBL",
            "Rs 1,200.00 refund received from Daraz to card **7710. Ref: {TID}",
            ExpectedOutcome.ACCEPTED, Category.OTHER, TxnKind.REFUND, 120_000),
        TestSample("cheezious_reversal", "Reversal", "HBL",
            "Your payment of Rs 2,500.00 at Cheezious has been reversed to card **7710. Ref: {TID}",
            ExpectedOutcome.ACCEPTED, Category.OTHER, TxnKind.REVERSAL, 250_000),
        TestSample("bank_fee", "Bank fee", "HBL",
            "Rs 450.00 debited from A/C **4821 for SMS charges and excise duty. Ref: {TID}",
            ExpectedOutcome.ACCEPTED, Category.FINANCIAL, TxnKind.FEE, 45_000),
        TestSample("duplicate_foodpanda", "Duplicate pair", "HBL",
            "Rs 3,450.00 spent at Foodpanda using card **7710. Ref: {TID}. Avail Bal: Rs 61,750.00",
            ExpectedOutcome.DUPLICATE, Category.FOOD, TxnKind.EXPENSE, 345_000, copies = 2),
        TestSample("otp", "OTP (drop)", "HBL",
            "Your OTP for transaction of Rs 5,000.00 is 482913. Do not share it with anyone.",
            ExpectedOutcome.DROPPED),
        TestSample("phishing", "Phishing (flag)", "8558",
            "Dear customer your JazzCash account will be blocked. Click link bit.ly/jc-kyc to update your KYC",
            ExpectedOutcome.SCAM),
    )
}
