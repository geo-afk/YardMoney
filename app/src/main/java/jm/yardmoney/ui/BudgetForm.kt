package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jm.yardmoney.AppModel
import jm.yardmoney.core.BudgetSplit
import jm.yardmoney.core.InputFormat
import jm.yardmoney.data.Profile

@Composable
internal fun BudgetForm(model: AppModel, p: Profile, busy: Boolean, close: () -> Unit) {
    val fields = remember {
        listOf(p.needsBp, p.wantsBp, p.savingsBp).map {
            mutableStateOf(
                BigDecimal(it).divide(BigDecimal(100)).stripTrailingZeros().toPlainString()
            )
        }
    }
    var preview by remember { mutableStateOf<BudgetSplit?>(null) }
    fun basis() = fields.map { BigDecimal(it.value).multiply(BigDecimal(100)).intValueExact() }
    AlertDialog(
        onDismissRequest = { if (!busy) close() },
        title = { Text("Budget percentages") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Needs %", "Wants %", "Savings %").forEachIndexed { i, label ->
                    Field(label, fields[i].value) {
                        fields[i].value = it
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
                                fields[i].value =
                                    BigDecimal(value)
                                        .divide(BigDecimal(100))
                                        .stripTrailingZeros()
                                        .toPlainString()
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
                onClick = close,
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Cancel")
            }
        },
    )
}
