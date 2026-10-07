package jm.yardmoney.ui

import androidx.test.espresso.Espresso
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
    @Test fun previewHelpStaysInQuickAddAndOnlyDetailsOpensExpenseForm() = runBlocking {
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
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Quick Add").fetchSemanticsNodes().size == 1 }
                for ((label, route) in listOf("Income" to "INCOME", "Transfer" to "TRANSFER",
                    "Scan receipt" to "SCAN", "Paste alert text" to "PASTE")) {
                    for (icon in listOf(false, true)) {
                        compose.onNodeWithContentDescription("Quick Add").performClick()
                        // Home also has a Scan receipt action; target the Quick Add dialog.
                        val option = compose.onNode(hasText(label) and hasAnyAncestor(isDialog()))
                        option.performScrollTo()
                        if (icon) compose.onNodeWithTag("Quick Add $route icon", useUnmergedTree = true).performTouchInput { click() }
                        else option.performTouchInput { click() }
                        when (route) {
                            "SCAN" -> {
                                compose.onNodeWithText("Capture a receipt").assertIsDisplayed()
                                compose.onNodeWithText("Record money").assertDoesNotExist()
                                Espresso.pressBack()
                            }
                            "PASTE" -> {
                                compose.onNodeWithText("Review alert text").assertIsDisplayed()
                                compose.onNodeWithText("Record money").assertDoesNotExist()
                                compose.onNodeWithContentDescription("Close editor").performClick()
                            }
                            else -> {
                                compose.onNodeWithText("Record money").assertIsDisplayed()
                                compose.onNodeWithText(label).assertIsSelected()
                                compose.onNodeWithText("Expense").assertIsNotSelected()
                                compose.onNodeWithContentDescription("Close editor").performClick()
                            }
                        }
                        compose.waitForIdle()
                        if (compose.onAllNodesWithText("Discard").fetchSemanticsNodes().isNotEmpty())
                            compose.onNodeWithText("Discard").performClick()
                        compose.onNodeWithContentDescription("Quick Add").assertIsDisplayed()
                        assertEquals(before, app.repository.dao.readTransactions().size)
                    }
                }
                compose.onNodeWithContentDescription("Quick Add").performClick()
                compose.onNodeWithText("What did you spend?").performTextInput("taxi 600 ${account.name} yesterday")
                compose.onNodeWithText("Amount: 600.00").assertExists().performClick()
                compose.onNodeWithText("About amount").assertIsDisplayed()
                compose.onNodeWithText("Record money").assertDoesNotExist()
                compose.onNodeWithText("Got it").performClick()
                compose.onNodeWithText("More details").performScrollTo().performClick()
                compose.onNodeWithText("Record money").assertIsDisplayed()
                compose.onNode(hasSetTextAction() and hasText("600.00")).assertExists()
                assertEquals(before, app.repository.dao.readTransactions().size)
            }
        } finally { prefs.edit().putBoolean(Prefs.LOCK, locked).commit() }
    }
}
