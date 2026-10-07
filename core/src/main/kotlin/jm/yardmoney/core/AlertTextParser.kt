package jm.yardmoney.core

import java.time.LocalDate

/** Generic, local suggestions. References, balances and absent fields never become a payment. */
object AlertTextParser {
    private val amount = Regex("(?i)(?:J\\$|JMD\\s*|\\$)\\s*[+-]?\\d[\\d,.]*")
    private val currencyPrefix = Regex("(?i)^(?:J\\$|JMD|\\$)\\s*")
    private val debit = Regex("(?i)\\b(debited|purchase|withdrawal|payment)\\b")
    private val credit = Regex("(?i)\\b(received|credited|deposit)\\b")
    private val merchant = Regex("(?i)(?:\\b(?:at|to|from)\\s+|@\\s*)(.+?)(?=\\s+(?:on|today|yesterday|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b|[.;\\n]|$)")
    private val date = Regex("(?i)\\b(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}(?:/\\d{4})?|today|yesterday|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b")

    fun parse(text: String, today: LocalDate): AlertPreview {
        if (text.length > 10000) return AlertPreview(null, null, null, null,
            listOf("Keep the alert text under 10,000 characters."))
        val issues = mutableListOf<String>()
        val amounts = amount.findAll(text).toList()
        val minor = amounts.singleOrNull()?.value?.let {
            runCatching { Money.positive(it.replace(currencyPrefix, "").trimEnd('.', ',')) }.getOrNull()
        }
        if (amounts.size > 1) issues += "Choose the payment amount; this text contains multiple amounts."
        else if (amounts.isNotEmpty() && minor == null) issues += "Check the amount and its comma grouping."
        val isDebit = debit.containsMatchIn(text)
        val isCredit = credit.containsMatchIn(text)
        val kind = when { isDebit && !isCredit -> "EXPENSE"; isCredit && !isDebit -> "INCOME"; else -> null }
        if (isDebit && isCredit) issues += "Choose income or expense; this text contains both directions."
        val dates = date.findAll(text).map { it.value }.toList()
        val parsedDate = dates.singleOrNull()?.let { token ->
            runCatching {
                when {
                    token.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> LocalDate.parse(token)
                    token.count { it == '/' } == 2 -> token.split('/').let { LocalDate.of(it[2].toInt(), it[1].toInt(), it[0].toInt()) }
                    else -> requireNotNull(QuickAddParser.parse("1 $token", emptyList(), today).date)
                }.also { require(it <= today) }
            }.getOrNull()
        }
        if (dates.size > 1) issues += "Choose one date; this text contains multiple dates."
        else if (dates.isNotEmpty() && parsedDate == null) issues += "Choose a valid date on or before today."
        val merchants = merchant.findAll(text).map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() && !amount.containsMatchIn(it) }.distinct().toList()
        return AlertPreview(minor, merchants.singleOrNull(), kind, parsedDate, issues)
    }
}
