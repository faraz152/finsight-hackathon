package com.finsight.android

import com.finsight.ai.CloudLlm
import com.finsight.ai.CloudUnavailable
import com.finsight.coach.SanitizedPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * THE ONLY NETWORK CODE IN THE APP. Accepts only a SanitizedPrompt (built by the on-device Redactor).
 *
 * Providers, tried in order (each gets exactly the same redacted payload):
 *  1. Google Gemini Developer API (GEMINI_API_KEY)
 *  2. Gemini via OpenRouter (OPENROUTER_API_KEY), a relay used as a hackathon fallback
 * If both fail, the caller (CoachAgent) answers on-device.
 * Production: Firebase AI Logic + App Check, Google only.
 */
class GeminiCloudLlm(
    private val geminiKey: String,
    private val geminiModel: String,
    private val openRouterKey: String = "",
    private val openRouterModel: String = "google/gemini-3.8-flash",
) : CloudLlm {
    override val configured: Boolean get() = geminiKey.isNotBlank() || openRouterKey.isNotBlank()

    /** A key that is rejected (401/403/404) stays rejected: skip it for the rest of the session. */
    @Volatile private var geminiDead = false
    @Volatile private var lastUsed: String = if (geminiKey.isNotBlank()) geminiModel else "$openRouterModel (OpenRouter)"
    override val modelName: String get() = lastUsed

    override suspend fun complete(prompt: SanitizedPrompt): String = withContext(Dispatchers.IO) {
        if (!configured) throw CloudUnavailable("no key")
        val errors = mutableListOf<String>()
        if (geminiKey.isNotBlank() && !geminiDead) {
            try { return@withContext callGemini(prompt).also { lastUsed = geminiModel } } catch (e: Exception) {
                errors += "Gemini: ${e.message}"
                if (e.message?.let { m -> listOf("HTTP 401", "HTTP 403", "HTTP 404").any { m.startsWith(it) } } == true) geminiDead = true
            }
        }
        if (openRouterKey.isNotBlank()) {
            try { return@withContext callOpenRouter(prompt).also { lastUsed = "$openRouterModel (OpenRouter)" } } catch (e: Exception) { errors += "OpenRouter: ${e.message}" }
        }
        throw CloudUnavailable(errors.joinToString("; ").take(200))
    }

    private fun callGemini(prompt: SanitizedPrompt): String {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt.render())))))
            .put("generationConfig", JSONObject().put("temperature", 0.3).put("maxOutputTokens", 400))
        val text = post("https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent", body, mapOf("x-goog-api-key" to geminiKey))
        val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        return buildString { for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text")) }.trim()
    }

    private fun callOpenRouter(prompt: SanitizedPrompt): String {
        val body = JSONObject()
            .put("model", openRouterModel)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt.render())))
            .put("temperature", 0.3).put("max_tokens", 400)
        val text = post(
            "https://openrouter.ai/api/v1/chat/completions", body,
            mapOf("Authorization" to "Bearer $openRouterKey", "X-Title" to "FinSight"),
        )
        return JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
    }

    private fun post(url: String, body: JSONObject, headers: Map<String, String>): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 5000; readTimeout = 12000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw CloudUnavailable("HTTP $code ${text.take(100)}")
            return text
        } finally {
            conn.disconnect()
        }
    }
}
