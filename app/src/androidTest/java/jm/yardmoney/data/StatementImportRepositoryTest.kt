package jm.yardmoney.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import jm.yardmoney.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.*
import org.junit.Assert.*

class StatementImportRepositoryTest {
    private lateinit var db: YardDatabase
    private lateinit var repo: FinanceRepository
    private lateinit var cash: String
    @Before fun setup() = runBlocking {
        System.loadLibrary("sqlcipher")
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, YardDatabase::class.java)
            .openHelperFactory(SupportOpenHelperFactory(ByteArray(32) { (it + 1).toByte() })).build()
        repo = FinanceRepository(db)
        val today = repo.today
        repo.onboard(Profile(name = "Rule test", typicalNetMinor = 0, frequency = "MONTHLY",
            nextPayday = today.plusDays(14).toString(), anchorDay = 20, secondDay = 28,
            needsBp = 5000, wantsBp = 3000, savingsBp = 2000,
            periodStart = today.toString(), budgetIncomeMinor = 0), 1000000)
        cash = repo.dao.readAccounts().single().account.id
    }
    @After fun close() { db.close() }

    private val mapping = StatementMapping(0, 1, 2, dateFormat = "yyyy-MM-dd")
    private fun document() = StatementCsv.read(java.io.StringReader("date,description,amount\n${repo.today},Taxi,-600\n${repo.today},Salary,1000"))
    @Test fun atomicImportUsesRulesIsIdempotentAndCanBeUndone() = runBlocking {
        repo.saveCategoryRule(CategoryRule("taxi", "Taxi", "EXACT", "Transport", "NEEDS", cash, 1))
        val d = document(); val rows = StatementCsv.preview(d, mapping, cash, emptyList(), repo.today)
        val batch = StatementCsv.batchId(cash, mapping, d)
        assertEquals(2, repo.importStatement(batch, cash, mapping, rows))
        assertEquals(0, repo.importStatement(batch, cash, mapping, rows, allowDuplicates = true))
        assertEquals(2, repo.dao.readTransactions().size)
        assertEquals(1040000L, repo.dao.accountBalance(cash))
        assertEquals("Transport", repo.dao.readTransactions().single { it.description == "Taxi" }.category)
        assertEquals(60000L, repo.dao.readSplits().single().amountMinor)
        val expense = repo.dao.readTransactions().single { it.kind == "EXPENSE" }
        assertEquals(-60000L, repo.dao.readEntries().single { it.transactionId == expense.id }.signedMinor)
        assertEquals(mapping, repo.dao.readImportMappings().single().mapping())
        repo.undoStatementImport(batch)
        assertTrue(repo.dao.readTransactions().isEmpty()); assertTrue(repo.dao.readImportBatches().isEmpty())
        assertEquals(1000000L, repo.dao.accountBalance(cash))
        db.clearAllTables(); assertTrue(repo.dao.readImportMappings().isEmpty())
    }
    @Test fun aRejectedRowRollsBackTheWholeBatchAndMapping() = runBlocking {
        val d = document(); val rows = StatementCsv.preview(d, mapping, cash, emptyList(), repo.today)
        val invalid = rows[1].copy(date = repo.today.plusDays(1))
        assertTrue(runCatching { repo.importStatement(StatementCsv.batchId(cash,mapping,d), cash, mapping, listOf(rows[0],invalid)) }.isFailure)
        assertTrue(repo.dao.readTransactions().isEmpty()); assertTrue(repo.dao.readImportBatches().isEmpty())
        assertTrue(repo.dao.readImportMappings().isEmpty()); assertEquals(1000000L, repo.dao.accountBalance(cash))
    }
    @Test fun duplicateSkippingIsRecheckedAtCommitAndUndoRejectsLinkedRefunds() = runBlocking {
        val d = document(); val rows = StatementCsv.preview(d, mapping, cash, emptyList(), repo.today)
        repo.post(TransactionInput("manual-taxi", "EXPENSE", 60000, repo.today, "Taxi", "Transport", "NEEDS", cash))
        val batch = StatementCsv.batchId(cash,mapping,d)
        assertEquals(1, repo.importStatement(batch,cash,mapping,rows))
        repo.undoStatementImport(batch)
        assertEquals(1, repo.dao.readTransactions().size)
        assertEquals(2, repo.importStatement(batch,cash,mapping,rows,true))
        val expense = repo.dao.importRecords(batch).mapNotNull { repo.dao.transaction(it.transactionId) }.single { it.kind=="EXPENSE" }
        repo.post(TransactionInput("refund", "REFUND", 10000, repo.today, "Refund", expense.category, expense.bucket, cash, refundOfId=expense.id))
        assertTrue(runCatching { repo.undoStatementImport(batch) }.isFailure)
        assertNotNull(repo.dao.importBatch(batch)); assertEquals(4, repo.dao.readTransactions().size)
    }
    @Test fun mappingInvalidationDoesNotReloadLedger() = runBlocking {
        val changes = Channel<FinanceSnapshot>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { repo.snapshot.collect { changes.send(it) } }
        try {
            val before = withTimeout(10000) { changes.receive() }
            repo.dao.put(mapping.persisted(cash))
            val after = withTimeout(10000) { var next=changes.receive(); while(next.imports.mappings.isEmpty()) next=changes.receive(); next }
            assertSame(before.ledger, after.ledger)
        } finally { job.cancelAndJoin(); changes.close() }
    }
}
