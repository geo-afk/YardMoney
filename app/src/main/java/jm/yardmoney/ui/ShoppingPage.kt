package jm.yardmoney.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
            route == "items" -> SavedItemsSection(data, busy, ::back)
            route == "receipts" -> ScannedReceiptsSection(model, data, busy, ::back)
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
internal fun ShopHeading(title: String, back: () -> Unit) {
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
