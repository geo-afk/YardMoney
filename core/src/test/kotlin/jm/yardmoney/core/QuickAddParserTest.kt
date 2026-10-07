package jm.yardmoney.core

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class QuickAddParserTest {
    private val today = LocalDate.of(2026, 10, 6)
    private val accounts = listOf(CaptureAccount("cash", "Cash"), CaptureAccount("card", "Card"),
        CaptureAccount("cu", "Credit Union"))

    @Test fun amountsAndDescriptionsAreExact() {
        val examples = listOf(
            "taxi 600 cash" to 60000L, "Hi-Lo 8,450 card yesterday" to 845000L,
            "lunch 1.2k" to 120000L, "rice 1,200" to 120000L,
            "rice 1200.50" to 120050L, "rice J$1,200" to 120000L,
            "rice $1200" to 120000L, "rice JMD 1200" to 120000L,
            "rice 0.01" to 1L, "rice 10K" to 1000000L,
            "600 TAXI CASH" to 60000L, "Rice 600 Credit Union" to 60000L,
            "  taxi   600  cash  " to 60000L, "taxi 600 today" to 60000L,
        )
        examples.forEach { (line, amount) ->
            assertEquals(line, amount, QuickAddParser.parse(line, accounts, today).amountMinor)
        }
        val taxi = QuickAddParser.parse("taxi 600 cash", accounts, today)
        assertEquals("taxi", taxi.description)
        assertEquals("cash", taxi.accountId)
        assertNull(taxi.date)
        assertEquals(today.minusDays(1), QuickAddParser.parse("600 yesterday", accounts, today).date)
    }

    @Test fun malformedAndAmbiguousInputNeverSuppliesAnAmount() {
        listOf("", "taxi", "taxi zero", "taxi -600", "taxi 0", "taxi 1,20",
            "taxi 1.234", "taxi 2..5", "taxi 600 700", "taxi 1,200k",
            "taxi 99999999999999999999999", "taxi 1.234k", "taxi +600")
            .forEach { assertNull(it, QuickAddParser.parse(it, accounts, today).amountMinor) }
    }

    @Test fun datesNeverMoveIntoTheFutureAndInvalidDatesStayMissing() {
        val valid = listOf("today" to today, "yesterday" to today.minusDays(1),
            "monday" to LocalDate.of(2026,10,5), "tuesday" to today,
            "Wednesday" to LocalDate.of(2026,9,30), "01/10" to LocalDate.of(2026,10,1))
        valid.forEach { (word, date) ->
            assertEquals(word, date, QuickAddParser.parse("taxi 600 $word", accounts, today).date)
        }
        listOf("31/02", "32/10", "07/10", "today yesterday").forEach {
            val parsed = QuickAddParser.parse("taxi 600 $it", accounts, today)
            assertNull(it, parsed.date)
            assertTrue(it, parsed.issues.isNotEmpty())
        }
    }

    @Test fun accountNamesRequireWholeTokensAndAmbiguousNamesNeedChoice() {
        assertNull(QuickAddParser.parse("cashier 600", accounts, today).accountId)
        assertNull(QuickAddParser.parse("taxi 600 Cash Credit Union", accounts, today).accountId)
        val nested = accounts + CaptureAccount("union", "Union")
        assertEquals("cu", QuickAddParser.parse("taxi 600 Credit Union", nested, today).accountId)
        val duplicated = accounts + CaptureAccount("cash2", "cash")
        val result = QuickAddParser.parse("taxi 600 cash", duplicated, today)
        assertNull(result.accountId)
        assertTrue(result.issues.isNotEmpty())
        assertNull(QuickAddParser.parse("a".repeat(1001), accounts, today).amountMinor)
    }
}
