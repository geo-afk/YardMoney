package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FormDropdownUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dropdownMatchesAnchorWidthAndPreservesOptionIds() {
        compose.setContent {
            YardTheme {
                Surface {
                    // Match the bounded content used by real pages; keep clear of landscape
                    // cutouts.
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(16.dp)) {
                        var selected by remember { mutableStateOf("cash") }
                        Column(Modifier.fillMaxWidth().testTag("anchor")) {
                            DropdownField(
                                "Account",
                                selected,
                                linkedMapOf("cash" to "Cash", "stash" to "Savings"),
                            ) {
                                selected = it
                            }
                        }
                        Text("Selected: " + selected)
                    }
                }
            }
        }
        val anchorWidth = compose.onNodeWithTag("anchor").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithText("Cash").performClick()
        val optionWidth = compose.onNodeWithText("Savings").fetchSemanticsNode().boundsInRoot.width
        assertEquals(anchorWidth, optionWidth, 2f)
        compose.onNodeWithText("Savings").performClick()
        compose.onNodeWithText("Selected: stash").assertIsDisplayed()
        compose.onNodeWithText("Cash").assertDoesNotExist()
    }

    @Test
    fun moneyEntryScrollsWhileSaveActionStaysVisible() {
        var saves = 0
        compose.setContent {
            YardTheme {
                MoneyEntrySheet("Record money", "Save expense", false, true, { saves++ }, {}) {
                    repeat(20) { index ->
                        MoneyEntrySection("Detail " + (index + 1)) { Text("Long form content") }
                    }
                }
            }
        }
        compose.onNodeWithText("Save expense").assertIsDisplayed()
        compose.onNodeWithText("Detail 20").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save expense").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }
}
