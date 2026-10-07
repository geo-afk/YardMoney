package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import jm.yardmoney.core.Money
import jm.yardmoney.core.RepeatExpense
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuickAddInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var edits = 0
    private var saves = 0
    private val actions = mutableListOf<QuickAddAction>()
    private val repeated = mutableListOf<Boolean>()
    private var busy by mutableStateOf(false)
    private var draft by mutableStateOf(QuickAddDraft())
    private val repeat = RepeatExpense("Bus", 60000, "Transport", "NEEDS", "cash", LocalDate.of(2026, 10, 6))

    private fun render() {
        compose.setContent {
            YardTheme { Surface {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    QuickAddContent("", draft, listOf(IdentityOption("cash", "Cash")),
                        listOf(IdentityOption("Transport", "Transport")), listOf(repeat), emptyList(),
                        busy, true, {}, { draft = it }, { _, editing -> repeated += editing },
                        { saves++ }, { edits++ }, { actions += it })
                }
            } }
        }
    }

    @Test fun everyPreviewLabelAndHelpIconOpensOnlyItsOwnHelp() {
        render()
        for (populated in listOf(false, true)) {
            compose.runOnIdle {
                draft = if (populated) QuickAddDraft("600.00", "Bus", "Transport", "NEEDS", "cash", "2026-10-06") else QuickAddDraft()
            }
            for ((field, value) in listOf("Amount" to "600.00", "Category" to "Transport", "Account" to "Cash", "Date" to "2026-10-06")) {
                val label = "$field: ${if (populated) value else "Not specified"}"
                compose.onNodeWithText(label).performScrollTo().performTouchInput { click() }
                compose.onNodeWithText("About ${field.lowercase()}").assertIsDisplayed()
                compose.onNodeWithText("Got it").performClick()
                compose.onNodeWithText(label).performScrollTo()
                compose.onNodeWithTag("Quick Add help $field", useUnmergedTree = true).performTouchInput { click() }
                compose.onNodeWithText("About ${field.lowercase()}").assertIsDisplayed()
                compose.onNodeWithText("Got it").performClick()
                assertEquals(0, edits)
                assertEquals(0, saves)
                assertTrue(actions.isEmpty())
            }
        }
    }

    @Test fun everyActionIconAndLabelDispatchesExactlyItsOwnAction() {
        render()
        for ((label, expected) in listOf("Income" to QuickAddAction.INCOME,
            "Transfer" to QuickAddAction.TRANSFER, "Scan receipt" to QuickAddAction.SCAN,
            "Paste alert text" to QuickAddAction.PASTE)) {
            compose.onNodeWithText(label).performScrollTo().performTouchInput { click() }
            assertEquals(listOf(expected), actions)
            actions.clear()
            compose.onNodeWithTag("Quick Add ${expected.name} icon", useUnmergedTree = true).performTouchInput { click() }
            assertEquals(listOf(expected), actions)
            actions.clear()
            assertEquals(0, edits)
            assertEquals(0, saves)
        }
        compose.onNodeWithText("More details").performScrollTo().performTouchInput { click() }
        compose.onNodeWithTag("Quick Add details icon", useUnmergedTree = true).performTouchInput { click() }
        assertEquals(2, edits)
        assertTrue(actions.isEmpty())
        compose.onNodeWithText("Save expense").performScrollTo().performClick()
        assertEquals(1, saves)
        assertEquals(2, edits)
    }

    @Test fun repeatTapPrefillsWhileLongPressRequestsEditing() {
        render()
        val chip = compose.onNodeWithContentDescription("Bus · ${Money.format(60000)}")
        chip.performScrollTo().performTouchInput { click() }
        assertEquals(listOf(false), repeated)
        chip.performTouchInput { longClick() }
        assertEquals(listOf(false, true), repeated)
        assertEquals(0, edits)
        assertEquals(0, saves)
        assertTrue(actions.isEmpty())
    }

    @Test fun editingFieldsDoesNotNavigateAndBusyDisablesActions() {
        draft = QuickAddDraft(date = "2026-10-06")
        render()
        compose.onNodeWithContentDescription("Amount").performScrollTo().performTextInput("42.50")
        compose.onNodeWithText("Description").performScrollTo().performTextInput("Lunch")
        compose.onNodeWithContentDescription("Open date selector").performScrollTo().performClick()
        compose.onNodeWithText("Use this date").performClick()
        assertEquals("2026-10-06", draft.date)
        compose.onNodeWithText("Account").performScrollTo().performClick()
        compose.onNodeWithText("Cash").performClick()
        compose.onNodeWithText("Category").performScrollTo().performClick()
        compose.onNodeWithText("Transport").performClick()
        compose.onNodeWithText("Budget group").performScrollTo().performClick()
        compose.onNodeWithText("Wants").performClick()
        assertEquals("cash", draft.accountId)
        assertEquals("Transport", draft.category)
        assertEquals("WANTS", draft.bucket)
        assertEquals("42.50", draft.amount)
        assertEquals("Lunch", draft.description)
        assertEquals(0, edits)
        assertTrue(actions.isEmpty())
        compose.runOnIdle { busy = true }
        for (label in listOf("Income", "Transfer", "Scan receipt", "Paste alert text", "More details", "Save expense")) {
            compose.onNodeWithText(label).performScrollTo().assertIsNotEnabled()
        }
    }
}
