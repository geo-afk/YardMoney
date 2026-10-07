package jm.yardmoney.core

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/** Suggestions only. Missing or ambiguous input never becomes a fabricated value. */
object QuickAddParser {
    private val dateToken = Regex("(?i)(?<!\\S)(today|yesterday|monday|tuesday|wednesday|thursday|friday|saturday|sunday|\\d{1,2}/\\d{1,2})(?!\\S)")
    private val amountToken = Regex("(?i)(?<!\\S)(?:J\\$|JMD\\s*|\\$)?[+-]?\\d[\\d,.]*(?:k)?(?!\\S)")
    private val currencyPrefix = Regex("(?i)^(J\\$|JMD\\s*|\\$)")
    private val abbreviatedAmount = Regex("[0-9]+(?:\\.[0-9]{1,2})?")
    private val spaces = Regex("\\s+")

    fun parse(line: String, accounts: List<CaptureAccount>, today: LocalDate): QuickAddPreview {
        if (line.length > 1000) return QuickAddPreview(null, null, null, null,
            listOf("Keep the smart line under 1,000 characters."))
        var remaining = line.trim()
        val issues = mutableListOf<String>()
        val matches = accounts.filter { it.name.isNotBlank() }.flatMap { account ->
            account.tokenPattern.findAll(remaining).map { account to it }.toList()
        }
        // A short account name nested inside a longer one is one token, not two accounts.
        val specific = matches.filter { candidate ->
            matches.none { other -> other.first.name.length > candidate.first.name.length &&
                candidate.second.range.first >= other.second.range.first &&
                candidate.second.range.last <= other.second.range.last }
        }
        val ids = specific.map { it.first.id }.distinct()
        val account = if (ids.size == 1) specific.first().first else null
        if (ids.size > 1) issues += "Choose one account in the smart line."
        if (account != null) specific.distinctBy { it.second.range }.sortedByDescending { it.second.range.first }
            .forEach { remaining = remaining.removeRange(it.second.range) }
        val dates = dateToken.findAll(remaining).toList()
        var date: LocalDate? = null
        if (dates.size > 1) issues += "Use one date in the smart line."
        else dates.singleOrNull()?.let { token ->
            date = runCatching {
                val word = token.value.lowercase(Locale.ROOT)
                when (word) {
                    "today" -> today
                    "yesterday" -> today.minusDays(1)
                    else -> if ('/' in word) {
                        val parts = word.split('/')
                        LocalDate.of(today.year, parts[1].toInt(), parts[0].toInt())
                    } else {
                        val day = DayOfWeek.valueOf(word.uppercase(Locale.ROOT))
                        today.minusDays(((today.dayOfWeek.value - day.value + 7) % 7).toLong())
                    }
                }.also { require(it <= today) }
            }.getOrNull()
            if (date == null) issues += "Choose a valid date on or before today."
            remaining = remaining.removeRange(token.range)
        }
        val amounts = amountToken.findAll(remaining).toList()
        val amount = if (amounts.size == 1) parseAmount(amounts.single().value) else null
        if (amounts.size > 1) issues += "Use one amount in the smart line."
        else if (amounts.size == 1 && amount == null)
            issues += "Enter an amount above zero with valid comma grouping and up to two decimal places."
        if (amounts.size == 1) remaining = remaining.removeRange(amounts.single().range)
        return QuickAddPreview(amount, remaining.replace(spaces, " ").trim().ifBlank { null },
            account?.id, date, issues)
    }

    private fun parseAmount(token: String): Long? = runCatching {
        val clean = token.replace(currencyPrefix, "")
        if (clean.endsWith("k", true)) {
            val number = clean.dropLast(1)
            require(abbreviatedAmount.matches(number))
            val minor = BigDecimal(number).multiply(BigDecimal.valueOf(100000)).longValueExact()
            require(minor in 1..Money.MAX_MINOR)
            minor
        } else Money.positive(clean)
    }.getOrNull()
}
