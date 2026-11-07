package com.finsight.android

import android.content.Context
import android.util.Log
import com.finsight.ai.LocalLlm
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device Gemma via Google LiteRT-LM. The model is NOT in the APK: push it with
 * `adb push gemma3-1b-it-int4.litertlm /sdcard/Android/data/com.finsight.app/files/model.litertlm`.
 * No file, or a load failure → available = false and every call returns null (rules-only app).
 * All engine work runs on one serial dispatcher; the engine is not safe for concurrent use.
 */
class LiteRtLlm(private val context: Context, fileName: String = "model.litertlm") : LocalLlm {
    val modelFile: File = File(context.getExternalFilesDir(null), fileName)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val serial: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    @Volatile private var engine: Engine? = null
    @Volatile private var loadFailed = false

    override val available: Boolean get() = !loadFailed && modelFile.exists()

    /** Loads the engine (can take ~10 s). Call off the main thread before first use. */
    suspend fun warmUp(): Boolean = withContext(serial) { ensureEngine() != null }

    private fun ensureEngine(): Engine? {
        engine?.let { return it }
        if (loadFailed || !modelFile.exists()) return null
        return try {
            val t0 = System.currentTimeMillis()
            // CPU backend: works on every arm64 phone with no OpenCL manifest entries. GPU is a later optimisation.
            val e = Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU, cacheDir = context.cacheDir.path))
            e.initialize()
            Log.i(TAG, "Gemma loaded from ${modelFile.name} in ${System.currentTimeMillis() - t0} ms")
            engine = e
            e
        } catch (t: Throwable) {
            Log.w(TAG, "LiteRT-LM load failed, staying rules-only", t)
            loadFailed = true
            null
        }
    }

    override suspend fun complete(prompt: String, maxTokens: Int): String? = withContext(serial) {
        val e = ensureEngine() ?: return@withContext null
        try {
            val t0 = System.currentTimeMillis()
            // Fresh conversation per call: pipeline prompts are independent; near-greedy sampling.
            val cfg = ConversationConfig(samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.1, seed = 0))
            e.createConversation(cfg).use { conv ->
                val reply = conv.sendMessage(Message.of(prompt))
                val text = reply.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                    .ifBlank { reply.toString() }
                Log.d(TAG, "Gemma ${System.currentTimeMillis() - t0} ms → ${text.take(40)}")
                text
            }
        } catch (t: Throwable) {
            Log.w(TAG, "LiteRT-LM inference failed", t)
            null
        }
    }

    companion object { private const val TAG = "LiteRtLlm" }
}
