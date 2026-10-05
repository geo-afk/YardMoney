package jm.yardmoney

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import org.junit.*
import org.junit.Assert.*

class AppModelStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var store: ViewModelStore
    private lateinit var model: AppModel

    @Before
    fun create() {
        instrumentation.runOnMainSync {
            store = ViewModelStore()
            val app = instrumentation.targetContext.applicationContext as YardMoneyApplication
            model =
                ViewModelProvider(
                    store,
                    ViewModelProvider.AndroidViewModelFactory.getInstance(app),
                )[AppModel::class.java]
        }
        waitUntil { !model.busy.value }
    }

    @After
    fun close() {
        instrumentation.runOnMainSync { store.clear() }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("State failed to settle", condition())
    }

    @Test
    fun doubleSubmissionRunsOnceAndAcknowledgesOnlyAfterCommit() {
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<Unit>()
        val done = CountDownLatch(1)
        var duplicate = false
        instrumentation.runOnMainSync {
            model.act(done = { done.countDown() }, successMessage = "Committed") {
                entered.countDown()
                gate.await()
            }
            model.act { duplicate = true }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertTrue(model.busy.value)
        assertNull(model.success.value)
        gate.complete(Unit)
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertFalse(duplicate)
        assertFalse(model.busy.value)
        assertEquals("Committed", model.success.value)
    }

    @Test
    fun rejectedSaveKeepsDialogOpenAndCanBeRetried() {
        var dismissed = false
        instrumentation.runOnMainSync {
            model.act(done = { dismissed = true }) {
                throw IllegalArgumentException("Choose a valid account.")
            }
        }
        waitUntil { !model.busy.value }
        assertFalse(dismissed)
        assertEquals("Choose a valid account.", model.error.value)
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync { model.act(done = { done.countDown() }) {} }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertNull(model.error.value)
        assertEquals("Saved", model.success.value)
    }

    @Test
    fun clearingViewModelCancelsWorkAndClearsProgressWithoutSuccess() {
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<Unit>()
        instrumentation.runOnMainSync {
            model.act {
                model.scanProgress.value = "Reading"
                entered.countDown()
                gate.await()
            }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { store.clear() }
        waitUntil { !model.busy.value }
        assertNull(model.scanProgress.value)
        assertNull(model.success.value)
        assertNull(model.error.value)
    }
}
