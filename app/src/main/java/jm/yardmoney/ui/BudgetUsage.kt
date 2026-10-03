package jm.yardmoney.ui

import java.time.LocalDate
import jm.yardmoney.core.*
import jm.yardmoney.data.*

internal data class BudgetUsage(val income: Long, val allocation: List<Long>, val used: List<Long>)

internal fun budgetUsage(data: FinanceSnapshot, today: LocalDate): BudgetUsage {
    val p = requireNotNull(data.ledger.profile)
    val current =
        data.ledger.transactions.filter { it.date >= p.periodStart && it.date <= today.toString() }
    val income = Money.sum(current.filter { it.kind == "INCOME" }.map { it.amountMinor })
    val allocated = BudgetSplit(p.needsBp, p.wantsBp, p.savingsBp).allocate(income)
    val used =
        listOf("NEEDS", "WANTS", "SAVINGS")
            .map { bucket ->
                Money.sum(bucketCategories(data.splits, current, bucket).map { it.second })
            }
            .toMutableList()
    val savingsAccounts =
        data.ledger.accounts.filter { it.account.kind == "SAVINGS" }.map { it.account.id }.toSet()
    val transfers = current.filter { it.kind == "TRANSFER" }.map { it.id }.toSet()
    val saved =
        Money.sum(
            data.accountEntries
                .filter { it.transactionId in transfers && it.accountId in savingsAccounts }
                .map { it.signedMinor }
        )
    used[2] = Math.addExact(used[2], saved)
    return BudgetUsage(income, allocated, used)
}
