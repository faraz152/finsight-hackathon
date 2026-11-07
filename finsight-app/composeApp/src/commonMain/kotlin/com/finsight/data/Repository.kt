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

/** In-memory store for the hackathon (SQLDelight is roadmap); optionally persisted to [store] after every mutation. */
class Repository(private val store: KeyValueStore? = null) {
    private val _txns = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions: StateFlow<List<Transaction>> = _txns.asStateFlow()
    private val _memory = MutableStateFlow<Map<String, Category>>(emptyMap())
    val memory: StateFlow<Map<String, Category>> = _memory.asStateFlow()
    private val _scams = MutableStateFlow<List<String>>(emptyList())
    val scams: StateFlow<List<String>> = _scams.asStateFlow()
    private val _lastImport = MutableStateFlow<ImportStats?>(null)
    val lastImport: StateFlow<ImportStats?> = _lastImport.asStateFlow()
    /** Extra own-account digits (tests / manual); accounts marked "Mine" are always included. */
    val ownAccounts = mutableSetOf<String>()
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    /** Last 4 digits of every account the user marked as theirs (feeds self-transfer detection in Integrity). */
    fun ownLast4(): Set<String> = ownAccounts + _accounts.value.filter { it.isMine }.mapNotNull { it.last4 }

    val pipeline = Pipeline(memory = { _memory.value }, ownAccounts = { ownLast4() })

    private var batching = false

    init { store?.read(STORE_KEY)?.let { restore(it) } }

    /** Serialized copy of all repository data (see [Codec]). */
    fun snapshot(): String = Codec.encodeRepo(RepoSnapshot(_txns.value, _memory.value, _scams.value, _lastImport.value, _accounts.value))

    /** Replace all data with a [snapshot]; keeps pipeline ids unique after restore. */
    fun restore(text: String) {
        val s = Codec.decodeRepo(text)
        _txns.value = s.transactions.sortedByDescending { it.timestamp }
        _memory.value = s.memory
        _scams.value = s.scams
        _lastImport.value = s.lastImport
        _accounts.value = s.accounts
        pipeline.nextId = maxOf(pipeline.nextId, (s.transactions.maxOfOrNull { it.id } ?: 0L) + 1)
    }

    private fun save() {
        val s = store ?: return
        if (batching) return
        runCatching { s.write(STORE_KEY, snapshot()) }
    }

    fun ingest(input: RawInput): PipelineResult {
        val r = pipeline.process(input)
        when (r) {
            is PipelineResult.Accepted -> add(withAccount(r.txn, input))
            is PipelineResult.ScamWarning -> _scams.update { listOf(r.text) + it }
            is PipelineResult.Dropped -> {}
        }
        if (r !is PipelineResult.Dropped) save()
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

    /** Resolve (or create) the account for [t], keep its latest balance, and tag the record. */
    private fun withAccount(t: Transaction, input: RawInput): Transaction {
        val res = AccountResolver.resolve(_accounts.value, input, t.accountLast4, t.timestamp) ?: return t
        var acct = res.account
        val bal = t.balance
        if (bal != null && bal.currency == acct.currency && (acct.balanceAt == null || t.timestamp >= acct.balanceAt!!))
            acct = acct.copy(balanceMinor = bal.minor, balanceAt = t.timestamp)
        _accounts.update { list -> if (res.created) list + acct else list.map { if (it.id == acct.id) acct else it } }
        return t.copy(accountId = acct.id, explanation = t.explanation + "Account: ${acct.displayName} (${res.reason})")
    }

    fun account(id: String?): Account? = id?.let { i -> _accounts.value.firstOrNull { it.id == i } }

    /** Records per account id (for the Accounts screen). */
    fun transactionCounts(): Map<String, Int> = _txns.value.mapNotNull { it.accountId }.groupingBy { it }.eachCount()

    fun renameAccount(id: String, nickname: String) {
        _accounts.update { l -> l.map { if (it.id == id) it.copy(nickname = nickname.trim().ifBlank { null }) else it } }; save()
    }

    fun setMine(id: String, mine: Boolean) {
        _accounts.update { l -> l.map { if (it.id == id) it.copy(isMine = mine) else it } }; save()
    }

    /** Merge [fromId] into [intoId]: its transactions move over, the newer balance wins, [fromId] disappears. */
    fun mergeAccounts(fromId: String, intoId: String) {
        if (fromId == intoId) return
        val from = account(fromId) ?: return
        val into = account(intoId) ?: return
        val newer = if ((from.balanceAt ?: Long.MIN_VALUE) > (into.balanceAt ?: Long.MIN_VALUE)) from else into
        val merged = into.copy(last4 = into.last4 ?: from.last4, balanceMinor = newer.balanceMinor, balanceAt = newer.balanceAt)
        _accounts.update { l -> l.filterNot { it.id == fromId }.map { if (it.id == intoId) merged else it } }
        _txns.update { l -> l.map { if (it.accountId == fromId) it.copy(accountId = intoId) else it } }
        save()
    }

    /** Remove the account only; its transactions stay (unassigned). */
    fun deleteAccount(id: String) {
        _accounts.update { l -> l.filterNot { it.id == id } }
        _txns.update { l -> l.map { if (it.accountId == id) it.copy(accountId = null) else it } }
        save()
    }

    fun addCashAccount(now: Long = _txns.value.maxOfOrNull { it.timestamp } ?: 0L): Account {
        val a = AccountResolver.cash(_accounts.value, now)
        _accounts.update { it + a }; save()
        return a
    }

    fun importBatch(inputs: List<RawInput>): ImportStats {
        var s = ImportStats()
        batching = true
        try { inputs.forEach { input ->
            val before = _txns.value.size
            when (val r = ingest(input)) {
                is PipelineResult.Accepted -> s = if (_txns.value.size == before) s.copy(duplicates = s.duplicates + 1)
                else s.copy(accepted = s.accepted + 1, autoAccepted = s.autoAccepted + if (r.txn.needsReview) 0 else 1, review = s.review + if (r.txn.needsReview) 1 else 0)
                is PipelineResult.Dropped -> s = s.copy(dropped = s.dropped + 1)
                is PipelineResult.ScamWarning -> s = s.copy(scams = s.scams + 1)
            }
        } } finally { batching = false }
        _lastImport.value = s
        save()
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
        save()
    }

    /** On-device Gemma (GemmaReviewer) relabels one record. Never overrides a user decision; does not write merchant memory. */
    fun relabel(id: Long, category: Category, confidence: Double, source: CategorySource, note: String) {
        _txns.update { list ->
            list.map {
                if (it.id != id || it.categorySource == CategorySource.USER || it.categorySource == CategorySource.USER_MEMORY) it
                else it.copy(category = category, confidence = confidence, categorySource = source,
                    needsReview = confidence < 0.90, explanation = it.explanation + note)
            }
        }
        save()
    }

    fun delete(id: Long) { _txns.update { l -> l.filterNot { it.id == id } }; save() }
    fun clearAll() { _txns.value = emptyList(); _memory.value = emptyMap(); _scams.value = emptyList(); _lastImport.value = null; _accounts.value = emptyList(); save() }

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

    companion object { const val STORE_KEY = "repository" }
}
