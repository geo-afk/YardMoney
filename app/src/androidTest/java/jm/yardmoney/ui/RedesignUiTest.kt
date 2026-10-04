package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import jm.yardmoney.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RedesignUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun billCardsOpenTheirOwnEditorAndPayIsSeparate() {
        val first = Commitment("one", "one", "Internet", "BILL", 400000, "2026-10-10")
        val second = Commitment("two", "two", "Water", "BILL", 150000, "2026-10-12")
        var edited: String? = null
        var paid: String? = null
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ReservationList(
                        listOf(CommitmentBalance(first, 0), CommitmentBalance(second, 0)),
                        pay = { paid = it.id },
                        edit = { edited = it.id },
                    )
                }
            }
        }
        compose.onNodeWithText("Water").performScrollTo().performClick()
        assertEquals("two", edited)
        assertNull(paid)
        compose.onNodeWithText("Internet").performScrollTo().performClick()
        assertEquals("one", edited)
        compose.onAllNodesWithText("Pay")[0].performClick()
        assertEquals("one", paid)
        compose.onNodeWithText("Edit reservation").assertDoesNotExist()
    }

    @Test
    fun editorStateFollowsDifferentInitialRecordInsteadOfRememberingTheFirst() {
        compose.setContent {
            var second by remember { mutableStateOf(false) }
            MaterialTheme {
                SimpleForm(
                    "Edit this reservation",
                    listOf("Name"),
                    listOf(if (second) "Water" else "Internet"),
                    false,
                    {},
                ) { _, _ ->
                    second = true
                }
            }
        }
        compose.onNodeWithText("Internet").assertExists()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Water").assertExists()
        compose.onNodeWithText("Internet").assertDoesNotExist()
    }

    @Test
    fun accountPickerCanReturnToCombinedScope() {
        var result: String? = null
        compose.setContent {
            MaterialTheme {
                IdentityPicker(
                    "Viewing",
                    "cash",
                    listOf(IdentityOption("", "All accounts"), IdentityOption("cash", "Cash")),
                ) {
                    result = it
                }
            }
        }
        compose.onNodeWithText("Cash").performClick()
        compose.onNodeWithText("All accounts").performClick()
        assertEquals("", result)
    }

    @Test
    fun transactionTypeSelectionChangesTheSelectedSemantics() {
        compose.setContent {
            MaterialTheme {
                var type by remember { mutableStateOf("EXPENSE") }
                TransactionTypePicker(type) { type = it }
            }
        }
        compose.onNodeWithText("Transfer").performClick()
        compose.onNodeWithText("Transfer").assertIsSelected()
        compose.onNodeWithText("Expense").assertIsNotSelected()
    }
}
