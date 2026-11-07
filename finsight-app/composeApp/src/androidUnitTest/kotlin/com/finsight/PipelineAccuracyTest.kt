package com.finsight

import com.finsight.data.Repository
import com.finsight.domain.Channel
import com.finsight.domain.RawInput
import com.finsight.domain.TxnType
import com.finsight.pipeline.PipelineResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Accuracy of the rules-only pipeline on the 58 labelled templates (plan 6.8 evaluation). */
class PipelineAccuracyTest {
    private data class Sample(val id: String, val country: String, val msg: String, val amount: Double?, val type: String?)

    private fun load(): List<Sample> {
        val text = File("src/androidMain/assets/sms_test_templates.json").readText()
        val block = Regex("""\{\s*"id":\s*"([^"]+)",\s*"country":\s*"([^"]+)".*?"message":\s*"((?:[^"\\]|\\.)*)".*?"expected":\s*\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
        return block.findAll(text).map { m ->
            val exp = m.groupValues[4]
            Sample(
                m.groupValues[1], m.groupValues[2], m.groupValues[3].replace("\\\"", "\"").replace("\\n", "\n"),
                Regex(""""amount":\s*([0-9.]+)""").find(exp)?.groupValues?.get(1)?.toDouble(),
                Regex(""""transactionType":\s*"(\w+)"""").find(exp)?.groupValues?.get(1),
            )
        }.toList()
    }

    @Test
    fun accuracy() {
        val samples = load()
        assertTrue(samples.size >= 50, "parsed ${samples.size} templates")
        val repo = Repository()
        var amountOk = 0; var typeOk = 0; var accepted = 0; var pkTotal = 0; var pkAmount = 0
        val misses = mutableListOf<String>()
        samples.forEachIndexed { i, s ->
            val r = repo.pipeline.process(RawInput(Channel.DEMO, "test", null, s.msg, 1_700_000_000_000L + i * 3_600_000L))
            if (s.country == "PK") pkTotal++
            if (r is PipelineResult.Accepted) {
                accepted++
                val t = r.txn
                val aOk = s.amount != null && kotlin.math.abs(t.amount.minor - Math.round(s.amount * 100)) <= 1
                if (aOk) { amountOk++; if (s.country == "PK") pkAmount++ } else misses += "${s.id}: amount ${t.amount.minor / 100.0} vs ${s.amount}"
                val expType = if (s.type == "credit") TxnType.CREDIT else TxnType.DEBIT
                if (t.type == expType) typeOk++ else misses += "${s.id}: type ${t.type} vs ${s.type}"
            } else misses += "${s.id}: ${r}"
        }
        println("TEMPLATES=${samples.size} ACCEPTED=$accepted AMOUNT_OK=$amountOk TYPE_OK=$typeOk PK_AMOUNT=$pkAmount/$pkTotal")
        misses.forEach { println("MISS $it") }
        assertTrue(amountOk * 100 / samples.size >= 70, "amount accuracy too low: $amountOk/${samples.size}")
    }
}
