package com.finsight

import com.finsight.data.ExpectedOutcome
import com.finsight.data.Repository
import com.finsight.data.TestSamples
import com.finsight.domain.Channel
import com.finsight.domain.RawInput
import com.finsight.pipeline.PipelineResult
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every Test Lab sample (in-app notifications + scripts/send_test_sms.sh) gives the outcome it is labelled with. */
class TestSamplesTest {
    /**
     * Sample ids whose expected category/kind fails only because of the current categorization rules (pipeline is
     * owned elsewhere). Listed here instead of editing pipeline; they are reported, not failed.
     */
    private val knownGaps = emptySet<String>()

    private fun ingest(repo: Repository, sender: String, text: String, at: Long) =
        repo.ingest(RawInput(Channel.NOTIFICATION, "com.finsight.app", sender, text, at))

    @Test
    fun catalogueIsComplete() {
        val all = TestSamples.all
        assertTrue(all.size >= 15, "need at least 15 samples, have ${all.size}")
        assertEquals(all.size, all.map { it.id }.toSet().size, "sample ids must be unique")
        ExpectedOutcome.entries.forEach { o -> assertTrue(all.any { it.outcome == o }, "no sample for $o") }
        all.filter { it.outcome == ExpectedOutcome.ACCEPTED || it.outcome == ExpectedOutcome.DUPLICATE }.forEach {
            assertTrue(TestSamples.TID in it.template, "${it.id}: needs a {TID} so repeated sends are not merged")
        }
        assertTrue(TestSamples.randomTid(Random(1)).matches(Regex("""\d{12}""")))
    }

    @Test
    fun everySampleGivesItsExpectedOutcome() {
        val failures = mutableListOf<String>()
        val gaps = mutableListOf<String>()
        TestSamples.all.forEachIndexed { i, s ->
            val repo = Repository()
            val tid = TestSamples.randomTid(Random(i))
            val text = s.render(tid)
            val results = (1..s.copies).map { ingest(repo, s.sender, text, 1_700_000_000_000L + it * 1_000L) }
            val r = results.first()
            when (s.outcome) {
                ExpectedOutcome.DROPPED -> if (r !is PipelineResult.Dropped) failures += "${s.id}: expected dropped, got $r"
                ExpectedOutcome.SCAM -> if (r !is PipelineResult.ScamWarning || repo.scams.value.isEmpty()) failures += "${s.id}: expected scam flag, got $r"
                ExpectedOutcome.ACCEPTED, ExpectedOutcome.DUPLICATE -> {
                    if (results.any { it !is PipelineResult.Accepted }) { failures += "${s.id}: expected accepted, got $results"; return@forEachIndexed }
                    val txns = repo.transactions.value
                    if (txns.size != 1) failures += "${s.id}: ${s.copies} post(s) should give 1 record, got ${txns.size}"
                    val t = txns.first()
                    if (t.evidenceCount != s.copies) failures += "${s.id}: evidenceCount ${t.evidenceCount}, expected ${s.copies}"
                    if (t.fingerprint != "ref:$tid") failures += "${s.id}: fingerprint ${t.fingerprint} should be ref:$tid (fresh TID per send)"
                    if (s.amountMinor != null && t.amount.minor != s.amountMinor) failures += "${s.id}: amount ${t.amount.minor}, expected ${s.amountMinor}"
                    val ruleMiss = listOfNotNull(
                        s.category?.takeIf { it != t.category }?.let { "category ${t.category}, expected $it" },
                        s.kind?.takeIf { it != t.kind }?.let { "kind ${t.kind}, expected $it" },
                    )
                    if (ruleMiss.isNotEmpty()) (if (s.id in knownGaps) gaps else failures) += "${s.id}: ${ruleMiss.joinToString("; ")}"
                }
            }
        }
        if (gaps.isNotEmpty()) println("TestSamples known gaps (pipeline rules):\n" + gaps.joinToString("\n"))
        assertTrue(failures.isEmpty(), "Test Lab sample failures:\n" + failures.joinToString("\n"))
    }

    @Test
    fun laptopScriptUsesTheSameSamples() {
        val script = java.io.File("../../scripts/send_test_sms.sh")
        if (!script.exists()) return // only present in the full repo checkout
        val lines = Regex("""^\s*"([^"|]+)\|([^"]+)"\s*$""", RegexOption.MULTILINE).findAll(script.readText())
            .map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue(lines.size >= 15, "script should carry the Test Lab samples")
        val known = TestSamples.all.map { it.sender to it.template }.toSet()
        lines.forEach { assertTrue(it in known, "script sample not in TestSamples.kt: $it") }
    }

    @Test
    fun freshTidPerSendKeepsRepeatedSamplesSeparate() {
        val repo = Repository()
        val s = TestSamples.byId("hbl_kfc")!!
        ingest(repo, s.sender, s.render("111111111111"), 1_700_000_000_000L)
        ingest(repo, s.sender, s.render("222222222222"), 1_700_000_005_000L)
        assertEquals(2, repo.transactions.value.size, "two sends with different TIDs are two payments")
    }

    @Test
    fun wholeBurstThroughOneRepository() {
        val repo = Repository()
        var at = 1_700_000_000_000L
        TestSamples.all.forEach { s ->
            val text = s.render(TestSamples.randomTid())
            repeat(s.copies) { ingest(repo, s.sender, text, at); at += 1_500 }
        }
        val accepted = TestSamples.all.count { it.outcome == ExpectedOutcome.ACCEPTED || it.outcome == ExpectedOutcome.DUPLICATE }
        assertEquals(accepted, repo.transactions.value.size, "one record per accepted sample (duplicate pair merged)")
        assertEquals(TestSamples.all.count { it.outcome == ExpectedOutcome.SCAM }, repo.scams.value.size)
        assertEquals(1, repo.transactions.value.count { it.evidenceCount == 2 })
    }
}
