package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import jm.yardmoney.data.*

@Composable
internal fun ReportsPanel(model: AppModel, data: FinanceSnapshot) {
    var query by remember { mutableStateOf("") }
    Text("Search your records", style = MaterialTheme.typography.titleLarge)
    Field("Merchant, category, product or description", query) { query = it }
    if (query.isNotBlank()) {
        data.ledger.transactions
            .filter { "${it.description} ${it.category} ${it.date}".contains(query, true) }
            .take(30)
            .forEach {
                Record(
                    it.description.ifBlank { it.category },
                    "${it.date} · ${it.kind.lowercase()}",
                    Money.format(it.amountMinor),
                )
            }
        data.receipt.receipts
            .filter { "${it.merchant} ${it.branch} ${it.rawText}".contains(query, true) }
            .take(20)
            .forEach {
                Record(it.merchant, "${it.branch} · ${it.date}", Money.format(it.totalMinor))
            }
        data.receipt.prices
            .filter { "${it.name} ${it.merchant} ${it.branch}".contains(query, true) }
            .take(20)
            .forEach {
                Record(
                    it.name,
                    "${it.merchant} · ${it.branch} · ${it.date}",
                    Money.format(it.packPriceMinor),
                )
            }
    }
    FinancialCharts(data, model.repo.today)
    Text("Spending this period", style = MaterialTheme.typography.titleLarge)
    val start = LocalDate.parse(data.ledger.profile!!.periodStart)
    val days = maxOf(1, ChronoUnit.DAYS.between(start, model.repo.today) + 1)
    val current =
        data.ledger.transactions.filter {
            it.date >= start.toString() && it.date <= model.repo.today.toString()
        }
    val ids = current.map { it.id }.toSet()
    val totals =
        listOf("NEEDS", "WANTS", "SAVINGS").map { bucket ->
            Money.sum(
                data.splits
                    .filter { it.transactionId in ids && it.bucket == bucket }
                    .map { it.amountMinor }
            )
        }
    val max = totals.maxOrNull()?.coerceAtLeast(1) ?: 1
    listOf("Needs", "Wants", "Savings spending").forEachIndexed { i, label ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AmountRow(label, totals[i])
            LinearProgressIndicator(
                progress = { (totals[i].toDouble() / max).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Text(
        "Expenses less refunds. Savings transfers stay outside spending totals.",
        style = MaterialTheme.typography.bodySmall,
    )
    val spent = Money.sum(totals)
    AmountRow("Daily spending over $days days", spent / days)
    val previousStart = start.minusDays(days)
    val previousEnd = start.minusDays(1)
    val previous =
        Money.sum(
            data.ledger.transactions
                .filter {
                    it.date >= previousStart.toString() &&
                        it.date <= previousEnd.toString() &&
                        it.kind in listOf("EXPENSE", "REFUND")
                }
                .map { if (it.kind == "REFUND") -it.amountMinor else it.amountMinor }
        )
    AmountRow("Previous $days days · $previousStart to $previousEnd", previous)
    if (current.any { it.kind == "EXPENSE" })
        Text(
            if (spent > previous)
                "Spending is ${Money.format(spent-previous)} higher than the previous equal-length window."
            else
                "Spending is ${Money.format(previous-spent)} lower than the previous equal-length window."
        )
    Text(
        "This compares recorded transactions only. Missing older records can make the comparison incomplete.",
        style = MaterialTheme.typography.bodySmall,
    )
}
