package jm.yardmoney.core

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AlertTextParserTest {
    private val today = LocalDate.of(2026, 10, 6)
    @Test fun genericSyntheticAlertsParseWithoutBankSpecificRules() {
        val cases = listOf(
            Triple("Payment J$1,200 at Market today", 120000L, "EXPENSE"),
            Triple("Purchase $1200.50 at Cafe yesterday", 120050L, "EXPENSE"),
            Triple("Debited JMD 600 to Taxi on 06/10", 60000L, "EXPENSE"),
            Triple("Withdrawal J$250 from ATM on 2026-10-05", 25000L, "EXPENSE"),
            Triple("Received JMD 5000 from Employer today", 500000L, "INCOME"),
            Triple("Credited $100.25 from Friend yesterday", 10025L, "INCOME"),
            Triple("Deposit J$3,000 from Cash on Monday", 300000L, "INCOME"),
            Triple("PURCHASE J$800 @ Hi-Lo today", 80000L, "EXPENSE"),
            Triple("Payment JMD 99.99 at Lunch on 05/10/2026", 9999L, "EXPENSE"),
            Triple("Received $12.30 from Family.", 1230L, "INCOME"),
            Triple("Payment J$25 at Cafe; ref 123456", 2500L, "EXPENSE"),
            Triple("Purchase J$999,999.99 at Store", 99999999L, "EXPENSE")
        )
        cases.forEach { (text, amount, kind) ->
            val p = AlertTextParser.parse(text, today)
            assertEquals(text, amount, p.amountMinor)
            assertEquals(text, kind, p.kind)
            assertNotNull(text, p.merchant)
        }
    }
    @Test fun missingAmbiguousAndInvalidFieldsStayUnspecified() {
        listOf("", "Payment at Cafe", "Payment J$0 at Cafe", "Payment J$-20 at Cafe",
            "Payment J$1,20 at Cafe", "Payment J$12.345 at Cafe", "J$20 paid; balance J$500",
            "JMD 9999999999999999999999999", "500 ref 1234", "x".repeat(10001)).forEach {
            assertNull(it, AlertTextParser.parse(it, today).amountMinor)
        }
        assertNull(AlertTextParser.parse("Payment and deposit J$200", today).kind)
        assertNull(AlertTextParser.parse("J$200 at Cafe", today).kind)
        assertNull(AlertTextParser.parse("Payment J$200", today).merchant)
        assertNull(AlertTextParser.parse("Payment J$200 at Cafe", today).date)
        listOf("31/02", "2026-10-07", "07/10/2026", "today yesterday").forEach {
            assertNull(it, AlertTextParser.parse("Payment J$200 at Cafe on $it", today).date)
        }
    }
    @Test fun datesAndMerchantPunctuationStayPredictable() {
        assertEquals(today.minusDays(1), AlertTextParser.parse("Payment J$200 at Hi-Lo yesterday", today).date)
        assertEquals("Hi-Lo", AlertTextParser.parse("Payment J$200 at Hi-Lo yesterday", today).merchant)
        assertEquals(today, AlertTextParser.parse("Received JMD200 from Jane on 06/10/2026", today).date)
        assertNull(AlertTextParser.parse("Payment J$200 at Shop to Cafe", today).date)
    }
}
