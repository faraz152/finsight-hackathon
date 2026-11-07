package com.finsight.data

import com.finsight.domain.*
import com.finsight.pipeline.Pipeline
import com.finsight.pipeline.PipelineResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ImportStats(val accepted: Int = 0, val autoAccepted: Int = 0, val review: Int = 0, val duplicates: Int = 0, val dropped: Int = 0, val scams: Int = 0)

data class Coverage(val percent: Int?, val basis: String)

/** In-memory store for the hackathon (SQLDelight is roadmap). */
class Repository {
    private val _txns = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _txns.asStateFlow()
    private val _memory = MutableStateFlow<Map<String, Category>>(emptyMap())
    val memory: StateFlow<Map<String, Category>> = _memory.asStateFlow()
    private val _scams = MutableStateFlow<List<String>>(emptyList())
    val scams: StateFlow<List<String>> = _scams.asStateFlow()
    private val _lastImport = MutableStateFlow<ImportStats?>(null)
    val lastImport: StateFlow<ImportStats?> = _lastImport.asStateFlow()
    val ownAccounts = mutableSetOf<String>()

    val pipeline = Pipeline(memory = { _memory.value }, ownAccounts = { ownAccounts })

    fun ingest(input: RawInput): PipelineResult {
        val r = pipeline.process(input)
        when (r) {
            is PipelineResult.Accepted -> add(r.txn)
            is PipelineResult.ScamWarning -> _scams.update { listOf(r.text) + it }
            is PipelineResult.Dropped -> {}
        }
        return r
    }

    /** Integrity: same fingerprint = one record with more evidence, not two transactions. Returns false if duplicate. */
    private fun add(t: Transaction): Boolean {
        var dup = false
        _txns.update { list ->
            val existing = list.indexOfFirst { it.fingerprint == t.fingerprint && (t.fingerprint.startsWith("ref:") || kotlin.math.abs(it.timestamp - t.timestamp) <= 10 * 60_000L) }
            if (existing >= 0) {
                dup = true
                list.toMutableList().also {
                    val e = it[existing]
                    it[existing] = e.copy(evidenceCount = e.evidenceCount + 1, explanation = e.explanation + "Duplicate notification merged (seen ${e.evidenceCount + 1}×)")
                }
            } else (list + t).sortedByDescending { it.timestamp }
        }
        return !dup
    }

    fun importBatch(inputs: List<RawInput>): ImportStats {
        var s = ImportStats()
        inputs.forEach { input ->
            val before = _txns.value.size
            when (val r = ingest(input)) {
                is PipelineResult.Accepted -> s = if (_txns.value.size == before) s.copy(duplicates = s.duplicates + 1)
                else s.copy(accepted = s.accepted + 1, autoAccepted = s.autoAccepted + if (r.txn.needsReview) 0 else 1, review = s.review + if (r.txn.needsReview) 1 else 0)
                is PipelineResult.Dropped -> s = s.copy(dropped = s.dropped + 1)
                is PipelineResult.ScamWarning -> s = s.copy(scams = s.scams + 1)
            }
        }
        _lastImport.value = s
        return s
    }

    /** Learn: correction writes merchant memory and re-applies to every record of that merchant. */
    fun correct(id: Long, category: Category) {
        val t = _txns.value.firstOrNull { it.id == id } ?: return
        t.merchantKey?.let { k -> _memory.update { it + (k to category) } }
        _txns.update { list ->
            list.map {
                if (it.id == id || (t.merchantKey != null && it.merchantKey == t.merchantKey))
                    it.copy(category = category, confidence = 0.98, categorySource = CategorySource.USER, needsReview = false,
                        explanation = it.explanation + "Corrected by you → ${category.label} (remembered for next time)")
                else it
            }
        }
    }

    fun delete(id: Long) = _txns.update { l -> l.filterNot { it.id == id } }
    fun clearAll() { _txns.value = emptyList(); _memory.value = emptyMap(); _scams.value = emptyList(); _lastImport.value = null }

    /**
     * Data coverage (plan 6.2): computed, never invented.
     * Balance chain per account: consecutive records whose balances explain each other are "covered".
     */
    fun coverage(): Coverage {
        val list = _txns.value.filter { it.balance != null && it.accountLast4 != null && it.amount.currency == "PKR" }
        var pairs = 0; var ok = 0
        list.groupBy { it.accountLast4 }.values.filter { it.size >= 3 }.forEach { acct ->
            val sorted = acct.sortedBy { it.timestamp }
            sorted.zipWithNext().forEach { (a, b) ->
                pairs++
                val delta = if (b.type == TxnType.DEBIT) -b.amount.minor else b.amount.minor
                if (kotlin.math.abs(a.balance!!.minor + delta - b.balance!!.minor) <= 100) ok++
            }
        }
        if (pairs >= 3) return Coverage(ok * 100 / pairs, "$ok of $pairs balance changes explained by captured transactions")
        val all = _txns.value
        if (all.isEmpty()) return Coverage(null, "No data yet")
        val days = all.map { it.timestamp / 86_400_000 }.toSet().size
        val span = ((all.maxOf { it.timestamp } - all.minOf { it.timestamp }) / 86_400_000 + 1).toInt()
        return Coverage((days * 100 / span).coerceIn(0, 100), "Activity on $days of $span days; cash and apps without notifications may be missing")
    }
}
