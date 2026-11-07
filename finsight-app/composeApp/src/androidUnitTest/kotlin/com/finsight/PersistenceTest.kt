package com.finsight

import com.finsight.coach.CloudLogEntry
import com.finsight.coach.CoachLog
import com.finsight.data.Codec
import com.finsight.data.InMemoryStore
import com.finsight.data.Repository
import com.finsight.domain.*
import com.finsight.pipeline.PipelineResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PersistenceTest {
    private val now = 1_790_000_000_000L
    private fun sms(text: String, minsAgo: Long) = RawInput(Channel.DEMO, "HBL", "HBL", text, now - minsAgo * 60_000L)

    @Test
    fun repositoryRoundTripsThroughStore() {
        val store = InMemoryStore()
        val repo = Repository(store)
        repo.importBatch(listOf(
            sms("Rs 3,450.00 spent at Foodpanda using card **7710. Avail Bal: Rs 61,750.00", 100),
            sms("Rs 8,450.00 paid to K-Electric bill via HBL Mobile from A/C **4821. Ref: KE88231190", 200),
            sms("Salary of Rs 185,000.00 credited to your A/C **4821 by ACME TECH PVT LTD.", 300),
            sms("Dear customer your account will be blocked. Click link bit.ly/hbl-kyc to update your KYC", 50),
        ))
        repo.ingest(RawInput(Channel.MANUAL, "You", null, "chai 150 cash", now))
        assertTrue(repo.transactions.value.size >= 3)
        val target = repo.transactions.value.first { it.merchantKey != null }
        repo.correct(target.id, Category.ENTERTAINMENT)

        // Simulate process death: fresh Repository from the same store.
        val restored = Repository(store)
        assertEquals(repo.transactions.value, restored.transactions.value)
        assertEquals(repo.memory.value, restored.memory.value)
        assertEquals(repo.scams.value, restored.scams.value)
        assertEquals(repo.lastImport.value, restored.lastImport.value)
        assertTrue(restored.memory.value.isNotEmpty())

        // Ids stay unique after restore.
        val r = restored.ingest(sms("Rs 650.00 paid to Careem ride using card **7710. Avail Bal: Rs 68,090.00", 10))
        assertTrue(r is PipelineResult.Accepted)
        assertTrue(r.txn.id > repo.transactions.value.maxOf { it.id })

        restored.delete(r.txn.id)
        assertEquals(repo.transactions.value, Repository(store).transactions.value)
        restored.clearAll()
        assertTrue(Repository(store).transactions.value.isEmpty())
    }

    @Test
    fun tricky_text_survives_codec() {
        val raw = "He said \"pay\" now\nline2\ttab \\ back\\n slash \\0 \r\n" +
            "آپ کے اکاؤنٹ سے 5,000 روپے کٹ گئے 😀 { } , ;"
        val t = Transaction(
            id = 42, amount = Money(500_000, "PKR"), type = TxnType.DEBIT, kind = TxnKind.EXPENSE, status = TxnStatus.POSTED,
            merchant = "Ali \"Bhai\" Store", merchantKey = null, accountLast4 = "4821", balance = null,
            category = Category.SHOPPING, confidence = 0.8123456789, categorySource = CategorySource.RULE,
            parseSource = ParseSource.REGEX, channel = Channel.NOTIFICATION, sourceApp = "", needsReview = true,
            rawText = raw, timestamp = now, fingerprint = "ref:X\tY", evidenceCount = 2,
            explanation = listOf("a\nb", "", "\\0", "اردو"),
        )
        val store = InMemoryStore()
        val repo = Repository(store)
        repo.restore(Codec.encodeRepo(com.finsight.data.RepoSnapshot(listOf(t), mapOf("ali \"bhai\"\nx" to Category.FOOD), listOf(raw), null)))
        val back = Codec.decodeRepo(repo.snapshot())
        assertEquals(listOf(t), back.transactions)
        assertEquals(mapOf("ali \"bhai\"\nx" to Category.FOOD), back.memory)
        assertEquals(listOf(raw), back.scams)
        assertNotEquals(42L, (repo.ingest(RawInput(Channel.MANUAL, "You", null, "chai 150 cash", now)) as PipelineResult.Accepted).txn.id)
    }

    @Test
    fun coachLogPersists() {
        val store = InMemoryStore()
        val log = CoachLog(store)
        val e = CloudLogEntry(now, "Cloud", "gemini", "{\"q\":\"kitna\nkharch?\"}", 120, "ok")
        log.add(e)
        assertEquals(listOf(e), CoachLog(store).entries.value)
        log.clear()
        assertTrue(CoachLog(store).entries.value.isEmpty())
    }
}
