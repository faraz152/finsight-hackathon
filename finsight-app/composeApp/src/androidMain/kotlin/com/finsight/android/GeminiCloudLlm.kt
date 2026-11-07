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
 * Hackathon: Gemini Developer API key from local.properties (debug only). Production: Firebase AI Logic + App Check.
 */
class GeminiCloudLlm(private val apiKey: String, override val modelName: String) : CloudLlm {
    override val configured: Boolean get() = apiKey.isNotBlank()

    override suspend fun complete(prompt: SanitizedPrompt): String = withContext(Dispatchers.IO) {
        if (!configured) throw CloudUnavailable("no key")
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt.render())))))
            .put("generationConfig", JSONObject().put("temperature", 0.3).put("maxOutputTokens", 400))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 6000; readTimeout = 9000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            if (code !in 200..299) throw CloudUnavailable("HTTP $code ${text.take(120)}")
            val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
            buildString { for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text")) }.trim()
        } finally {
            conn.disconnect()
        }
    }
}
