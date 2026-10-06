package jm.yardmoney.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvSafetyTest {
    @Test
    fun formulaStartersGetALeadingApostrophe() {
        assertEquals("'=HYPERLINK(\"x\")", spreadsheetSafe("=HYPERLINK(\"x\")"))
        assertEquals("'+1 cash", spreadsheetSafe("+1 cash"))
        assertEquals("'-5% coupon", spreadsheetSafe("-5% coupon"))
        assertEquals("'@SUM(A1)", spreadsheetSafe("@SUM(A1)"))
        assertEquals("'\tTabbed", spreadsheetSafe("\tTabbed"))
        assertEquals("'\rReturn", spreadsheetSafe("\rReturn"))
    }

    @Test
    fun ordinaryTextIsUntouched() {
        assertEquals("Supermarket", spreadsheetSafe("Supermarket"))
        assertEquals("2026-10-05", spreadsheetSafe("2026-10-05"))
        assertEquals("Total = 5", spreadsheetSafe("Total = 5"))
        assertEquals("", spreadsheetSafe(""))
    }
}
