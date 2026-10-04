package jm.yardmoney.ui

import jm.yardmoney.data.*
import org.junit.Assert.*
import org.junit.Test

class TransactionAccountsTest {
    private val cash = AccountBalance(Account("cash", "Cash", "CASH", 0, true), 10)
    private val bank = AccountBalance(Account("bank", "Bank", "CURRENT", 0, true), 20)

    @Test
    fun transfersKeepAccountDisplayOrderAndRemoveDuplicateEntries() {
        val entries =
            listOf(
                AccountEntry("a", "transfer", "bank", 100),
                AccountEntry("b", "transfer", "cash", -100),
                AccountEntry("c", "transfer", "bank", 1),
            )
        assertEquals(
            listOf(cash, bank),
            transactionAccounts(entries, listOf(cash, bank))["transfer"],
        )
    }

    @Test
    fun unrelatedAndMissingAccountsDoNotLeakIntoTheRow() {
        val entries =
            listOf(
                AccountEntry("a", "first", "cash", -100),
                AccountEntry("b", "second", "bank", 100),
                AccountEntry("c", "first", "missing", 10),
            )
        val index = transactionAccounts(entries, listOf(cash, bank))
        assertEquals(listOf(cash), index["first"])
        assertEquals(listOf(bank), index["second"])
        assertNull(index["unknown"])
    }

    @Test
    fun aNewRevisionUpdatesAccountNamesAndNewRecords() {
        val entries = listOf(AccountEntry("a", "first", "cash", -100))
        val renamed = cash.copy(account = cash.account.copy(name = "Renamed cash"))
        assertEquals(
            "Renamed cash",
            transactionAccounts(entries, listOf(renamed))["first"]!!.single().account.name,
        )
        assertTrue(transactionAccounts(emptyList(), listOf(cash)).isEmpty())
    }
}
