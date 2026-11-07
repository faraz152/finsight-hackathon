package com.finsight

import com.finsight.data.Repository
import com.finsight.domain.Channel
import com.finsight.domain.RawInput
import com.finsight.domain.TxnType
import com.finsight.pipeline.PipelineResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Accuracy of the rules-only pipeline on the labelled fixtures (plan 6.8 evaluation). */
class PipelineAccuracyTest {
    private data class Sample(val id: String, val country: String, val msg: String, val amount: Double?, val type: String?, val merchant: String?)

    data class Score(val n: Int, val amountOk: Int, val typeOk: Int, val merchantN: Int, val merchantOk: Int, val review: Int, val accepted: Int, val misses: List<String>) {
        fun pct(a: Int, b: Int) = if (b == 0) 0 else Math.round(a * 1000.0 / b) / 10.0
        override fun toString() = "n=$n accepted=$accepted amount=${pct(amountOk, n)}% ($amountOk/$n) direction=${pct(typeOk, n)}% ($typeOk/$n) " +
            "merchant=${pct(merchantOk, merchantN)}% ($merchantOk/$merchantN) review=${pct(review, accepted)}% ($review/$accepted)"
    }

    private fun load(path: String): List<Sample> {
        val text = File(path).readText()
        val block = Regex("""\{\s*"id":\s*"([^"]+)",\s*"country":\s*"([^"]+)".*?"message":\s*"((?:[^"\\]|\\.)*)".*?"expected":\s*\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
        return block.findAll(text).map { m ->
            val exp = m.groupValues[4]
            Sample(
                m.groupValues[1], m.groupValues[2], m.groupValues[3].replace("\\\"", "\"").replace("\\n", "\n"),
                Regex(""""amount":\s*([0-9.]+)""").find(exp)?.groupValues?.get(1)?.toDouble(),
                Regex(""""transactionType":\s*"(\w+)"""").find(exp)?.groupValues?.get(1),
                Regex(""""merchantName":\s*"([^"]*)"""").find(exp)?.groupValues?.get(1),
            )
        }.toList()
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("""[^a-z0-9]"""), "")

    private fun evaluate(path: String): Score {
        val samples = load(path)
        val repo = Repository()
        var amountOk = 0; var typeOk = 0; var accepted = 0; var merchantN = 0; var merchantOk = 0; var review = 0
        val misses = mutableListOf<String>()
        samples.forEachIndexed { i, s ->
            val r = repo.pipeline.process(RawInput(Channel.DEMO, "test", null, s.msg, 1_700_000_000_000L + i * 3_600_000L))
            if (!s.merchant.isNullOrBlank()) merchantN++
            if (r is PipelineResult.Accepted) {
                accepted++
                val t = r.txn
                if (t.needsReview) review++
                val aOk = s.amount != null && kotlin.math.abs(t.amount.minor - Math.round(s.amount * 100)) <= 1
                if (aOk) amountOk++ else misses += "${s.id}: amount ${t.amount.minor / 100.0} vs ${s.amount}"
                val expType = if (s.type == "credit") TxnType.CREDIT else TxnType.DEBIT
                if (t.type == expType) typeOk++ else misses += "${s.id}: type ${t.type} vs ${s.type}"
                if (!s.merchant.isNullOrBlank()) {
                    val got = t.merchant?.let(::norm).orEmpty()
                    val want = norm(s.merchant)
                    if (got.isNotEmpty() && (got.contains(want) || want.contains(got))) merchantOk++ else misses += "${s.id}: merchant ${t.merchant} vs ${s.merchant}"
                }
            } else misses += "${s.id}: $r"
        }
        return Score(samples.size, amountOk, typeOk, merchantN, merchantOk, review, accepted, misses)
    }

    @Test
    fun accuracy() {
        val s = evaluate("src/androidMain/assets/sms_test_templates.json")
        println("TEMPLATES sms_test_templates.json $s")
        s.misses.forEach { println("MISS $it") }
        assertTrue(s.n >= 50, "parsed ${s.n} templates")
        assertTrue(s.amountOk * 100 / s.n >= 95, "amount accuracy too low: $s")
        assertTrue(s.typeOk * 100 / s.n >= 95, "direction accuracy too low: $s")
    }

    @Test
    fun pkExtraAccuracy() {
        val s = evaluate("src/androidMain/assets/pk_extra_templates.json")
        println("ACCURACY pk_extra_templates.json $s")
        s.misses.forEach { println("MISS $it") }
        assertTrue(s.n >= 30, "parsed ${s.n} templates")
        assertTrue(s.amountOk * 100 / s.n >= 95, "PK amount accuracy too low: $s")
        assertTrue(s.typeOk * 100 / s.n >= 95, "PK direction accuracy too low: $s")
    }
}
