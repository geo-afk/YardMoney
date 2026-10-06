package jm.yardmoney.ui

import java.time.LocalDate
import java.time.YearMonth
import java.util.Random
import jm.yardmoney.core.Money
import jm.yardmoney.data.*
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reports tab and category limits used to compute inline on every recomposition. These tests
 * keep a copy of that original arithmetic as an oracle and require identical results on varied
 * ledgers, so moving it into pure functions cannot change a number.
 */
class ReportCalculationsTest {
    private val today = LocalDate.of(2026, 10, 5)
    private val categories = listOf("Groceries", "groceries", "Transport", "Dining", "Bills", "Fun")
    private val merchants = listOf("Hi-Lo", "Hi-Lo", "Digicel", "", "Shell", "Hi-Lo ")
    private val buckets = listOf("NEEDS", "WANTS", "SAVINGS")
    private val kinds =
        listOf("EXPENSE", "EXPENSE", "EXPENSE", "REFUND", "INCOME", "TRANSFER", "ADJUSTMENT")
    private val accounts = listOf("cash", "bank")
    private val limitAccounts = listOf(null, "cash", "bank", "ghost")

    private fun ledger(seed: Long): FinanceSnapshot {
        val r = Random(seed)
        val periodStart = today.minusDays(r.nextInt(40).toLong() + 5)
        val profile =
            Profile(
                id = 1,
                name = "Test",
                typicalNetMinor = 0,
                frequency = "MONTHLY",
                nextPayday = today.plusDays(10).toString(),
                anchorDay = 15,
                secondDay = 30,
                needsBp = 5000,
                wantsBp = 3000,
                savingsBp = 2000,
                periodStart = periodStart.toString(),
                budgetIncomeMinor = 0,
            )
        val txs = mutableListOf<MoneyTransaction>()
        val splits = mutableListOf<TransactionSplit>()
        val entries = mutableListOf<AccountEntry>()
        repeat(60 + r.nextInt(120)) { i ->
            val kind = kinds[r.nextInt(kinds.size)]
            // Includes a few future-dated rows (a restored backup or clock change can create them).
            val date = today.minusDays(r.nextInt(200).toLong() - 20)
            val refundOf =
                if (kind != "REFUND") null
                else
                    when (r.nextInt(3)) {
                        0 -> null
                        1 -> "missing"
                        else -> txs.lastOrNull { it.kind == "EXPENSE" }?.id
                    }
            val tx =
                MoneyTransaction(
                    id = "t$i",
                    submissionKey = "k$i",
                    kind = kind,
                    amountMinor = 100L + r.nextInt(50_000),
                    date = date.toString(),
                    description = merchants[r.nextInt(merchants.size)],
                    category = categories[r.nextInt(categories.size)],
                    bucket = buckets[r.nextInt(3)],
                    refundOfId = refundOf,
                )
            txs += tx
            repeat(1 + r.nextInt(2)) { s ->
                splits +=
                    TransactionSplit(
                        id = "s$i-$s",
                        transactionId = tx.id,
                        category = categories[r.nextInt(categories.size)],
                        bucket = buckets[r.nextInt(3)],
                        amountMinor = 50L + r.nextInt(20_000),
                    )
            }
            val signed = if (kind == "INCOME") tx.amountMinor else -tx.amountMinor
            entries += AccountEntry("e$i", tx.id, accounts[r.nextInt(accounts.size)], signed)
        }
        val limits =
            (0 until 6).map { i ->
                CategoryLimit(
                    id = "l$i",
                    category = categories[r.nextInt(categories.size)],
                    bucket = buckets[r.nextInt(3)],
                    limitMinor = 1000L + r.nextInt(80_000),
                    accountId = limitAccounts[r.nextInt(limitAccounts.size)],
                )
            }
        return FinanceSnapshot(
            LedgerState(profile, emptyList(), txs, emptyList(), emptyList()),
            ReceiptState(emptyList(), emptyList(), emptyList()),
            ShoppingState(emptyList(), emptyList()),
            splits,
            limits,
            entries,
        )
    }

