package jm.yardmoney.core

import org.junit.Assert.*
import org.junit.Test

class CsvTest {
    @Test
    fun preservesCommasQuotesAndLineBreaks() {
        assertEquals("\"Shop, \"\"A\"\"\nKingston\"", Csv.text("Shop, \"A\"\nKingston"))
    }

    @Test
    fun neutralizesFormulaCells() {
        listOf("=HYPERLINK(\"x\")", "+SUM(1,2)", "-1+2", "@SUM(1)", "  =1+1", "\t=1+1").forEach {
            assertTrue(Csv.text(it).startsWith("\"'"))
        }
    }

    @Test
    fun ordinaryTextRemainsReadable() {
        assertEquals("\"Groceries\",\"2026-10-02\"\r\n", Csv.row(listOf("Groceries", "2026-10-02")))
    }
}
