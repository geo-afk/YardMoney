package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jm.yardmoney.AppModel
import jm.yardmoney.core.BudgetSplit
import jm.yardmoney.core.InputFormat
import jm.yardmoney.data.Profile

@Composable
internal fun BudgetForm(model: AppModel, p: Profile, busy: Boolean, close: () -> Unit) {
    var needs by rememberSaveable {
        mutableStateOf(
            BigDecimal(p.needsBp).divide(BigDecimal(100)).stripTrailingZeros().toPlainString()
        )
    }
    var wants by rememberSaveable {
        mutableStateOf(
            BigDecimal(p.wantsBp).divide(BigDecimal(100)).stripTrailingZeros().toPlainString()
        )
    }
    var savings by rememberSaveable {
        mutableStateOf(
            BigDecimal(p.savingsBp).divide(BigDecimal(100)).stripTrailingZeros().toPlainString()
        )
    }
    val fields =
        listOf(
            rememberUpdatedState(needs),
            rememberUpdatedState(wants),
            rememberUpdatedState(savings),
        )
    fun change(index: Int, value: String) {
        when (index) {
            0 -> needs = value
            1 -> wants = value
            else -> savings = value
        }
    }
    var preview by remember { mutableStateOf<BudgetSplit?>(null) }
    fun basis() = fields.map { BigDecimal(it.value).multiply(BigDecimal(100)).intValueExact() }
    EditFormSheet(
        titleText = "Budget percentages",
        busy = busy,
        keyValue = "$needs / $wants / $savings",
        dirty =
            runCatching { basis() != listOf(p.needsBp, p.wantsBp, p.savingsBp) }.getOrDefault(true),
        onDismissRequest = { if (!busy) close() },
        title = { Text("Budget percentages") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Needs %", "Wants %", "Savings %").forEachIndexed { i, label ->
                    Field(label, fields[i].value) {
                        change(i, it)
                        preview = null
                    }
                }
                Text(
                    "Total must be exactly 100%. Rebalance preserves the proportions you enter and shows the result first."
                )
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        runCatching { BudgetSplit.rebalance(basis()) }
                            .onSuccess { preview = it }
                            .onFailure { model.error.value = it.message }
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Preview rebalance")
                }
                preview?.let { split ->
                    Text(
                        "Proposed: ${InputFormat.percent(split.needs)} needs · ${InputFormat.percent(split.wants)} wants · ${InputFormat.percent(split.savings)} savings"
                    )
                    TextButton(
                        onClick = {
                            split.values.forEachIndexed { i, value ->
                                change(
                                    i,
                                    BigDecimal(value)
                                        .divide(BigDecimal(100))
                                        .stripTrailingZeros()
                                        .toPlainString(),
                                )
                            }
                            preview = null
                        },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text("Use these percentages")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val values = fields.map { it.value }
                    model.act(close) {
                        val b = values.map {
                            BigDecimal(it).multiply(BigDecimal(100)).intValueExact()
                        }
                        model.repo.updateSplit(BudgetSplit(b[0], b[1], b[2]))
                    }
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = LocalEditDismiss.current,
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Cancel")
            }
        },
    )
}
