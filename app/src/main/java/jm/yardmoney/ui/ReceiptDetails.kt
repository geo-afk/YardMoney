package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
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
        produceState<List<ReceiptItem>?>(null, receipt.id) {
            value = withContext(Dispatchers.IO) { model.repo.dao.receiptItems(receipt.id) }
        }
    var removing by remember { mutableStateOf(false) }
    var deleteExpense by remember { mutableStateOf(false) }
    if (!removing)
        StagedEditSheet(
            title = "Receipt",
            keyValue = "${receipt.merchant} · ${Money.format(receipt.totalMinor)}",
            busy = busy,
            dirty = false,
            close = close,
            actions = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    TextButton(onClick = { removing = true }, enabled = !busy) {
                        Text("Remove receipt")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = close) { Text("Done") }
                }
            },
        ) {
            val loaded = items
            if (loaded == null) CircularProgressIndicator()
            else SavedReceiptContent(receipt, loaded)
        }
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

// Total-only ledger entries can still contain useful scanned product lines. Display them
// without creating price-history records or changing the saved financial transaction.
internal fun savedReceiptPreview(receipt: Receipt, items: List<ReceiptItem>): ShopReceiptModel {
    val parsed = ReceiptDraftCodec.parse(receipt.rawText)
    val lines =
        if (items.isEmpty())
            parsed.lines.mapIndexed { index, line -> capturedReceiptLine("scan:$index", line) }
        else
            items.map { item ->
                val price = runCatching {
                    Money.unitPrice(
                        item.totalMinor,
                        jm.yardmoney.core.Quantity.parse(item.quantity),
                    )
                }
                    .getOrNull()
                ShopReceiptLine(
                    ShoppingItem(
                        item.id,
                        "",
                        item.confirmedName.ifBlank { item.rawName },
                        item.quantity,
                        null,
                        price,
                        false,
                    ),
                    price,
                    item.totalMinor,
                )
            }
    return ShopReceiptModel(
        receipt.merchant.ifBlank { "Not specified" },
        receipt.date.ifBlank { "Not specified" },
        lines,
        receipt.totalMinor,
        scanned = true,
        recordedSubtotal = parsed.subtotalMinor,
    )
}

@Composable
internal fun SavedReceiptContent(receipt: Receipt, items: List<ReceiptItem>) {
    ShopReceipt(remember(receipt, items) { savedReceiptPreview(receipt, items) })
}
