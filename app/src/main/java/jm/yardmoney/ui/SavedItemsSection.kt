package jm.yardmoney.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import jm.yardmoney.data.*
import org.json.JSONObject

// Catalog corrections are metadata in existing private preferences: historical receipt
// lines and totals must remain intact when a user edits or removes a saved suggestion.
@Composable
internal fun rememberSavedCatalog(originals: List<CatalogItem>): List<CatalogItem> {
    val prefs = LocalContext.current.getSharedPreferences("saved_items", 0)
    var revision by remember(prefs) { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return remember(originals, revision) {
        val corrections =
            originals
                .mapNotNull { item ->
                    val key = item.sourceKeys.first()
                    if (prefs.getBoolean("deleted:$key", false))
                        key to CatalogCorrection(item.name, item.price, item.category, true)
                    else
                        prefs.getString("item:$key", null)?.let { encoded ->
                            runCatching {
                                val saved = JSONObject(encoded)
                                key to
                                    CatalogCorrection(
                                        saved.getString("name"),
                                        if (saved.isNull("price")) null else saved.getLong("price"),
                                        saved.getString("category"),
                                    )
                            }
                                .getOrNull()
                        }
                }
                .toMap()
        correctedShopCatalog(originals, corrections)
    }
}

@Composable
internal fun SavedItemsSection(data: FinanceSnapshot, busy: Boolean) {
    val prefs = LocalContext.current.getSharedPreferences("saved_items", 0)
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf("Name") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val originals = remember(data) { shopCatalog(data) }
    val catalog = rememberSavedCatalog(originals)
    MoneyCard {
        Text("Find an item", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            query,
            { query = it },
            label = { Text("Search saved items") },
            modifier = Modifier.fillMaxWidth(),
        )
        Choice("Sort", sort, listOf("Name", "Latest", "Price")) { sort = it }
        Choice(
            "Category",
            category,
            listOf("All") + catalog.map { it.category }.distinct().sorted(),
        ) {
            category = it
        }
        val matches =
            searchShopCatalog(catalog, query).filter {
                category == "All" || it.category == category
            }
        val sorted =
            when (sort) {
                "Latest" -> matches.sortedByDescending { it.date }
                "Price" ->
                    matches.sortedWith(
                        compareBy<CatalogItem> { it.price == null }.thenBy { it.price }
                    )
                else -> matches.sortedBy { it.searchKey }
            }
        if (busy && catalog.isEmpty()) repeat(3) { ShopLoadingSkeleton() }
        else if (sorted.isEmpty())
            Text(
                if (catalog.isEmpty())
                    "Save a shopping item or confirm a receipt to build your collection."
                else "No matching saved items."
            )
        sorted.forEach { item ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                    editing = item.sourceKeys.first()
                }
            ) {
                IdentityBadge(categoryIdentity(item.category))
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(item.price?.let(Money::format) ?: "Not specified")
                    if (item.purchases > 0) Text("${item.purchases} purchases")
                }
                TextButton(
                    onClick = { deleting = item.sourceKeys },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Delete")
                }
            }
        }
    }
    val original = originals.find { it.searchKey == editing }
    if (original != null) {
        val item =
            catalog.find { original.searchKey in it.sourceKeys }
                ?: runCatching {
                        JSONObject(prefs.getString("item:${original.searchKey}", "{}")!!).let {
                            saved ->
                            original.copy(
                                name = saved.getString("name"),
                                category = saved.getString("category"),
                                price = if (saved.isNull("price")) null else saved.getLong("price"),
                            )
                        }
                    }
                    .getOrDefault(original)
        val savedFields =
            remember(original.searchKey, revision) {
                runCatching { JSONObject(prefs.getString("item:${original.searchKey}", "{}")!!) }
                    .getOrDefault(JSONObject())
            }
        ShopItemFields(
            item.name,
            ShoppingItem(
                "catalog:${original.searchKey}",
                "",
                item.name,
                savedFields.optString("quantity", "1"),
                item.productKey,
                item.price,
                savedFields.optBoolean("optional", false),
                category = item.category,
                note = savedFields.optString("note", ""),
            ),
            busy,
            cancel = { editing = null },
        ) { updated ->
            val edit = prefs.edit()
            item.sourceKeys.forEach { sourceKey ->
                edit.putString(
                    "item:$sourceKey",
                    JSONObject()
                        .put("name", updated.name)
                        .put("category", updated.category)
                        .put("price", updated.manualPriceMinor ?: JSONObject.NULL)
                        .put("quantity", updated.quantity)
                        .put("note", updated.note)
                        .put("optional", updated.optional)
                        .toString(),
                )
            }
            edit.apply()
            editing = null
        }
    }
    if (deleting != null)
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete saved item?") },
            text = {
                Text(
                    "Remove this suggestion from Saved Items? Your purchase history remains available."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs
                            .edit()
                            .apply {
                                deleting.orEmpty().forEach { putBoolean("deleted:$it", true) }
                            }
                            .apply()
                        deleting = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
}

@Composable
internal fun ScannedReceiptsSection(model: AppModel, data: FinanceSnapshot, busy: Boolean) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var draftId by rememberSaveable { mutableStateOf<String?>(null) }
    MoneyCard {
        Text("Scanned receipts", style = MaterialTheme.typography.titleLarge)
        if (busy && data.receipt.receipts.isEmpty()) repeat(3) { ShopLoadingSkeleton() }
        if (data.receipt.receipts.isEmpty() && data.receipt.drafts.isEmpty() && !busy)
            Text("Your scanned receipts will appear here.")
        data.receipt.receipts.forEach { receipt ->
            val storedCount = data.receiptItems.count { it.receiptId == receipt.id }
            val count =
                if (storedCount > 0) storedCount
                else
                    remember(receipt.rawText) {
                        ReceiptDraftCodec.parse(receipt.rawText).lines.size
                    }
            Record(
                receipt.merchant.ifBlank { "Not specified" },
                "${receipt.date.ifBlank { "Not specified" }} · $count items",
                Money.format(receipt.totalMinor),
            ) {
                selected = receipt.id
            }
        }
        data.receipt.drafts.forEach { draft ->
            val parsed = remember(draft.rawText) { ReceiptDraftCodec.parse(draft.rawText) }
            Record(
                parsed.merchant.ifBlank { "Not specified" },
                "${parsed.date?.toString() ?: "Not specified"} · ${parsed.lines.size} items · Unconfirmed",
                parsed.totalMinor?.let(Money::format) ?: "Not specified",
            ) {
                draftId = draft.id
            }
        }
    }
    data.receipt.receipts
        .find { it.id == selected }
        ?.let { ReceiptDetails(model, it, busy) { selected = null } }
    data.receipt.drafts
        .find { it.id == draftId }
        ?.let { draft ->
            val parsed = remember(draft.rawText) { ReceiptDraftCodec.parse(draft.rawText) }
            StagedEditSheet(
                "Scanned receipt",
                parsed.merchant.ifBlank { "Not specified" },
                false,
                false,
                { draftId = null },
            ) {
                ShopReceipt(
                    ShopReceiptModel(
                        parsed.merchant.ifBlank { "Not specified" },
                        parsed.date?.toString(),
                        parsed.lines.mapIndexed { index, line ->
                            capturedReceiptLine("draft:$index", line)
                        },
                        parsed.totalMinor,
                        scanned = true,
                        recordedSubtotal = parsed.subtotalMinor,
                    )
                )
            }
        }
}

@Composable
private fun ShopLoadingSkeleton() {
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        Modifier.fillMaxWidth().height(64.dp).clearAndSetSemantics {
            contentDescription = "Loading saved records"
        },
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(color, CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.fillMaxWidth(.75f)
                    .height(16.dp)
                    .background(color, MaterialTheme.shapes.small)
            )
            Box(
                Modifier.fillMaxWidth(.45f)
                    .height(12.dp)
                    .background(color, MaterialTheme.shapes.small)
            )
        }
    }
}
