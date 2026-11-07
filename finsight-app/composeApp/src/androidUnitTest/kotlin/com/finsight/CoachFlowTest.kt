package com.finsight

import com.finsight.ai.CloudLlm
import com.finsight.coach.*
import com.finsight.domain.Channel
import com.finsight.domain.RawInput
import com.finsight.ui.AppState
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachFlowTest {
    private val day = 86_400_000L
    private val now = 1_790_000_000_000L
    private var lastPrompt: SanitizedPrompt? = null
    private val fakeCloud = object : CloudLlm {
        override val configured = true
        override val modelName = "fake-gemini"
        override suspend fun complete(prompt: SanitizedPrompt): String { lastPrompt = prompt; return "Your savings rate is 99%. Build an emergency fund of 3 months." }
    }

    private fun demo(): List<RawInput> {
        val text = File("src/androidMain/assets/sms_test_templates.json").readText()
        val msgs = Regex(""""message":\s*"((?:[^"\\]|\\.)*)"""").findAll(text).map { it.groupValues[1] }.toList()
        val extra = listOf(
            2 to "Salary of Rs 185,000.00 credited to your A/C **4821 by ACME TECH PVT LTD. Avail Bal: Rs 212,450.00",
            32 to "Salary of Rs 185,000.00 credited to your A/C **4821 by ACME TECH PVT LTD.",
            5 to "Rs 8,450.00 paid to K-Electric bill via HBL Mobile from A/C **4821. Ref: KE88231190",
            35 to "Rs 7,920.00 paid to K-Electric bill via HBL Mobile from A/C **4821. Ref: KE77120944",
            1 to "Rs 3,450.00 spent at Foodpanda using card **7710 on 06-Nov-26. Avail Bal: Rs 61,750.00",
            3 to "Rs 2,890.00 spent at KFC Gulberg using card **7710. Avail Bal: Rs 65,200.00",
            38 to "Rs 1,600.00 spent at Foodpanda using card **7710. Avail Bal: Rs 88,000.00",
            7 to "Rs 1,200.00 refund received from Daraz to card **7710. Avail Bal: Rs 72,840.00",
            10 to "Your OTP for transaction is 482913. Do not share it with anyone.",
            11 to "Dear customer your account will be blocked. Click link bit.ly/hbl-kyc to update your KYC",
        )
        return msgs.mapIndexed { i, m -> RawInput(Channel.DEMO, "demo", null, m, now - (i * 59L / msgs.size) * day) } +
            extra.map { (d, m) -> RawInput(Channel.DEMO, "HBL", null, m, now - d * day) } +
            RawInput(Channel.NOTIFICATION, "Foodpanda", null, extra[4].second, now - 1 * day + 60_000)
    }

    @Test
    fun flow() = runBlocking {
        val s = AppState(fakeCloud, { true }, { now }, {}, { false }, ::demo)
        val st = s.repo.importBatch(demo())
        println("IMPORT $st coverage=${s.repo.coverage()}")
        assertTrue(st.duplicates >= 1, "duplicate merged"); assertTrue(st.scams >= 1, "scam flagged")
        val h = HealthScore.compute(s.tools, Profile.SALARIED, s.repo.coverage().percent)
        println("HEALTH ${h.score} ${h.band} used=${h.used} checks=${h.checks.map { it.name + "=" + it.detail }}")
        println("PULSE ${Pulse.cards(s.tools)}")
        listOf("Where did my money go this week?", "How healthy are my finances and what should I fix first?", "Can I afford a laptop for PKR 150,000?",
            "Meri spending kahan gayi?", "Make my monthly report", "Compare with last month", "What are my recurring bills?", "What is an emergency fund?",
            "How much did I pay KFC Gulberg?").forEach { q ->
            val a = s.coach.ask(q)
            println("Q: $q\n  [${a.route}] ${a.text.replace("\n", " | ")}\n  note=${a.note} shared=${a.shared?.replace("\n", "")}")
        }
        // Privacy: merchant name in question must stay local
        val a = s.coach.ask("How much did I pay KFC Gulberg?")
        assertNull(a.shared)
        val h2 = s.coach.ask("How healthy are my finances?")
        assertNotNull(h2.shared); assertTrue(!h2.shared!!.contains("KFC") && !h2.shared!!.contains("4821"))
        s.cloudEnabled.value = false
        assertNull(s.coach.ask("How healthy are my finances?").shared)
        s.repo.transactions.value.first { it.merchantKey != null }.let { s.repo.correct(it.id, com.finsight.domain.Category.SHOPPING) }
        println("LOG ${s.log.entries.value.size}")
    }
}
