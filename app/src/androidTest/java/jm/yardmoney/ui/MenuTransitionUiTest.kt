package jm.yardmoney.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MenuTransitionUiTest {
    @get:Rule val compose = createComposeRule()
    private val destination = mutableStateOf("Home")

    private fun show(style: MotionStyle) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMotion provides style) {
                    Box(Modifier.fillMaxSize().background(Color.White).testTag("viewport")) {
                        MenuTransition(destination.value) { shown ->
                            Box(
                                Modifier.fillMaxSize()
                                    .background(if (shown == "Home") Color.Red else Color.Blue)
                            ) {
                                Text(shown)
                            }
                        }
                    }
                }
            }
        }
    }

    // Compare captured frames rather than raw RGB constants: physical displays can tone-map colors.
    private fun centerPixel(): Color {
        val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    @Test
    fun calmRendersDistinctPagesDuringTransitionAndFadesPixelsBeforeSettling() {
        show(MotionStyle.Calm)
        val initial = centerPixel()
        compose.runOnIdle { destination.value = "Shop" }
        compose.mainClock.advanceTimeBy(32)
        // Both identities must remain distinct while AnimatedContent owns their lifetimes.
        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithText("Shop").assertExists()
        compose.mainClock.advanceTimeBy(96)
        val intermediate = centerPixel()
        assertNotEquals(initial, intermediate)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithText("Shop").assertIsDisplayed()
        val settled = centerPixel()
        assertNotEquals(initial, settled)
        assertNotEquals(intermediate, settled)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(settled, centerPixel())
        assertTrue(settled.blue > settled.red)
    }

    @Test
    fun rapidMenuChangesSettleOnLatestDestination() {
        show(MotionStyle.Slide)
        compose.runOnIdle { destination.value = "Activity" }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { destination.value = "Plan" }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { destination.value = "More" }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("More").assertIsDisplayed()
        listOf("Home", "Activity", "Plan").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun expressiveFinishesAtFullSizeAndRemovesOutgoingPage() {
        show(MotionStyle.Expressive)
        compose.runOnIdle { destination.value = "Plan" }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Home").assertExists()
        compose.onNodeWithText("Plan").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithText("Plan").assertIsDisplayed()
        val settled = centerPixel()
        compose.mainClock.advanceTimeBy(400)
        assertEquals(settled, centerPixel())
        assertTrue(settled.blue > settled.red)
    }

    @Test
    fun offSwitchesWithoutAnAnimatedOverlap() {
        show(MotionStyle.Off)
        compose.runOnIdle { destination.value = "Shop" }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithText("Shop").assertIsDisplayed()
        val settled = centerPixel()
        compose.mainClock.advanceTimeBy(400)
        assertEquals(settled, centerPixel())
        assertTrue(settled.blue > settled.red)
    }
}
