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

class CategoryRulesRepositoryTest {
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

    @Test fun rulesCanBeEditedDeletedAndErased() = runBlocking {
        val rule = CategoryRule("taxi", "taxi", "CONTAINS", "Transport", "NEEDS", cash, 1)
        repo.saveCategoryRule(rule)
        assertEquals("Transport", repo.categorySuggestion("Taxi ride", cash)?.category)
        repo.saveCategoryRule(rule.copy(category = "Travel"))
        assertEquals("Travel", repo.categorySuggestion("taxi", cash)?.category)
        repo.deleteCategoryRule(rule.id)
        assertNull(repo.categorySuggestion("taxi", cash))
        repo.saveCategoryRule(rule)
        db.clearAllTables()
        assertTrue(repo.dao.readCategoryRules().isEmpty())
    }
    @Test fun invalidAccountAndPatternCannotBeSaved() = runBlocking {
        val rule = CategoryRule("rule", "Store", "EXACT", "Groceries", "NEEDS", "missing", 1)
        assertTrue(runCatching { repo.saveCategoryRule(rule) }.isFailure)
        assertTrue(runCatching { repo.saveCategoryRule(rule.copy(pattern = "", accountId = cash)) }.isFailure)
        assertTrue(repo.dao.readCategoryRules().isEmpty())
    }
    @Test fun receiptConfirmationUsesTheMatchingRuleWithoutSavingAPhoto() = runBlocking {
        repo.saveCategoryRule(CategoryRule("store", "Store", "EXACT", "Dining", "WANTS", cash, 1))
        val draft = repo.saveScannedDraft("STORE\nTOTAL 600.00", "rules-receipt")
        val transaction = repo.confirmReceipt(ConfirmedReceipt(draft, "Store", "", "", repo.today,
            60000, 0, emptyList(), cash, "rule-confirmation", totalOnly = true))
        assertEquals("Dining", repo.dao.transaction(transaction)?.category)
        assertEquals("WANTS", repo.dao.transaction(transaction)?.bucket)
        assertEquals(940000L, repo.dao.accountBalance(cash))
        assertNull(repo.dao.readReceipts().single().imageRef)
    }
    @Test fun ruleInvalidationReusesTheLedgerSlice() = runBlocking {
        val changes = Channel<FinanceSnapshot>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { repo.snapshot.collect { changes.send(it) } }
        try {
            val before = withTimeout(10000) { changes.receive() }
            repo.saveCategoryRule(CategoryRule("taxi", "taxi", "EXACT", "Transport", "NEEDS", cash, 1))
            val after = withTimeout(10000) {
                var next = changes.receive()
                while (next.categoryRules.isEmpty()) next = changes.receive()
                next
            }
            assertSame(before.ledger, after.ledger)
        } finally { job.cancelAndJoin(); changes.close() }
    }
}
