package jm.yardmoney.ui

import org.junit.Assert.*
import org.junit.Test

class FormDismissGateTest {
    @Test
    fun returningToTopDoesNotDismissEvenAfterLargeOverscroll() {
        val gate = FormDismissGate(120f)
        gate.begin(false)
        gate.pull(500f)
        assertFalse(gate.finish())
        gate.begin(true)
        gate.pull(121f)
        assertTrue(gate.finish())
    }

    @Test
    fun ShortPullAndDirectionReversalDoNotDismiss() {
        val gate = FormDismissGate(120f)
        gate.begin(true)
        gate.pull(119f)
        assertFalse(gate.finish())
        gate.begin(true)
        gate.pull(150f)
        gate.pull(-90f)
        assertFalse(gate.finish())
        assertFalse(gate.finish())
    }
}
