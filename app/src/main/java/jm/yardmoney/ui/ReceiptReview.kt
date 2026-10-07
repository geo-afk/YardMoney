package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import jm.yardmoney.data.*
import org.json.JSONArray
import org.json.JSONObject

private class ReviewLine(
    rawLine: String,
    nameValue: String,
    totalValue: String,
    quantityValue: String = "1",
    val confidence: String = "Manually entered",
) {
    val raw = rawLine
    var name by mutableStateOf(nameValue)
    var quantity by mutableStateOf(quantityValue)
    var total by mutableStateOf(totalValue)
    var size by mutableStateOf("")
    var unit by mutableStateOf("item")
    var verified by mutableStateOf(false)
}

@Composable
internal fun ReceiptReview(
    model: AppModel,
    data: FinanceSnapshot,
    draft: ReceiptDraft,
    busy: Boolean,
    close: () -> Unit,
    rescanned: (String) -> Unit = {},
    initialAccountId: String? = null,
) {
    val suggestion = remember(draft.id) { ReceiptParser.parse(draft.rawText) }
    val saved =
        remember(draft.id) {
            runCatching {
                draft.rawText
                    .takeIf { it.contains("[Review edits]") }
                    ?.substringAfter("[Review edits]")
                    ?.let(::JSONObject)
                    ?.takeIf { it.optInt("version") == 1 }
            }
                .getOrNull()
        }
    fun savedText(name: String, fallback: String) = saved?.optString(name, fallback) ?: fallback
    val key = rememberSaveable(draft.id) { FinanceRepository.id() }
    var merchant by
        rememberSaveable(draft.id) { mutableStateOf(savedText("merchant", suggestion.merchant)) }
    var branch by
        rememberSaveable(draft.id) { mutableStateOf(savedText("branch", suggestion.location)) }
    var parish by rememberSaveable(draft.id) { mutableStateOf(savedText("parish", "")) }
    var date by
        rememberSaveable(draft.id) {
            mutableStateOf(savedText("date", suggestion.date?.toString() ?: ""))
        }
    var total by
        rememberSaveable(draft.id) {
            mutableStateOf(savedText("total", suggestion.totalMinor?.let(Money::input) ?: ""))
        }
    var adjustment by
        rememberSaveable(draft.id) {
            mutableStateOf(savedText("adjustment", Money.input(suggestion.adjustmentMinor)))
        }
    var account by
        rememberSaveable(draft.id) {
            mutableStateOf(
                savedText(
                    "account",
                    initialAccountId ?: data.ledger.accounts.firstOrNull()?.account?.id.orEmpty(),
                )
            )
        }
    var totalOnly by
        rememberSaveable(draft.id) {
            mutableStateOf(
                saved?.optBoolean("totalOnly", suggestion.lines.isEmpty())
                    ?: suggestion.lines.isEmpty()
            )
        }
    var checked by rememberSaveable(draft.id) { mutableStateOf(false) }
    var duplicate by rememberSaveable(draft.id) { mutableStateOf(false) }
    var linked by rememberSaveable(draft.id) { mutableStateOf(savedText("linked", "")) }
    var time by rememberSaveable(draft.id) { mutableStateOf(savedText("time", suggestion.time)) }
    var payment by
        rememberSaveable(draft.id) {
            mutableStateOf(savedText("payment", suggestion.paymentMethod))
        }
    var receiptNo by
        rememberSaveable(draft.id) {
            mutableStateOf(savedText("receiptNo", suggestion.receiptNumber))
        }
    var transactionNo by
        rememberSaveable(draft.id) {
            mutableStateOf(savedText("transactionNo", suggestion.transactionNumber))
        }
    var subtotal by
        rememberSaveable(draft.id) {
            mutableStateOf(savedText("subtotal", suggestion.subtotalMinor?.let(Money::input) ?: ""))
        }
    var tax by
        rememberSaveable(draft.id) {
            mutableStateOf(savedText("tax", suggestion.taxMinor?.let(Money::input) ?: ""))
        }
    var discount by
        rememberSaveable(draft.id) {
            mutableStateOf(savedText("discount", suggestion.discountMinor?.let(Money::input) ?: ""))
        }
    var rescanPrompt by remember { mutableStateOf(false) }
    val rowSaver =
        listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<ReviewLine>, String>(
            save = { list ->
                list.flatMap {
                    listOf(
                        it.raw,
                        it.name,
                        it.total,
                        it.quantity,
                        it.confidence,
                        it.size,
                        it.unit,
                        it.verified.toString(),
                    )
                }
            },
            restore = { list ->
                mutableStateListOf<ReviewLine>().apply {
                    list.chunked(8).forEach { v ->
                        add(
                            ReviewLine(v[0], v[1], v[2], v[3], v[4]).also {
                                it.size = v[5]
                                it.unit = v[6]
                                it.verified = v[7].toBoolean()
                            }
                        )
                    }
                }
            },
        )
    val rows =
        rememberSaveable(draft.id, saver = rowSaver) {
            mutableStateListOf<ReviewLine>().apply {
                addAll(
                    if (saved?.optJSONArray("items") != null) {
                        val items = saved.getJSONArray("items")
                        (0 until items.length()).map { i ->
                            val v = items.getJSONObject(i)
                            ReviewLine(
                                    v.optString("raw"),
                                    v.optString("name"),
                                    v.optString("total"),
                                    v.optString("quantity", "1"),
                                    v.optString("confidence", "Previously edited; check again"),
                                )
                                .also {
                                    it.size = v.optString("size")
                                    it.unit = v.optString("unit", "item")
                                }
                        }
                    } else
                        suggestion.lines.map {
                            ReviewLine(
                                it.raw,
                                it.name,
                                it.totalMinor?.let(Money::input) ?: "",
                                it.quantity,
                                it.confidence,
                            )
                        }
                )
            }
        }
    fun reviewEdits(): String {
        val json = JSONObject().put("version", 1)
        mapOf(
                "merchant" to merchant,
                "branch" to branch,
                "parish" to parish,
                "date" to date,
                "total" to total,
                "adjustment" to adjustment,
                "account" to account,
                "linked" to linked,
                "time" to time,
                "payment" to payment,
                "receiptNo" to receiptNo,
                "transactionNo" to transactionNo,
                "subtotal" to subtotal,
                "tax" to tax,
                "discount" to discount,
            )
            .forEach { (label, value) -> json.put(label, value) }
        json.put("totalOnly", totalOnly)
        val items = JSONArray()
        rows.forEach { row ->
            items.put(
                JSONObject()
                    .put("raw", row.raw)
                    .put("name", row.name)
                    .put("total", row.total)
                    .put("quantity", row.quantity)
                    .put("confidence", row.confidence)
                    .put("size", row.size)
                    .put("unit", row.unit)
            )
        }
        json.put("items", items)
        return json.toString()
    }
    fun keepDraft() {
        model.act(close) { model.repo.saveReviewDraft(draft.id, reviewEdits()) }
    }
    if (rescanPrompt)
        AlertDialog(
            onDismissRequest = { rescanPrompt = false },
            title = { Text("Rescan this photo?") },
            text = {
                Text(
                    "Recognition replaces these suggestions and edits only after it succeeds. Saved financial records stay unchanged."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rescanPrompt = false
                        model.rescanReceipt(draft, rescanned)
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Rescan")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { rescanPrompt = false },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Cancel")
                }
            },
        )
    var editingDetails by rememberSaveable(draft.id) { mutableStateOf(false) }
    // Deliberate discard closes without saving edits; Keep draft below explicitly saves them.
    StagedEditSheet("Check your receipt", "$merchant · $total", busy, true, close) {
        Text("Check the receipt below. Use Edit details to correct anything before saving.")
        val previewLines = rows.mapIndexed { index, line ->
            val amount = runCatching { Money.parse(line.total) }.getOrNull()
            val quantity = runCatching { Quantity.parse(line.quantity) }.getOrNull()
            val quantitySpecified =
                suggestion.lines.find { it.raw == line.raw }?.quantitySpecified == true ||
                    line.quantity != "1"
            val price =
                if (amount != null && quantity != null && quantitySpecified)
                    runCatching { Money.unitPrice(amount, quantity) }.getOrNull()
                else null
            ShopReceiptLine(
                ShoppingItem(
                    "review:$index",
                    "",
                    line.name.ifBlank { "Not specified" },
                    line.quantity,
                    null,
                    price,
                    false,
                    category = "Not specified",
                ),
                price,
                amount,
                quantitySpecified = quantitySpecified,
            )
        }
        ShopReceipt(
            ShopReceiptModel(
                merchant.ifBlank { "Not specified" },
                date.takeIf { it.isNotBlank() },
                previewLines,
                runCatching { Money.parse(total) }.getOrNull(),
                scanned = true,
                recordedSubtotal = runCatching { Money.parse(subtotal) }.getOrNull(),
            )
        )
        OutlinedButton(
            onClick = { editingDetails = !editingDetails },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (editingDetails) "Hide details" else "Edit details")
        }
        if (editingDetails) {
            Field("Merchant", merchant) {
                merchant = it
                checked = false
            }
            Field("Branch", branch) {
                branch = it
                checked = false
            }
            Field("Parish", parish) {
                parish = it
                checked = false
            }
            Field("Purchase date (YYYY-MM-DD)", date) {
                date = it
                checked = false
            }
            Field("Receipt time (optional)", time) {
                time = it
                checked = false
            }
            Field("Payment method (optional)", payment) {
                payment = it
                checked = false
            }
            Field("Receipt number (optional)", receiptNo) {
                receiptNo = it
                checked = false
            }
            Field("Transaction number (optional)", transactionNo) {
                transactionNo = it
                checked = false
            }
            Field("Subtotal (J$; optional)", subtotal) {
                subtotal = it
                checked = false
            }
            Field("Tax (J$; optional)", tax) {
                tax = it
                checked = false
            }
            Field("Discount (J$; optional)", discount) {
                discount = it
                checked = false
            }
            Text(
                "Tax may already be included. Check the adjustment rather than adding tax twice.",
                style = MaterialTheme.typography.bodySmall,
            )
            Field("Receipt total (J$)", total) {
                total = it
                checked = false
            }
            IdChoice(
                "Pay from",
                account,
                data.ledger.accounts.associate { it.account.id to it.account.name },
            ) {
                account = it
                checked = false
            }
            IdChoice(
                "Link an existing expense (optional)",
                linked,
                mapOf("" to "Create a new expense") +
                    data.ledger.transactions
                        .filter { it.kind == "EXPENSE" }
                        .associate {
                            it.id to
                                "${it.description} · ${Money.format(it.amountMinor)} · ${it.date}"
                        },
            ) {
                linked = it
                checked = false
            }
            Tick("Save a total-only expense (no product prices)", totalOnly) {
                totalOnly = it
                checked = false
            }
            if (!totalOnly) {
                Text(
                    "Use kg for weight, L for liquids or item for counts. Convert g ÷ 1000 and mL ÷ 1000. Package size is per package; quantity is packages bought.",
                    style = MaterialTheme.typography.bodySmall,
                )
                rows.forEachIndexed { index, line ->
                    OutlinedCard {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Item ${index+1}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(line.confidence, style = MaterialTheme.typography.bodySmall)
                            Field("Product name", line.name) {
                                line.name = it
                                line.verified = false
                            }
                            Field("Quantity", line.quantity) {
                                line.quantity = it
                                line.verified = false
                            }
                            Field("Line total (J$)", line.total) {
                                line.total = it
                                line.verified = false
                            }
                            Field("Package size (optional)", line.size) {
                                line.size = it
                                line.verified = false
                            }
                            Choice("Unit", line.unit, listOf("kg", "L", "item")) {
                                line.unit = it
                                line.verified = false
                            }
                            Tick("I checked this item", line.verified) { line.verified = it }
                            TextButton(
                                enabled = !busy,
                                onClick = { rows.remove(line) },
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text("Remove item")
                            }
                        }
                    }
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = { rows.add(ReviewLine("", "", "")) },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Add missing item")
                }
                Field("Tax / discount adjustment (J$; negative for discount)", adjustment) {
                    adjustment = it
                    checked = false
                }
                val itemSum = runCatching {
                    Money.sum(rows.map { Money.parse(it.total) })
                }
                    .getOrNull()
                val reconciled = runCatching {
                    itemSum != null &&
                        ReceiptParser.reconciles(
                            listOf(itemSum),
                            Money.parse(adjustment, true),
                            Money.parse(total),
                        )
                }
                    .getOrDefault(false)
                Text(
                    if (reconciled) "Items + adjustment match the total."
                    else "Items + adjustment do not yet match the total.",
                    color =
                        if (reconciled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error,
                )
                if (itemSum != null) AmountRow("Detected item sum", itemSum)
                Text(
                    "Prices are added only when reviewed items reconcile with no receipt-level adjustment. This prevents an unallocated tax or discount from giving a misleading price."
                )
            }
        }
        val validReceipt =
            merchant.isNotBlank() &&
                runCatching { LocalDate.parse(date) }.isSuccess &&
                runCatching { Money.positive(total) }.isSuccess &&
                data.ledger.accounts.any { it.account.id == account }
        if (!validReceipt)
            Text("Use Edit details to add the missing store, date, total or payment account.")
        Tick("I checked the items, total, date and JMD currency", checked) {
            checked = it
            rows.forEach { line -> line.verified = it }
        }
        val possible =
            data.receipt.receipts.filter {
                it.fingerprint == draft.fingerprint ||
                    it.merchant.equals(merchant.trim(), true) &&
                        it.date == date &&
                        it.totalMinor == runCatching { Money.parse(total) }.getOrNull()
            }
        if (possible.isNotEmpty()) {
            Text(
                "Possible duplicate: ${possible.first().merchant} · ${possible.first().date}. Check your saved expenses first.",
                color = MaterialTheme.colorScheme.error,
            )
            Tick("Save anyway: this is a separate purchase", duplicate) { duplicate = it }
        }
        Button(
            enabled = !busy && validReceipt && checked && (totalOnly || rows.all { it.verified }),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                model.act(close) {
                    // Keep the display snapshot even when the ledger expense is total-only.
                    model.repo.saveReviewDraft(draft.id, reviewEdits())
                    model.repo.confirmReceipt(
                        ConfirmedReceipt(
                            draft.id,
                            merchant,
                            branch,
                            parish,
                            LocalDate.parse(date),
                            Money.positive(total),
                            if (totalOnly) 0 else Money.parse(adjustment, true),
                            if (totalOnly) emptyList()
                            else
                                rows.map {
                                    ConfirmedItem(
                                        it.raw,
                                        it.name,
                                        it.quantity,
                                        Money.parse(it.total),
                                        it.size,
                                        it.unit,
                                        it.verified,
                                    )
                                },
                            account,
                            key,
                            totalOnly,
                            duplicate,
                            linked.takeIf { it.isNotBlank() },
                            mapOf(
                                    "Time" to time,
                                    "Payment" to payment,
                                    "Receipt number" to receiptNo,
                                    "Transaction number" to transactionNo,
                                    "Subtotal" to subtotal,
                                    "Tax" to tax,
                                    "Discount" to discount,
                                )
                                .filterValues { it.isNotBlank() },
                        )
                    )
                }
            },
            shape = MaterialTheme.shapes.small,
        ) {
            Text(if (busy) "Saving…" else "Confirm and save")
        }
        if (draft.imageRef != null)
            OutlinedButton(
                enabled = !busy,
                onClick = { rescanPrompt = true },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Rescan saved photo")
            }
        TextButton(
            enabled = !busy,
            onClick = { keepDraft() },
            shape = MaterialTheme.shapes.small,
        ) {
            Text("Save edits as draft")
        }
        TextButton(
            enabled = !busy,
            onClick = {
                model.act(close) {
                    model.repo.dao.deleteDraft(draft.id)
                    draft.imageRef?.let { model.app.storage.deleteReceipt(it) }
                }
            },
            shape = MaterialTheme.shapes.small,
        ) {
            Text("Discard draft")
        }
    }
}

@Composable
internal fun Tick(label: String, value: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = value,
                    enabled = enabled && !LocalSaving.current,
                    role = Role.Checkbox,
                    onValueChange = change,
                ),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Checkbox(value, null, enabled = enabled && !LocalSaving.current)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
    }
}
