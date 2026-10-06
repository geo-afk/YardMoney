package jm.yardmoney.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import jm.yardmoney.core.*
import jm.yardmoney.data.*

@Composable
internal fun ShopDetail(
    list: ShoppingList,
    items: List<ShoppingItem>,
    safeMinor: Long,
    busy: Boolean,
    back: () -> Unit,
    edit: () -> Unit,
    toggle: (ShoppingItem) -> Unit,
    update: (ShoppingItem) -> Unit,
    remove: (ShoppingItem) -> Unit,
    duplicate: () -> Unit,
    rename: (String) -> Unit,
    delete: () -> Unit,
    groceryRemaining: Long? = null,
) {
    var grouped by rememberSaveable { mutableStateOf(true) }
    var closed by rememberSaveable { mutableStateOf(listOf<String>()) }
    var receiptShown by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable(list.id) { mutableStateOf(list.name) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val receipt = remember(list, items) { shopReceipt(list, items) }
    val groups = if (grouped) items.groupBy { it.category } else mapOf("All items" to items)
    LazyColumn(
        Modifier.widthIn(max = 840.dp).fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ShopHeading(list.name, back) }
        item {
            MoneyCard {
                Text(
                    "${items.count { it.checked }} of ${items.size} picked up",
                    style = MaterialTheme.typography.titleLarge,
                )
                val progress by
                    animateFloatAsState(
                        if (items.isEmpty()) 0f
                        else items.count { it.checked }.toFloat() / items.size,
                        LocalMotion.current.floatSpec(),
                        label = "Shopping progress",
                    )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                )
            }
        }
        item {
            MoneyCard {
                Text(
                    "Remaining ${Money.format(receipt.remaining)}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (receipt.remainingMissing > 0)
                    Text(
                        "Partial estimate • ${shopCount(receipt.remainingMissing, "remaining item")} without a price"
                    )
                Text(
                    list.createdDate ?: "Date not recorded",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(edit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add or edit items")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            renaming = true
                            newName = list.name
                        },
                        enabled = !busy,
                    ) {
                        Text("Rename")
                    }
                    TextButton(duplicate, enabled = !busy && items.isNotEmpty()) {
                        Text("Duplicate")
                    }
                    IconButton(onClick = { deleting = true }, enabled = !busy) {
                        Icon(Icons.Default.DeleteOutline, "Delete list")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Group by category", Modifier.weight(1f))
                Switch(
                    grouped,
                    { grouped = it },
                    modifier = Modifier.semantics { contentDescription = "Group by category" },
                )
            }
        }
        groups.forEach { (category, rows) ->
            if (grouped)
                item(key = "category:$category") {
                    Card(
                        onClick = {
                            closed =
                                if (category in closed) closed - category else closed + category
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IdentityBadge(categoryIdentity(category))
                            Text(
                                "$category • ${rows.size}",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Icon(
                                if (category in closed) Icons.Default.ExpandMore
                                else Icons.Default.ExpandLess,
                                "${if(category in closed) "Expand" else "Collapse"} $category",
                            )
                        }
                    }
                }
            if (!grouped || category !in closed)
                items(rows, key = { it.id }) { item ->
                    ShopItemRow(item, busy, { toggle(item) }, update, { remove(item) })
                }
        }
        if (items.isEmpty())
            item {
                EmptyState(
                    "Everything cleared",
                    "Add an item to prepare your next trip.",
                    icon = Icons.Default.ShoppingBasket,
                )
            }
        item {
            OutlinedButton(
                onClick = { receiptShown = !receiptShown },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, null)
                Spacer(Modifier.width(8.dp))
                Text(if (receiptShown) "Hide receipt" else "View receipt")
            }
        }
        if (receiptShown) item { ShopReceipt(receipt) }
        item { ShopAffordability(receipt, safeMinor, groceryRemaining) }
        item {
            Text(
                "Swipe right to check; left to remove. Long press for item actions. Record purchases separately in Activity.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (renaming)
        EditFormSheet(
            busy = busy,
            dirty = newName != list.name,
            keyValue = newName,
            titleText = "Rename list",
            onDismissRequest = { renaming = false },
            title = { Text("Rename list") },
            text = {
                OutlinedTextField(
                    newName,
                    { newName = it.take(120) },
                    label = { Text("List name") },
                    isError = newName.isBlank(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rename(newName)
                        renaming = false
                    },
                    enabled = newName.isNotBlank() && !busy,
                ) {
                    Text("Save name")
                }
            },
            dismissButton = { TextButton(onClick = LocalEditDismiss.current) { Text("Cancel") } },
        )
    if (deleting)
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete ${list.name}?") },
            text = { Text("Remove this list and its items? You can undo immediately afterwards.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = false
                        delete()
                    },
                    enabled = !busy,
                ) {
                    Text("Delete list")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Keep list") } },
        )
}

@Composable
internal fun ShopAffordability(
    receipt: ShopReceiptModel,
    safeMinor: Long,
    groceryRemaining: Long? = null,
) {
    MoneyCard {
        Text("A little budget confidence", style = MaterialTheme.typography.titleMedium)
        if (groceryRemaining != null)
            Text(
                "Groceries budget remaining: ${Money.format(groceryRemaining)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        else
            Text(
                "Set a Groceries limit in Plan for a separate grocery budget check.",
                style = MaterialTheme.typography.bodySmall,
            )
        Text(
            when {
                receipt.remainingMissing > 0 ->
                    "Add ${shopCount(receipt.remainingMissing, "missing price")} to compare with safe to spend."
                receipt.remaining <= safeMinor ->
                    "Fits your selected account scope. ${Money.format(safeMinor-receipt.remaining)} safe to spend would remain."
                else ->
                    "Above safe to spend by ${Money.format(receipt.remaining-safeMinor)} in this account scope."
            }
        )
    }
}
