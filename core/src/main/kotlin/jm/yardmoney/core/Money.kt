package jm.yardmoney.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** All stored JMD values are cents. Decimal text is parsed once, never through Double. */
object Money {
    const val MAX_MINOR = 100_000_000_000_000L

    fun parse(text: String, allowNegative: Boolean = false): Long {
        val trimmed = text.trim()
        val entered = if (trimmed.count { it == '.' } == 1) trimmed.removeSuffix(".") else trimmed
        if (entered.contains(','))
            require(
                Regex(
                        if (allowNegative) "-?[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]{1,2})?"
                        else "[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]{1,2})?"
                    )
                    .matches(entered)
            ) {
                "Check the comma grouping in this amount."
            }
        val clean = entered.replace(",", "")
        require(
            Regex(if (allowNegative) "-?[0-9]+(\\.[0-9]{1,2})?" else "[0-9]+(\\.[0-9]{1,2})?")
                .matches(clean)
        ) {
            "Enter an amount with up to two decimal places."
        }
        val amount = BigDecimal(clean).movePointRight(2).longValueExact()
        require(amount in -MAX_MINOR..MAX_MINOR) { "This amount is too large." }
        return amount
    }

    fun positive(text: String): Long =
        parse(text).also { require(it > 0) { "Enter an amount above zero." } }

    fun format(minor: Long): String {
        val symbols = DecimalFormatSymbols(Locale.ENGLISH)
        val pattern = if (minor % 100L == 0L) "#,##0" else "#,##0.00"
        return "J$" + DecimalFormat(pattern, symbols).format(BigDecimal.valueOf(minor, 2))
    }

    fun input(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()

    fun sum(values: Iterable<Long>): Long = values.fold(0L, Math::addExact)

    fun unitPrice(lineMinor: Long, quantity: BigDecimal): Long {
        require(lineMinor >= 0 && quantity > BigDecimal.ZERO)
        return BigDecimal.valueOf(lineMinor)
            .divide(quantity, 0, RoundingMode.HALF_UP)
            .longValueExact()
    }
}
