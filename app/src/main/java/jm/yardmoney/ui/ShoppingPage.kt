package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import jm.yardmoney.data.*

@Composable
internal fun ShoppingPage(
    model: AppModel,
    data: FinanceSnapshot,
    safe: SafeToSpend,
    busy: Boolean,
) {
    var create by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var listId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var manual by remember { mutableStateOf("") }
    var product by remember { mutableStateOf("") }
    var optional by remember { mutableStateOf(false) }
    val selected = data.shopping.lists.find { it.id == listId } ?: data.shopping.lists.firstOrNull()
    val prices =
        data.receipt.prices
            .groupBy { it.productKey }
            .mapValues { (_, rows) -> rows.maxBy { it.date } }
    Page {
        SectionHeading(
            "A smarter shopping trip",
            subtitle = "A little preparation goes a long way.",
        )
        Text(
            "Estimate from your own dated receipts or prices you enter. Store prices can change; this is not a live quote."
        )
        Button(
            onClick = { create = true },
            shape = MaterialTheme.shapes.small,
        ) {
            Icon(Icons.Default.AddShoppingCart, null)
            Spacer(Modifier.width(8.dp))
            Text("New shopping list")
        }
        if (selected != null) {
            IdentityPicker(
                "Shopping list",
                selected.id,
                data.shopping.lists.map {
                    IdentityOption(
                        it.id,
                        it.name,
                        identity = MoneyIdentity(Icons.Default.ShoppingBasket, 0xFF00865A),
                    )
                },
            ) {
                listId = it
            }
            val items = data.shopping.items.filter { it.listId == selected.id }
            val pending = items.filter { !it.checked }
            MoneyCard {
                Text("Ready for the checkout", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${items.size-pending.size} of ${items.size} items checked",
                    style = MaterialTheme.typography.headlineSmall,
                )
                LinearProgressIndicator(
                    progress = {
                        if (items.isEmpty()) 0f
                        else (items.size - pending.size).toFloat() / items.size
                    },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                )
            }
            val estimates = pending.map { item ->
                val price = item.manualPriceMinor ?: prices[item.productKey]?.packPriceMinor
                price?.let { runCatching { Quantity.estimate(it, item.quantity) }.getOrNull() }
            }
            val known = Money.sum(estimates.filterNotNull())
            val unknown = estimates.count { it == null }
            AmountRow(
                if (unknown > 0) "Known subtotal · $unknown items need a price"
                else "Estimated remaining shop",
                known,
            )
            Text(
                if (unknown > 0) "Add missing prices before checking affordability."
                else if (known <= safe.safeMinor)
                    "Estimated shop fits within safe to spend. ${Money.format(safe.safeMinor-known)} would remain."
                else
                    "Estimated shop exceeds safe to spend by ${Money.format(known-safe.safeMinor)}."
            )
            val grocery =
                data.limits.firstOrNull {
                    it.category.equals("Groceries", true) && it.bucket == "NEEDS"
                }
            if (grocery == null)
                Text("Set a Groceries category limit in Plan for a separate grocery budget check.")
            else {
                val ids =
                    data.ledger.transactions
                        .filter {
                            it.date >= data.ledger.profile!!.periodStart &&
                                it.date <= model.repo.today.toString()
                        }
                        .map { it.id }
                        .toSet()
                val used =
                    Money.sum(
                        data.splits
                            .filter {
                                it.transactionId in ids &&
                                    it.category.equals("Groceries", true) &&
                                    it.bucket == "NEEDS"
                            }
                            .map { it.amountMinor }
                    )
                AmountRow("Grocery budget remaining before shop", grocery.limitMinor - used)
                if (unknown == 0)
                    Text(
                        if (known <= grocery.limitMinor - used)
                            "This estimate also fits the grocery limit."
                        else
                            "This estimate exceeds the grocery limit by ${Money.format(known-(grocery.limitMinor-used))}."
                    )
            }
            OutlinedButton(
                onClick = {
                    adding = true
                    name = ""
                    quantity = "1"
                    manual = ""
                    product = ""
                    optional = false
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Add item")
            }
            items.forEach { item ->
                Card(
                    shape = YardShape.card,
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IdentityBadge(categoryIdentity(item.name))
                        Tick(
                            "${item.name} × ${item.quantity}${if(item.optional)" · optional" else ""}",
                            item.checked,
                        ) {
                            model.act { model.repo.toggleItem(item) }
                        }
                        val observation = prices[item.productKey]
                        Text(
                            item.manualPriceMinor?.let {
                                "Manual price ${Money.format(it)} / package"
                            }
                                ?: observation?.let {
                                    "${Money.format(it.packPriceMinor)} / package · ${it.merchant}, ${it.branch} · ${it.date}"
                                }
                                ?: "No price yet"
                        )
                        TextButton(
                            enabled = !busy,
                            onClick = { model.act { model.repo.dao.deleteShoppingItem(item.id) } },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }
            Text(
                "Checking an item is a shopping checklist action. Record your purchase separately; it does not change your balance.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else
            EmptyState(
                "Your next shop starts here",
                "Create a list and add items to compare its cost with your budget.",
                icon = Icons.Default.ShoppingBasket,
            )
        SectionHeading(
            "Your price notebook",
            subtitle = "Compare the prices you have actually paid.",
        )
        if (data.receipt.prices.isEmpty())
            EmptyState(
                "Build your own price history",
                "Review a receipt and confirm its package sizes to compare purchases here.",
                icon = Icons.Default.PriceCheck,
            )
        data.receipt.prices
            .groupBy { it.productKey }
            .forEach { (_, observations) ->
                val latest = observations.maxBy { it.date }
                Text(
                    "${latest.name} · ${latest.packageSize} ${latest.unit}",
                    style = MaterialTheme.typography.titleMedium,
                )
                observations
                    .sortedBy { it.unitPriceMinor }
                    .forEach { p ->
                        Record(
                            "${p.merchant} · ${p.branch.ifBlank{"branch unknown"}}",
                            "${p.date} · ${p.parish} · ${Money.format(p.packPriceMinor)} / package",
                            "${Money.format(p.unitPriceMinor)} / ${p.unit}",
                        )
                    }
            }
    }
    if (create)
        SimpleForm("Shopping list", listOf("Name"), listOf(""), busy, { create = false }) { v, _ ->
            model.act({ create = false }) { model.repo.createList(v[0]) }
        }
    if (adding && selected != null)
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add shopping item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field("Name", name) { name = it }
                    Field("Packages / quantity", quantity) { quantity = it }
                    IdChoice(
                        "Use a receipt price",
                        product,
                        mapOf("" to "Enter a manual price") +
                            prices.mapValues { (_, p) ->
                                "${p.name} · ${p.packageSize}${p.unit} · ${p.date}"
                            },
                    ) {
                        product = it
                        if (it.isNotBlank()) name = prices[it]!!.name
                    }
                    Field("Manual price per package (J$; optional)", manual) { manual = it }
                    Tick("Optional item", optional) { optional = it }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        model.act({ adding = false }) {
                            model.repo.addListItem(
                                selected.id,
                                name,
                                quantity,
                                product.takeIf { it.isNotBlank() },
                                manual.takeIf { it.isNotBlank() }?.let(Money::positive),
                                optional,
                            )
                        }
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { adding = false },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Cancel")
                }
            },
        )
}
