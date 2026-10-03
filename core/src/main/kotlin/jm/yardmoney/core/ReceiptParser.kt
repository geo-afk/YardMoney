package jm.yardmoney.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SuggestedLine(
    val raw: String,
    val name: String,
    val quantity: String,
    val totalMinor: Long?,
    val needsReview: Boolean = true,
    val unitPriceMinor: Long? = null,
    val confidence: String = "Check against photo",
)

data class ReceiptSuggestion(
    val merchant: String,
    val date: LocalDate?,
    val totalMinor: Long?,
    val lines: List<SuggestedLine>,
    val warnings: List<String>,
    val location: String = "",
    val time: String = "",
    val subtotalMinor: Long? = null,
    val taxMinor: Long? = null,
    val discountMinor: Long? = null,
    val paymentMethod: String = "",
    val receiptNumber: String = "",
    val transactionNumber: String = "",
) {
    val adjustmentMinor: Long
        get() = Math.subtractExact(taxMinor ?: 0, discountMinor ?: 0)
}

/** Layout-tolerant suggestions. Uncertain OCR never bypasses the review/ledger invariants. */
object ReceiptParser {
    private val ending =
        Regex(
            "(?i)(?<![A-Za-z0-9])(?:J\\s*\\$|JMD|\\$)?\\s*(-?[0-9OIl][0-9OIl,.]*[.,][0-9OIl]{2})\\s*[A-Z*]?\\s*$"
        )
    private val totals =
        Regex(
            "(?i)^(?:grand\\s+total|total(?:\\s+(?:due|amount|sale|jmd))?|amount\\s+(?:due|paid)|balance\\s+due)\\b"
        )
    private val sub = Regex("(?i)\\bsub\\s*total\\b")
    private val tax = Regex("(?i)^(?:gct|tax|vat|sales tax|service charge)\\b")
    private val discount = Regex("(?i)\\b(?:discount|coupon|savings|promotion)\\b")
    private val excluded =
        Regex(
            "(?i)\\b(?:cash|change|tender|visa|mastercard|debit|credit|payment|balance|receipt|transaction|invoice|tel|phone|trn|cashier|terminal|auth|approval|taxable|tax included)\\b"
        )
    private val qty = Regex("(?i)^(\\d+(?:[.,]\\d+)?)\\s*(?:x|@|at)\\s*")
    private val dateLike =
        Regex("\\b(?:20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}[-/](?:20)?\\d{2})\\b")

    private fun matchAmount(line: String) = ending.find(line)

    private fun amount(line: String): Long? =
        matchAmount(line)?.groupValues?.get(1)?.let { token ->
            val cleaned =
                token
                    .replace('O', '0')
                    .replace('o', '0')
                    .replace('I', '1')
                    .replace('l', '1')
                    .replace(" ", "")
            val normalized =
                if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.'))
                    cleaned.replace(".", "").replace(',', '.')
                else cleaned
            runCatching { Money.parse(normalized, true) }.getOrNull()
        }

    private fun name(line: String) =
        matchAmount(line)?.let { line.substring(0, it.range.first).trim() } ?: line

