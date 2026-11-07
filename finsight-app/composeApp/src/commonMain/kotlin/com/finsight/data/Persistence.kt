package com.finsight.data

import com.finsight.coach.CloudLogEntry
import com.finsight.domain.*

/** Tiny platform-agnostic storage (Android: files in app-private filesDir). */
interface KeyValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

class InMemoryStore : KeyValueStore {
    val data = mutableMapOf<String, String>()
    override fun read(key: String): String? = data[key]
    override fun write(key: String, value: String) { data[key] = value }
}

/** Everything the Repository needs to survive a process death. */
data class RepoSnapshot(
    val transactions: List<Transaction>,
    val memory: Map<String, Category>,
    val scams: List<String>,
    val lastImport: ImportStats?,
)

/**
 * Hand-written line format (no serialization library available):
 * one record per line, fields separated by TAB, every field escaped so it never contains a raw
 * TAB/CR/LF; null is the token `\0`. Enums by name, Money as minor + currency.
 * Records that fail to parse (e.g. unknown enum after an app update) are skipped, never crash.
 */
object Codec {
    private const val VERSION = "finsight-v1"
    private const val NULL = "\\0"

    fun esc(s: String?): String {
        if (s == null) return NULL
        val b = StringBuilder(s.length + 8)
        for (c in s) when (c) {
            '\\' -> b.append("\\\\")
            '\t' -> b.append("\\t")
            '\n' -> b.append("\\n")
            '\r' -> b.append("\\r")
            else -> b.append(c)
        }
        return b.toString()
    }

    fun unesc(s: String): String? {
        if (s == NULL) return null
        if ('\\' !in s) return s
        val b = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    't' -> b.append('\t'); 'n' -> b.append('\n'); 'r' -> b.append('\r'); '\\' -> b.append('\\')
                    else -> b.append(n)
                }
                i += 2
            } else { b.append(c); i++ }
        }
        return b.toString()
    }

    private fun line(vararg fields: String?): String = fields.joinToString("\t") { esc(it) }
    private fun fields(line: String): List<String?> = line.split('\t').map { unesc(it) }

    // ---- Repository ----

    fun encodeRepo(s: RepoSnapshot): String = buildString {
        appendLine(line(VERSION))
        s.transactions.forEach { appendLine(encodeTxn(it)) }
        s.memory.forEach { (k, v) -> appendLine(line("M", k, v.name)) }
        s.scams.forEach { appendLine(line("S", it)) }
        s.lastImport?.let { appendLine(line("I", "${it.accepted}", "${it.autoAccepted}", "${it.review}", "${it.duplicates}", "${it.dropped}", "${it.scams}")) }
    }

    fun decodeRepo(text: String): RepoSnapshot {
        val txns = mutableListOf<Transaction>()
        val memory = LinkedHashMap<String, Category>()
        val scams = mutableListOf<String>()
        var last: ImportStats? = null
        text.lineSequence().filter { it.isNotEmpty() }.forEach { raw ->
            runCatching {
                val f = fields(raw)
                when (f[0]) {
                    "T" -> txns += decodeTxn(f)
                    "M" -> memory[f[1]!!] = Category.valueOf(f[2]!!)
                    "S" -> scams += f[1]!!
                    "I" -> { val n = f.drop(1).map { it!!.toInt() }; last = ImportStats(n[0], n[1], n[2], n[3], n[4], n[5]) }
                }
            }
        }
        return RepoSnapshot(txns, memory, scams, last)
    }

    private fun encodeTxn(t: Transaction): String = line(
        "T", "${t.id}", "${t.amount.minor}", t.amount.currency, t.type.name, t.kind.name, t.status.name,
        t.merchant, t.merchantKey, t.accountLast4, t.balance?.minor?.toString(), t.balance?.currency,
        t.category.name, t.confidence.toString(), t.categorySource.name, t.parseSource.name, t.channel.name,
        t.sourceApp, t.needsReview.toString(), t.rawText, "${t.timestamp}", t.fingerprint, "${t.evidenceCount}",
        *t.explanation.toTypedArray(),
    )

    private fun decodeTxn(f: List<String?>): Transaction {
        val balMinor = f[10]?.toLong()
        return Transaction(
            id = f[1]!!.toLong(), amount = Money(f[2]!!.toLong(), f[3]!!), type = TxnType.valueOf(f[4]!!),
            kind = TxnKind.valueOf(f[5]!!), status = TxnStatus.valueOf(f[6]!!),
            merchant = f[7], merchantKey = f[8], accountLast4 = f[9],
            balance = balMinor?.let { Money(it, f[11] ?: "PKR") },
            category = Category.valueOf(f[12]!!), confidence = f[13]!!.toDouble(),
            categorySource = CategorySource.valueOf(f[14]!!), parseSource = ParseSource.valueOf(f[15]!!),
            channel = Channel.valueOf(f[16]!!), sourceApp = f[17]!!, needsReview = f[18]!!.toBoolean(),
            rawText = f[19]!!, timestamp = f[20]!!.toLong(), fingerprint = f[21]!!, evidenceCount = f[22]!!.toInt(),
            explanation = f.drop(23).map { it ?: "" },
        )
    }

    // ---- Coach cloud log ----

    fun encodeLog(entries: List<CloudLogEntry>): String = buildString {
        appendLine(line(VERSION))
        entries.forEach { appendLine(line("L", "${it.time}", it.route, it.model, it.payload, "${it.latencyMs}", it.status)) }
    }

    fun decodeLog(text: String): List<CloudLogEntry> = text.lineSequence().filter { it.isNotEmpty() }.mapNotNull { raw ->
        runCatching {
            val f = fields(raw)
            if (f[0] != "L") null else CloudLogEntry(f[1]!!.toLong(), f[2]!!, f[3]!!, f[4]!!, f[5]!!.toLong(), f[6]!!)
        }.getOrNull()
    }.toList()
}
