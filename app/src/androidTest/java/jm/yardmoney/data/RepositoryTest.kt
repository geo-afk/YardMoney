package jm.yardmoney.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import jm.yardmoney.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test
    fun unknownPayAndEmptyOpeningBalanceAreAllowed() = runBlocking {
        db.clearAllTables()
        repo.onboard(
            Profile(
                name = "",
                typicalNetMinor = 0,
                frequency = "IRREGULAR",
                nextPayday = repo.today.plusDays(14).toString(),
                anchorDay = 15,
                secondDay = 30,
                needsBp = 5000,
                wantsBp = 3000,
                savingsBp = 2000,
                periodStart = repo.today.toString(),
                budgetIncomeMinor = 0,
            ),
            0,
        )
        val snapshot = repo.snapshot.first()
        assertEquals(0L, snapshot.ledger.accounts.single().balanceMinor)
        assertTrue(snapshot.ledger.transactions.isEmpty())
    }

    @Test
    fun textOnlyScansPersistAndCanBeParsedAgainWithoutAPhoto() = runBlocking {
        // Existing nullable imageRef and rawText columns cover new scans without a schema fork.
        val raw = "MARKET\n2026-10-04\nRICE 12.00\nTOTAL 12.00"
        val id = repo.saveScannedDraft(raw, "text-only-scan")
        val stored = requireNotNull(repo.dao.draft(id))
        assertNull(stored.imageRef)
        assertEquals(raw, stored.rawText.substringBefore("\n\n[Review edits]"))
        assertTrue(stored.rawText.contains("[Review edits]"))
        val parsed = ReceiptDraftCodec.parse(stored.rawText)
        assertEquals(1200L, parsed.totalMinor)
        assertEquals("RICE", parsed.lines.single().name)
    }

    @Test
    fun olderScansAndUnknownFieldsRemainReadableWithoutSchemaChanges() = runBlocking {
        val raw = "MARKET\nRICE 12.00"
        val oldId = repo.saveDraft(raw, null, "legacy-draft")
        val newId = repo.saveScannedDraft(raw, "parsed-draft")
        val legacy = ReceiptDraftCodec.parse(requireNotNull(repo.dao.draft(oldId)).rawText)
        val parsed = ReceiptDraftCodec.parse(requireNotNull(repo.dao.draft(newId)).rawText)
        assertEquals(legacy.merchant, parsed.merchant)
        assertEquals(legacy.lines, parsed.lines)
        assertNull(parsed.date)
        assertNull(parsed.totalMinor)
        assertNull(parsed.subtotalMinor)
    }

    private lateinit var db: YardDatabase
    private lateinit var repo: FinanceRepository
    private lateinit var cash: String
    private val today = LocalDate.of(2026, 10, 2)

    @Before
    fun setup() = runBlocking {
        System.loadLibrary("sqlcipher")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db =
            Room.inMemoryDatabaseBuilder(context, YardDatabase::class.java)
                .openHelperFactory(SupportOpenHelperFactory(ByteArray(32) { (it + 1).toByte() }))
                .build()
        repo = FinanceRepository(db)
        repo.onboard(
            Profile(
                name = "Test",
                typicalNetMinor = 100000,
                frequency = "FORTNIGHTLY",
                nextPayday = repo.today.plusDays(14).toString(),
                anchorDay = 15,
                secondDay = 30,
                needsBp = 5000,
                wantsBp = 3000,
                savingsBp = 2000,
                periodStart = repo.today.toString(),
                budgetIncomeMinor = 0,
            ),
            100000,
        )
        cash = repo.snapshot.first().ledger.accounts.single().account.id
    }

    @After
    fun cleanup() {
        db.close()
    }

    private fun expense(key: String = "expense", amount: Long = 10000, commit: String? = null) =
        TransactionInput(
            key,
            "EXPENSE",
            amount,
            today,
            "Food",
            "Groceries",
            "NEEDS",
            cash,
            commitmentId = commit,
        )

    private suspend fun fails(block: suspend () -> Unit) {
        var threw = false
        try {
            block()
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Expected invariant rejection", threw)
    }

    @Test
    fun retryPostsOnce() = runBlocking {
        val a = repo.post(expense())
        assertEquals(a, repo.post(expense()))
        assertEquals(90000L, repo.snapshot.first().ledger.accounts.single().balanceMinor)
        assertEquals(1, repo.snapshot.first().ledger.transactions.size)
    }

    @Test
    fun invalidSplitRollsBackEverything() = runBlocking {
        fails { repo.post(expense().copy(splits = listOf("Food" to 9000))) }
        val state = repo.snapshot.first()
        assertTrue(state.ledger.transactions.isEmpty())
        assertEquals(100000L, state.ledger.accounts.single().balanceMinor)
    }

    @Test
    fun partialBillPaymentMovesAndReleasesOnce() = runBlocking {
        repo.addCommitment("Bill", "BILL", 20000, repo.today)
        val c = repo.snapshot.first().ledger.commitments.single().commitment
        repo.post(expense(commit = c.id))
        val state = repo.snapshot.first()
        assertEquals(90000L, state.ledger.accounts.single().balanceMinor)
        assertEquals(10000L, state.ledger.commitments.single().remainingMinor)
        val result =
            BudgetEngine.safeToSpend(
                90000,
                listOf(Reserve(c.id, 10000, repo.today)),
                repo.today,
                repo.today.plusDays(14),
            )
        assertEquals(80000L, result.safeMinor)
    }

    @Test
    fun excessBillPaymentIsAtomic() = runBlocking {
        repo.addCommitment("Bill", "BILL", 1000, repo.today)
        val c = repo.snapshot.first().ledger.commitments.single().commitment
        fails { repo.post(expense(commit = c.id)) }
        assertEquals(100000L, repo.snapshot.first().ledger.accounts.single().balanceMinor)
    }

    @Test
    fun savingsMovesToProtectedAccountWithoutExpense() = runBlocking {
        repo.addAccount("Savings", "SAVINGS", 0, false)
        val target = repo.snapshot.first().ledger.accounts.first { !it.account.included }.account.id
        repo.addGoal("Goal", 50000, 0)
        val goal = repo.snapshot.first().ledger.goals.single().goal.id
        repo.post(
            TransactionInput(
                "save",
                "TRANSFER",
                10000,
                today,
                "Save",
                "Savings",
                "SAVINGS",
                cash,
                target,
                goalId = goal,
            )
        )
        val state = repo.snapshot.first()
        assertEquals(100000L, Money.sum(state.ledger.accounts.map { it.balanceMinor }))
        assertEquals(10000L, state.ledger.goals.single().savedMinor)
        assertTrue(state.splits.isEmpty())
    }

    @Test
    fun savingsCannotReleaseIntoSpendableAccount() = runBlocking {
        repo.addAccount("Bank", "CURRENT", 0, true)
        val target =
            repo.snapshot.first().ledger.accounts.first { it.account.id != cash }.account.id
        repo.addCommitment("Save", "SAVINGS", 10000, null)
        val commit = repo.snapshot.first().ledger.commitments.single().commitment.id
        fails {
            repo.post(
                TransactionInput(
                    "save",
                    "TRANSFER",
                    10000,
                    today,
                    "Save",
                    "Savings",
                    "SAVINGS",
                    cash,
                    target,
                    commitmentId = commit,
                )
            )
        }
        assertTrue(repo.snapshot.first().ledger.transactions.isEmpty())
    }

    @Test
    fun cumulativeRefundCannotExceedOriginal() = runBlocking {
        val original = repo.post(expense())
        repo.post(
            TransactionInput(
                "refund1",
                "REFUND",
                6000,
                today,
                "Refund",
                "Groceries",
                "NEEDS",
                cash,
                refundOfId = original,
            )
        )
        fails {
            repo.post(
                TransactionInput(
                    "refund2",
                    "REFUND",
                    5000,
                    today,
                    "Refund",
                    "Groceries",
                    "NEEDS",
                    cash,
                    refundOfId = original,
                )
            )
        }
        assertEquals(96000L, repo.snapshot.first().ledger.accounts.single().balanceMinor)
    }

    @Test
    fun receiptRequiresReviewAndExactTotal() = runBlocking {
        val draft = repo.saveDraft("STORE\nRICE 100.00\nTOTAL 100.00", null, "sample")
        val r =
            ConfirmedReceipt(
                draft,
                "Store",
                "Branch",
                "Kingston",
                today,
                10000,
                0,
                listOf(ConfirmedItem("Rice", "Rice", "1", 9000, "1", "kg", false)),
                cash,
                "receipt",
                false,
            )
        fails { repo.confirmReceipt(r) }
        fails { repo.confirmReceipt(r.copy(items = listOf(r.items[0].copy(verified = true)))) }
        assertEquals(1, repo.snapshot.first().receipt.drafts.size)
        assertTrue(repo.snapshot.first().ledger.transactions.isEmpty())
    }

    @Test
    fun reviewedReceiptCreatesOneExpenseAndDatedPrice() = runBlocking {
        val draft = repo.saveDraft("STORE", null, "sample")
        val r =
            ConfirmedReceipt(
                draft,
                "Store",
                "Branch",
                "Kingston",
                today,
                10000,
                0,
                listOf(ConfirmedItem("Rice", "Rice", "2", 10000, "0.5", "kg", true)),
                cash,
                "receipt",
                false,
            )
        val id = repo.confirmReceipt(r)
        assertEquals(id, repo.confirmReceipt(r))
        val state = repo.snapshot.first()
        assertEquals(1, state.ledger.transactions.size)
        assertEquals(5000L, state.receipt.prices.single().packPriceMinor)
        assertEquals(10000L, state.receipt.prices.single().unitPriceMinor)
        assertEquals("Branch", state.receipt.prices.single().branch)
        assertTrue(state.receipt.drafts.isEmpty())
    }

    @Test
    fun merchantCaseChangeStillRequiresDuplicateAcknowledgement() = runBlocking {
        val firstDraft = repo.saveDraft("STORE", null, "first-image")
        val first =
            ConfirmedReceipt(
                firstDraft,
                "Café Store",
                "Branch",
                "Kingston",
                today,
                10000,
                0,
                emptyList(),
                cash,
                "first-receipt",
                true,
            )
        repo.confirmReceipt(first)
        val secondDraft = repo.saveDraft("STORE", null, "second-crop")
        val second =
            first.copy(
                draftId = secondDraft,
                merchant = "CAFÉ STORE",
                submissionKey = "second-receipt",
            )
        fails { repo.confirmReceipt(second) }
        assertEquals(1, repo.snapshot.first().ledger.transactions.size)
        assertEquals(1, repo.snapshot.first().receipt.drafts.size)
        val acknowledged = second.copy(allowDuplicate = true)
        val id = repo.confirmReceipt(acknowledged)
        assertEquals(id, repo.confirmReceipt(acknowledged))
        assertEquals(2, repo.snapshot.first().ledger.transactions.size)
    }

    @Test
    fun reservationRetriesWithSameSubmissionKeySaveOnlyOnce() = runBlocking {
        repeat(2) {
            repo.addCommitment("Power", "BILL", 10000, repo.today, submissionKey = "one-bill")
        }
        assertEquals(1, repo.snapshot.first().ledger.commitments.size)
        assertEquals("one-bill", repo.snapshot.first().ledger.commitments.single().commitment.id)
    }

    @Test
    fun recurringReservationRetriesDoNotCreateASecondSeries() = runBlocking {
        repeat(2) {
            repo.addCommitment(
                "Rent",
                "BILL",
                10000,
                repo.today,
                frequency = "MONTHLY",
                submissionKey = "recurring-bill",
            )
        }
        assertEquals(1, repo.dao.templates().size)
        val first = repo.snapshot.first().ledger.commitments
        assertTrue(first.size >= 2)
        repo.materializeBills()
        assertEquals(
            first.map { it.commitment.id },
            repo.snapshot.first().ledger.commitments.map { it.commitment.id },
        )
    }

    @Test
    fun recurrenceMaterializationIsIdempotent() = runBlocking {
        repo.addCommitment("Rent", "BILL", 10000, repo.today, frequency = "MONTHLY")
        val first = repo.snapshot.first().ledger.commitments.size
        repo.materializeBills()
        assertTrue(first >= 2)
        assertEquals(first, repo.snapshot.first().ledger.commitments.size)
    }

    @Test
    fun reviewEditsPersistWithoutPostingMoneyAndCanBeReplaced() = runBlocking {
        val id = repo.saveDraft("STORE\nSOAP 100.00\nTOTAL 100.00", null, "draft-photo")
        repo.saveReviewDraft(id, "{\"version\":1,\"merchant\":\"Corrected Store\"}")
        repo.saveReviewDraft(id, "{\"version\":1,\"merchant\":\"Final Store\"}")
        val raw = repo.dao.draft(id)!!.rawText
        assertTrue(raw.contains("Final Store"))
        assertFalse(raw.contains("Corrected Store"))
        assertEquals(1, Regex("\\[Review edits]").findAll(raw).count())
        assertTrue(repo.snapshot.first().ledger.transactions.isEmpty())
        assertEquals(1, ReceiptParser.parse(raw).lines.size)
    }

    @Test
    fun totalOnlyConfirmationRetainsReviewedDisplayItemsWithoutCreatingPrices() = runBlocking {
        val draft = repo.saveScannedDraft("STORE\n1 Rice 100.00\nTOTAL 100.00", "display-only")
        repo.saveReviewDraft(
            draft,
            """{"version":1,"merchant":"Store","date":"$today","total":"100.00","items":[{"raw":"1 Rice 100.00","name":"Brown rice","quantity":"1","quantitySpecified":true,"total":"100.00"}]}""",
        )
        repo.confirmReceipt(
            ConfirmedReceipt(
                draft,
                "Store",
                "",
                "",
                today,
                10000,
                0,
                emptyList(),
                cash,
                "display-only-save",
                true,
                metadata = mapOf("Receipt number" to "ABC"),
            )
        )
        val state = repo.snapshot.first()
        assertEquals(
            "Brown rice",
            ReceiptDraftCodec.parse(state.receipt.receipts.single().rawText).lines.single().name,
        )
        assertEquals(10000L, state.ledger.transactions.single().amountMinor)
        assertTrue(state.receiptItems.isEmpty())
        assertTrue(state.receipt.prices.isEmpty())
    }

    @Test
    fun receiptMetadataPersistsWithoutChangingSchemaOrLedgerTotal() = runBlocking {
        val draft = repo.saveDraft("STORE", null, "metadata-image")
        val r =
            ConfirmedReceipt(
                draft,
                "Store",
                "Branch",
                "Kingston",
                today,
                10000,
                0,
                emptyList(),
                cash,
                "metadata-receipt",
                true,
                metadata = mapOf("Time" to "14:32", "Receipt number" to "AB123"),
            )
        repo.confirmReceipt(r)
        val state = repo.snapshot.first()
        assertEquals(10000L, state.ledger.transactions.single().amountMinor)
        assertTrue(state.receipt.receipts.single().rawText.contains("Receipt number: AB123"))
    }

    @Test
    fun linkedSavingsWithdrawalReducesGoalAndCannotOverdrawIt() = runBlocking {
        repo.addAccount("Savings", "SAVINGS", 0, false)
        val target = repo.snapshot.first().ledger.accounts.first { !it.account.included }.account.id
        repo.addGoal("Goal", 50000, 0)
        val goal = repo.snapshot.first().ledger.goals.single().goal.id
        val deposit =
            repo.post(
                TransactionInput(
                    "save",
                    "TRANSFER",
                    10000,
                    repo.today,
                    "Save",
                    "Savings",
                    "SAVINGS",
                    cash,
                    target,
                    goalId = goal,
                )
            )
        repo.post(
            TransactionInput(
                "withdraw",
                "TRANSFER",
                4000,
                repo.today,
                "Withdraw",
                "Savings",
                "SAVINGS",
                target,
                cash,
                goalId = goal,
            )
        )
        assertEquals(6000L, repo.snapshot.first().ledger.goals.single().savedMinor)
        fails {
            repo.post(
                TransactionInput(
                    "overdraw",
                    "TRANSFER",
                    7000,
                    repo.today,
                    "Withdraw",
                    "Savings",
                    "SAVINGS",
                    target,
                    cash,
                    goalId = goal,
                )
            )
        }
        fails { repo.deleteTransaction(deposit) }
        assertEquals(6000L, repo.snapshot.first().ledger.goals.single().savedMinor)
    }
}
