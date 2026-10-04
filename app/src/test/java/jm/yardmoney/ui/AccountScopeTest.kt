package jm.yardmoney.ui

import java.time.LocalDate
import jm.yardmoney.data.*
import org.junit.Assert.*
import org.junit.Test

class AccountScopeTest {
    private fun fixture(): FinanceSnapshot {
        val profile =
            Profile(
                name = "Test",
                typicalNetMinor = 100000,
                frequency = "MONTHLY",
                nextPayday = "2026-10-30",
                anchorDay = 30,
                secondDay = 15,
                needsBp = 5000,
                wantsBp = 3000,
                savingsBp = 2000,
                periodStart = "2026-10-01",
                budgetIncomeMinor = 0,
            )
        val accounts =
            listOf(
                AccountBalance(Account("cash", "Cash", "CASH", 0, true), 7000),
                AccountBalance(Account("bank", "Bank", "CURRENT", 0, true), 15000),
                AccountBalance(Account("saving", "Saving", "SAVINGS", 0, false), 1000),
            )
        val tx =
            listOf(
                MoneyTransaction(
                    "cash-pay",
                    "cash-pay",
                    "INCOME",
                    10000,
                    "2026-10-03",
                    "Pay",
                    "Salary",
                    "NEEDS",
                ),
                MoneyTransaction(
                    "bank-pay",
                    "bank-pay",
                    "INCOME",
                    15000,
                    "2026-10-03",
                    "Pay",
                    "Salary",
                    "NEEDS",
                ),
                MoneyTransaction(
                    "shop",
                    "shop",
                    "EXPENSE",
                    3000,
                    "2026-10-03",
                    "Shop",
                    "Food",
                    "NEEDS",
                ),
                MoneyTransaction(
                    "save",
                    "save",
                    "TRANSFER",
                    1000,
                    "2026-10-03",
                    "Save",
                    "Savings",
                    "SAVINGS",
                ),
            )
        val entries =
            listOf(
                AccountEntry("1", "cash-pay", "cash", 10000),
                AccountEntry("2", "bank-pay", "bank", 15000),
                AccountEntry("3", "shop", "cash", -3000),
                AccountEntry("4", "save", "bank", -1000),
                AccountEntry("5", "save", "saving", 1000),
            )
        val bills =
            listOf(
                CommitmentBalance(
                    Commitment(
                        "bill-cash",
                        "bill-cash",
                        "Cash bill",
                        "BILL",
                        500,
                        "2026-10-10",
                        accountId = "cash",
                    ),
                    0,
                ),
                CommitmentBalance(
                    Commitment("shared", "shared", "Shared bill", "BILL", 2000, null),
                    0,
                ),
            )
        return FinanceSnapshot(
            LedgerState(profile, accounts, tx, bills, emptyList()),
            ReceiptState(emptyList(), emptyList(), emptyList()),
            ShoppingState(emptyList(), emptyList()),
            listOf(TransactionSplit("s", "shop", "Food", "NEEDS", 3000)),
            listOf(
                CategoryLimit("global", "Food", "NEEDS", 8000),
                CategoryLimit("cash-limit", "Food", "NEEDS", 5000, "cash"),
            ),
            entries,
        )
    }

    @Test
    fun accountScopeFiltersLedgerChartsAndAssignedBillsTogether() {
        val scoped = scopedFinance(fixture(), "cash")
        assertEquals(listOf("cash"), scoped.ledger.accounts.map { it.account.id })
        assertEquals(setOf("cash-pay", "shop"), scoped.ledger.transactions.map { it.id }.toSet())
        assertEquals(listOf("bill-cash"), scoped.ledger.commitments.map { it.commitment.id })
        assertEquals(listOf("cash-limit"), scoped.limits.map { it.id })
        val usage = budgetUsage(scoped, LocalDate.of(2026, 10, 3))
        assertEquals(10000, usage.income)
        assertEquals(3000, usage.used[0])
        assertEquals(6500, safe(scoped, LocalDate.of(2026, 10, 3)).safeMinor)
    }

    @Test
    fun transferIsIncludedOnceOnEachRelevantAccountButNeverAsIncome() {
        val scoped = scopedFinance(fixture(), "bank")
        assertEquals(1, scoped.ledger.transactions.count { it.kind == "TRANSFER" })
        val usage = budgetUsage(scoped, LocalDate.of(2026, 10, 3))
        assertEquals(15000, usage.income)
        assertEquals(1000, usage.used[2])
        assertEquals(1, scopedFinance(fixture(), "saving").ledger.transactions.size)
    }

    @Test
    fun allAccountsAndStaleSelectionKeepTheOriginalSnapshot() {
        val data = fixture()
        assertSame(data, scopedFinance(data, null))
        assertSame(data, scopedFinance(data, "deleted"))
        assertEquals(2, scopedFinance(data, null).ledger.commitments.size)
    }

    @Test
    fun goalContributionsAreScopedWithoutClaimingUnassignedOpeningSavings() {
        val data =
            fixture().let {
                it.copy(
                    ledger =
                        it.ledger.copy(
                            goals = listOf(GoalBalance(Goal("g", "Goal", 20000, 9000), 1000))
                        ),
                    contributions = listOf(GoalContribution("g1", "g", "save", 1000)),
                )
            }
        assertTrue(scopedFinance(data, "cash").ledger.goals.isEmpty())
        assertEquals(1000, scopedFinance(data, "bank").ledger.goals.single().savedMinor)
        assertEquals(10000, scopedFinance(data, null).ledger.goals.single().savedMinor)
    }
}
