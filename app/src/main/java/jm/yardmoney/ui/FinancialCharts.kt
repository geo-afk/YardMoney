package jm.yardmoney.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import jm.yardmoney.core.*
import jm.yardmoney.data.*

internal fun bucketCategories(
    splits: List<TransactionSplit>,
    transactions: List<MoneyTransaction>,
    bucket: String,
): List<Pair<String, Long>> {
    val ids =
        transactions.filter { it.kind == "EXPENSE" || it.kind == "REFUND" }.map { it.id }.toSet()
    return splits
        .filter { it.transactionId in ids && it.bucket == bucket }
        .groupBy { it.category }
        .map { (category, rows) -> category to Money.sum(rows.map { it.amountMinor }) }
        .sortedByDescending { it.second }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BudgetOverview(
    data: FinanceSnapshot,
    today: LocalDate,
    onChangeSplit: (() -> Unit)? = null,
) {
    val profile = data.ledger.profile ?: return
    val current =
        data.ledger.transactions.filter {
            it.date >= profile.periodStart && it.date <= today.toString()
        }
    val usage = budgetUsage(data, today)
    val income = usage.income
    val shares = listOf(profile.needsBp, profile.wantsBp, profile.savingsBp)
    val allocation = usage.allocation
    val colors = chartColors()
    val labels = listOf("Needs", "Wants", "Savings")
    val motion = LocalMotion.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Your money, with a purpose",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                if (income == 0L)
                    "Record income to fund this plan. Your target percentages stay in place."
                else
                    "Allocated from " +
                        Money.format(income) +
                        " received this pay period. Transfers are excluded.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            AllocationDonut(shares, allocation, usage.used)
            if (onChangeSplit != null)
                OutlinedButton(onClick = onChangeSplit, shape = MaterialTheme.shapes.small) {
                    Text("Change percentages")
                }
            HorizontalDivider()
            Text(
                "Recorded activity",
                style = MaterialTheme.typography.titleMedium,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selected == index,
                        onClick = { selected = index },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index,
                                labels.size,
                                MaterialTheme.shapes.small,
                            ),
                        icon = {},
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            AnimatedContent(
                selected,
                transitionSpec = { motion.transition() },
                label = "Bucket activity",
            ) { index ->
                val categories =
                    bucketCategories(
                        data.splits,
                        current,
                        listOf("NEEDS", "WANTS", "SAVINGS")[index],
                    )
                val used = usage.used[index]
                val remaining = allocation[index] - used
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmountRow(
                        if (index == 2) "Spent and net saved" else "Spending less refunds",
                        used,
                    )
                    AmountRow(
                        if (remaining < 0) "Over target" else "Remaining target",
                        if (remaining < 0) -remaining else remaining,
                    )
                    val progress by
                        animateFloatAsState(
                            if (allocation[index] > 0)
                                (used.toDouble() / allocation[index]).toFloat().coerceIn(0f, 1f)
                            else 0f,
                            animationSpec = motion.floatSpec(),
                            label = "Budget progress",
                        )
                    LinearProgressIndicator(
                        progress = { progress },
                        color = colors[index],
                        trackColor = track,
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                    )
                    if (categories.isEmpty())
                        Text(
                            "No spending recorded for " +
                                labels[index].lowercase() +
                                " this pay period.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    else ChartBars(categories, listOf(colors[index]), categoryColors = true)
                    if (index == 2) {
                        Text("Goal savings", style = MaterialTheme.typography.titleSmall)
                        if (data.ledger.goals.isEmpty())
                            Text("Add a savings goal in Plan to track contributions.")
                        data.ledger.goals.forEach { goal ->
                            AmountRow(goal.goal.name, goal.savedMinor)
                            LinearProgressIndicator(
                                progress = {
                                    if (goal.goal.targetMinor > 0)
                                        (goal.savedMinor.toDouble() / goal.goal.targetMinor)
                                            .toFloat()
                                            .coerceIn(0f, 1f)
                                    else 0f
                                },
                                modifier = Modifier.fillMaxWidth().height(12.dp),
                                color = colors[index],
                            )
                            Text(
                                "Target " + Money.format(goal.goal.targetMinor),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            "Goal balances include recorded contributions across all periods. Savings transfers are separate from spending.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FinancialCharts(data: FinanceSnapshot, today: LocalDate) {
    val p = data.ledger.profile ?: return
    val transactions =
        data.ledger.transactions.filter { it.date >= p.periodStart && it.date <= today.toString() }
    val colors = chartColors()
    val income = Money.sum(transactions.filter { it.kind == "INCOME" }.map { it.amountMinor })
    val spending =
        Money.sum(
            transactions
                .filter { it.kind in listOf("EXPENSE", "REFUND") }
                .map { if (it.kind == "REFUND") -it.amountMinor else it.amountMinor }
        )
    MoneyCard {
        SectionHeading("Income and spending")
        ChartBars(listOf("Income" to income, "Spending less refunds" to spending), colors)
    }
    val ids = transactions.map { it.id }.toSet()
    val categories =
        data.splits
            .filter { it.transactionId in ids }
            .groupBy { it.category }
            .map { (label, rows) -> label to Money.sum(rows.map { it.amountMinor }) }
            .sortedByDescending { it.second }
            .take(8)
    MoneyCard {
        SectionHeading("Spending by category")
        ChartBars(categories, colors, categoryColors = true)
    }
    // Refunds retain their original merchant grouping rather than an arbitrary refund description.
    val merchants =
        transactions
            .filter { it.kind in listOf("EXPENSE", "REFUND") }
            .groupBy { t ->
                if (t.kind == "REFUND")
                    data.ledger.transactions.find { it.id == t.refundOfId }?.description
                        ?: t.description
                else t.description
            }
            .map { (label, rows) ->
                label.ifBlank { "Unspecified merchant" } to
                    Money.sum(
                        rows.map { if (it.kind == "REFUND") -it.amountMinor else it.amountMinor }
                    )
            }
            .sortedByDescending { it.second }
            .take(8)
    MoneyCard {
        SectionHeading("Spending by merchant")
        ChartBars(merchants, colors)
    }
    val month = YearMonth.from(today)
    val months =
        (5 downTo 0).map { ago ->
            val m = month.minusMonths(ago.toLong())
            m.toString() to
                Money.sum(
                    data.ledger.transactions
                        .filter {
                            it.date >= m.atDay(1).toString() &&
                                it.date <= minOf(m.atEndOfMonth(), today).toString() &&
                                it.kind in listOf("EXPENSE", "REFUND")
                        }
                        .map { if (it.kind == "REFUND") -it.amountMinor else it.amountMinor }
                )
        }
    MoneyCard {
        SectionHeading("Six-month spending trend")
        ChartBars(months, colors)
        Text(
            "Recorded data only; zero months may have no records. Transfers are excluded.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    SectionHeading("Savings progress")
    if (data.ledger.goals.isEmpty())
        EmptyState(
            "Make space for a goal",
            "Create a savings goal in Plan to see its progress here.",
        )
    data.ledger.goals.forEach { goal ->
        ProgressMoneyCard(
            goal.goal.name,
            "Recorded savings toward your target",
            goal.savedMinor,
            goal.goal.targetMinor,
            categoryIdentity(goal.goal.name),
        )
    }
}

@Composable
private fun ChartBars(
    values: List<Pair<String, Long>>,
    colors: List<Color>,
    categoryColors: Boolean = false,
) {
    val motion = LocalMotion.current
    if (values.isEmpty()) {
        Text("No spending recorded yet. Add an expense or review a receipt to begin.")
        return
    }
    val max = values.maxOf { it.second }.coerceAtLeast(1)
    values.forEachIndexed { i, (label, amount) ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (categoryColors) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IdentityBadge(categoryIdentity(label))
                    Column(Modifier.weight(1f)) { AmountRow(label, amount) }
                }
            } else AmountRow(label, amount)
            val progress by
                animateFloatAsState(
                    (amount.toDouble() / max).toFloat().coerceIn(0f, 1f),
                    animationSpec = motion.floatSpec(),
                    label = "Spending bar",
                )
            LinearProgressIndicator(
                progress = { progress },
                color =
                    if (categoryColors) identityColor(categoryIdentity(label))
                    else colors[i % colors.size],
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier =
                    Modifier.fillMaxWidth().height(12.dp).semantics {
                        contentDescription = "$label: ${Money.format(amount)}"
                    },
            )
        }
    }
}
