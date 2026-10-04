package jm.yardmoney.ui

import jm.yardmoney.core.Money
import jm.yardmoney.data.*

// A single projection feeds every destination, so account filters cannot drift between charts and
// lists.
internal fun scopedFinance(data: FinanceSnapshot, accountId: String?): FinanceSnapshot {
    if (accountId == null || data.ledger.accounts.none { it.account.id == accountId }) return data
    val ids =
        data.accountEntries.filter { it.accountId == accountId }.map { it.transactionId }.toSet()
    val receipts = data.receipt.receipts.filter { it.transactionId in ids }
    val receiptIds = receipts.map { it.id }.toSet()
    val items = data.receiptItems.filter { it.receiptId in receiptIds }
    val itemIds = items.map { it.id }.toSet()
    val contributions = data.contributions.filter { it.transactionId in ids }
    val goalIds = contributions.map { it.goalId }.toSet()
    return data.copy(
        ledger =
            data.ledger.copy(
                accounts = data.ledger.accounts.filter { it.account.id == accountId },
                transactions = data.ledger.transactions.filter { it.id in ids },
                commitments =
                    data.ledger.commitments.filter { it.commitment.accountId == accountId },
                goals =
                    data.ledger.goals
                        .filter { it.goal.id in goalIds }
                        .map { g ->
                            g.copy(
                                goal = g.goal.copy(initialSavedMinor = 0),
                                contributedMinor =
                                    Money.sum(
                                        contributions
                                            .filter { it.goalId == g.goal.id }
                                            .map { it.amountMinor }
                                    ),
                            )
                        },
            ),
        receipt =
            data.receipt.copy(
                receipts = receipts,
                prices = data.receipt.prices.filter { it.itemId in itemIds },
                drafts = emptyList(),
            ),
        splits = data.splits.filter { it.transactionId in ids },
        // Explicit account limits override shared guidelines for the same category and bucket.
        limits =
            data.limits
                .filter { it.accountId == null || it.accountId == accountId }
                .groupBy { it.bucket to it.category.lowercase() }
                .map { (_, rows) ->
                    rows.firstOrNull { it.accountId == accountId } ?: rows.first()
                },
        accountEntries = data.accountEntries.filter { it.transactionId in ids },
        contributions = contributions,
        receiptItems = items,
        savingsAccountIds =
            data.ledger.accounts
                .filter { it.account.kind == "SAVINGS" }
                .map { it.account.id }
                .toSet(),
    )
}
