package jm.yardmoney.core

import java.math.BigDecimal
import java.math.RoundingMode

object Quantity {
    fun parse(text: String): BigDecimal {
        require(Regex("[0-9]{1,7}(\\.[0-9]{1,6})?").matches(text.trim())) {
            "Use a quantity with up to six decimal places."
        }
        return BigDecimal(text.trim()).also {
            require(it > BigDecimal.ZERO && it <= BigDecimal("1000000")) {
                "Quantity must be between zero and one million."
            }
        }
    }

    fun estimate(priceMinor: Long, quantity: String): Long =
        BigDecimal.valueOf(priceMinor)
            .multiply(parse(quantity))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
            .also { require(it in 0..Money.MAX_MINOR) { "The estimate is too large." } }
}
