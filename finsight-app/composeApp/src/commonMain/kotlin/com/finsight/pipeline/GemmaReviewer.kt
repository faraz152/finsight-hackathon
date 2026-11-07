package com.finsight.pipeline

import com.finsight.ai.LocalLlm
import com.finsight.data.Repository
import com.finsight.domain.CategorySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background second opinion: runs on-device Gemma over the transactions the rules were unsure about
 * (needsReview && source RULE/NONE), one at a time, and relabels them with the hybrid score.
 * Does nothing when no model is loaded. Each record is tried at most once per app session.
 */
class GemmaReviewer(
    private val repo: Repository,
    private val llm: LocalLlm,
    private val categorizer: LlmCategorizer = LlmCategorizer(llm),
) {
    private val tried = mutableSetOf<Long>()
    private val lock = Mutex()

    private fun pendingIds(): List<Long> = repo.transactions.value
        .filter { it.needsReview && (it.categorySource == CategorySource.NONE || it.categorySource == CategorySource.RULE) }
        .map { it.id }

    /** Processes every pending record not yet tried. Returns how many were relabelled. */
    suspend fun reviewPending(): Int = lock.withLock {
        var changed = 0
        for (id in pendingIds()) {
            if (!llm.available) break
            if (!tried.add(id)) continue
            val t = repo.transactions.value.firstOrNull { it.id == id } ?: continue
            val corrections = repo.memory.value.entries.take(4).map { it.key to it.value }
            val verdict = runCatching { categorizer.classify(t, corrections) }.getOrNull() ?: continue
            // Re-read: the user may have corrected it while Gemma was thinking.
            val now = repo.transactions.value.firstOrNull { it.id == id } ?: continue
            if (now.categorySource != CategorySource.NONE && now.categorySource != CategorySource.RULE) continue
            val d = LlmCategorizer.combine(now.category, now.confidence, now.categorySource, verdict)
            repo.relabel(id, d.category, d.confidence, d.source, d.note)
            changed++
        }
        changed
    }

    /** Re-runs (debounced) whenever the set of pending records changes, e.g. after an import or a new notification. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope, debounceMs: Long = 1_500): Job = scope.launch {
        repo.transactions
            .map { pendingIds() }
            .distinctUntilChanged()
            .debounce(debounceMs)
            .collect { if (it.isNotEmpty() && llm.available) runCatching { reviewPending() } }
    }
}
