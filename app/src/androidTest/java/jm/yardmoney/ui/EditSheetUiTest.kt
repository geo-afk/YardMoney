package jm.yardmoney.ui

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditSheetUiTest {
    @get:Rule val compose = createComposeRule()

    private fun stage(name: String) =
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, name))

    @Test
    fun allStagesRestoreTypedInputAndRequireDiscardConfirmation() {
        val restore = StateRestorationTester(compose)
        var closed = 0
        restore.setContent {
            var value by rememberSaveable { mutableStateOf("") }
            YardTheme {
                MoneyEntrySheet(
                    "Edit category",
                    "Save",
                    false,
                    true,
                    {},
                    { closed++ },
                    dirty = value.isNotBlank(),
                    keyValue = "J$ 100",
                ) {
                    OutlinedTextField(value, { value = it })
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Typed before rotation")
        // Close the keyboard before moving to a stage intended only for the summary.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        stage("Full").performSemanticsAction(SemanticsActions.Collapse)
        stage("Half").assertExists().performSemanticsAction(SemanticsActions.Collapse)
        stage("Peek").assertExists()
        restore.emulateSavedInstanceStateRestore()
        stage("Peek").assertExists().performSemanticsAction(SemanticsActions.Expand)
        stage("Half").performSemanticsAction(SemanticsActions.Expand)
        compose.onNode(hasSetTextAction()).assertTextEquals("Typed before rotation")
        stage("Full").performSemanticsAction(SemanticsActions.Dismiss)
        compose.onNodeWithText("Keep editing").performClick()
        compose.runOnIdle { assertEquals(0, closed) }
        stage("Full").performSemanticsAction(SemanticsActions.Dismiss)
        compose.onNodeWithText("Discard").performClick()
        compose.runOnIdle { assertEquals(1, closed) }
    }
}