    fun parse(raw: String): ReceiptSuggestion {
        val textLines =
            raw.substringBefore("[Review edits]")
                .substringBefore("[Image review]")
                .substringBefore("[Verified receipt details]")
                .lines()
                .map { it.trim().replace(Regex("[\\t ]+"), " ") }
                .filter { it.isNotEmpty() }
        val items = mutableListOf<SuggestedLine>()
        val taxes = mutableListOf<Long>()
        val discounts = mutableListOf<Long>()
        var subtotal: Long? = null
        var total: Long? = null
        var pending = ""
        var inSummary = false
        var pendingQty = "1"
        var pendingUnit: Long? = null
        val warnings =
            mutableListOf(
                "Check every suggestion against the photo. OCR confidence is not a guarantee."
            )
        if (raw.contains("[Image review]"))
            warnings.addAll(
                raw.substringAfter("[Image review]")
                    .substringBefore("[Verified receipt details]")
                    .lines()
                    .filter { it.isNotBlank() }
            )
        for ((index, line) in textLines.withIndex()) {
            val nextAmount =
                textLines.getOrNull(index + 1)?.takeIf { name(it).isBlank() }?.let(::amount)
            val value = amount(line) ?: nextAmount
            when {
                sub.containsMatchIn(line) -> {
                    subtotal = value
                    inSummary = true
                    pending = ""
                }
                totals.containsMatchIn(line) -> {
                    total = value?.takeIf { it > 0 }
                    inSummary = true
                    pending = ""
                }
                tax.containsMatchIn(line) -> {
                    if (value != null && !line.contains("included", true))
                        taxes.add(kotlin.math.abs(value))
                    pending = ""
                }
                discount.containsMatchIn(line) -> {
                    if (value != null) discounts.add(kotlin.math.abs(value))
                    pending = ""
                }
                excluded.containsMatchIn(line) ||
                    dateLike.containsMatchIn(line) ||
                    Regex("\\b\\d{1,2}:\\d{2}").containsMatchIn(line) -> {
                    pending = ""
                }
                inSummary -> Unit
                qty.containsMatchIn(line) -> {
                    val q = qty.find(line)!!
                    val number = q.groupValues[1].replace(',', '.')
                    val remainder = line.substring(q.range.last + 1)
                    val values =
                        Regex("[0-9][0-9,]*\\.[0-9]{2}")
                            .findAll(remainder)
                            .mapNotNull { runCatching { Money.parse(it.value) }.getOrNull() }
                            .toList()
                    if (values.size >= 2 && pending.isNotBlank()) {
                        items.add(
                            SuggestedLine(
                                "$pending\n$line",
                                pending,
                                number,
                                values.last(),
                                unitPriceMinor = values.first(),
                                confidence = "Quantity and extended price detected",
                            )
                        )
                        pending = ""
                        pendingQty = "1"
                        pendingUnit = null
                    } else if (items.isNotEmpty() && pending.isBlank()) {
                        val previous = items.last()
                        // Quantity printed below a priced item describes that item, not a second
                        // purchase.
                        if (
                            values.isNotEmpty() &&
                                runCatching {
                                        Quantity.estimate(values.first(), number) ==
                                            previous.totalMinor
                                    }
                                    .getOrDefault(false)
                        ) {
                            items[items.lastIndex] =
                                previous.copy(
                                    quantity = number,
                                    unitPriceMinor = values.first(),
                                    raw = previous.raw + "\n" + line,
                                )
                        } else {
                            pendingQty = number
                            pendingUnit = values.firstOrNull()
                            warnings.add("Check the quantity near: $line")
                        }
                    } else {
                        pendingQty = number
                        pendingUnit = values.firstOrNull()
                    }
                }
                amount(line) != null -> {
                    var n = name(line)
                    val columns =
                        Regex("\\s+(\\d+(?:\\.\\d+)?)\\s+([0-9][0-9,]*\\.[0-9]{2})$").find(n)
                    if (columns != null) {
                        pendingQty = columns.groupValues[1]
                        pendingUnit =
                            runCatching { Money.parse(columns.groupValues[2]) }.getOrNull()
                        n = n.substring(0, columns.range.first).trim()
                    }
                    val combined = listOf(pending, n).filter { it.isNotBlank() }.joinToString(" ")
                    if (combined.isNotBlank() && value != null && value >= 0) {
                        // Multiple numeric columns: the rightmost amount is the extended price.
                        val prefixQty =
                            Regex("^(\\d+(?:\\.\\d+)?)\\s+(.+)").find(combined)?.takeIf {
                                runCatching {
                                        Quantity.parse(it.groupValues[1]) <=
                                            java.math.BigDecimal("100")
                                    }
                                    .getOrDefault(false)
                            }
                        val product = prefixQty?.groupValues?.get(2) ?: combined
                        val quantity = prefixQty?.groupValues?.get(1) ?: pendingQty
                        items.add(
                            SuggestedLine(
                                listOf(pending, line).filter { it.isNotBlank() }.joinToString("\n"),
                                product.take(180),
                                quantity,
                                amount(line),
                                unitPriceMinor = pendingUnit,
                                confidence =
                                    if (pending.isBlank()) "Price matched on the same line"
                                    else "Wrapped name / price matched; verify",
                            )
                        )
                    }
                    pending = ""
                    pendingQty = "1"
                    pendingUnit = null
                }
                line.any(Char::isLetter) && index > 0 -> {
                    if (
                        !Regex(
                                "(?i)\\b(?:welcome|thank|address|road|street|avenue|kingston|jamaica|open|hours|www|http)\\b"
                            )
                            .containsMatchIn(line)
                    )
                        pending =
                            listOf(pending, line)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .take(240)
                }
            }
        }
        val dates =
            dateLike
                .findAll(raw)
                .mapNotNull { m ->
                    val p = m.value.split('-', '/')
                    runCatching {
                        if (p[0].length == 4) LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].toInt())
                        else
                            LocalDate.of(
                                if (p[2].length == 2) 2000 + p[2].toInt() else p[2].toInt(),
                                p[1].toInt(),
                                p[0].toInt(),
                            )
                    }
                        .getOrNull()
                }
                .toList()
        val namedDate =
            Regex("(?i)\\b(\\d{1,2})[ -]([A-Z]{3,9})[ ,-]+(20\\d{2})\\b").find(raw)?.let { m ->
                runCatching {
                    LocalDate.parse(
                        "${m.groupValues[1]} ${m.groupValues[2].take(3).lowercase().replaceFirstChar { it.uppercase() }} ${m.groupValues[3]}",
                        DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
                    )
                }
                    .getOrNull()
            }
        val date = dates.firstOrNull() ?: namedDate
        if (date == null) warnings.add("Confirm the purchase date; none was read reliably.")
        if (Regex("\\b(?:0?[1-9]|1[0-2])/(?:0?[1-9]|1[0-2])/(?:20)?\\d{2}\\b").containsMatchIn(raw))
            warnings.add("Numeric dates are interpreted day/month/year. Confirm the date order.")
        val taxSum = taxes.takeIf { it.isNotEmpty() }?.let(Money::sum)
        val discountSum = discounts.takeIf { it.isNotEmpty() }?.let(Money::sum)
        val sum = runCatching { Money.sum(items.mapNotNull { it.totalMinor }) }.getOrNull()
        if (total == null) warnings.add("Enter the unreadable total manually.")
        if (items.isEmpty())
            warnings.add("No reliable item lines found. Add items or save a total-only expense.")
        if (subtotal != null && sum != subtotal)
            warnings.add(
                "Item sum does not match the subtotal; check missing lines and item discounts."
            )
        if (total != null && sum != null && sum + (taxSum ?: 0) - (discountSum ?: 0) != total)
            warnings.add(
                "Items, taxes and discounts do not reconcile with the total. Inclusive taxes or missing lines may explain the difference."
            )
        fun identifier(label: String) =
            Regex(
                    "(?im)\\b(?:$label)\\s*(?:no\\.?|number|#|id)?\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9/-]{2,})"
                )
                .find(raw)
                ?.groupValues
                ?.get(1) ?: ""
        val merchant =
            textLines
                .firstOrNull {
                    it.any(Char::isLetter) &&
                        !excluded.containsMatchIn(it) &&
                        !dateLike.containsMatchIn(it)
                }
                ?.take(120) ?: ""
        val location =
            textLines
                .take(10)
                .filter {
                    Regex(
                            "(?i)\\b(?:road|rd|street|st|avenue|ave|plaza|kingston|montego|portmore|mandeville|town|branch|parish)\\b"
                        )
                        .containsMatchIn(it) && amount(it) == null
                }
                .joinToString(", ")
                .take(240)
        val payment =
            Regex("(?i)\\b(?:visa|mastercard|debit|credit card|cash|lynk|jam.?dex)\\b")
                .findAll(raw)
                .map { it.value.uppercase() }
                .distinct()
                .joinToString(" / ")
        val time =
            Regex("(?i)\\b(?:[01]?\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?(?:\\s*[AP]M)?\\b")
                .find(raw)
                ?.value ?: ""
        return ReceiptSuggestion(
            merchant,
            date,
            total,
            items,
            warnings.distinct(),
            location,
            time,
            subtotal,
            taxSum,
            discountSum,
            payment,
            identifier("receipt|invoice|bill"),
            identifier("transaction|txn|trans"),
        )
    }

    fun reconciles(items: List<Long>, adjustment: Long, total: Long): Boolean =
        total > 0 && Math.addExact(Money.sum(items), adjustment) == total
}
