package com.finsight

import com.finsight.coach.Tools
import com.finsight.data.AccountResolver
import com.finsight.data.Codec
import com.finsight.data.InMemoryStore
import com.finsight.data.RepoSnapshot
import com.finsight.data.Repository
import com.finsight.domain.*
import com.finsight.pipeline.PipelineResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountsTest {
    private val now = 1_790_000_000_000L
    private fun sms(app: String, sender: String?, text: String, minsAgo: Long) = RawInput(Channel.DEMO, app, sender, text, now - minsAgo * 60_000L)

    private val jazz = sms("JazzCash", "8558", "Rs 2,500.00 sent to ALI RAZA Account: 03001234567 on 05-Nov-26. Your JazzCash balance is Rs 12,340.50. TID: 041122334455", 300)
    private val card = sms("HBL", "HBL", "Rs 3,450.00 spent at Foodpanda using card **7710 on 06-Nov-26. Avail Bal: Rs 61,750.00", 200)
    private val acOld = sms("HBL", "HBL", "Rs 8,450.00 paid to K-Electric bill via HBL Mobile from A/C **4821. Avl Bal: Rs 120,000.00 Ref: KE88231190", 150)
    private val acNew = sms("HBL", "HBL", "Rs 1,000.00 paid to PTCL bill via HBL Mobile from A/C **4821. Avl Bal: Rs 119,000.00 Ref: PT55511122", 100)

    private fun accepted(r: PipelineResult) = (r as PipelineResult.Accepted).txn

    private fun threeAccounts(store: InMemoryStore? = null): Repository {
        val repo = Repository(store)
        // Newer balance first, older after: the latest-by-timestamp must win.
        repo.importBatch(listOf(jazz, card, acNew, acOld))
        return repo
    }

    @Test fun walletCardAndBankAccountResolved() {
        val repo = threeAccounts()
        val accts = repo.accounts.value
        assertEquals(3, accts.size, accts.toString())
        val wallet = accts.single { it.type == AccountType.WALLET }
        assertEquals("JazzCash", wallet.institution)
        assertEquals(1_234_050L, wallet.balanceMinor)
        val hblCard = accts.single { it.type == AccountType.CARD }
        assertEquals("HBL" to "7710", hblCard.institution to hblCard.last4)
        assertEquals(6_175_000L, hblCard.balanceMinor)
        val hblAc = accts.single { it.type == AccountType.BANK }
        assertEquals("HBL" to "4821", hblAc.institution to hblAc.last4)
        assertEquals(11_900_000L, hblAc.balanceMinor)
        assertEquals(now - 100 * 60_000L, hblAc.balanceAt)
        // Every record is tagged with its account.
        assertTrue(repo.transactions.value.all { it.accountId != null })
        assertEquals(2, repo.transactionCounts()[hblAc.id])
        // Total balance = sum of "Mine" accounts.
        val tools = Tools({ repo.transactions.value }, { now }, { repo.accounts.value })
        assertEquals(1_234_050L + 6_175_000L + 11_900_000L, tools.latestBalance())
        repo.setMine(wallet.id, false)
        assertEquals(6_175_000L + 11_900_000L, tools.latestBalance())
        // Plain debit from an own account is still an expense, not a self transfer.
        val bill = repo.transactions.value.first { it.rawText.contains("PTCL") }
        assertFalse(bill.ownTransfer)
        assertTrue(bill.isSpend)
    }

    @Test fun renameMergeDeleteAndCash() {
        val repo = threeAccounts()
        val ac = repo.accounts.value.single { it.type == AccountType.BANK }
        val cardAcct = repo.accounts.value.single { it.type == AccountType.CARD }
        repo.renameAccount(ac.id, "Salary account")
        assertEquals("Salary account", repo.account(ac.id)!!.displayName)

        repo.mergeAccounts(cardAcct.id, ac.id)
        assertEquals(2, repo.accounts.value.size)
        assertNull(repo.account(cardAcct.id))
        assertEquals(3, repo.transactionCounts()[ac.id])
        assertEquals(11_900_000L, repo.account(ac.id)!!.balanceMinor) // newer balance kept

        val before = repo.transactions.value.size
        repo.deleteAccount(ac.id)
        assertEquals(before, repo.transactions.value.size)
        assertEquals(1, repo.accounts.value.size)
        assertTrue(repo.transactions.value.none { it.accountId == ac.id })

        val cash = repo.addCashAccount(now)
        assertEquals(AccountType.CASH, cash.type)
        // Quick add "chai 150 cash" lands in the cash wallet.
        val t = accepted(repo.ingest(RawInput(Channel.MANUAL, "You", null, "chai 150 cash", now)))
        assertEquals(cash.id, repo.transactions.value.first { it.id == t.id }.accountId)
    }

    @Test fun selfTransferBetweenOwnAccountsIsNotSpend() {
        val repo = threeAccounts()
        repo.ingest(sms("Meezan", "Meezan", "PKR 1,000.00 debited from your A/C **5566 for POS purchase at Imtiaz. Bal: PKR 40,000.00", 90))
        val meezan = repo.accounts.value.single { it.institution == "Meezan Bank" }
        assertEquals("5566", meezan.last4)
        assertTrue("5566" in repo.ownLast4())

        val t = accepted(repo.ingest(sms("HBL", "HBL", "PKR 50,000.00 debited from your A/C **4821 and transferred to A/C ****5566 via IBFT. Avl Bal: PKR 69,000.00. Ref: IBFT12345678", 80)))
        assertEquals(TxnKind.TRANSFER_OUT, t.kind)
        assertTrue(t.ownTransfer)
        assertFalse(t.isSpend)
        val hblAc = repo.accounts.value.single { it.institution == "HBL" && it.type == AccountType.BANK }
        assertEquals(hblAc.id, repo.transactions.value.first { it.id == t.id }.accountId)
        assertEquals(6_900_000L, hblAc.balanceMinor)

        // Not mine any more → ordinary outgoing transfer (counts as spend).
        repo.setMine(meezan.id, false)
        val t2 = accepted(repo.ingest(sms("HBL", "HBL", "PKR 5,000.00 debited from your A/C **4821 and transferred to A/C ****5566 via IBFT. Avl Bal: PKR 64,000.00. Ref: IBFT99887766", 70)))
        assertEquals(TxnKind.TRANSFER_OUT, t2.kind)
        assertFalse(t2.ownTransfer)
        assertTrue(t2.isSpend)
    }

    @Test fun institutionFallback() {
        assertEquals("JazzCash", AccountResolver.institutionGuess("8558", "", "anything")?.name)
        assertEquals("Easypaisa", AccountResolver.institutionGuess("3737", "", "anything")?.name)
        assertEquals("UBL", AccountResolver.institutionGuess(null, "share", "UBL: Rs 500 debited from A/C **1111")?.name)
        assertEquals(AccountType.WALLET, AccountResolver.institutionGuess(null, "pk.com.telenor.phoenix", "x")?.type)
        assertNull(AccountResolver.institutionGuess(null, "share", "Rs 500 spent using card **1234"))
    }

    @Test fun accountsPersistAndOldLinesDecode() {
        val store = InMemoryStore()
        val repo = threeAccounts(store)
        repo.renameAccount(repo.accounts.value.first().id, "Pocket\twallet")
        val restored = Repository(store)
        assertEquals(repo.accounts.value, restored.accounts.value)
        assertEquals(repo.transactions.value, restored.transactions.value)

        // A line saved before accounts existed (no #v2 marker) still decodes with its explanation intact.
        val t = repo.transactions.value.first().copy(accountId = null, ownTransfer = false, explanation = listOf("first", "second"))
        val newText = Codec.encodeRepo(RepoSnapshot(listOf(t), emptyMap(), emptyList(), null))
        val oldText = newText.replace("\t#v2\t\\0\tfalse", "")
        assertTrue(oldText != newText)
        val back = Codec.decodeRepo(oldText).transactions.single()
        assertEquals(t, back)
        assertEquals(listOf("first", "second"), back.explanation)
        val withAcct = t.copy(accountId = "hbl-bank-4821", ownTransfer = true)
        assertEquals(withAcct, Codec.decodeRepo(Codec.encodeRepo(RepoSnapshot(listOf(withAcct), emptyMap(), emptyList(), null))).transactions.single())
        assertNotNull(Codec.decodeRepo(newText).accounts)
    }
}
