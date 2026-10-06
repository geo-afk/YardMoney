package jm.yardmoney.ui

import java.time.LocalDate
import jm.yardmoney.core.Money
import jm.yardmoney.data.FinanceSnapshot

/**
 * Spending counted against each category limit in the current budget period, keyed by limit id.
 * Computed once per data revision; account-specific limits only count splits of that account.
 */
internal fun categoryLimitUsage(data: FinanceSnapshot, today: LocalDate): Map<String, Long> {
    val periodStart = data.ledger.profile?.periodStart ?: return emptyMap()
    val todayText = today.toString()
    val inPeriod =
        data.ledger.transactions
            .filter { it.date >= periodStart && it.date <= todayText }
            .map { it.id }
            .toSet()
    val periodSplits = data.splits.filter { it.transactionId in inPeriod }
    val transactionsByAccount =
        data.accountEntries
            .groupBy({ it.accountId }, { it.transactionId })
            .mapValues { it.value.toSet() }
    return data.limits.associate { limit ->
        val accountTransactions = limit.accountId?.let { transactionsByAccount[it].orEmpty() }
        limit.id to
            Money.sum(
                periodSplits
                    .filter {
                        (accountTransactions == null || it.transactionId in accountTransactions) &&
                            it.bucket == limit.bucket &&
                            it.category.equals(limit.category, true)
                    }
                    .map { it.amountMinor }
            )
    }
}
