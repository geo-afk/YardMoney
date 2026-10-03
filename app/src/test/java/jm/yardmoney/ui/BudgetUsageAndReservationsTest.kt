package jm.yardmoney.ui

import java.time.LocalDate
import jm.yardmoney.data.*
import org.junit.Assert.*
import org.junit.Test

class BudgetUsageAndReservationsTest {
    private val today = LocalDate.of(2026, 10, 2)

    private fun tx(id: String, kind: String, amount: Long, date: String = "2026-10-02") =
        MoneyTransaction(id, id, kind, amount, date, id, "Food", "NEEDS")

    private fun snapshot(
        transactions: List<MoneyTransaction>,
        splits: List<TransactionSplit>,
        entries: List<AccountEntry> = emptyList(),
    ) =
        FinanceSnapshot(
            LedgerState(
                Profile(
                    name = "Test",
                    typicalNetMinor = 999999,
                    frequency = "MONTHLY",
                    nextPayday = "2026-11-01",
                    anchorDay = 1,
                    secondDay = 15,
                    needsBp = 5000,
                    wantsBp = 3000,
                    savingsBp = 2000,
                    periodStart = "2026-10-01",
                    budgetIncomeMinor = 999999,
                ),
                listOf(
                    AccountBalance(Account("cash", "Cash", "CASH", 999999, true), 999999),
                    AccountBalance(Account("saving", "Savings", "SAVINGS", 0, false), 0),
                ),
                transactions,
                emptyList(),
                emptyList(),
            ),
            ReceiptState(emptyList(), emptyList(), emptyList()),
            ShoppingState(emptyList(), emptyList()),
            splits,
            emptyList(),
            entries,
        )

    @Test
    fun expenseAndRefundUpdateUsedWhileIncomeControlsAllocation() {
        val rows =
            listOf(
                tx("income", "INCOME", 10000),
                tx("food", "EXPENSE", 1000),
                tx("refund", "REFUND", 250),
                tx("old", "INCOME", 20000, "2026-09-30"),
            )
        val split =
            listOf(
                TransactionSplit("a", "food", "Food", "NEEDS", 1000),
                TransactionSplit("b", "refund", "Food", "NEEDS", -250),
                TransactionSplit("c", "old", "Other", "NEEDS", 20000),
            )
        val usage = budgetUsage(snapshot(rows, split), today)
        assertEquals(listOf(5000L, 3000L, 2000L), usage.allocation)
        assertEquals(listOf(750L, 0L, 0L), usage.used)
        assertEquals(10000L, usage.income)
    }

    @Test
    fun noIncomeOverspendingAndRefundOnlyRemainVisible() {
        val usage =
            budgetUsage(
                snapshot(
                    listOf(tx("expense", "EXPENSE", 1000)),
                    listOf(TransactionSplit("a", "expense", "Food", "WANTS", 1000)),
                ),
                today,
            )
        assertEquals(listOf(0L, 0L, 0L), usage.allocation)
        assertEquals(1000L, usage.used[1])
        val refunded =
            budgetUsage(
                snapshot(
                    listOf(tx("refund", "REFUND", 250)),
                    listOf(TransactionSplit("a", "refund", "Food", "NEEDS", -250)),
                ),
                today,
            )
        assertEquals(-250L, refunded.used[0])
    }

    @Test
    fun savingsTransfersCountOnceAndWithdrawalsReduceFunding() {
        val rows =
            listOf(
                tx("pay", "INCOME", 10000),
                tx("in", "TRANSFER", 1500),
                tx("out", "TRANSFER", 400),
                tx("move", "TRANSFER", 800),
            )
        val entries =
            listOf(
                AccountEntry("a", "in", "cash", -1500),
                AccountEntry("b", "in", "saving", 1500),
                AccountEntry("c", "out", "saving", -400),
                AccountEntry("d", "out", "cash", 400),
                AccountEntry("e", "move", "cash", -800),
                AccountEntry("f", "move", "other", 800),
            )
        assertEquals(
            listOf(0L, 0L, 1100L),
            budgetUsage(snapshot(rows, emptyList(), entries), today).used,
        )
    }

    private fun reservation(id: String, key: String, date: String, paid: Long = 0) =
        CommitmentBalance(Commitment(id, key, "Rent", "BILL", 10000, date), paid)

    @Test
    fun recurringDatesGroupOnceAndUseEarliestUnpaidOccurrence() {
        val rows =
            listOf(
                reservation("a", "series@2026-10-01", "2026-10-01", 10000),
                reservation("b", "series@2026-11-01", "2026-11-01", 3000),
                reservation("c", "series@2026-12-01", "2026-12-01"),
            )
        val groups = reservationGroups(rows)
        assertEquals(1, groups.size)
        assertEquals("b", groups.single().primary.commitment.id)
        assertEquals(3, groups.single().occurrences.size)
        assertEquals(7000L, groups.single().primary.remainingMinor)
    }

    @Test
    fun sameNamesDoNotMergeUnrelatedBillsOrOneTimeReservations() {
        val rows =
            listOf(
                reservation("a", "series1@2026-10-01", "2026-10-01"),
                reservation("b", "series2@2026-10-01", "2026-10-01"),
                reservation("c", "one", "2026-10-01"),
                reservation("d", "two", "2026-10-01"),
            )
        assertEquals(4, reservationGroups(rows).size)
    }
}
