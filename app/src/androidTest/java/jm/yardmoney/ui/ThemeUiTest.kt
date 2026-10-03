package jm.yardmoney.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

/** Uses the test host activity and restores preferences; never opens or clears the ledger. */
class ThemeUiTest {
    @get:Rule val compose = createComposeRule()
    private val prefs
        get() =
            InstrumentationRegistry.getInstrumentation()
                .targetContext
                .getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private var original: Map<String, *> = emptyMap<String, Any>()

    @Before
    fun savePreferences() {
        original = prefs.all
    }

    @After
    fun restorePreferences() {
        val edit = prefs.edit().clear()
        original.forEach { (key, value) ->
            when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
            }
        }
        edit.commit()
    }

    @Test
    fun smallWindowLargeTextAndWholeRowSelectionRemainUsable() {
        prefs.edit().putString("theme", "Light").putBoolean("dynamic", false).commit()
        compose.setContent {
            YardTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    Surface {
                        Column(
                            Modifier.width(320.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(
                                "A little clarity before payday.",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                "Typical pay guides your plan and never adds money automatically.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            var value by remember { mutableStateOf("") }
                            Field("Typical take-home pay (J$; optional)", value) { value = it }
                            var checked by remember { mutableStateOf(false) }
                            Tick("I verified the receipt total and Jamaican dollars", checked) {
                                checked = it
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("A little clarity before payday.").assertIsDisplayed()
        compose
            .onNodeWithText("I verified the receipt total and Jamaican dollars")
            .performScrollTo()
            .performClick()
        compose.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun persistedModesAndAccentChangesReachTheSharedTheme() {
        var color: androidx.compose.ui.graphics.Color? = null
        compose.setContent {
            YardTheme {
                color = MaterialTheme.colorScheme.background
                Surface { Text("Theme preview") }
            }
        }
        for (mode in listOf("Light", "Dark", "AMOLED")) {
            compose.runOnIdle {
                prefs
                    .edit()
                    .putString("theme", mode)
                    .putBoolean("dynamic", false)
                    .putInt("accent", 0xFFAD2857.toInt())
                    .apply()
            }
            compose.waitForIdle()
            compose.onNodeWithText("Theme preview").assertIsDisplayed()
            if (mode == "AMOLED")
                compose.runOnIdle { assertEquals(androidx.compose.ui.graphics.Color.Black, color) }
        }
    }

    @Test
    fun dynamicColorPreferenceHasSafeOlderAndroidFallback() {
        prefs.edit().putString("theme", "Light").putBoolean("dynamic", true).commit()
        compose.setContent { YardTheme { Surface { Text("Wallpaper theme preview") } } }
        compose.onNodeWithText("Wallpaper theme preview").assertIsDisplayed()
    }
}
