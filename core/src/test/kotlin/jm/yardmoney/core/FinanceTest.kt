package jm.yardmoney.core

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FinanceTest {
    @Test
    fun commaGroupingMustBeValid() {
        listOf("1,,2", "12,34", "1,00.00").forEach {
            assertThrows(IllegalArgumentException::class.java) { Money.parse(it) }
        }
    }

    @Test
    fun quantitiesHaveBoundedPrecision() {
        listOf("0", "-1", "1e100", "0.0000001", "1000001").forEach {
            assertThrows(IllegalArgumentException::class.java) { Quantity.parse(it) }
        }
        assertEquals(BigDecimal("0.125"), Quantity.parse("0.125"))
    }

    @Test
    fun fractionalShoppingQuantityRoundsExactly() {
        assertEquals(151L, Quantity.estimate(301, "0.5"))
    }

    @Test
    fun exactMoneyAndFormatting() {
        assertEquals(125050L, Money.parse("1,250.50"))
        assertEquals("J$1,250.50", Money.format(125050))
        assertEquals(10L, Money.parse("0.10"))
    }

    @Test
    fun rejectsFractionalCents() {
        assertThrows(IllegalArgumentException::class.java) { Money.parse("1.001") }
    }

    @Test
    fun rejectsInvalidAndZeroExpense() {
        listOf("", "abc", "-1", "0").forEach {
            assertThrows(IllegalArgumentException::class.java) { Money.positive(it) }
        }
    }

    @Test
    fun boundsAmounts() {
        assertThrows(IllegalArgumentException::class.java) { Money.parse("1000000000001") }
    }

    @Test
    fun standardSplit() {
        assertEquals(listOf(4100000L, 2460000L, 1640000L), BudgetSplit().allocate(8200000))
    }

    @Test
    fun customSplit() {
        assertEquals(listOf(600L, 200L, 200L), BudgetSplit(6000, 2000, 2000).allocate(1000))
    }

    @Test
    fun rejectsInvalidSplits() {
        assertThrows(IllegalArgumentException::class.java) { BudgetSplit(6000, 2500, 2000) }
        assertThrows(IllegalArgumentException::class.java) { BudgetSplit(-1, 8001, 2000) }
    }

    @Test
    fun everyCentConserved() {
        for (income in 0L..10000L) {
            assertEquals(income, Money.sum(BudgetSplit().allocate(income)))
        }
    }

    @Test
    fun handlesMaximumExactly() {
        assertEquals(Money.MAX_MINOR, Money.sum(BudgetSplit().allocate(Money.MAX_MINOR)))
    }

    @Test
    fun aggregateIncomeCanExceedOneTransactionLimit() {
        assertEquals(Long.MAX_VALUE, Money.sum(BudgetSplit().allocate(Long.MAX_VALUE)))
    }

    @Test
    fun rebalanceConservesAndIsDeterministic() {
        val a = BudgetSplit.rebalance(listOf(6000, 2500, 2000))
        assertEquals(10000, a.values.sum())
        assertEquals(a, BudgetSplit.rebalance(listOf(6000, 2500, 2000)))
    }

    @Test
    fun zeroRebalanceRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BudgetSplit.rebalance(listOf(0, 0, 0))
        }
    }

    @Test
    fun safeSampleAndBoundary() {
        val date = LocalDate.of(2026, 10, 2)
        val payday = date.plusDays(7)
        val result =
            BudgetEngine.safeToSpend(
                4200000,
                listOf(
                    Reserve("b", 1200000, date.plusDays(3)),
                    Reserve("s", 800000, null),
                    Reserve("d", 358000, payday),
                    Reserve("later", 99999, payday.plusDays(1)),
                ),
                date,
                payday,
            )
        assertEquals(1842000L, result.safeMinor)
        assertEquals(2358000L, result.protectedMinor)
        assertEquals(263142L, result.dailyMinor)
    }

    @Test
    fun shortfallPreserved() {
        assertEquals(
            -100L,
            BudgetEngine.safeToSpend(
                    100,
                    listOf(Reserve("a", 200, null)),
                    LocalDate.now(),
                    LocalDate.now().plusDays(1),
                )
                .safeMinor,
        )
    }

    @Test
    fun paydayTodayNeverDividesByZero() {
        assertNull(
            BudgetEngine.safeToSpend(100, emptyList(), LocalDate.now(), LocalDate.now()).dailyMinor
        )
    }

    @Test
    fun duplicateReserveRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BudgetEngine.safeToSpend(
                100,
                listOf(Reserve("a", 1, null), Reserve("a", 1, null)),
                LocalDate.now(),
                LocalDate.now(),
            )
        }
    }

    @Test
    fun fortnightlyNotTwiceMonthly() {
        val date = LocalDate.of(2026, 10, 15)
        assertEquals(
            LocalDate.of(2026, 10, 29),
            PaySchedule.nextAfter(date, PayFrequency.FORTNIGHTLY),
        )
        assertEquals(
            LocalDate.of(2026, 10, 30),
            PaySchedule.nextAfter(date, PayFrequency.TWICE_MONTHLY, 15, 30),
        )
    }

    @Test
    fun monthEndPreservesAnchorAndLeapYear() {
        assertEquals(
            LocalDate.of(2028, 2, 29),
            PaySchedule.nextAfter(LocalDate.of(2028, 1, 31), PayFrequency.MONTHLY, 31),
        )
        assertEquals(
            LocalDate.of(2028, 3, 31),
            PaySchedule.nextAfter(LocalDate.of(2028, 2, 29), PayFrequency.MONTHLY, 31),
        )
    }

    @Test
    fun irregularHasNoInventedPayday() {
        assertNull(PaySchedule.nextAfter(LocalDate.now(), PayFrequency.IRREGULAR))
    }

    @Test
    fun unitPricesUseQuantity() {
        assertEquals(80000L, Money.unitPrice(160000, BigDecimal("2")))
        assertEquals(100000L, Money.unitPrice(50000, BigDecimal("0.5")))
    }

    @Test
    fun receiptDoesNotUseCashAsTotal() {
        val result =
            ReceiptParser.parse(
                "STORE\nRICE 980.00\nEGGS 780.00\nTOTAL 1760.00\nCASH 2000.00\nCHANGE 240.00"
            )
        assertEquals(176000L, result.totalMinor)
        assertEquals(2, result.lines.size)
        assertTrue(result.lines.all { it.needsReview })
    }

    @Test
    fun receiptMissingTotalNeedsReview() {
        assertNull(ReceiptParser.parse("STORE\nRICE 980.00").totalMinor)
    }

    @Test
    fun reconciliationExplicitAdjustment() {
        assertTrue(ReceiptParser.reconciles(listOf(100L, 200L), -10, 290))
        assertFalse(ReceiptParser.reconciles(listOf(100L, 200L), 0, 290))
    }
}
