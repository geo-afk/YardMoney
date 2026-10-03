package jm.yardmoney.core

import java.math.BigDecimal
import java.time.LocalDate

object InputFormat {
    fun percent(basisPoints: Int): String =
        BigDecimal(basisPoints).movePointLeft(2).stripTrailingZeros().toPlainString() + "%"

    fun currencyText(text: String): String {
        var value = text.trim().replace(Regex("(?i)^(?:J\\$|JMD\\s*|\\$)\\s*"), "")
        if (Regex("-?[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{0,2})?").matches(value))
            value = value.replace(",", "")
        if (value.startsWith(".")) value = "0$value"
        if (value.startsWith("-.")) value = "-0" + value.drop(1)
        return value
    }

    fun changeDate(
        date: LocalDate,
        year: Int = date.year,
        month: Int = date.monthValue,
        day: Int = date.dayOfMonth,
    ): LocalDate {
        val first = LocalDate.of(year, month, 1)
        return first.withDayOfMonth(day.coerceIn(1, first.lengthOfMonth()))
    }

    data class MoneyDisplay(
        val text: String,
        val originalToDisplay: List<Int>,
        val displayToOriginal: List<Int>,
    )

    /** Keeps cursor positions stable around inserted grouping separators and optional cents. */
    fun moneyDisplay(raw: String, editing: Boolean): MoneyDisplay {
        if (!Regex("-?[0-9]*(?:\\.[0-9]{0,2})?").matches(raw) || raw.isEmpty() || raw == "-")
            return MoneyDisplay(raw, (0..raw.length).toList(), (0..raw.length).toList())
        val integerEnd = raw.indexOf('.').takeIf { it >= 0 } ?: raw.length
        val sign = if (raw.startsWith('-')) 1 else 0
        val result = StringBuilder()
        val forward = MutableList(raw.length + 1) { 0 }
        val reverse = mutableListOf(0)
        for (i in raw.indices) {
            if (i >= sign && i < integerEnd && i > sign && (integerEnd - i) % 3 == 0) {
                result.append(',')
                reverse.add(i)
            }
            forward[i] = result.length
            result.append(raw[i])
            reverse.add(i + 1)
        }
        forward[raw.length] = result.length
        if (!editing) {
            val decimals = if (integerEnd == raw.length) 0 else raw.length - integerEnd - 1
            if (integerEnd == raw.length) {
                result.append('.')
                reverse.add(raw.length)
            }
            repeat(2 - decimals) {
                result.append('0')
                reverse.add(raw.length)
            }
        }
        return MoneyDisplay(result.toString(), forward, reverse)
    }
}