    // ---- oracle: the original FinancialCharts arithmetic, verbatim --------------------------
    private fun legacySummary(data: FinanceSnapshot, today: LocalDate): FinancialSummary {
        val p = data.ledger.profile!!
        val transactions =
            data.ledger.transactions.filter {
                it.date >= p.periodStart && it.date <= today.toString()
            }
        val income = Money.sum(transactions.filter { it.kind == "INCOME" }.map { it.amountMinor })
        val spending =
            Money.sum(
                transactions
                    .filter { it.kind in listOf("EXPENSE", "REFUND") }
                    .map { if (it.kind == "REFUND") -it.amountMinor else it.amountMinor }
            )
        val ids = transactions.map { it.id }.toSet()
        val categories =
            data.splits
                .filter { it.transactionId in ids }
                .groupBy { it.category }
                .map { (label, rows) -> label to Money.sum(rows.map { it.amountMinor }) }
                .sortedByDescending { it.second }
                .take(8)
        val merchants =
            transactions
                .filter { it.kind in listOf("EXPENSE", "REFUND") }
                .groupBy { t ->
                    if (t.kind == "REFUND")
                        data.ledger.transactions.find { it.id == t.refundOfId }?.description
                            ?: t.description
                    else t.description
                }
                .map { (label, rows) ->
                    label.ifBlank { "Unspecified merchant" } to
                        Money.sum(
                            rows.map { if (it.kind == "REFUND") -it.amountMinor else it.amountMinor }
                        )
                }
                .sortedByDescending { it.second }
                .take(8)
        val month = YearMonth.from(today)
        val months =
            (5 downTo 0).map { ago ->
                val m = month.minusMonths(ago.toLong())
                m.toString() to
                    Money.sum(
                        data.ledger.transactions
                            .filter {
                                it.date >= m.atDay(1).toString() &&
                                    it.date <= minOf(m.atEndOfMonth(), today).toString() &&
                                    it.kind in listOf("EXPENSE", "REFUND")
                            }
                            .map { if (it.kind == "REFUND") -it.amountMinor else it.amountMinor }
                    )
            }
        return FinancialSummary(income, spending, categories, merchants, months)
    }

    // ---- oracle: the original CategoryLimits arithmetic, verbatim ---------------------------
    private fun legacyUsage(data: FinanceSnapshot, today: LocalDate): Map<String, Long> {
        val ids =
            data.ledger.transactions
                .filter {
                    it.date >= data.ledger.profile!!.periodStart && it.date <= today.toString()
                }
                .map { it.id }
                .toSet()
        return data.limits.associate { limit ->
            val accountTxIds =
                limit.accountId?.let { accountId ->
                    data.accountEntries
                        .filter { it.accountId == accountId }
                        .map { it.transactionId }
                        .toSet()
                }
            limit.id to
                Money.sum(
                    data.splits
                        .filter {
                            it.transactionId in ids &&
                                (accountTxIds == null || it.transactionId in accountTxIds) &&
                                it.bucket == limit.bucket &&
                                it.category.equals(limit.category, true)
                        }
                        .map { it.amountMinor }
                )
        }
    }

    @Test
    fun financialSummaryMatchesTheOriginalArithmetic() {
        for (seed in 1L..300L) {
            val data = ledger(seed)
            assertEquals("seed $seed", legacySummary(data, today), financialSummary(data, today))
        }
    }

    @Test
    fun categoryLimitUsageMatchesTheOriginalArithmetic() {
        for (seed in 1L..300L) {
            val data = ledger(seed)
            assertEquals("seed $seed", legacyUsage(data, today), categoryLimitUsage(data, today))
        }
    }

    @Test
    fun nothingIsComputedBeforeSetup() {
        val empty = ledger(1).let { it.copy(ledger = it.ledger.copy(profile = null)) }
        assertEquals(null, financialSummary(empty, today))
        assertEquals(emptyMap<String, Long>(), categoryLimitUsage(empty, today))
    }

    @Test
    fun emptyLedgerGivesZeroTotalsAndSixMonthBuckets() {
        val data =
            ledger(1).let {
                it.copy(
                    ledger = it.ledger.copy(transactions = emptyList()),
                    splits = emptyList(),
                    accountEntries = emptyList(),
                )
            }
        val summary = financialSummary(data, today)!!
        assertEquals(0L, summary.income)
        assertEquals(0L, summary.spending)
        assertEquals(6, summary.months.size)
        assertEquals("2026-10", summary.months.last().first)
        assertEquals(true, summary.categories.isEmpty() && summary.merchants.isEmpty())
    }
}
