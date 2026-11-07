package com.finsight.ai

import com.finsight.coach.SanitizedPrompt

/** On-device model (Gemma via LiteRT-LM). Null implementation until the model is on the phone. */
interface LocalLlm {
    val available: Boolean
    suspend fun complete(prompt: String, maxTokens: Int = 256): String?
}

object NoLocalLlm : LocalLlm {
    override val available = false
    override suspend fun complete(prompt: String, maxTokens: Int): String? = null
}

class CloudUnavailable(msg: String) : Exception(msg)

/** Cloud model (Gemini). Accepts ONLY a SanitizedPrompt, which only the Redactor can build. */
interface CloudLlm {
    val configured: Boolean
    val modelName: String
    suspend fun complete(prompt: SanitizedPrompt): String
}
