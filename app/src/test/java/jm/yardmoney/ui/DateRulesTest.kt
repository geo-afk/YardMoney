package jm.yardmoney.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateRulesTest {
    private val today = LocalDate.of(2026, 10, 5)

    @Test
    fun labelsMapToTheWindowTheRepositoryEnforces() {
        assertEquals(DateWindow.UpToToday, dateWindowFor("Date (YYYY-MM-DD)"))
        assertEquals(DateWindow.UpToToday, dateWindowFor("Purchase date (YYYY-MM-DD)"))
        assertEquals(DateWindow.FromToday, dateWindowFor("Next payday (YYYY-MM-DD)"))
        assertEquals(DateWindow.Any, dateWindowFor("Due date (YYYY-MM-DD; optional)"))
        assertEquals(DateWindow.Any, dateWindowFor("Due date"))
        assertEquals(DateWindow.Any, dateWindowFor("Name"))
    }

    @Test
    fun purchaseDatesCannotBeInTheFuture() {
        assertTrue(DateWindow.UpToToday.allows(today, today))
        assertTrue(DateWindow.UpToToday.allows(today.minusYears(3), today))
        assertFalse(DateWindow.UpToToday.allows(today.plusDays(1), today))
        assertTrue(DateWindow.UpToToday.allowsYear(2026, today))
        assertFalse(DateWindow.UpToToday.allowsYear(2027, today))
    }

    @Test
    fun paydaysMustBeTodayOrLaterWithinFiveYears() {
        assertTrue(DateWindow.FromToday.allows(today, today))
        assertTrue(DateWindow.FromToday.allows(today.plusYears(5), today))
        assertFalse(DateWindow.FromToday.allows(today.minusDays(1), today))
        assertFalse(DateWindow.FromToday.allows(today.plusYears(5).plusDays(1), today))
        assertFalse(DateWindow.FromToday.allowsYear(2025, today))
        assertTrue(DateWindow.FromToday.allowsYear(2031, today))
        assertFalse(DateWindow.FromToday.allowsYear(2032, today))
    }

    @Test
    fun anyWindowAllowsEverything() {
        assertTrue(DateWindow.Any.allows(LocalDate.of(1999, 1, 1), today))
        assertTrue(DateWindow.Any.allowsYear(2100, today))
    }
}
