package jm.yardmoney.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.math.BigDecimal
import jm.yardmoney.core.*
import jm.yardmoney.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShopEditor(
    draft: ShopDraft,
    catalog: List<CatalogItem>,
    busy: Boolean,
    safeMinor: Long,
    change: (ShopDraft) -> Unit,
    back: () -> Unit,
    save: () -> Unit,
    groceryRemaining: Long? = null,
) {
    val originalDraft = rememberSaveable(draft.list.id) { draft.toString() }
    var previewExpanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(previewExpanded) { previewExpanded = false }
    val receipt = remember(draft) { shopReceipt(draft.list, draft.items) }
    var editError by rememberSaveable { mutableStateOf<String?>(null) }
    fun publish(items: List<ShoppingItem>) {
        runCatching { shopReceipt(draft.list, items) }
            .onSuccess {
                editError = null
                change(draft.copy(items = items))
            }
            .onFailure { editError = it.message ?: "Check the quantity and price." }
    }
    StagedEditSheet(
        if (draft.replacing) "Edit Shopping List" else "New Shopping List",
        "${draft.list.name} · ${Money.format(receipt.subtotal)}",
        busy,
        draft.toString() != originalDraft,
        back,
        scrollContent = false,
    ) {
        val dismiss = LocalEditDismiss.current
        BoxWithConstraints(Modifier.widthIn(max = 840.dp).fillMaxSize().imePadding()) {
            val compactHeight = maxHeight < 360.dp
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val peekHeight = (if (compactHeight) 64.dp else 96.dp) + 64.dp * (fontScale - 1f)
            val receiptHeight = if (compactHeight) maxHeight else maxHeight * .85f
            ShopReceiptScaffold(
                if (previewExpanded) receiptHeight else peekHeight,
                sheetContent = {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = receiptHeight).widthIn(max = 840.dp)
                    ) {
                        TextButton(
                            onClick = {
                                previewExpanded = !previewExpanded
                            },
                            modifier =
                                Modifier.fillMaxWidth().heightIn(min = peekHeight).semantics {
                                    contentDescription = "Expand or collapse receipt preview"
                                },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Live receipt", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${Money.format(receipt.subtotal)}${if(receipt.missing > 0) " • partial estimate" else " • estimated total"}"
                                )
                            }
                            Icon(
                                if (previewExpanded) Icons.Default.ExpandMore
                                else Icons.Default.ExpandLess,
                                null,
                            )
                        }
                        Column(
                            Modifier.weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 20.dp)
                        ) {
                            ShopReceipt(receipt)
                        }
                    }
                },
            ) { padding ->
                LazyColumn(
                    Modifier.widthIn(max = 840.dp).fillMaxSize().testTag("shop-editor-list"),
                    contentPadding =
                        PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 12.dp,
                            bottom = padding.calculateBottomPadding() + 24.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        ShopHeading(
                            if (draft.replacing) "Edit Shopping List" else "New Shopping List",
                            dismiss,
                        )
                    }
                    item {
                        OutlinedTextField(
                            draft.list.name,
                            { change(draft.copy(list = draft.list.copy(name = it.take(120)))) },
                            label = { Text("List name") },
                            isError = draft.list.name.isBlank(),
                            supportingText = {
                                Text(
                                    if (draft.list.name.isBlank()) "A name is required"
                                    else "${draft.list.name.length}/120"
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                    item {
                        Button(
                            save,
                            enabled =
                                !busy && draft.list.name.isNotBlank() && draft.items.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Default.Check, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (busy) "Saving…" else "Save shopping list")
                        }
                    }
                    item {
                        ShopCatalogPicker(catalog, busy) { item ->
                            val old =
                                draft.items.find {
                                    normalizedShopName(it.name) == normalizedShopName(item.name)
                                }
                            val combined = old?.let {
                                Quantity.parse(it.quantity).add(Quantity.parse(item.quantity))
                            }
                            if (combined != null && combined > BigDecimal("1000000")) {
                                editError = "Quantity cannot exceed one million."
                                return@ShopCatalogPicker
                            }
                            val items =
                                if (old != null)
                                    draft.items.map {
                                        if (it.id == old.id)
                                            it.copy(
                                                quantity =
                                                    Quantity.parse(it.quantity)
                                                        .add(Quantity.parse(item.quantity))
                                                        .stripTrailingZeros()
                                                        .toPlainString()
                                            )
                                        else it
                                    }
                                else draft.items + item.copy(listId = draft.list.id)
                            publish(items)
                        }
                    }
                    if (editError != null)
                        item {
                            Text(
                                editError!!,
                                color = MaterialTheme.colorScheme.error,
                                modifier =
                                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    item {
                        SectionHeading(
                            "In your basket",
                            subtitle =
                                "${shopCount(draft.items.size, "item")} • duplicate additions increase quantity",
                        )
                    }
                    if (draft.items.isEmpty())
                        item {
                            EmptyState(
                                "Your basket is open",
                                "Search your receipts above, or add something new. Prices are optional.",
                                icon = Icons.Default.AddShoppingCart,
                            )
                        }
                    items(draft.items, key = { it.id }) { item ->
                        ShopItemRow(
                            item,
                            busy,
                            toggle = null,
                            update = { updated ->
                                val next =
                                    draft.items.map { if (it.id == updated.id) updated else it }
                                publish(next)
                            },
                            remove = {
                                change(
                                    draft.copy(items = draft.items.filterNot { it.id == item.id })
                                )
                            },
                        )
                    }
                    item { ShopAffordability(receipt, safeMinor, groceryRemaining) }
                }
            }
        }
    }
}

// A fixed preview panel leaves the list viewport above it unobstructed. A second nested
// draggable scaffold can expand during BringIntoView and intercept basket button taps.
@Composable
private fun ShopReceiptScaffold(
    previewHeight: Dp,
    sheetContent: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { content(PaddingValues(0.dp)) }
        Surface(
            Modifier.fillMaxWidth().height(previewHeight),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        ) {
            sheetContent()
        }
    }
}

