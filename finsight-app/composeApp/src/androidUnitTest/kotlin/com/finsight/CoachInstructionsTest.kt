package com.finsight

import com.finsight.coach.CoachInstructions
import com.finsight.coach.TopicGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachInstructionsTest {
    @Test
    fun financeQuestionsAreInScope() {
        listOf(
            "What is an emergency fund?", "How does compound interest work?", "Meri spending kahan gayi?",
            "Can I afford a laptop for PKR 150,000?", "How do I calculate Zakat?", "Is this OTP message a scam?",
            "What is Raast?", "How much did I pay KFC Gulberg?", "Should I keep money in a savings account or gold?",
            "What is riba?",
        ).forEach { assertFalse(TopicGuard.isOffTopic(it), "should be in scope: $it") }
    }

    @Test
    fun offTopicQuestionsAreRejectedOnDevice() {
        listOf(
            "Who won the cricket match yesterday?", "Write me a poem about the sea", "What is the capital of France?",
            "Give me a biryani recipe", "Write python code to sort a list", "Tell me a joke",
        ).forEach { assertTrue(TopicGuard.isOffTopic(it), "should be off topic: $it") }
    }

    @Test
    fun modelOffTopicMarkerBecomesRefusal() {
        assertNull(CoachInstructions.postProcess("OFF_TOPIC", false))
        assertNull(CoachInstructions.postProcess(" OFF_TOPIC. ", false))
        assertEquals("Save 10% first.", CoachInstructions.postProcess("Save 10% first.", false))
    }

    @Test
    fun promptContainsScopeAndFacts() {
        val p = CoachInstructions.prompt("{\"savings_rate\":\"45%\"}", "How healthy am I?")
        assertTrue("SCOPE" in p && "OUT OF SCOPE" in p && "OFF_TOPIC" in p && "45%" in p && "How healthy am I?" in p)
    }
}
