package jm.yardmoney.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import jm.yardmoney.data.*
import org.junit.Rule
import org.junit.Test

class BudgetActivityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun categorySelectorChangesRecordedActivityWithoutMixingBuckets() {
        val profile =
            Profile(
                name = "Test",
                typicalNetMinor = 10000,
                frequency = "MONTHLY",
                nextPayday = "2026-11-01",
                anchorDay = 1,
                secondDay = 15,
                needsBp = 5000,
                wantsBp = 3000,
                savingsBp = 2000,
                periodStart = "2026-10-01",
                budgetIncomeMinor = 10000,
            )
        val transactions =
            listOf(
                MoneyTransaction(
                    "pay",
                    "pay",
                    "INCOME",
                    10000,
                    "2026-10-01",
                    "Pay",
                    "Income",
                    "NEEDS",
                ),
                MoneyTransaction(
                    "food",
                    "food",
                    "EXPENSE",
                    1000,
                    "2026-10-02",
                    "Store",
                    "Food",
                    "NEEDS",
                ),
                MoneyTransaction(
                    "fun",
                    "fun",
                    "EXPENSE",
                    500,
                    "2026-10-02",
                    "Cinema",
                    "Leisure",
                    "WANTS",
                ),
            )
        val data =
            FinanceSnapshot(
                LedgerState(profile, emptyList(), transactions, emptyList(), emptyList()),
                ReceiptState(emptyList(), emptyList(), emptyList()),
                ShoppingState(emptyList(), emptyList()),
                listOf(
                    TransactionSplit("a", "food", "Food", "NEEDS", 1000),
                    TransactionSplit("b", "fun", "Leisure", "WANTS", 500),
                ),
                emptyList(),
            )
        compose.setContent {
            YardTheme {
                Surface {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        BudgetOverview(data, LocalDate.of(2026, 10, 2))
                    }
                }
            }
        }
        compose.onNodeWithText("Food").assertExists()
        compose.onNode(hasText("Wants") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Food").assertDoesNotExist()
        compose.onNodeWithText("Leisure").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Savings") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Goal savings").assertExists()
        compose.onNodeWithText("Leisure").assertDoesNotExist()
    }
}
