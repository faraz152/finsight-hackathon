package com.finsight.coach

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The only kinds of personal data that may leave the phone (allowlist, plan 5.4). */
sealed interface SafeFact {
    val name: String
    fun render(): String

    data class Percentage(override val name: String, val value: Int) : SafeFact { override fun render() = "$value%" }
    data class RoundedAmount(override val name: String, private val minor: Long) : SafeFact {
        val rounded: Long = ((minor / 100 + 500) / 1000) * 1000
        override fun render() = "PKR ~${rounded}"
    }
    data class CategoryShare(override val name: String, val category: String, val percent: Int) : SafeFact { override fun render() = "$category $percent%" }
    data class Count(override val name: String, val value: Int) : SafeFact { override fun render() = value.toString() }
    data class MonthsRatio(override val name: String, val value: Double) : SafeFact {
        override fun render() = ((value * 10).toInt() / 10.0).toString()
    }
    data class Label(override val name: String, val value: String) : SafeFact { override fun render() = value }
}

/** Can only be built by [Redactor]: the compiler enforces the privacy boundary. */
class SanitizedPrompt private constructor(val question: String, val facts: List<SafeFact>) {
    fun payloadJson(): String {
        val f = facts.joinToString(",\n    ") { "\"${it.name}\": \"${it.render().replace("\"", "'")}\"" }
        return "{\n  \"question\": \"${question.replace("\"", "'")}\",\n  \"facts\": {\n    $f\n  }\n}"
    }

    fun render(): String = """
You are FinSight Coach, a financial-education assistant inside a budgeting app in Pakistan.
You get a question and a few facts about the user, computed on their phone (percentages and rounded amounts only).
For anything about the user, use only these facts; never invent figures. Answer in plain language in at most 110 words,
then give up to 3 short next steps as bullet points. This is education, not regulated financial advice: do not recommend
specific stocks, funds or financial products. For tax, Zakat or savings-scheme rates, say they change and name the official source.
Reply in the language of the question (English, Urdu or Roman Urdu).
Facts: ${payloadJson()}
Question: $question
""".trimIndent()

    internal companion object {
        fun create(question: String, facts: List<SafeFact>) = SanitizedPrompt(question, facts)
    }
}

object Redactor {
    private val digits = Regex("""\d{4,}""")
    private val phone = Regex("""(\+92|0)3\d{2}[- ]?\d{7}""")

    /** Returns null when the question itself carries something that cannot leave the phone. */
    fun scrub(question: String, knownNames: Collection<String>): String? {
        if (phone.containsMatchIn(question) || digits.containsMatchIn(question.replace(",", ""))) {
            // amounts like 150000 are fine only if they look like money; keep it simple: allow if preceded by Rs/PKR
            val stripped = question.replace(Regex("""(?i)(rs\.?|pkr)\s*[0-9,]+"""), "")
            if (phone.containsMatchIn(stripped) || digits.containsMatchIn(stripped.replace(",", ""))) return null
        }
        val lower = question.lowercase()
        if (knownNames.any { it.length >= 3 && lower.contains(it.lowercase()) }) return null
        return question.trim()
    }

    fun build(scrubbedQuestion: String, facts: List<SafeFact>): SanitizedPrompt = SanitizedPrompt.create(scrubbedQuestion, facts)
}

/** "Gemini proposes, the device verifies": drop sentences with figures that are not in the facts. */
object NumberVerifier {
    private val number = Regex("""\d[\d,]*(?:\.\d+)?""")

    fun verify(answer: String, facts: List<SafeFact>): Pair<String, Int> {
        val allowed = facts.flatMap { number.findAll(it.render()).map { m -> m.value.replace(",", "") }.toList() }.toSet()
        var dropped = 0
        val kept = answer.split(Regex("""(?<=[.!?])\s+|\n""")).filter { sentence ->
            val personal = Regex("""(?i)\b(your|you|aap|apka|apki|tumhara)\b""").containsMatchIn(sentence)
            val nums = number.findAll(sentence).map { it.value.replace(",", "") }.filter { it.length >= 2 }.toList()
            val ok = !personal || nums.all { it in allowed || it.toDoubleOrNull()?.let { v -> v <= 12 } == true }
            if (!ok) dropped++
            ok
        }
        return kept.joinToString("\n").trim() to dropped
    }
}

data class CloudLogEntry(val time: Long, val route: String, val model: String, val payload: String, val latencyMs: Long, val status: String)

class CoachLog {
    private val _entries = MutableStateFlow<List<CloudLogEntry>>(emptyList())
    val entries: StateFlow<List<CloudLogEntry>> = _entries.asStateFlow()
    fun add(e: CloudLogEntry) = _entries.update { listOf(e) + it }
    fun clear() { _entries.value = emptyList() }
}
