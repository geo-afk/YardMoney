package jm.yardmoney.ui

import jm.yardmoney.data.*

internal fun transactionAccounts(
    entries: List<AccountEntry>,
    accounts: List<AccountBalance>,
): Map<String, List<AccountBalance>> {
    val order = accounts.mapIndexed { index, row -> row.account.id to index }.toMap()
    val byId = accounts.associateBy { it.account.id }
    // Index once per ledger revision instead of scanning every entry for each visible row.
    return entries
        .groupBy { it.transactionId }
        .mapValues { (_, rows) ->
            rows
                .map { it.accountId }
                .distinct()
                .sortedBy { order[it] ?: Int.MAX_VALUE }
                .mapNotNull { byId[it] }
        }
}
