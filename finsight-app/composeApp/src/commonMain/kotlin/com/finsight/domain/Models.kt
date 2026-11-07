package com.finsight.domain

/** Money is always Long in the smallest unit (paisa) + ISO currency. Never Double. */
data class Money(val minor: Long, val currency: String = "PKR") {
    val major: Double get() = minor / 100.0
    override fun toString(): String = formatPkr(minor, currency)
}

fun formatPkr(minor: Long, currency: String = "PKR"): String {
    val whole = kotlin.math.abs(minor) / 100
    val s = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (minor < 0) "-" else "") + "$currency $s"
}

enum class Channel { NOTIFICATION, FILE, SHARE, MANUAL, DEMO }
enum class TxnType { DEBIT, CREDIT }

/** Financial meaning after the Integrity Layer (plan 6.2). */
enum class TxnKind { EXPENSE, INCOME, REFUND, TRANSFER_OUT, TRANSFER_IN, FEE, REVERSAL }
enum class TxnStatus { POSTED, PENDING, REVERSED }

enum class ParseSource { REGEX, LLM, MERGED, USER }
enum class CategorySource { USER_MEMORY, RULE, LLM, HYBRID, USER, NONE }

enum class Category(val label: String, val essential: Boolean) {
    FOOD("Food", true), GROCERIES("Groceries", true), TRANSPORT("Transport", true),
    BILLS("Bills & Utilities", true), MOBILE("Mobile & Internet", true), HEALTH("Health", true),
    HOUSING("Housing", true), EDUCATION("Education", true), SHOPPING("Shopping", false),
    ENTERTAINMENT("Entertainment", false), TRANSFER("Transfer", false), INCOME("Income", false),
    FINANCIAL("Fees & Financial", false), COMMITTEE("Committee", false), OTHER("Other", false),
}

/** Every input channel produces this. */
data class RawInput(
    val channel: Channel,
    val sourceApp: String,
    val sender: String?,
    val text: String,
    val receivedAt: Long,
)

data class Transaction(
    val id: Long,
    val amount: Money,
    val type: TxnType,
    val kind: TxnKind,
    val status: TxnStatus,
    val merchant: String?,
    val merchantKey: String?,
    val accountLast4: String?,
    val balance: Money?,
    val category: Category,
    val confidence: Double,
    val categorySource: CategorySource,
    val parseSource: ParseSource,
    val channel: Channel,
    val sourceApp: String,
    val needsReview: Boolean,
    val rawText: String,
    val timestamp: Long,
    val fingerprint: String,
    val evidenceCount: Int = 1,
    val explanation: List<String> = emptyList(),
    /** [Account.id] this record belongs to (null = unknown / not yet resolved). */
    val accountId: String? = null,
    /** Debit between two of the user's own accounts: a transfer, never spending. */
    val ownTransfer: Boolean = false,
) {
    val band: Band get() = when {
        confidence >= 0.90 -> Band.GREEN
        confidence >= 0.75 -> Band.YELLOW
        else -> Band.RED
    }
    /** Counts toward spending analytics (refunds, own transfers, reversals excluded). */
    val isSpend: Boolean get() = type == TxnType.DEBIT && status != TxnStatus.REVERSED && !ownTransfer &&
        (kind == TxnKind.EXPENSE || kind == TxnKind.FEE || kind == TxnKind.TRANSFER_OUT)
    val isIncome: Boolean get() = type == TxnType.CREDIT && kind == TxnKind.INCOME && status != TxnStatus.REVERSED
}

enum class Band { GREEN, YELLOW, RED }

enum class AccountType(val label: String) { BANK("Bank"), WALLET("Wallet"), CARD("Card"), CASH("Cash") }

/** A bank account, wallet, card or cash pocket that transactions land in (old FinSight `accounts` table). */
data class Account(
    val id: String,
    val institution: String,
    val type: AccountType,
    val last4: String?,
    val nickname: String?,
    val isMine: Boolean = true,
    val balanceMinor: Long?,
    val balanceAt: Long?,
    val currency: String = "PKR",
    val createdAt: Long,
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() }
        ?: (if (type == AccountType.CASH) institution else "$institution ${type.label}" + (last4?.let { " ••$it" } ?: ""))
}
