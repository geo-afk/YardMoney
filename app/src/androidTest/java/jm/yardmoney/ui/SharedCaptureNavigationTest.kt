package jm.yardmoney.ui

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import jm.yardmoney.*
import jm.yardmoney.data.Profile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SharedCaptureNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun warmSharedIntentWaitsForUnlockAndReviewWithoutPosting() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "jm.yardmoney.testhost")
        val app = context.applicationContext as YardMoneyApplication
        val prefs = app.appearancePrefs()
        val lock = prefs.getBoolean(Prefs.LOCK, false)
        prefs.edit().putBoolean(Prefs.LOCK, false).commit()
        if (app.repository.dao.getProfile() == null) {
            val today = app.repository.today
            app.repository.onboard(Profile(name = "Capture test", typicalNetMinor = 0, frequency = "MONTHLY",
                nextPayday = today.plusDays(14).toString(), anchorDay = 20, secondDay = 28,
                needsBp = 5000, wantsBp = 3000, savingsBp = 2000,
                periodStart = today.toString(), budgetIncomeMinor = 0), 1000000)
        }
        val before = app.repository.dao.readTransactions().size
        try {
            // Keep the monitored action/type stable when singleTop replaces the payload.
            val launch = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            ActivityScenario.launch<MainActivity>(launch).use { scenario ->
                compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Quick Add").fetchSemanticsNodes().size == 1 }
                scenario.onActivity { activity ->
                    ViewModelProvider(activity)[LockSession::class.java].apply { locked = true; autoPrompt = false; promptInFlight = true }
                }
                compose.waitForIdle()
                scenario.onActivity { activity ->
                    activity.startActivity(Intent(activity, MainActivity::class.java).setAction(Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Payment J$600 at Taxi yesterday")
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
                compose.waitForIdle()
                compose.onNodeWithText("Review alert text").assertDoesNotExist()
                scenario.onActivity { activity ->
                    assertNotNull(ViewModelProvider(activity)[LockSession::class.java].pendingCapture)
                    ViewModelProvider(activity)[LockSession::class.java].apply { locked = false; promptInFlight = false }
                }
                compose.waitUntil(10000) { compose.onAllNodesWithText("Merchant: Taxi").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Review record").performScrollTo().performClick()
                compose.onNodeWithText("Record money").assertExists()
                compose.onNode(hasSetTextAction() and hasText("600.00")).assertExists()
                scenario.recreate()
                compose.waitUntil(10000) { compose.onAllNodesWithText("Record money").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Review alert text").assertDoesNotExist()
                scenario.onActivity { assertNull(ViewModelProvider(it)[LockSession::class.java].pendingCapture) }
                assertEquals(before, app.repository.dao.readTransactions().size)
            }
        } finally { prefs.edit().putBoolean(Prefs.LOCK, lock).commit() }
    }
}
