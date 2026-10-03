package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class InputSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun calendarDialogOpensAndOptionalDateCanBeCleared() {
        compose.setContent {
            YardTheme {
                Surface {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        var date by remember { mutableStateOf("2026-10-02") }
                        DateDropdown("Due date", date, true) { date = it }
                    }
                }
            }
        }
        compose.onNodeWithText("2 Oct 2026").performClick()
        compose.onNodeWithText("Use this date").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Clear date").performClick()
        compose.onNodeWithText("No date selected").assertIsDisplayed()
        compose.onNodeWithText("Use this date").assertDoesNotExist()
    }

    @Test
    fun cancellingCalendarPreservesExistingDate() {
        compose.setContent {
            YardTheme {
                Surface {
                    var date by remember { mutableStateOf("2024-02-29") }
                    DateDropdown("Due date", date) { date = it }
                }
            }
        }
        compose.onNodeWithText("29 Feb 2024").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("29 Feb 2024").assertIsDisplayed()
        compose.onNodeWithText("Use this date").assertDoesNotExist()
    }

    @Test
    fun currencyFieldShowsPrefixGroupingAndCents() {
        compose.setContent {
            YardTheme {
                Surface {
                    var amount by remember { mutableStateOf("1234.50") }
                    MoneyField("Amount (J$)", amount) { amount = it }
                }
            }
        }
        compose.onNodeWithText("J$ ", substring = true).assertIsDisplayed()
        compose.onNodeWithText("1,234.50").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("5678.90")
        compose.onNodeWithText("5,678.90").assertIsDisplayed()
    }

    @Test
    fun settingsStayCollapsedUntilTheirCategoryIsClicked() {
        compose.setContent {
            YardTheme {
                Surface {
                    var expanded by remember { mutableStateOf(false) }
                    SettingsGroup(
                        "Appearance",
                        "Theme and accent",
                        Icons.Default.Palette,
                        expanded,
                        { expanded = !expanded },
                    ) {
                        Text("Theme controls")
                    }
                }
            }
        }
        compose.onNodeWithText("Theme controls").assertDoesNotExist()
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithText("Theme controls").assertIsDisplayed()
        compose.onNodeWithText("Appearance").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Theme controls").assertDoesNotExist()
    }
}
