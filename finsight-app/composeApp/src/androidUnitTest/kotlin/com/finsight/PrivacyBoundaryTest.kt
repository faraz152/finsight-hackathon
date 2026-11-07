package com.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Architecture test (plan 2 / 5.4): exactly one file may talk to the network, and it may only accept a
 * SanitizedPrompt. Fails the build if networking appears anywhere else (pipeline, data, sms capture, UI...).
 */
class PrivacyBoundaryTest {
    private val network = Regex("""java\.net\.|HttpURLConnection|okhttp|io\.ktor|retrofit|Socket\(|URL\(""")
    private val allowed = setOf("GeminiCloudLlm.kt")

    @Test
    fun onlyTheCloudClientUsesTheNetwork() {
        val offenders = File("src").walkTopDown().filter { it.extension == "kt" && "Test" !in it.path }
            .filter { f -> f.readLines().any { l -> !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*") && network.containsMatchIn(l) } }
            .map { it.name }.toSet()
        assertEquals(allowed, offenders, "Network code found outside the cloud client: ${offenders - allowed}")
    }

    @Test
    fun cloudClientAcceptsOnlySanitizedPrompt() {
        val llm = File("src/commonMain/kotlin/com/finsight/ai/Llm.kt").readText()
        assertTrue(Regex("""suspend fun complete\(prompt: SanitizedPrompt\)""").containsMatchIn(llm), "CloudLlm must take SanitizedPrompt only")
        val privacy = File("src/commonMain/kotlin/com/finsight/coach/Privacy.kt").readText()
        assertTrue("class SanitizedPrompt private constructor" in privacy, "SanitizedPrompt constructor must stay private")
    }
}
