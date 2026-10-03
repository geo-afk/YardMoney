package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jm.yardmoney.AppModel
import jm.yardmoney.core.Money
import jm.yardmoney.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ReceiptDetails(model: AppModel, receipt: Receipt, busy: Boolean, close: () -> Unit) {
    val items by
        produceState<List<ReceiptItem>>(emptyList(), receipt.id) {
            value = withContext(Dispatchers.IO) { model.repo.dao.receiptItems(receipt.id) }
        }
    var removing by remember { mutableStateOf(false) }
    var deleteExpense by remember { mutableStateOf(false) }
    if (!removing)
        AlertDialog(
            onDismissRequest = close,
            title = { Text(receipt.merchant) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "${receipt.date} · ${receipt.branch.ifBlank{"Branch unknown"}} · ${receipt.parish}"
                    )
                    AmountRow("Reviewed total", receipt.totalMinor)
                    items.forEach {
                        Text(
                            "${it.confirmedName} × ${it.quantity} · ${Money.format(it.totalMinor)}"
                        )
                    }
                    if (items.isEmpty()) Text("Total-only expense: no product prices were stored.")
                    if (receipt.adjustmentMinor != 0L)
                        AmountRow("Tax / discount adjustment", receipt.adjustmentMinor)
                    Text(receipt.rawText, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = close,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { removing = true },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Remove receipt")
                }
            },
        )
    else
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text("Remove receipt?") },
            text = {
                Column {
                    Text(
                        "The receipt and its price history will be removed. Keep the account expense unless you select the option below."
                    )
                    Tick("Also delete the linked expense", deleteExpense) { deleteExpense = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        model.act(close) {
                            if (deleteExpense) model.repo.deleteTransaction(receipt.transactionId)
                            else model.repo.dao.deleteReceipt(receipt.id)
                            receipt.imageRef?.let { model.app.storage.deleteReceipt(it) }
                        }
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { removing = false },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Cancel")
                }
            },
        )
}
