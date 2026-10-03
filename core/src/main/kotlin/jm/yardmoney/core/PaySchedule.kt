package jm.yardmoney.core

import java.time.LocalDate
import java.time.YearMonth

enum class PayFrequency {
    WEEKLY,
    FORTNIGHTLY,
    TWICE_MONTHLY,
    MONTHLY,
    IRREGULAR,
}

object PaySchedule {
    fun nextAfter(
        current: LocalDate,
        frequency: PayFrequency,
        firstDay: Int = current.dayOfMonth,
        secondDay: Int = 28,
    ): LocalDate? {
        require(firstDay in 1..31 && secondDay in 1..31)
        return when (frequency) {
            PayFrequency.WEEKLY -> current.plusDays(7)
            PayFrequency.FORTNIGHTLY -> current.plusDays(14)
            PayFrequency.IRREGULAR -> null
            PayFrequency.MONTHLY ->
                YearMonth.from(current).plusMonths(1).let {
                    it.atDay(minOf(firstDay, it.lengthOfMonth()))
                }
            PayFrequency.TWICE_MONTHLY -> {
                require(firstDay < secondDay) { "Choose two different payday dates, in order." }
                (0L..2L)
                    .flatMap { offset ->
                        val month = YearMonth.from(current).plusMonths(offset)
                        listOf(firstDay, secondDay).map {
                            month.atDay(minOf(it, month.lengthOfMonth()))
                        }
                    }
                    .distinct()
                    .first { it > current }
            }
        }
    }
}
