package jm.yardmoney.ui

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.util.concurrent.atomic.AtomicInteger
import jm.yardmoney.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TransactionRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun thirtyVisibleRowsResolveAccountsFromALargeLedger() {
        val visits = AtomicInteger()
        val raw = List(1000) { AccountEntry("e$it", "t$it", "cash", -100) }
        val entries =
            object : AbstractList<AccountEntry>() {
                override val size
                    get() = raw.size

                override fun get(index: Int): AccountEntry {
                    visits.incrementAndGet()
                    return raw[index]
                }
            }
        val rows =
            List(30) {
                MoneyTransaction(
                    "t$it",
                    "s$it",
                    "EXPENSE",
                    100,
                    "2026-10-03",
                    "Record $it",
                    "Food",
                    "NEEDS",
                )
            }
        val data =
            FinanceSnapshot(
                LedgerState(
                    null,
                    listOf(AccountBalance(Account("cash", "Test cash", "CASH", 0, true), 0)),
                    rows,
                    emptyList(),
                    emptyList(),
                ),
                ReceiptState(emptyList(), emptyList(), emptyList()),
                ShoppingState(emptyList(), emptyList()),
                emptyList(),
                emptyList(),
                entries,
            )
        var current by mutableStateOf(data)
        compose.setContent {
            YardTheme {
                Surface {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TransactionRows(rows, current) {}
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Record 0").assertExists()
        compose.onNodeWithText("Record 29").assertExists()
        compose.onAllNodesWithText("Test cash").assertCountEquals(30)
        Log.i(
            "YardNavPerf",
            "transaction_entry_visits=${visits.get()}; visible_rows=30; ledger_entries=1000",
        )
        assertTrue(
            "Ledger entries should be indexed once, not per visible row",
            visits.get() in 1000..1999,
        )
        compose.runOnIdle {
            current =
                data.copy(
                    ledger =
                        data.ledger.copy(
                            accounts =
                                data.ledger.accounts.map {
                                    it.copy(account = it.account.copy(name = "Renamed cash"))
                                }
                        )
                )
        }
        compose.onAllNodesWithText("Renamed cash").assertCountEquals(30)
        compose.runOnIdle {
            val bank = AccountBalance(Account("bank", "Test bank", "CURRENT", 0, true), 0)
            current =
                current.copy(
                    ledger = current.ledger.copy(accounts = current.ledger.accounts + bank),
                    accountEntries = raw + AccountEntry("new", "t0", "bank", 100),
                )
        }
        compose.onAllNodesWithText("Test bank").assertCountEquals(1)
        compose.onAllNodesWithText("Renamed cash").assertCountEquals(30)
    }
}
