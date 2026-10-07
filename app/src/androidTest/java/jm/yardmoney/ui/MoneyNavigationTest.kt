package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MoneyNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun allDestinationsRemainReachableAndSelectionIsAccessible() {
        var selected by mutableStateOf("Home")
        compose.setContent {
            YardTheme {
                Box(Modifier.width(360.dp)) { MoneyNavigation(selected, { selected = it }, {}) }
            }
        }
        compose.onNodeWithContentDescription("Home").assertIsSelected()
        listOf("Activity", "Plan", "Shop", "More", "Home").forEach {
            compose
                .onNodeWithContentDescription(it)
                .assertIsDisplayed()
                .performClick()
                .assertIsSelected()
            assertEquals(it, selected)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun quickAddHasAnEqualTargetAndRespectsBusyState() {
        var busy by mutableStateOf(false)
        var added = 0
        lateinit var inputMode: InputModeManager
        compose.setContent {
            inputMode = LocalInputModeManager.current
            YardTheme {
                Box(Modifier.width(360.dp)) {
                    MoneyNavigation("Home", {}, { added++ }, addEnabled = !busy)
                }
            }
        }
        val add = compose.onNodeWithContentDescription("Quick Add")
        add.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, added)
        // Buttons accept keyboard focus in keyboard mode, matching a connected keyboard.
        compose.runOnIdle { assertTrue(inputMode.requestInputMode(InputMode.Keyboard)) }
        try {
            val home = compose.onNodeWithContentDescription("Home")
            home.performSemanticsAction(SemanticsActions.RequestFocus) { assertTrue(it()) }
            home.assertIsFocused()
            home.performKeyInput { repeat(3) { pressKey(Key.Tab) } }
            add.assertIsFocused()
            add.performKeyInput { pressKey(Key.Enter) }
            assertEquals(2, added)
        } finally {
            compose.runOnIdle { inputMode.requestInputMode(InputMode.Touch) }
        }
        val bounds = add.getUnclippedBoundsInRoot()
        val planBounds = compose.onNodeWithContentDescription("Plan").getUnclippedBoundsInRoot()
        assertEquals((planBounds.right - planBounds.left).value, (bounds.right - bounds.left).value, .1f)
        assertEquals((planBounds.bottom - planBounds.top).value, (bounds.bottom - bounds.top).value, .1f)
        compose.runOnIdle { busy = true }
        add.assertIsNotEnabled()
    }

    @Test
    fun quickAddSelectionDoesNotChangeTheUnderlyingTab() {
        var tab by mutableStateOf("Plan")
        var open by mutableStateOf(false)
        compose.setContent {
            YardTheme { Box(Modifier.width(360.dp)) {
                MoneyNavigation(if (open) "Quick Add" else tab, { tab = it }, { open = true })
            } }
        }
        compose.onNodeWithText("Quick Add").performTouchInput { click() }
        compose.onNodeWithContentDescription("Quick Add").assertIsSelected()
        compose.onNodeWithContentDescription("Plan").assertIsNotSelected()
        assertEquals("Plan", tab)
        compose.runOnIdle { open = false }
        compose.onNodeWithContentDescription("Quick Add").assertIsNotSelected()
        compose.onNodeWithContentDescription("Plan").assertIsSelected()
        compose.onNodeWithTag("Navigation Quick Add icon", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithContentDescription("Quick Add").assertIsSelected()
        assertEquals("Plan", tab)
    }

    @Test
    fun smallPhoneAtDoubleFontScaleKeepsEveryTargetVisible() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) {
                YardTheme { Box(Modifier.width(320.dp)) { MoneyNavigation("Plan", {}, {}) } }
            }
        }
        listOf("Home", "Activity", "Plan", "Shop", "More", "Quick Add").forEach {
            compose
                .onNodeWithContentDescription(it)
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
            compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun quickAddRoutesIncomeExpenseTransferAndReceiptSeparately() {
        var visible by mutableStateOf(true)
        var action = ""
        compose.setContent {
            YardTheme {
                if (visible)
                    StagedEditSheet("Quick Add", "", false, false, { visible = false }) {
                        QuickAddContent("", QuickAddDraft(), emptyList(), emptyList(), emptyList(), emptyList(),
                            false, false, onLine = {}, onDraft = {}, onRepeat = { _, _ -> }, onSave = {},
                            onEdit = { action = "EXPENSE"; visible = false },
                            onAction = { action = it.name; visible = false })
                    }
            }
        }
        listOf(
                "Income" to "INCOME",
                "More details" to "EXPENSE",
                "Transfer" to "TRANSFER",
                "Scan receipt" to "SCAN",
                "Paste alert text" to "PASTE",
            )
            .forEach { (label, route) ->
                compose.runOnIdle { visible = true }
                compose.onNodeWithText(label).performScrollTo().performClick()
                compose.runOnIdle { assertEquals(route, action) }
            }
    }
}
