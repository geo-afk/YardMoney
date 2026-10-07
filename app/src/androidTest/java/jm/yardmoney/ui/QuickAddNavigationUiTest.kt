package jm.yardmoney.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import jm.yardmoney.MainActivity
import jm.yardmoney.YardMoneyApplication
import jm.yardmoney.Prefs
import jm.yardmoney.appearancePrefs
import jm.yardmoney.data.Profile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuickAddNavigationUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun smartPreviewOpensTheDetailedFormWithoutPostingMoney() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "jm.yardmoney.testhost") { "Use the isolated test installation." }
        val app = context.applicationContext as YardMoneyApplication
        val prefs = app.appearancePrefs()
        val locked = prefs.getBoolean(Prefs.LOCK, false)
        prefs.edit().putBoolean(Prefs.LOCK, false).commit()
        if (app.repository.dao.getProfile() == null) {
            val today = app.repository.today
            app.repository.onboard(Profile(name = "Navigation test", typicalNetMinor = 0, frequency = "MONTHLY",
                nextPayday = today.plusDays(14).toString(), anchorDay = 20, secondDay = 28,
                needsBp = 5000, wantsBp = 3000, savingsBp = 2000,
                periodStart = today.toString(), budgetIncomeMinor = 0), 1000000)
        }
        val account = app.repository.dao.readAccounts().first().account
        val before = app.repository.dao.readTransactions().size
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Add money or scan receipt").fetchSemanticsNodes().size == 1 }
                compose.onNodeWithContentDescription("Add money or scan receipt").performClick()
                compose.onNodeWithText("What did you spend?").performTextInput("taxi 600 ${account.name} yesterday")
                compose.onNodeWithText("Amount: 600.00").assertExists().performClick()
                compose.onNodeWithText("Record money").assertIsDisplayed()
                compose.onNode(hasSetTextAction() and hasText("600.00")).assertExists()
                assertEquals(before, app.repository.dao.readTransactions().size)
            }
        } finally { prefs.edit().putBoolean(Prefs.LOCK, locked).commit() }
    }
}
