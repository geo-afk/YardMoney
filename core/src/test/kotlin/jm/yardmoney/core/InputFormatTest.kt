package jm.yardmoney.core

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class InputFormatTest {
    @Test
    fun wholePercentagesHaveNoDecimalSuffix() {
        assertEquals("50%", InputFormat.percent(5000))
        assertEquals("30%", InputFormat.percent(3000))
        assertEquals("20%", InputFormat.percent(2000))
        assertEquals("12.5%", InputFormat.percent(1250))
        assertEquals("33.33%", InputFormat.percent(3333))
        assertEquals("0%", InputFormat.percent(0))
    }

    @Test
    fun currencyPasteKeepsExactCents() {
        assertEquals(123450L, Money.parse(InputFormat.currencyText("J$1,234.50")))
        assertEquals(123450L, Money.parse(InputFormat.currencyText("$1,234.50")))
        assertEquals(-1250L, Money.parse(InputFormat.currencyText("J$-12.50"), true))
        assertEquals("0.50", InputFormat.currencyText(".50"))
        assertEquals(125000L, Money.parse("1250."))
        assertTrue(runCatching { Money.parse("1250.5.") }.isFailure)
    }

    @Test
    fun ambiguousCommaDecimalIsRejectedRatherThanMultiplied() {
        assertEquals("12,50", InputFormat.currencyText("12,50"))
        assertTrue(runCatching { Money.parse(InputFormat.currencyText("12,50")) }.isFailure)
    }

    @Test
    fun moneyDisplayGroupsAndShowsCentsWithoutRoundingInput() {
        assertEquals("1,234.50", InputFormat.moneyDisplay("1234.5", false).text)
        assertEquals("1,234.5", InputFormat.moneyDisplay("1234.5", true).text)
        assertEquals("-1,234.00", InputFormat.moneyDisplay("-1234", false).text)
        assertEquals("", InputFormat.moneyDisplay("", false).text)
    }

    @Test
    fun groupingCursorMappingsStayBoundedMonotoneAndReversible() {
        for (raw in
            listOf("0", "1234", "1234567.89", "-1234567.89", "1.", "-", "", "bad")) for (editing in
            listOf(true, false)) {
            val r = InputFormat.moneyDisplay(raw, editing)
            assertEquals(raw.length + 1, r.originalToDisplay.size)
            assertEquals(r.text.length + 1, r.displayToOriginal.size)
            assertTrue(r.originalToDisplay.zipWithNext().all { it.first <= it.second })
            assertTrue(r.displayToOriginal.zipWithNext().all { it.first <= it.second })
            for (i in 0..raw.length) {
                assertEquals(i, r.displayToOriginal[r.originalToDisplay[i]])
            }
        }
    }

    @Test
    fun monthAndYearSelectionsClampOnlyInvalidDays() {
        assertEquals(
            LocalDate.of(2026, 2, 28),
            InputFormat.changeDate(LocalDate.of(2026, 1, 31), month = 2),
        )
        assertEquals(
            LocalDate.of(2028, 2, 29),
            InputFormat.changeDate(LocalDate.of(2028, 1, 31), month = 2),
        )
        assertEquals(
            LocalDate.of(2027, 2, 28),
            InputFormat.changeDate(LocalDate.of(2028, 2, 29), year = 2027),
        )
        assertEquals(
            LocalDate.of(2026, 10, 20),
            InputFormat.changeDate(LocalDate.of(2026, 10, 2), day = 20),
        )
    }
}
