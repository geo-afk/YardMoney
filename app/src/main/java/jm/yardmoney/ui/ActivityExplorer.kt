package jm.yardmoney.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import jm.yardmoney.core.Money
import jm.yardmoney.data.MoneyTransaction

internal val recordKinds =
    linkedMapOf(
        "EXPENSE" to "Expenses",
        "INCOME" to "Income",
        "TRANSFER" to "Transfers",
        "REFUND" to "Refunds",
        "ADJUSTMENT" to "Adjustments",
    )
internal val activityRanges =
    linkedMapOf(
        "PERIOD" to "This pay period",
        "SIX_MONTHS" to "Last six months",
        "ALL" to "All recorded dates",
    )

internal fun activityRecords(
    transactions: List<MoneyTransaction>,
    kind: String,
    range: String,
    periodStart: LocalDate,
    today: LocalDate,
): List<MoneyTransaction> {
    val start =
        when (range) {
            "PERIOD" -> periodStart
            "SIX_MONTHS" -> today.withDayOfMonth(1).minusMonths(5)
            else -> LocalDate.MIN
        }
    return transactions
        .filter {
            it.kind == kind &&
                !LocalDate.parse(it.date).isBefore(start) &&
                it.date <= today.toString()
        }
        .sortedWith(compareByDescending<MoneyTransaction> { it.date }.thenBy { it.id })
}

internal data class ActivityBin(
    val start: LocalDate,
    val end: LocalDate,
    val amount: Long,
    val count: Int,
)

internal fun activityBins(
    records: List<MoneyTransaction>,
    start: LocalDate,
    today: LocalDate,
): List<ActivityBin> {
    if (start > today) return emptyList()
    val days = ChronoUnit.DAYS.between(start, today) + 1
    val binCount = minOf(6L, days).toInt()
    val grouped = records.groupBy {
        (ChronoUnit.DAYS.between(start, LocalDate.parse(it.date)) * binCount / days).toInt()
    }
    return (0 until binCount).map { i ->
        val first = start.plusDays((i * days + binCount - 1) / binCount)
        val last = start.plusDays(((i + 1) * days + binCount - 1) / binCount - 1)
        val rows = grouped[i].orEmpty()
        ActivityBin(first, last, Money.sum(rows.map { it.amountMinor }), rows.size)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivityExplorer(
    transactions: List<MoneyTransaction>,
    periodStart: LocalDate,
    today: LocalDate,
    recordsContent: @Composable (List<MoneyTransaction>) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf("EXPENSE") }
    var range by rememberSaveable { mutableStateOf("PERIOD") }
    var query by rememberSaveable { mutableStateOf("") }
    val records =
        remember(transactions, kind, range, periodStart, today, query) {
            activityRecords(transactions, kind, range, periodStart, today).filter {
                (it.description + " " + it.category + " " + it.date).contains(query, true)
            }
        }
    val start =
        when (range) {
            "PERIOD" -> periodStart
            "SIX_MONTHS" -> today.withDayOfMonth(1).minusMonths(5)
            else -> records.minOfOrNull { LocalDate.parse(it.date) } ?: today
        }
    val bins = remember(records, start, today) { activityBins(records, start, today) }
    val label = recordKinds.getValue(kind)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Explore your records", style = MaterialTheme.typography.titleLarge)
            Text(
                "Choose a record type to see its timeline and matching entries.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recordKinds.forEach { (id, name) ->
                    FilterChip(
                        selected = kind == id,
                        onClick = { kind = id },
                        label = { Text(name) },
                    )
                }
            }
            Field("Search transactions", query) { query = it }
            DropdownField("Date range", range, activityRanges) { range = it }
            AmountRow(
                if (kind == "ADJUSTMENT") "Net adjustments" else "$label total",
                Money.sum(records.map { it.amountMinor }),
            )
            Text(
                "${records.size} ${if (records.size == 1) "record" else "records"} · $start to $today",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                when (kind) {
                    "TRANSFER" ->
                        "Money moved between your accounts, counted once. This is not income or spending."
                    "REFUND" ->
                        "Returned money shown separately. These amounts reduce spending in your budget."
                    "ADJUSTMENT" ->
                        "Signed balance corrections. Below-zero bars reduce balances; opposite corrections in one interval can cancel out."
                    "INCOME" ->
                        "Money actually received. Typical pay and opening balances are excluded."
                    else ->
                        "Recorded expenses before refunds. Choose Refunds to see returned money."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (records.isEmpty()) {
                Text("No ${label.lowercase()} in this date range.")
                Text(
                    "Choose another range or record type, or add a record.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                ActivityColumnChart(bins, kind)
            }
        }
    }
    Text("$label records", style = MaterialTheme.typography.titleLarge)
    recordsContent(records)
}

@Composable
private fun ActivityColumnChart(bins: List<ActivityBin>, kind: String) {
    if (bins.isEmpty()) return
    var selected by rememberSaveable(kind, bins) { mutableIntStateOf(bins.lastIndex) }
    val active = bins[selected.coerceIn(bins.indices)]
    val positive = bins.maxOf { it.amount }.coerceAtLeast(0).toDouble()
    val negative = bins.minOf { it.amount }.coerceAtMost(0).toDouble()
    val span = (positive - negative).coerceAtLeast(1.0)
    val baseline = (positive / span).toFloat()
    val primary = identityColor(categoryIdentity(kind))
    val negativeColor = MaterialTheme.colorScheme.error
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val dateFormat = remember { DateTimeFormatter.ofPattern("MMM d") }
    Text("Recorded amounts over time", style = MaterialTheme.typography.titleMedium)
    Text(
        "Tap a column for its exact amount and dates. Every column uses the same scale.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "Scale: ${Money.format(bins.minOf { it.amount }.coerceAtMost(0))} to ${Money.format(bins.maxOf { it.amount }.coerceAtLeast(0))}",
        style = MaterialTheme.typography.bodySmall,
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnWidth = ((maxWidth - 8.dp * (bins.size - 1)) / bins.size).coerceAtLeast(48.dp)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            bins.forEachIndexed { index, bin ->
                Column(
                    Modifier.width(columnWidth)
                        .clickable(role = Role.Button) { selected = index }
                        .semantics(mergeDescendants = true) {
                            contentDescription =
                                "${bin.start} to ${bin.end}: ${Money.format(bin.amount)}, ${bin.count} records${if (selected == index) ", selected" else ""}"
                        },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                        val zero = 6.dp.toPx() + (size.height - 12.dp.toPx()) * baseline
                        drawLine(lineColor, Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
                        val extent =
                            (kotlin.math.abs(bin.amount.toDouble()) / span).toFloat() *
                                (size.height - 12.dp.toPx())
                        if (extent > 0f)
                            drawRect(
                                color = (if (bin.amount < 0) negativeColor else primary),
                                topLeft = Offset(0f, if (bin.amount >= 0) zero - extent else zero),
                                size = Size(size.width, extent),
                            )
                        if (selected == index)
                            drawLine(
                                primary,
                                Offset(0f, size.height),
                                Offset(size.width, size.height),
                                3.dp.toPx(),
                            )
                    }
                    Text(bin.start.format(dateFormat), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    Text("${active.start} – ${active.end}", style = MaterialTheme.typography.bodyMedium)
    AmountRow(
        "Selected interval · ${active.count} ${if (active.count == 1) "record" else "records"}",
        active.amount,
    )
}
