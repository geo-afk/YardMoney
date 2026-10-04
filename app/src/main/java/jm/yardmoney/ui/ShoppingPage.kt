package jm.yardmoney.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.math.BigDecimal
import jm.yardmoney.AppModel
import jm.yardmoney.core.*
import jm.yardmoney.data.*
import kotlinx.coroutines.launch

@Composable
internal fun ShoppingPage(
    model: AppModel,
    data: FinanceSnapshot,
    safe: SafeToSpend,
    busy: Boolean,
    snack: SnackbarHostState,
) {
    val drafts: ShopDraftViewModel = viewModel()
    val encoded by drafts.state.collectAsStateWithLifecycle()
    val draft = remember(encoded) { ShopDraft.decode(encoded) }
    var route by rememberSaveable { mutableStateOf("home") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var discard by rememberSaveable { mutableStateOf(false) }
    var replaceDraft by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val catalog = rememberSavedCatalog(remember(data) { shopCatalog(data) })
    val groceryRemaining =
        remember(data.limits, data.splits, data.ledger.transactions, data.ledger.profile) {
            groceryBudgetRemaining(data, model.repo.today.toString())
        }
    val selected = data.shopping.lists.find { it.id == selectedId }
    val selectedItems = data.shopping.items.filter { it.listId == selectedId }
    fun newList() {
        drafts.store(
            ShopDraft(
                ShoppingList(
                    FinanceRepository.id(),
                    "Groceries, ${model.repo.today}",
                    model.repo.today.toString(),
                ),
                emptyList(),
            )
        )
        route = "edit"
    }
    fun startNew() {
        if (draft != null) replaceDraft = true else newList()
    }
    fun back() {
        if (route == "edit") discard = true else route = if (route == "detail") "lists" else "home"
    }
    BackHandler(route != "home") { back() }
    Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.TopCenter) {
        when {
            route == "edit" && draft != null ->
                ShopEditor(
                    draft,
                    catalog,
                    busy,
                    safe.safeMinor,
                    groceryRemaining = groceryRemaining,
                    change = drafts::store,
                    // The shared sheet has already confirmed discard; avoid a second prompt.
                    back = {
                        drafts.store(null)
                        route = "home"
                    },
                    save = {
                        val current = draft
                        model.act(
                            done = {
                                drafts.store(null)
                                selectedId = current.list.id
                                route = "detail"
                            },
                            successMessage = "Shopping list saved",
                        ) {
                            model.repo.saveShoppingList(
                                current.list,
                                current.items,
                                current.replacing,
                            )
                        }
                    },
                )
            route == "detail" && selected != null ->
                ShopDetail(
                    selected,
                    selectedItems,
                    safe.safeMinor,
                    busy,
                    groceryRemaining = groceryRemaining,
                    back = ::back,
                    edit = {
                        drafts.store(ShopDraft(selected, selectedItems, true))
                        route = "edit"
                    },
                    toggle = { item ->
                        model.act(
                            done = {
                                scope.launch {
                                    if (
                                        snack.showShopUndo(
                                            if (item.checked) "Unchecked ${item.name}"
                                            else "Picked up ${item.name}"
                                        ) == SnackbarResult.ActionPerformed
                                    )
                                        model.act(successMessage = null) {
                                            model.repo.dao.setShoppingChecked(
                                                item.id,
                                                item.checked,
                                                !item.checked,
                                            )
                                        }
                                }
                            },
                            successMessage = null,
                        ) {
                            model.repo.dao.setShoppingChecked(item.id, !item.checked, item.checked)
                        }
                    },
                    update = { item ->
                        model.act(successMessage = "Item updated") {
                            model.repo.dao.put(validatedShopItem(item))
                        }
                    },
                    remove = { item ->
                        model.act(
                            done = {
                                scope.launch {
                                    if (
                                        snack.showShopUndo("${item.name} removed") ==
                                            SnackbarResult.ActionPerformed
                                    )
                                        model.act(successMessage = null) {
                                            model.repo.dao.put(item)
                                        }
                                }
                            },
                            successMessage = null,
                        ) {
                            model.repo.dao.deleteShoppingItem(item.id)
                        }
                    },
                    duplicate = {
                        model.act(successMessage = "List duplicated") {
                            model.repo.duplicateShoppingList(selected, selectedItems)
                        }
                    },
                    rename = { name ->
                        model.act(successMessage = "List renamed") {
                            require(name.trim().length in 1..120)
                            model.repo.dao.put(selected.copy(name = name.trim()))
                        }
                    },
                    delete = {
                        model.act(
                            done = {
                                route = "lists"
                                scope.launch {
                                    if (
                                        snack.showShopUndo("List deleted") ==
                                            SnackbarResult.ActionPerformed
                                    )
                                        model.act(successMessage = null) {
                                            model.repo.restoreShoppingList(selected, selectedItems)
                                        }
                                }
                            },
                            successMessage = null,
                        ) {
                            model.repo.dao.deleteShoppingList(selected.id)
                        }
                    },
                )
            route == "items" || route == "receipts" ->
                LazyColumn(
                    Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        ShopHeading(
                            if (route == "items") "Saved Items" else "Scanned receipts",
                            ::back,
                        )
                    }
                    if (route == "items") {
                        item { SavedItemsSection(data, busy) }
                        item { ShopPriceNotebook(data.receipt.prices) }
                    } else item { ScannedReceiptsSection(model, data, busy) }
                }
            route == "lists" ->
                LazyColumn(
                    Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { ShopHeading("Shopping Lists", ::back) }
                    item {
                        FilledTonalButton(onClick = ::startNew, enabled = !busy) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("New Shopping List")
                        }
                    }
                    if (data.shopping.lists.isEmpty())
                        item {
                            EmptyState(
                                "A fresh start",
                                "Your saved lists will live here. Plan your next trip in a few taps.",
                                icon = Icons.Default.ShoppingBasket,
                            )
                        }
                    items(data.shopping.lists, key = { it.id }) { list ->
                        val items = data.shopping.items.filter { it.listId == list.id }
                        ShopListCard(list, items) {
                            selectedId = list.id
                            route = "detail"
                        }
                    }
                }
            else ->
                LazyColumn(
                    Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item {
                        SectionHeading(
                            "Make room for a better shop",
                            subtitle = "Plan it. Price it. Pick it up.",
                        )
                    }
                    if (draft != null)
                        item {
                            ShopEntryCard(
                                "Continue your list",
                                "${draft.list.name} • ${draft.items.size} items",
                                MoneyIdentity(Icons.Default.EditNote, 0xFFB52672),
                            ) {
                                route = "edit"
                            }
                        }
                    item {
                        ShopEntryCard(
                            "New Shopping List",
                            "Build your basket from receipts or start fresh",
                            MoneyIdentity(Icons.Default.AddShoppingCart, 0xFF00865A),
                            enabled = !busy,
                        ) {
                            if (draft != null) replaceDraft = true else newList()
                        }
                    }
                    item {
                        ShopEntryCard(
                            "Shopping Lists",
                            "${data.shopping.lists.size} saved • ready when you are",
                            MoneyIdentity(Icons.Default.ShoppingBasket, 0xFF6651DB),
                            badge = data.shopping.lists.size,
                        ) {
                            route = "lists"
                        }
                    }
                    item {
                        ShopEntryCard(
                            "Saved Items",
                            "${catalog.size} saved • search, browse and edit",
                            MoneyIdentity(Icons.Default.PriceCheck, 0xFF007F91),
                            badge = catalog.size,
                        ) {
                            route = "items"
                        }
                    }
                    item {
                        val count = data.receipt.receipts.size + data.receipt.drafts.size
                        ShopEntryCard(
                            "Scanned receipts",
                            "$count saved • view your purchases",
                            MoneyIdentity(Icons.AutoMirrored.Filled.ReceiptLong, 0xFFAD4324),
                            badge = count,
                        ) {
                            route = "receipts"
                        }
                    }
                }
        }
    }
    if (replaceDraft)
        AlertDialog(
            onDismissRequest = { replaceDraft = false },
            title = { Text("Start a fresh list?") },
            text = { Text("There is an unfinished list. Starting fresh discards that draft.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        replaceDraft = false
                        newList()
                    },
                    enabled = !busy,
                ) {
                    Text("Discard and start new")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        replaceDraft = false
                        route = "edit"
                    }
                ) {
                    Text("Continue draft")
                }
            },
        )
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Keep your shopping draft?") },
            text = { Text("Your items are kept until you save or discard this draft.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discard = false
                        route = "home"
                    }
                ) {
                    Text("Keep draft")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        discard = false
                        drafts.store(null)
                        route = "home"
                    }
                ) {
                    Text("Discard draft")
                }
            },
        )
}

