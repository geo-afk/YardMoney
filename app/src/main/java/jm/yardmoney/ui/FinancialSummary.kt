package jm.yardmoney.ui

import java.time.LocalDate
import java.time.YearMonth
import jm.yardmoney.core.Money
import jm.yardmoney.data.FinanceSnapshot
import jm.yardmoney.data.MoneyTransaction

/** Spending is positive for purchases and negative for refunds; other kinds do not count. */
private fun MoneyTransaction.spendMinor(): Long? =
    when (kind) {
        "EXPENSE" -> amountMinor
        "REFUND" -> -amountMinor
        else -> null
    }

/**
 * Income, spending, category, merchant and six-month totals for the reports tab. Pure and
 * allocation-light so it can be computed once per data revision instead of on every redraw.
 * Returns null until setup has created a profile.
 */
internal fun financialSummary(data: FinanceSnapshot, today: LocalDate): FinancialSummary? {
    val profile = data.ledger.profile ?: return null
    val todayText = today.toString()
    val transactions =
        data.ledger.transactions.filter { it.date >= profile.periodStart && it.date <= todayText }
    val income = Money.sum(transactions.filter { it.kind == "INCOME" }.map { it.amountMinor })
    val spending = Money.sum(transactions.mapNotNull { it.spendMinor() })

    val ids = transactions.map { it.id }.toSet()
    val categories =
        data.splits
            .filter { it.transactionId in ids }
            .groupBy { it.category }
            .map { (label, rows) -> label to Money.sum(rows.map { it.amountMinor }) }
            .sortedByDescending { it.second }
            .take(8)

    // Refunds retain their original merchant grouping rather than an arbitrary refund description.
    val byId = data.ledger.transactions.associateBy { it.id }
    val merchants =
        transactions
            .filter { it.spendMinor() != null }
            .groupBy { t ->
                if (t.kind == "REFUND") t.refundOfId?.let { byId[it] }?.description ?: t.description
                else t.description
            }
            .map { (label, rows) ->
                label.ifBlank { "Unspecified merchant" } to
                    Money.sum(rows.mapNotNull { it.spendMinor() })
            }
            .sortedByDescending { it.second }
            .take(8)

    val spendByDate =
        data.ledger.transactions.mapNotNull { t -> t.spendMinor()?.let { t.date to it } }
    val month = YearMonth.from(today)
    val months =
        (5 downTo 0).map { ago ->
            val m = month.minusMonths(ago.toLong())
            val from = m.atDay(1).toString()
            val to = minOf(m.atEndOfMonth(), today).toString()
            m.toString() to
                Money.sum(
                    spendByDate.filter { (date, _) -> date >= from && date <= to }.map { it.second }
                )
        }
    return FinancialSummary(income, spending, categories, merchants, months)
}
