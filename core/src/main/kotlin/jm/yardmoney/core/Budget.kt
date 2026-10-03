package jm.yardmoney.core

import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class BudgetSplit(val needs: Int = 5000, val wants: Int = 3000, val savings: Int = 2000) {
    val values
        get() = listOf(needs, wants, savings)

    init {
        require(values.all { it in 0..10000 } && values.sum() == 10000) {
            "Your percentages must total exactly 100%."
        }
    }

    fun allocate(incomeMinor: Long): List<Long> {
        require(incomeMinor >= 0)
        val numerators = values.map {
            BigInteger.valueOf(incomeMinor).multiply(BigInteger.valueOf(it.toLong()))
        }
        val denominator = BigInteger.valueOf(10000)
        val result = numerators.map { it.divide(denominator).longValueExact() }.toMutableList()
        val remainder = incomeMinor - Money.sum(result)
        val order =
            numerators.indices.sortedWith(
                compareByDescending<Int> { numerators[it].remainder(denominator) }.thenBy { it }
            )
        repeat(remainder.toInt()) { result[order[it]]++ }
        return result
    }

    companion object {
        fun rebalance(percentages: List<Int>): BudgetSplit {
            require(
                percentages.size == 3 && percentages.all { it in 0..10000 } && percentages.sum() > 0
            )
            val sum = percentages.sum()
            val weighted = percentages.map { it.toLong() * 10000 }
            val result = weighted.map { (it / sum).toInt() }.toMutableList()
            val order =
                weighted.indices.sortedWith(
                    compareByDescending<Int> { weighted[it] % sum }.thenBy { it }
                )
            repeat(10000 - result.sum()) { result[order[it]]++ }
            return BudgetSplit(result[0], result[1], result[2])
        }
    }
}

data class Reserve(val id: String, val remainingMinor: Long, val dueDate: LocalDate?)

data class SafeToSpend(
    val availableMinor: Long,
    val protectedMinor: Long,
    val safeMinor: Long,
    val days: Long,
    val dailyMinor: Long?,
)

object BudgetEngine {
    fun safeToSpend(
        available: Long,
        reserves: List<Reserve>,
        today: LocalDate,
        nextPayday: LocalDate,
    ): SafeToSpend {
        require(reserves.map { it.id }.distinct().size == reserves.size) {
            "A commitment was counted twice."
        }
        require(reserves.all { it.remainingMinor >= 0 })
        val protected =
            Money.sum(
                reserves
                    .filter { it.dueDate == null || !it.dueDate.isAfter(nextPayday) }
                    .map { it.remainingMinor }
            )
        val safe = Math.subtractExact(available, protected)
        val days = ChronoUnit.DAYS.between(today, nextPayday)
        return SafeToSpend(
            available,
            protected,
            safe,
            days,
            if (days > 0 && safe > 0) safe / days else null,
        )
    }
}
