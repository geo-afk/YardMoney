package jm.yardmoney.core

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class RobustInputTest {
    @Test
    fun enormousAmountsAndEstimatesHaveActionableErrors() {
        for (text in
            listOf("9".repeat(5000), "999999999999999999999999999.99", "1000000000000.01")) {
            val failure = runCatching { Money.parse(text) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals("This amount is too large.", failure?.message)
        }
        val failure = runCatching {
            Quantity.estimate(Money.MAX_MINOR, "1000000")
        }
            .exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals("The estimate is too large.", failure?.message)
    }

    @Test
    fun monetaryRoundTripsStayExactAcrossLocalesAndBoundaries() {
        val original = Locale.getDefault()
        try {
            for (locale in
                listOf(
                    Locale.US,
                    Locale.FRANCE,
                    Locale.forLanguageTag("ar-JM"),
                    Locale.forLanguageTag("tr-TR"),
                )) {
                Locale.setDefault(locale)
                for (minor in
                    listOf(0L, 1L, 99L, 100L, 101L, Money.MAX_MINOR, -1L, -Money.MAX_MINOR)) {
                    assertEquals(minor, Money.parse(Money.input(minor), allowNegative = true))
                    assertEquals(
                        minor,
                        Money.parse(Money.format(minor).removePrefix("J$"), allowNegative = true),
                    )
                }
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun currencyGarbageCannotBecomeAnAccidentalPurchase() {
        for (text in
            listOf(
                "",
                " ",
                "NaN",
                "Infinity",
                "1e5",
                "$12",
                "€12",
                "💰",
                "1,2",
                "-1",
                "0.001",
                "1\u00002",
            )) {
            assertTrue(text, runCatching { Money.positive(text) }.isFailure)
        }
        assertTrue(runCatching { Money.positive("0") }.isFailure)
        assertEquals(1L, Money.positive("0.01"))
    }
}
