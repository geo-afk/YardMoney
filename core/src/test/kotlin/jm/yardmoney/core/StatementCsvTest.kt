package jm.yardmoney.core

import java.io.StringReader
import java.io.ByteArrayInputStream
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class StatementCsvTest {
    private val today = LocalDate.of(2026, 10, 6)
    private val mapping = StatementMapping(0, 1, 2, dateFormat = "dd/MM/yyyy")
    private fun document(csv: String) = StatementCsv.read(StringReader(csv))
    @Test fun quotesBomCrlfEmbeddedNewlinesAndDelimitersAreStreamed() {
        val d = document("\uFEFFdate,description,amount\r\n05/10/2026,\"Hi-Lo, \"\"branch\"\"\nmarket\",\"(1,200.50)\"\r\n")
        assertEquals(',', d.delimiter)
        assertTrue(d.likelyHeader)
        assertEquals("Hi-Lo, \"branch\"\nmarket", d.records[1][1])
        assertEquals(-120050L, StatementCsv.preview(d, mapping, "cash", emptyList(), today).single().signedMinor)
        listOf(';', '\t').forEach { sep ->
            val parsed = document("date${sep}description${sep}amount\n05/10/2026${sep}Taxi${sep}-600\n")
            assertEquals(sep, parsed.delimiter)
            assertEquals(-60000L, StatementCsv.preview(parsed, mapping, "cash", emptyList(), today).single().signedMinor)
        }
    }
    @Test fun dateFormatsNeedAnExplicitChoiceAndInvalidDatesStayInvalid() {
        val d = document("date,description,amount\n01/02/2026,Taxi,-600\n31/02/2026,Cafe,-200\n07/10/2026,Future,-300")
        assertThrows(IllegalArgumentException::class.java) { StatementCsv.preview(d, mapping.copy(dateFormat = ""), "cash", emptyList(), today) }
        val dm = StatementCsv.preview(d, mapping, "cash", emptyList(), today)
        val md = StatementCsv.preview(d, mapping.copy(dateFormat = "MM/dd/yyyy"), "cash", emptyList(), today)
        assertEquals(LocalDate.of(2026, 2, 1), dm[0].date)
        assertEquals(LocalDate.of(2026, 1, 2), md[0].date)
        assertNull(dm[1].date); assertNull(dm[2].date)
        val iso = document("date,description,amount\n2026-10-05,Taxi,-600")
        assertNotNull(StatementCsv.preview(iso, mapping.copy(dateFormat = "yyyy-MM-dd"), "cash", emptyList(), today).single().date)
    }
    @Test fun splitDebitCreditBalancesAndHeaderlessFiles() {
        val d = document("date,description,debit,credit,balance\n05/10/2026,Taxi,600,,400\n05/10/2026,Pay,,1000,1400\n05/10/2026,Both,2,3,1401\n05/10/2026,Balance,2,,broken")
        val rows = StatementCsv.preview(d, StatementMapping(0,1,-1,2,3,4,"dd/MM/yyyy"), "cash", emptyList(), today)
        assertEquals(-60000L, rows[0].signedMinor); assertEquals(100000L, rows[1].signedMinor)
        assertNull(rows[2].signedMinor); assertTrue(rows[3].issues.isNotEmpty())
        val bare = document("05/10/2026,Taxi,-600")
        assertFalse(bare.likelyHeader)
        assertEquals(1, StatementCsv.preview(bare, mapping.copy(header = false), "cash", emptyList(), today).size)
    }
    @Test fun duplicateFingerprintsAreScopedAndWhitespaceInsensitive() {
        val d = document("date,description,amount\n05/10/2026,  TAXI   ride ,-600\n05/10/2026,taxi ride,-600\n05/10/2026,taxi ride,-601")
        val existing = listOf(StatementExisting("cash", today.minusDays(1), -60000, "Taxi ride"))
        val rows = StatementCsv.preview(d, mapping, "cash", existing, today)
        assertTrue(rows[0].duplicate); assertTrue(rows[1].duplicate); assertFalse(rows[2].duplicate)
        val other = StatementCsv.preview(d, mapping, "card", existing, today)
        assertFalse(other[0].duplicate); assertTrue(other[1].duplicate)
        assertNotEquals(rows[0].hash, rows[1].hash)
        assertEquals(StatementCsv.batchId("cash",mapping,d), StatementCsv.batchId("cash",mapping,d))
        assertNotEquals(StatementCsv.batchId("cash",mapping,d), StatementCsv.batchId("card",mapping,d))
    }
    @Test fun byteLimitIsEnforcedWhileStreamingWithoutAnAllocatedRawFile() {
        val input = object : java.io.InputStream() {
            var remaining = StatementCsv.MAX_BYTES + 1
            override fun read(): Int = if (remaining-- > 0) '\n'.code else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val n = minOf(length.toLong(), remaining).toInt()
                java.util.Arrays.fill(bytes, offset, offset+n, '\n'.code.toByte())
                remaining -= n
                return n
            }
        }
        val error = assertThrows(IllegalArgumentException::class.java) { StatementCsv.read(input) }
        assertEquals("Choose a CSV file smaller than 20 MB.", error.message)
    }
    @Test fun malformedAndOversizedInputIsRejectedWithoutPartialRows() {
        listOf("date,description,amount\n1,\"open", "date,description,amount\n1,\"closed\"junk,2", "", "x".repeat(10001)).forEach {
            assertThrows(it.take(30), IllegalArgumentException::class.java) { document(it) }
        }
        val hugeRows = "date,description,amount\n" + "05/10/2026,Taxi,-1\n".repeat(20001)
        assertThrows(IllegalArgumentException::class.java) { document(hugeRows) }
        assertThrows(java.nio.charset.MalformedInputException::class.java) { StatementCsv.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }
        val bad = document("date,description,amount\n05/10/2026,Taxi,0\n05/10/2026,Cafe,\"1,20\"\n05/10/2026,Huge,99999999999999999999999")
        assertTrue(StatementCsv.preview(bad, mapping, "cash", emptyList(), today).all { it.issues.isNotEmpty() })
        assertThrows(IllegalArgumentException::class.java) { StatementCsv.preview(bad, mapping.copy(descriptionColumn=0), "cash", emptyList(), today) }
    }
}
