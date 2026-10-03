package jm.yardmoney.core

import org.junit.Assert.*
import org.junit.Test

class ReceiptFormatsTest {
    @Test
    fun wrappedItemAndFollowingPrice() {
        val r = ReceiptParser.parse("CORNER SHOP\nLONG GRAIN\nRICE\n125.00\nTOTAL\n125.00")
        assertEquals(1, r.lines.size)
        assertEquals("LONG GRAIN RICE", r.lines.single().name)
        assertEquals(12500L, r.totalMinor)
    }

    @Test
    fun quantityBelowItemIsNotAnotherPurchase() {
        val r = ReceiptParser.parse("SUPERMARKET\nRICE 250.00\n2 x 125.00\nTOTAL 250.00")
        assertEquals(1, r.lines.size)
        assertEquals("2", r.lines.single().quantity)
        assertEquals(12500L, r.lines.single().unitPriceMinor)
    }

    @Test
    fun wholesaleExtendedPriceAndTaxes() {
        val r =
            ReceiptParser.parse(
                "WHOLESALE\nBAG OF FLOUR\n2 x 100.00 200.00\nSUBTOTAL 200.00\nGCT 30.00\nDISCOUNT 10.00\nTOTAL 220.00\nCASH 300.00\nCHANGE 80.00"
            )
        assertEquals(1, r.lines.size)
        assertEquals(20000L, r.lines.single().totalMinor)
        assertEquals("2", r.lines.single().quantity)
        assertEquals(20000L, r.subtotalMinor)
        assertEquals(3000L, r.taxMinor)
        assertEquals(1000L, r.discountMinor)
        assertTrue(
            ReceiptParser.reconciles(
                r.lines.mapNotNull { it.totalMinor },
                r.adjustmentMinor,
                r.totalMinor!!,
            )
        )
    }

    @Test
    fun jamaicanMetadataAndDayFirstDate() {
        val r =
            ReceiptParser.parse(
                "PHARMACY\n10 Hope Road Kingston\n02/10/2026 14:32\nReceipt No: RX12345\nTransaction # TX987\nSOAP 100.00\nTOTAL 100.00\nVISA"
            )
        assertEquals("2026-10-02", r.date.toString())
        assertEquals("14:32", r.time)
        assertEquals("RX12345", r.receiptNumber)
        assertEquals("TX987", r.transactionNumber)
        assertTrue(r.location.contains("Kingston"))
        assertEquals("VISA", r.paymentMethod)
        assertEquals(1, r.lines.size)
    }

    @Test
    fun fadedNumericGlyphsAreOnlyCorrectedInsideAmounts() {
        val r = ReceiptParser.parse("STORE\nSOAP 1O0.OO\nTOTAL 1O0.OO")
        assertEquals(10000L, r.totalMinor)
        assertEquals("SOAP", r.lines.single().name)
        assertTrue(r.lines.single().needsReview)
    }

    @Test
    fun decimalCommaAmounts() {
        val r = ReceiptParser.parse("STORE\nMILK 1.250,00\nTOTAL 1.250,00")
        assertEquals(125000L, r.totalMinor)
    }

    @Test
    fun repeatedIdenticalPurchasesRemainSeparate() {
        val r = ReceiptParser.parse("STORE\nSOAP 100.00\nSOAP 100.00\nTOTAL 200.00")
        assertEquals(2, r.lines.size)
    }

    @Test
    fun longReceiptKeepsEveryItem() {
        val raw =
            "STORE\n" + (1..120).joinToString("\n") { "Product $it 10.00" } + "\nTOTAL 1200.00"
        val r = ReceiptParser.parse(raw)
        assertEquals(120, r.lines.size)
        assertEquals(120000L, Money.sum(r.lines.mapNotNull { it.totalMinor }))
    }

    @Test
    fun multipleColumnsMatchQuantityUnitPriceAndExtendedTotal() {
        val r = ReceiptParser.parse("HARDWARE\nCEMENT 2 100.00 200.00\nTOTAL 200.00")
        assertEquals("CEMENT", r.lines.single().name)
        assertEquals("2", r.lines.single().quantity)
        assertEquals(10000L, r.lines.single().unitPriceMinor)
        assertEquals(20000L, r.lines.single().totalMinor)
    }

    @Test
    fun restaurantAndGasLayoutsKeepUsefulAmounts() {
        val restaurant =
            ReceiptParser.parse("RESTAURANT\nLUNCH\n450.00\nSERVICE CHARGE 45.00\nTOTAL DUE 495.00")
        assertEquals(1, restaurant.lines.size)
        assertEquals(4500L, restaurant.taxMinor)
        assertEquals(49500L, restaurant.totalMinor)
        val gas = ReceiptParser.parse("FUEL STATION\nUNLEADED 2500.00\nTOTAL SALE 2500.00\nDEBIT")
        assertEquals(250000L, gas.totalMinor)
        assertEquals("DEBIT", gas.paymentMethod)
    }

    @Test
    fun skuPrefixIsNotAnEnormousQuantity() {
        val r = ReceiptParser.parse("PHARMACY\n123456 SOAP 100.00\nTOTAL 100.00")
        assertEquals("1", r.lines.single().quantity)
    }

    @Test
    fun inclusiveTaxMismatchRequiresReview() {
        val r = ReceiptParser.parse("STORE\nSOAP 115.00\nGCT 15.00\nTOTAL 115.00")
        assertTrue(r.warnings.any { it.contains("do not reconcile") })
    }
}
