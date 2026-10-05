package jm.yardmoney.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import org.junit.Rule
import org.junit.Test

// https://developer.android.com/develop/ui/compose/accessibility/testing
// ATF complements font/RTL/keyboard tests; it does not replace a human TalkBack walkthrough.
class AccessibilityQualityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun keyboardAndDpadCanReachAndActivateMenuControls() {
        compose.setContent {
            YardTheme {
                var selected by remember { mutableStateOf("Home") }
                Surface { MoneyNavigation(selected, { selected = it }, {}) }
            }
        }
        // A full suite can launch this host before its window receives focus from the previous
        // test.
        compose.waitUntil(10000) { compose.activity.hasWindowFocus() }
        // Establish only the starting target, then exercise real D-pad and keyboard events.
        compose.onNodeWithContentDescription("Home").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.RequestFocus
        ) {
            it()
        }
        compose.onNodeWithContentDescription("Home").assertIsFocused()
        compose.onNodeWithContentDescription("Home").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithContentDescription("Activity").assertIsFocused()
        compose.onNodeWithContentDescription("Activity").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithContentDescription("Activity").assertIsSelected()
        compose.onNodeWithContentDescription("Activity").performKeyInput { pressKey(Key.Tab) }
        compose.onNodeWithContentDescription("Plan").assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun smallRtlNavigationKeepsLabelsAndAccessibleTargetsWithLargeText() {
        compose.setContent {
            YardTheme {
                val density = LocalDensity.current
                var selected by remember { mutableStateOf("Home") }
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, 2f),
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    Surface {
                        Box(Modifier.width(320.dp)) {
                            MoneyNavigation(selected, { selected = it }, {})
                        }
                    }
                }
            }
        }
        compose.enableAccessibilityChecks()
        compose.onRoot().tryPerformAccessibilityChecks()
        for (label in listOf("Home", "Activity", "Plan", "Shop", "More")) {
            compose
                .onNodeWithContentDescription(label)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .performClick()
                .assertIsSelected()
        }
    }
}
