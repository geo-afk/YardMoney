package jm.yardmoney.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import jm.yardmoney.MainActivity
import jm.yardmoney.YardMoneyApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

// End-to-end tests must never seed, clear, or change a person's installed app.
class EndToEndQualityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app
        get() =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as YardMoneyApplication

    @Before
    fun freshIsolatedInstallation() = runBlocking {
        check(app.packageName == "jm.yardmoney.testhost")
        app.database.clearAllTables()
    }

    private fun field(label: String) =
        compose.onNode(
            hasSetTextAction() and
                (hasText(label.replace(" (J$)", "")) or
                    hasContentDescription(label.replace(" (J$)", "")))
        )

    private fun appModelBusy() =
        compose.onAllNodesWithText("Saving…").fetchSemanticsNodes().isNotEmpty()

    private fun awaitText(text: String) {
        try {
            compose.waitUntil(10000) {
                compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError("Missing $text\n${compose.onRoot().printToString()}", failure)
        }
    }

    @Test
    fun firstTimeUserCanOnboardRotateAnExpenseAndReturnToEveryMainMenu() = runBlocking {
        awaitText("Make my plan")
        field("What should we call you?").performScrollTo().performTextInput("E2E household")
        field("Typical take-home pay (J$; optional)").performScrollTo().performTextInput("1000")
        field("Cash you have right now (J$)").performScrollTo().performTextReplacement("2500")
        closeSoftKeyboard()
        compose.onNodeWithText("Make my plan").performScrollTo().performClick()
        awaitText("Hello, E2E household")
        compose.waitUntil(10000) { !appModelBusy() }
        assertEquals(250000L, app.repository.dao.readAccounts().single().balanceMinor)
        compose.onNodeWithText("Add expense").performScrollTo().performClick()
        awaitText("Save expense")
        field("Amount (J$)").performScrollTo().performTextInput("garbage")
        compose.onNodeWithText("Save expense").assertIsNotEnabled()
        field("Amount (J$)").performTextReplacement("12.34")
        field("Description").performScrollTo().performTextInput("Bread 🍞 & milk")
        closeSoftKeyboard()
        compose.activityRule.scenario.recreate()
        field("Amount (J$)").assertTextContains("12.34")
        field("Description").performScrollTo().assertTextContains("Bread 🍞 & milk")
        compose.onNodeWithText("Save expense").performClick()
        awaitText("Hello, E2E household")
        assertEquals(1, app.repository.dao.readTransactions().size)
        assertEquals(248766L, app.repository.dao.readAccounts().single().balanceMinor)
        compose.onNodeWithContentDescription("Activity").performClick()
        compose
            .onNode(hasText("Bread 🍞 & milk") and hasClickAction())
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitText("Edit record")
        // Simulate another writer removing the active record, including after process restore.
        app.repository.deleteTransaction(app.repository.dao.readTransactions().single().id)
        awaitText("This record is no longer available.")
        compose.onNodeWithText("OK").performClick()
        assertEquals(250000L, app.repository.dao.readAccounts().single().balanceMinor)
        compose.onNodeWithContentDescription("Plan").performClick()
        compose
            .onNodeWithContentDescription("Add bill or reservation")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitText("Save reservation")
        field("Name").performTextInput("E2E internet")
        field("Amount (J$)").performScrollTo().performTextInput("10")
        closeSoftKeyboard()
        compose.onNodeWithText("Save reservation").performClick()
        awaitText("E2E internet")
        compose.onNodeWithText("Pay").performScrollTo().performSemanticsAction(
            SemanticsActions.OnClick
        ) {
            it()
        }
        awaitText("Save expense")
        app.repository.removeCommitment(
            app.repository.dao.commitments().first().single().commitment.id
        )
        awaitText("This record is no longer available.")
        compose.onNodeWithText("OK").performClick()
        assertTrue(app.repository.dao.readTransactions().isEmpty())
        for (destination in listOf("Activity", "Plan", "Shop", "More", "Home")) {
            compose.onNodeWithContentDescription(destination).performClick().assertIsSelected()
        }
        compose.onNodeWithContentDescription("Account details").performClick()
        compose.onNodeWithContentDescription("Add account").performClick()
        field("Name").performTextInput("Savings 🏦")
        field("Opening balance (J$)").performTextReplacement("0")
        closeSoftKeyboard()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10000) { runBlocking { app.repository.dao.readAccounts().size == 2 } }
        assertEquals(2, app.repository.dao.readAccounts().size)
    }
}
