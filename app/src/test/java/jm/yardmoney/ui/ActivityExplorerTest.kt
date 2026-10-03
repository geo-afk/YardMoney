package jm.yardmoney.ui

import java.time.LocalDate
import jm.yardmoney.data.MoneyTransaction
import org.junit.Assert.*
import org.junit.Test

class ActivityExplorerTest {
    private val today = LocalDate.of(2026, 10, 2)

    private fun tx(id: String, kind: String, cents: Long, date: String = "2026-10-02") =
        MoneyTransaction(id, id, kind, cents, date, id, "Other", "NEEDS")

    @Test
    fun typeAndDatesStaySeparateAndTransfersAreCountedOnce() {
        val rows =
            listOf(
                tx("spend", "EXPENSE", 1000),
                tx("return", "REFUND", 300),
                tx("move", "TRANSFER", 5000),
                tx("old", "EXPENSE", 900, "2026-09-30"),
                tx("future", "EXPENSE", 100, "2026-10-03"),
            )
        assertEquals(
            listOf("spend"),
            activityRecords(rows, "EXPENSE", "PERIOD", today.minusDays(1), today).map { it.id },
        )
        assertEquals(
            5000L,
            activityBins(
                    activityRecords(rows, "TRANSFER", "PERIOD", today.minusDays(1), today),
                    today.minusDays(1),
                    today,
                )
                .sumOf { it.amount },
        )
        assertEquals(
            listOf("return"),
            activityRecords(rows, "REFUND", "ALL", today, today).map { it.id },
        )
        assertEquals(2, activityRecords(rows, "EXPENSE", "ALL", today, today).size)
    }

    @Test
    fun sixMonthRangeUsesCalendarMonthsAndOmitsFutureRecords() {
        val rows =
            listOf(
                tx("outside", "INCOME", 10, "2026-04-30"),
                tx("inside", "INCOME", 20, "2026-05-01"),
            )
        assertEquals(
            listOf("inside"),
            activityRecords(rows, "INCOME", "SIX_MONTHS", today, today).map { it.id },
        )
    }

    @Test
    fun binsHaveNoGapsAndPreserveSignedTotalsAndCounts() {
        val start = today.minusDays(16)
        val rows =
            (0L..16L).map { i ->
                tx(
                    "$i",
                    "ADJUSTMENT",
                    if (i % 2L == 0L) 100L else -250L,
                    start.plusDays(i).toString(),
                )
            }
        val bins = activityBins(rows, start, today)
        assertEquals(6, bins.size)
        assertEquals(start, bins.first().start)
        assertEquals(today, bins.last().end)
        bins.zipWithNext().forEach { (a, b) -> assertEquals(a.end.plusDays(1), b.start) }
        assertEquals(rows.sumOf { it.amountMinor }, bins.sumOf { it.amount })
        assertEquals(rows.size, bins.sumOf { it.count })
        assertTrue(bins.any { it.amount < 0 })
    }

    @Test
    fun singleDayAndNoRecordsDoNotInventActivity() {
        assertEquals(1, activityBins(emptyList(), today, today).size)
        assertEquals(0L, activityBins(emptyList(), today, today).single().amount)
        assertTrue(activityBins(emptyList(), today.plusDays(1), today).isEmpty())
    }
}
