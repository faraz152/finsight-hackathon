package com.finsight

import com.finsight.ai.LocalLlm
import com.finsight.data.Repository
import com.finsight.domain.Category
import com.finsight.domain.CategorySource
import com.finsight.domain.Channel
import com.finsight.domain.RawInput
import com.finsight.pipeline.GemmaReviewer
import com.finsight.pipeline.LlmCategorizer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LlmCategorizerTest {
    /** Replies in order; last reply repeats. Counts calls. */
    private class FakeLlm(vararg val replies: String?, override val available: Boolean = true) : LocalLlm {
        var calls = 0
        val prompts = mutableListOf<String>()
        override suspend fun complete(prompt: String, maxTokens: Int): String? {
            prompts += prompt
            return replies[minOf(calls++, replies.size - 1)]
        }
    }

    private val now = 1_790_000_000_000L

    /** Unknown merchant, keyword hit → RULE with confidence < 0.90 (needs review). */
    private fun uncertainRepo(): Repository = Repository().also {
        it.ingest(RawInput(Channel.DEMO, "HBL", "HBL", "Rs 1,200.00 spent at Gulshan Biryani Point using card **7710.", now))
        it.ingest(RawInput(Channel.DEMO, "HBL", "HBL", "Rs 900.00 spent at XYZ Traders using card **7710.", now - 3_600_000))
    }

    @Test fun parsesDefensively() {
        assertEquals(Category.FOOD, LlmCategorizer.parse("  Food\n"))
        assertEquals(Category.BILLS, LlmCategorizer.parse("bills."))
        assertEquals(Category.TRANSPORT, LlmCategorizer.parse("{\"category\": \"transport\"}"))
        assertEquals(Category.FOOD, LlmCategorizer.parse("Category: food"))
        assertNull(LlmCategorizer.parse("I think this might be a restaurant or maybe shopping, hard to say really"))
        assertNull(LlmCategorizer.parse("banana"))
        assertNull(LlmCategorizer.parse(""))
        assertNull(LlmCategorizer.parse(null))
    }

    @Test fun promptListsAllowedIds() {
        val t = uncertainRepo().transactions.value.first()
        val p = LlmCategorizer.prompt(t, listOf("cafe aylanto" to Category.FOOD))
        assertTrue(p.startsWith("Pick exactly one category id"))
        assertTrue("food, groceries, transport" in p)
        assertTrue(p.indexOf("cafe aylanto -> food") < p.indexOf("KFC Gulberg"), "user corrections come first")
    }

    @Test fun agreeBoostsToHybrid() = runBlocking {
        val repo = uncertainRepo()
        val t = repo.transactions.value.first { it.merchant?.contains("Biryani") == true }
        assertEquals(CategorySource.RULE, t.categorySource)
        assertTrue(t.needsReview)
        val llm = FakeLlm("food")
        val v = assertNotNull(LlmCategorizer(llm).classify(t))
        assertEquals(0.80, v.confidence)
        val d = LlmCategorizer.combine(t.category, t.confidence, t.categorySource, v)
        assertEquals(Category.FOOD, d.category)
        assertEquals(CategorySource.HYBRID, d.source)
        assertEquals(minOf(1.0, maxOf(t.confidence, 0.8) + 0.15), d.confidence, 1e-9)
        assertFalse(d.needsReview)
        assertTrue(d.note.startsWith("Gemma: food; rule: food"))
    }

    @Test fun disagreeCapsAndKeepsReview() {
        val g = LlmCategorizer.Companion
        // Gemma (0.80) beats a weak rule (0.70): Gemma wins, capped at 0.85, still in review.
        val win = g.combine(Category.SHOPPING, 0.70, CategorySource.RULE, com.finsight.pipeline.LlmVerdict(Category.FOOD, 0.80, false))
        assertEquals(Category.FOOD, win.category)
        assertEquals(CategorySource.LLM, win.source)
        assertEquals(0.80, win.confidence, 1e-9)
        assertTrue(win.needsReview)
        assertEquals("Gemma: food; rule: shopping → food (disagree, please review)", win.note)
        // Rule (0.85) beats repaired Gemma (0.60): rule kept, capped at 0.85.
        val lose = g.combine(Category.SHOPPING, 0.88, CategorySource.RULE, com.finsight.pipeline.LlmVerdict(Category.FOOD, 0.60, true))
        assertEquals(Category.SHOPPING, lose.category)
        assertEquals(CategorySource.RULE, lose.source)
        assertEquals(0.85, lose.confidence, 1e-9)
        assertTrue(lose.needsReview)
        // No rule answer → Gemma alone.
        val only = g.combine(Category.OTHER, 0.5, CategorySource.NONE, com.finsight.pipeline.LlmVerdict(Category.SHOPPING, 0.80, false))
        assertEquals(Category.SHOPPING, only.category)
        assertEquals(CategorySource.LLM, only.source)
    }

    @Test fun garbageTwiceGivesNull() = runBlocking {
        val t = uncertainRepo().transactions.value.first()
        val llm = FakeLlm("As an AI language model I cannot determine that with certainty, sorry about it", "banana")
        assertNull(LlmCategorizer(llm).classify(t))
        assertEquals(2, llm.calls, "exactly one retry")
        assertTrue(llm.prompts[1].startsWith("Reply with one id from the list"))
    }

    @Test fun retryRepairsWithLowerConfidence() = runBlocking {
        val t = uncertainRepo().transactions.value.first()
        val llm = FakeLlm("hmm", "shopping")
        val v = assertNotNull(LlmCategorizer(llm).classify(t))
        assertEquals(Category.SHOPPING, v.category)
        assertEquals(0.60, v.confidence)
        assertTrue(v.repaired)
    }

    @Test fun nullAndUnavailableAreSafe() = runBlocking {
        val t = uncertainRepo().transactions.value.first()
        assertNull(LlmCategorizer(FakeLlm(null)).classify(t))
        val off = FakeLlm("food", available = false)
        assertNull(LlmCategorizer(off).classify(t))
        assertEquals(0, off.calls)
    }

    @Test fun reviewerRelabelsOnlyUncertainAndRespectsUser() = runBlocking {
        val repo = uncertainRepo()
        val pending = repo.transactions.value.filter { it.needsReview }
        assertTrue(pending.size >= 2)
        // User corrects one first; Gemma must not touch it.
        val userFixed = pending.first { it.merchant?.contains("XYZ") == true }
        repo.correct(userFixed.id, Category.GROCERIES)
        val llm = FakeLlm("food")
        val reviewer = GemmaReviewer(repo, llm)
        val changed = reviewer.reviewPending()
        assertTrue(changed >= 1)
        val after = repo.transactions.value
        assertEquals(CategorySource.USER, after.first { it.id == userFixed.id }.categorySource)
        val biryani = after.first { it.merchant?.contains("Biryani") == true }
        assertEquals(CategorySource.HYBRID, biryani.categorySource)
        assertFalse(biryani.needsReview)
        assertTrue(biryani.explanation.last().startsWith("Gemma: food"))
        // Second pass: each record is tried once per session, so no extra model calls.
        val callsBefore = llm.calls
        assertEquals(0, reviewer.reviewPending())
        assertEquals(callsBefore, llm.calls)
    }
}
