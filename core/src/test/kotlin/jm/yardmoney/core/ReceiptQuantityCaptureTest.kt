package jm.yardmoney.core

import org.junit.Assert.*
import org.junit.Test

class ReceiptQuantityCaptureTest {
    @Test
    fun defaultQuantityIsNotPresentedAsCaptured() {
        val parsed = ReceiptParser.parse("MARKET\nRice 12.00\nTOTAL 12.00")
        assertEquals("1", parsed.lines.single().quantity)
        assertFalse(parsed.lines.single().quantitySpecified)
    }

    @Test
    fun explicitQuantityColumnsRemainCapturedEvenForOne() {
        val parsed = ReceiptParser.parse("MARKET\nRice 1 12.00 12.00\nTOTAL 12.00")
        assertEquals("1", parsed.lines.single().quantity)
        assertTrue(parsed.lines.single().quantitySpecified)
    }
}