@Composable
internal fun ShopCatalogPicker(
    catalog: List<CatalogItem>,
    busy: Boolean,
    add: (ShoppingItem) -> Unit,
) {
    val pickerState: ShopDraftViewModel = viewModel()
    val pickerEncoded by pickerState.form("shopCatalog").collectAsStateWithLifecycle()
    val picker =
        remember(pickerEncoded) {
            if (pickerEncoded.isBlank()) org.json.JSONObject()
            else org.json.JSONObject(pickerEncoded)
        }
    val query = picker.optString("query", "")
    val creating = picker.optString("creating", "false").toBoolean()
    fun setQuery(value: String) {
        pickerState.formField("shopCatalog", "query", value)
    }
    fun setCreating(value: Boolean) {
        pickerState.formField("shopCatalog", "creating", value.toString())
    }
    val found = remember(catalog, query) { searchShopCatalog(catalog, query) }
    MoneyCard {
        Text("Find your favourites", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            query,
            {
                setQuery(it)
                setCreating(false)
            },
            label = { Text("Search receipt items") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        )
        Text(
            "Receipt prices are dated estimates. Adding the same item increases its quantity.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (catalog.isEmpty() && query.isBlank())
            Text("No confirmed receipt items in this scope yet. Type a name to start.")
        // A bounded catalog scroll keeps long receipt histories fast without nesting unbounded
        // lists.
        if (found.isNotEmpty())
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(found, key = { it.searchKey }) { item ->
                    Card(
                        onClick = {
                            add(
                                ShoppingItem(
                                    FinanceRepository.id(),
                                    "",
                                    item.name,
                                    "1",
                                    item.productKey,
                                    item.price,
                                    false,
                                    category = item.category,
                                )
                            )
                        },
                        enabled = !busy,
                        shape = MaterialTheme.shapes.medium,
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IdentityBadge(categoryIdentity(item.category))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${item.price?.let(Money::format) ?: "Not specified"} • ${item.date}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Icon(Icons.Default.Add, "Add ${item.name}")
                        }
                    }
                }
            }
        if (offerNewShopItem(catalog, query)) {
            FilledTonalButton(
                onClick = { setCreating(true) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add '$query' as new item")
            }
        } else if (query.isBlank())
            TextButton(onClick = { setCreating(true) }) { Text("Add a new item") }
        if (creating)
            key(query) {
                ShopItemFields(query, null, busy, cancel = { setCreating(false) }) { item ->
                    add(item)
                    setQuery("")
                    setCreating(false)
                }
            }
    }
}

@Composable
internal fun ShopItemFields(
    initialName: String,
    original: ShoppingItem?,
    busy: Boolean,
    cancel: () -> Unit,
    save: (ShoppingItem) -> Unit,
) {
    val formState: ShopDraftViewModel = viewModel()
    val formKey = "shopForm:${original?.id ?: normalizedShopName(initialName)}"
    val encoded by formState.form(formKey).collectAsStateWithLifecycle()
    val values =
        remember(encoded) {
            if (encoded.isBlank()) org.json.JSONObject() else org.json.JSONObject(encoded)
        }
    val name = values.optString("name", original?.name ?: initialName)
    val quantity = values.optString("quantity", original?.quantity ?: "1")
    val price = values.optString("price", original?.manualPriceMinor?.let(Money::input) ?: "")
    val note = values.optString("note", original?.note ?: "")
    val category = values.optString("category", original?.category ?: "Groceries")
    val optional =
        values.optString("optional", (original?.optional ?: false).toString()).toBoolean()
    val validation = runCatching {
        require(name.trim().length in 1..120) { "Enter an item name (up to 120 characters)." }
        Quantity.parse(quantity)
        val entered = price.takeIf { it.isNotBlank() }?.let(Money::parse)
        entered?.let { Quantity.estimate(it, quantity) }
        require(category.trim().length in 1..120) { "Choose a category (up to 120 characters)." }
        entered
    }
    StagedEditSheet(
        "Edit item",
        "$name · ${price.ifBlank { "Not specified" }}",
        busy,
        encoded.isNotBlank(),
        {
            formState.clearForm(formKey)
            cancel()
        },
    ) {
        val requestDismiss = LocalEditDismiss.current
        OutlinedTextField(
            name,
            { formState.formField(formKey, "name", it.take(120)) },
            label = { Text("Item name") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        )
        NumberField("Quantity", quantity) { formState.formField(formKey, "quantity", it) }
        MoneyField("Price per item (optional)", price) { formState.formField(formKey, "price", it) }
        IdentityPicker(
            "Category",
            category,
            listOf("Groceries", "Household", "Health", "Clothing", "Other").map {
                IdentityOption(it, it, identity = categoryIdentity(it))
            },
            allowCustom = true,
        ) {
            formState.formField(formKey, "category", it)
        }
        OutlinedTextField(
            note,
            { formState.formField(formKey, "note", it.take(500)) },
            label = { Text("Note (optional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        )
        Tick("Optional item", optional) { formState.formField(formKey, "optional", it.toString()) }
        validation.exceptionOrNull()?.message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val item =
                        (original
                                ?: ShoppingItem(
                                    FinanceRepository.id(),
                                    "",
                                    "",
                                    "1",
                                    null,
                                    null,
                                    false,
                                ))
                            .copy(
                                name = name.trim(),
                                quantity = quantity.trim(),
                                manualPriceMinor = validation.getOrThrow(),
                                category = category,
                                note = note,
                                optional = optional,
                                productKey =
                                    original?.productKey.takeIf {
                                        price == original?.manualPriceMinor?.let(Money::input)
                                    },
                            )
                    save(item)
                    formState.clearForm(formKey)
                },
                enabled = !busy && validation.isSuccess,
            ) {
                Text(if (original == null) "Add to list" else "Update item")
            }
            TextButton(onClick = requestDismiss) {
                Text("Cancel")
            }
        }
    }
}
