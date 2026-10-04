package jm.yardmoney.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import jm.yardmoney.core.*
import jm.yardmoney.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveBudgetAndBillsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun waitForUsage(needle: String) {
        compose.waitUntil(10000) {
            compose
                .onAllNodes(hasContentDescription(needle, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun committedRecordsRefreshDonutWithoutChangingMenus() = runBlocking {
        System.loadLibrary("sqlcipher")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db =
            Room.inMemoryDatabaseBuilder(context, YardDatabase::class.java)
                .openHelperFactory(SupportOpenHelperFactory(ByteArray(32) { (it + 1).toByte() }))
                .build()
        val repo = FinanceRepository(db)
        try {
            val today = repo.today
            repo.onboard(
                Profile(
                    name = "Test",
                    typicalNetMinor = 100000,
                    frequency = "MONTHLY",
                    nextPayday = today.plusDays(30).toString(),
                    anchorDay = 1,
                    secondDay = 15,
                    needsBp = 5000,
                    wantsBp = 3000,
                    savingsBp = 2000,
                    periodStart = today.toString(),
                    budgetIncomeMinor = 100000,
                ),
                100000,
            )
            val cash = repo.snapshot.first().ledger.accounts.single().account.id
            compose.setContent {
                // Room emits on its executor; keep the test's UI producer on Android's main thread,
                // matching the lifecycle collectors used by the application.
                val data by
                    repo.snapshot.collectAsState(
                        initial = null,
                        context = kotlinx.coroutines.Dispatchers.Main.immediate,
                    )
                YardTheme {
                    Surface {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            if (data != null) BudgetOverview(data!!, today)
                            else Text("Loading test ledger")
                        }
                    }
                }
            }
            waitForUsage("J$0 used of J$0")
            repo.post(
                TransactionInput("pay", "INCOME", 10000, today, "Pay", "Income", "NEEDS", cash)
            )
            waitForUsage("J$0 used of J$50")
            val expense =
                repo.post(
                    TransactionInput(
                        "spend",
                        "EXPENSE",
                        1000,
                        today,
                        "Store",
                        "Food",
                        "NEEDS",
                        cash,
                    )
                )
            waitForUsage("J$10 used of J$50")
            val refund =
                repo.post(
                    TransactionInput(
                        "return",
                        "REFUND",
                        250,
                        today,
                        "Store",
                        "Food",
                        "NEEDS",
                        cash,
                        refundOfId = expense,
                    )
                )
            waitForUsage("J$7.50 used of J$50")
            repo.post(
                TransactionInput("pay2", "INCOME", 5000, today, "Pay", "Income", "NEEDS", cash)
            )
            waitForUsage("J$7.50 used of J$75")
            repo.deleteTransaction(refund)
            waitForUsage("J$10 used of J$75")
            repo.deleteTransaction(expense)
            waitForUsage("J$0 used of J$75")
            repo.addAccount("Stash", "SAVINGS", 0, false)
            val savings =
                repo.snapshot
                    .first()
                    .ledger
                    .accounts
                    .first { it.account.kind == "SAVINGS" }
                    .account
                    .id
            repo.post(
                TransactionInput(
                    "save",
                    "TRANSFER",
                    2000,
                    today,
                    "Saved",
                    "Savings",
                    "SAVINGS",
                    cash,
                    toAccountId = savings,
                )
            )
            waitForUsage("J$20 used of J$30")
        } finally {
            db.close()
        }
    }

    @Test
    fun recurringBillShowsOneRowUntilDatesAreExpanded() {
        val rows =
            (1..3).map { n ->
                CommitmentBalance(
                    Commitment("$n", "series@2026-10-0$n", "Power", "BILL", 1000, "2026-10-0$n"),
                    0,
                )
            }
        compose.setContent {
            YardTheme {
                Surface {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ReservationList(rows, {}, {})
                    }
                }
            }
        }
        compose.onAllNodesWithText("Power").assertCountEquals(1)
        compose.onNodeWithText("Show 2 other scheduled payments").performScrollTo().performClick()
        compose.onAllNodesWithText("Power").assertCountEquals(3)
        compose.onNodeWithText("Hide scheduled payments").performScrollTo().performClick()
        compose.onAllNodesWithText("Power").assertCountEquals(1)
    }
}
