package jm.yardmoney.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class LargeLedgerTest {
    @Test
    fun thousandsOfTransactionsKeepExactBalancesAndConsistentSnapshots() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "large-ledger-${System.nanoTime()}.db"
        val db = Room.databaseBuilder(context, YardDatabase::class.java, name).build()
        try {
            val repo = FinanceRepository(db)
            db.withTransaction {
                repo.dao.insert(Account("cash", "Cash", "CASH", 10000000L, true))
                repeat(10000) { i ->
                    val id = "transaction-$i"
                    repo.dao.insert(
                        MoneyTransaction(
                            id,
                            "submission-$i",
                            "EXPENSE",
                            101L,
                            "2026-10-01",
                            "Food 🍚 $i",
                            "Groceries",
                            "NEEDS",
                        )
                    )
                    repo.dao.insertEntries(listOf(AccountEntry("entry-$i", id, "cash", -101L)))
                    repo.dao.insertSplits(
                        listOf(TransactionSplit("split-$i", id, "Groceries", "NEEDS", 101L))
                    )
                }
            }
            val snapshot = withTimeout(10000) { repo.snapshot.first() }
            assertEquals(10000, snapshot.ledger.transactions.size)
            assertEquals(10000, snapshot.splits.size)
            assertEquals(8990000L, snapshot.ledger.accounts.single().balanceMinor)
            assertEquals(1010000L, snapshot.splits.sumOf { it.amountMinor })
            db.openHelper.readableDatabase.query("PRAGMA integrity_check").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("ok", c.getString(0))
            }
            db.close()
            val reopened = Room.databaseBuilder(context, YardDatabase::class.java, name).build()
            try {
                assertEquals(8990000L, reopened.finance().accountBalance("cash"))
            } finally {
                reopened.close()
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun cancelledTransactionRollsBackAndConcurrentSubmissionIsIdempotent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, YardDatabase::class.java).build()
        try {
            val repo = FinanceRepository(db)
            repo.dao.insert(Account("cash", "Cash", "CASH", 10000, true))
            assertTrue(
                runCatching {
                    db.withTransaction {
                        repo.dao.insert(Account("interrupted", "Interrupted", "CASH", 100, true))
                        throw CancellationException("Simulated process/save interruption")
                    }
                }
                    .isFailure
            )
            assertNull(repo.dao.account("interrupted"))
            val input =
                TransactionInput(
                    "stable",
                    "EXPENSE",
                    100,
                    repo.today,
                    "Bread",
                    "Groceries",
                    "NEEDS",
                    "cash",
                )
            val ids =
                (0 until 20).map { async(Dispatchers.Default) { repo.post(input) } }.awaitAll()
            assertEquals(1, ids.distinct().size)
            assertEquals(1, repo.dao.readTransactions().size)
            assertEquals(9900L, repo.dao.accountBalance("cash"))
        } finally {
            db.close()
        }
    }
}
