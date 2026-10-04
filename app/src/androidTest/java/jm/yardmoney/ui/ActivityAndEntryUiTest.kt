package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import jm.yardmoney.data.MoneyTransaction
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ActivityAndEntryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun returningToTopKeepsFormAndPullsVisitEveryStageBeforeDismissal() {
        var closed by mutableStateOf(false)
        compose.setContent {
            YardTheme {
                if (!closed)
                    MoneyEntrySheet(
                        "Record money",
                        "Save expense",
                        false,
                        true,
                        {},
                        { closed = true },
                    ) {
                        repeat(12) { index ->
                            MoneyEntrySection("Detail ${index + 1}") {
                                Text("Scroll safely without losing the form")
                            }
                        }
                    }
            }
        }
        fun stage(name: String) =
            compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, name))
        compose.onNodeWithText("Detail 12").performScrollTo()
        compose.onNodeWithTag("money-entry-scroll").performTouchInput {
            swipeDown(durationMillis = 300)
        }
        stage("Full").assertExists()
        compose.onNodeWithTag("money-entry-scroll").performSemanticsAction(
            SemanticsActions.ScrollBy
        ) {
            it(0f, -100000f)
        }
        // Continue the pointer beyond the handle while the sheet follows it to the next anchor.
        fun pullToNextStage() {
            val body =
                compose.onNodeWithTag("money-entry-scroll").fetchSemanticsNode().boundsInRoot.height
            compose.onNodeWithTag("edit-sheet-handle").performTouchInput {
                swipe(Offset(width / 2f, 4f), Offset(width / 2f, height + body * .8f - 8f), 600)
            }
            compose.waitForIdle()
        }
        pullToNextStage()
        stage("Half").assertExists()
        compose.runOnIdle { assertFalse(closed) }
        pullToNextStage()
        stage("Peek").assertExists()
        compose.runOnIdle { assertFalse(closed) }
        compose.onNodeWithTag("edit-sheet-handle").performTouchInput {
            swipe(Offset(width / 2f, 4f), Offset(width / 2f, height - 8f), 600)
        }
        compose.onNodeWithText("Keep editing").performClick()
        stage("Peek").assertExists()
        stage("Peek").performSemanticsAction(SemanticsActions.Dismiss)
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithText("Record money").assertDoesNotExist()
    }

    @Test
    fun enteredAmountSurvivesFlingAndKeyboardSaveStaysReachable() {
        var value = ""
        compose.setContent {
            YardTheme {
                MoneyEntrySheet("Record money", "Save expense", false, true, {}, {}) {
                    MoneyEntrySection("Amount") {
                        var entered by remember { mutableStateOf("") }
                        Field("Amount (J$)", entered) {
                            entered = it
                            value = it
                        }
                    }
                    repeat(12) { i ->
                        MoneyEntrySection("Detail ${i + 1}") { Text("More record details") }
                    }
                }
            }
        }
        compose.onNode(hasSetTextAction()).performClick().performTextInput("125.50")
        compose.onNodeWithTag("money-entry-scroll").performTouchInput {
            swipeUp(durationMillis = 200)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Save expense").assertIsDisplayed()
        compose.onNodeWithText("Record money").assertIsDisplayed()
        compose.runOnIdle { assertEquals("125.50", value) }
    }

    @Test
    fun typeSelectorFiltersChartAndListAndShowsNegativeAdjustments() {
        val today = LocalDate.of(2026, 10, 2)
        fun tx(id: String, kind: String, amount: Long) =
            MoneyTransaction(id, id, kind, amount, today.toString(), id, "Other", "NEEDS")
        val rows =
            listOf(
                tx("Food record", "EXPENSE", 1000),
                tx("Transfer record", "TRANSFER", 5000),
                tx("Correction record", "ADJUSTMENT", -250),
            )
        compose.setContent {
            YardTheme {
                Surface {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ActivityExplorer(rows, today.minusDays(1), today) { selected ->
                            selected.forEach { Text(it.description) }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Food record").assertExists()
        compose.onNodeWithText("Transfers").performScrollTo().performClick()
        compose.onNodeWithText("Food record").assertDoesNotExist()
        compose.onNodeWithText("Transfer record").assertExists()
        compose.onNodeWithText("Adjustments").performScrollTo().performClick()
        compose.onNodeWithText("Transfer record").assertDoesNotExist()
        compose.onNodeWithText("Correction record").assertExists()
        compose.onNode(hasContentDescription("J$-2.50", substring = true)).assertExists()
        compose.onNodeWithText("Income").performScrollTo().performClick()
        compose.onNodeWithText("No income in this date range.").assertExists()
        compose.onNodeWithText("Correction record").assertDoesNotExist()
    }
}