@Composable
private fun ShopHeading(title: String, back: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(back, Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to shopping")
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ShopEntryCard(
    title: String,
    subtitle: String,
    identity: MoneyIdentity,
    badge: Int? = null,
    enabled: Boolean = true,
    click: () -> Unit,
) {
    Card(
        onClick = click,
        enabled = enabled,
        shape = YardShape.card,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(containerColor = identityColor(identity).copy(alpha = .10f)),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IdentityBadge(identity)
                Spacer(Modifier.weight(1f))
                if (badge != null) Badge { Text(badge.toString()) }
                Icon(Icons.Default.ChevronRight, null)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShopListCard(list: ShoppingList, items: List<ShoppingItem>, click: () -> Unit) {
    val receipt = remember(list, items) { shopReceipt(list, items) }
    Card(
        onClick = click,
        modifier = Modifier.fillMaxWidth(),
        shape = YardShape.card,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IdentityBadge(MoneyIdentity(Icons.Default.ShoppingBasket, 0xFF6651DB))
                Column(Modifier.weight(1f)) {
                    Text(list.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        list.createdDate ?: "Date not recorded",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Icon(Icons.Default.ChevronRight, null)
            }
            Text(
                "${items.size} items • ${Money.format(receipt.subtotal)}${if(receipt.missing > 0) " partial estimate" else " estimated"}"
            )
            LinearProgressIndicator(
                progress = {
                    if (items.isEmpty()) 0f else items.count { it.checked }.toFloat() / items.size
                },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Text(
                "${items.count { it.checked }} of ${items.size} picked up",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ShopItemRow(
    item: ShoppingItem,
    busy: Boolean,
    toggle: (() -> Unit)?,
    update: (ShoppingItem) -> Unit,
    remove: () -> Unit,
) {
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var actions by rememberSaveable(item.id) { mutableStateOf(false) }
    val fade by
        animateFloatAsState(
            if (item.checked) .65f else 1f,
            LocalMotion.current.floatSpec(),
            label = "Picked up item",
        )
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    // Gestures are transient: restoring a dismissed anchor from LazyColumn's saved state
    // would delete an item again when undo reintroduces its stable ID.
    val swipe =
        remember(threshold) { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled) { threshold } }
    // currentValue can change while a finger is down; settledValue fires once after dismissal.
    LaunchedEffect(swipe.settledValue) {
        when (swipe.settledValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                toggle?.invoke()
                swipe.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                remove()
                swipe.reset()
            }
            else -> Unit
        }
    }
    SwipeToDismissBox(
        modifier = Modifier.testTag("shop-item-${item.id}"),
        state = swipe,
        enableDismissFromStartToEnd = toggle != null && !busy,
        enableDismissFromEndToStart = !busy && !editing,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer, YardShape.card)
                    .padding(20.dp),
                contentAlignment =
                    if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                        Alignment.CenterEnd
                    else Alignment.CenterStart,
            ) {
                Icon(
                    if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                        Icons.Default.Delete
                    else Icons.Default.Check,
                    null,
                )
            }
        },
    ) {
        Card(
            Modifier.fillMaxWidth().animateContentSize(tween(LocalMotion.current.duration())),
            shape = YardShape.card,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(
                            enabled = !busy,
                            onClick = { if (toggle != null) toggle() else editing = !editing },
                            onLongClick = { actions = true },
                            onLongClickLabel = "Item actions",
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IdentityBadge(categoryIdentity(item.category))
                    Column(Modifier.weight(1f).alpha(fade)) {
                        AnimatedShopItemName(item.name, item.checked)
                        Text(
                            "${item.quantity} × ${item.manualPriceMinor?.let(Money::format) ?: "Not specified"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            item.category + if (item.optional) " • optional" else "",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (item.note.isNotBlank())
                            Text(item.note, style = MaterialTheme.typography.bodySmall)
                    }
                    if (toggle != null)
                        Checkbox(
                            item.checked,
                            onCheckedChange = { toggle() },
                            enabled = !busy,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "Picked up ${item.name}"
                                },
                        )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val next = Quantity.parse(item.quantity).subtract(BigDecimal.ONE)
                            if (next > BigDecimal.ZERO)
                                update(
                                    item.copy(quantity = next.stripTrailingZeros().toPlainString())
                                )
                        },
                        enabled = !busy && Quantity.parse(item.quantity) > BigDecimal.ONE,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Remove, "Decrease ${item.name} quantity")
                    }
                    Text(item.quantity, style = MaterialTheme.typography.titleMedium)
                    IconButton(
                        onClick = {
                            val next = Quantity.parse(item.quantity).add(BigDecimal.ONE)
                            if (next <= BigDecimal("1000000"))
                                update(
                                    item.copy(quantity = next.stripTrailingZeros().toPlainString())
                                )
                        },
                        enabled = !busy && Quantity.parse(item.quantity) < BigDecimal("1000000"),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Add, "Increase ${item.name} quantity")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { editing = !editing },
                        enabled = !busy,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Edit, "Edit ${item.name}")
                    }
                    IconButton(remove, enabled = !busy, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.DeleteOutline, "Remove ${item.name}")
                    }
                }
                if (editing)
                    ShopItemFields(item.name, item, busy, cancel = { editing = false }) {
                        update(it)
                        editing = false
                    }
            }
        }
    }
    if (actions)
        AlertDialog(
            onDismissRequest = { actions = false },
            title = { Text(item.name) },
            text = { Text("Edit its price, quantity or note, or remove it from your list.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions = false
                        editing = true
                    }
                ) {
                    Text("Edit item")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        actions = false
                        remove()
                    }
                ) {
                    Text("Remove item")
                }
            },
        )
}

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
private fun ShopAffordability(
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

@Composable
private fun ShopPriceNotebook(prices: List<PriceObservation>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    MoneyCard {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Icons.Default.PriceCheck, null)
            Spacer(Modifier.width(12.dp))
            Text("Your price notebook", Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (expanded) {
            if (prices.isEmpty()) Text("Confirm a receipt to compare dated package prices here.")
            prices
                .groupBy { it.productKey }
                .forEach { (_, rows) ->
                    Text(rows.maxBy { it.date }.name, style = MaterialTheme.typography.titleMedium)
                    rows
                        .sortedBy { it.unitPriceMinor }
                        .forEach { price ->
                            Record(
                                "${price.merchant} • ${price.branch.ifBlank { "Branch unknown" }}",
                                "${price.date} • ${price.packageSize} ${price.unit} • ${Money.format(price.packPriceMinor)} / package",
                                "${Money.format(price.unitPriceMinor)} / ${price.unit}",
                            )
                        }
                }
        }
    }
}

@Composable
private fun AnimatedShopItemName(name: String, checked: Boolean) {
    val progress by
        animateFloatAsState(
            if (checked) 1f else 0f,
            LocalMotion.current.floatSpec(),
            label = "Checklist strike",
        )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val color = MaterialTheme.colorScheme.onSurface
    Text(
        name,
        style = MaterialTheme.typography.titleMedium,
        onTextLayout = { layout = it },
        modifier =
            Modifier.semantics { stateDescription = if (checked) "Picked up" else "Not picked up" }
                .drawWithContent {
                    drawContent()
                    layout?.let { result ->
                        repeat(result.lineCount) { line ->
                            val left = result.getLineLeft(line)
                            val right = result.getLineRight(line)
                            val y = (result.getLineTop(line) + result.getLineBottom(line)) / 2
                            drawLine(
                                color,
                                Offset(left, y),
                                Offset(left + (right - left) * progress, y),
                                strokeWidth = 1.5.dp.toPx(),
                            )
                        }
                    }
                },
    )
}
