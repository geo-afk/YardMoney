package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import jm.yardmoney.data.*
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ShopUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun catalogSearchAndInlineNewItemKeepUnspecifiedPrice() {
        var added: ShoppingItem? = null
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ShopCatalogPicker(
                        listOf(CatalogItem("Milk", "milk", 15000, "Groceries", "2026-10-03")),
                        false,
                    ) {
                        added = it
                    }
                }
            }
        }
        compose.onNodeWithText("Milk").performScrollTo().performClick()
        assertEquals(15000L, added!!.manualPriceMinor)
        assertEquals("1", added.quantity)
        compose.onNodeWithText("Search receipt items").performTextInput("Oats")
        compose.onNodeWithText("Add 'Oats' as new item").performScrollTo().performClick()
        compose.onNodeWithText("Add to list").performScrollTo().performClick()
        assertEquals("Oats", added.name)
        assertNull(added.manualPriceMinor)
    }

    @Test
    fun liveReceiptUpdatesWithCatalogAndQuantityChanges() {
        var saved = false
        compose.setContent {
            var draft by remember {
                mutableStateOf(
                    ShopDraft(ShoppingList("trip", "Weekly shop", "2026-10-03"), emptyList())
                )
            }
            MaterialTheme {
                ShopEditor(
                    draft,
                    listOf(CatalogItem("Milk", "milk", 15000, "Groceries", "2026-10-03")),
                    false,
                    100000,
                    { draft = it },
                    {},
                    { saved = true },
                )
            }
        }
        compose.onNodeWithText("Save shopping list").assertIsNotEnabled()
        compose.onNodeWithText("Milk").performScrollTo().performClick()
        compose.onNodeWithText("J$150 • estimated total").assertExists()
        compose
            .onNodeWithTag("shop-editor-list")
            .performScrollToNode(hasContentDescription("Increase Milk quantity"))
        compose
            .onNodeWithContentDescription("Increase Milk quantity")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("J$300 • estimated total").assertExists()
        compose.onNodeWithTag("shop-editor-list").performScrollToNode(hasText("Save shopping list"))
        compose.onNodeWithText("Save shopping list").performScrollTo().performClick()
        assertTrue(saved)
        compose.onNodeWithText("YARDMONEY").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Expand or collapse receipt preview").performClick()
        compose.onNodeWithText("Subtotal (priced items)").assertExists()
        // Back now dismisses the staged list editor; use the preview's own collapse action.
        compose.onNodeWithContentDescription("Expand or collapse receipt preview").performClick()
        compose.onNodeWithText("YARDMONEY").assertIsNotDisplayed()
    }

    @Test
    fun savedListCheckEditRenameDuplicateDeleteAndReceiptAreSeparateActions() {
        var duplicated = false
        var deleted = false
        var renamed = ""
        val list = ShoppingList("trip", "Weekly shop", "2026-10-03")
        compose.setContent {
            var items by remember {
                mutableStateOf(
                    listOf(
                        ShoppingItem(
                            "milk",
                            "trip",
                            "Milk",
                            "1",
                            null,
                            15000,
                            false,
                            category = "Groceries",
                        )
                    )
                )
            }
            MaterialTheme {
                ShopDetail(
                    list,
                    items,
                    100000,
                    false,
                    {},
                    {},
                    toggle = { item ->
                        items = items.map {
                            if (it.id == item.id) it.copy(checked = !it.checked) else it
                        }
                    },
                    update = { updated -> items = listOf(updated) },
                    remove = { items = emptyList() },
                    duplicate = { duplicated = true },
                    rename = { renamed = it },
                    delete = { deleted = true },
                )
            }
        }
        compose.onNodeWithContentDescription("Picked up Milk").performScrollTo().performClick()
        compose.onNodeWithText("1 of 1 picked up").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Edit Milk").performScrollTo().performClick()
        compose.onNodeWithText("Note (optional)").performScrollTo().performTextInput("Low fat")
        compose.onNodeWithText("Update item").performScrollTo().performClick()
        compose.onNodeWithText("Low fat").assertExists()
        compose.onNodeWithText("Duplicate").performScrollTo().performClick()
        assertTrue(duplicated)
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithText("List name").performTextReplacement("Market trip")
        compose.onNodeWithText("Save name").performClick()
        assertEquals("Market trip", renamed)
        compose.onNodeWithText("View receipt").performScrollTo().performClick()
        compose.onNodeWithText("YARDMONEY").assertExists()
        compose.onNodeWithContentDescription("Delete list").performScrollTo().performClick()
        compose.onNodeWithText("Delete list").performClick()
        assertTrue(deleted)
    }

    @Test
    fun savedStateRestoresDraftAndUnfinishedItemFields() {
        val saved = SavedStateHandle()
        val vm = ShopDraftViewModel(saved)
        val draft =
            ShopDraft(
                ShoppingList("trip", "Weekly shop", "2026-10-03"),
                listOf(
                    ShoppingItem(
                        "rice",
                        "trip",
                        "Rice",
                        "1.5",
                        null,
                        null,
                        true,
                        category = "Groceries",
                        note = "Brown",
                    )
                ),
            )
        vm.store(draft)
        vm.formField("shopForm:rice", "price", "15.")
        vm.formField("shopCatalog", "query", "Oats")
        vm.formField("shopCatalog", "creating", "true")
        val restored =
            ShopDraftViewModel(
                SavedStateHandle(
                    mapOf(
                        "shopDraft" to saved.get<String>("shopDraft"),
                        "shopForm:rice" to saved.get<String>("shopForm:rice"),
                        "shopCatalog" to saved.get<String>("shopCatalog"),
                    )
                )
            )
        assertEquals(draft, ShopDraft.decode(restored.state.value))
        assertTrue(restored.form("shopForm:rice").value.contains("15."))
        assertTrue(restored.form("shopCatalog").value.contains("Oats"))
        assertTrue(restored.form("shopCatalog").value.contains("true"))
    }

    @Test
    fun saveFeedbackStaysVisibleAfterItsMessageIsConsumed() {
        val success = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
        compose.setContent { MaterialTheme { SnackbarHost(rememberSuccessSnackbar(success)) } }
        compose.runOnIdle { success.value = "Shopping list saved" }
        compose.onNodeWithText("Shopping list saved").assertIsDisplayed()
        compose.runOnIdle { assertNull(success.value) }
        compose.onNodeWithText("Shopping list saved").assertIsDisplayed()
    }

    @Test
    fun largeFontNewItemFormRemainsScrollableAndValid() {
        var added: ShoppingItem? = null
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(density.density, 2f)
            ) {
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ShopItemFields("Oats", null, false, {}) { added = it }
                    }
                }
            }
        }
        compose.onNodeWithText("Add to list").performScrollTo().performClick()
        assertEquals("Oats", added!!.name)
        assertNull(added.manualPriceMinor)
    }

    @Test
    fun swipeChecksRemovesAndRestoresTheSameLazyItemWithoutDeletingAgain() {
        compose.setContent {
            val original = ShoppingItem("milk", "trip", "Milk", "1", null, 15000, false)
            var rows by remember { mutableStateOf(listOf(original)) }
            var removed by remember { mutableStateOf<ShoppingItem?>(null) }
            MaterialTheme {
                LazyColumn {
                    items(rows, key = { it.id }) { current ->
                        ShopItemRow(
                            current,
                            false,
                            toggle = { rows = listOf(current.copy(checked = !current.checked)) },
                            update = { rows = listOf(it) },
                            remove = {
                                removed = current
                                rows = emptyList()
                            },
                        )
                    }
                    if (rows.isEmpty())
                        item {
                            Text("Item removed")
                            Button(onClick = { rows = listOf(removed!!) }) {
                                Text("Undo item removal")
                            }
                        }
                }
            }
        }
        compose.onNodeWithTag("shop-item-milk").performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription("Picked up Milk").assertIsOn()
        compose.onNodeWithTag("shop-item-milk").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("shop-item-milk").assertDoesNotExist()
        compose.onNodeWithText("Item removed").assertIsDisplayed()
        compose.onNodeWithText("Undo item removal").performClick()
        compose.onNodeWithTag("shop-item-milk").assertIsDisplayed()
        compose.onNodeWithContentDescription("Picked up Milk").assertIsOn()
        compose.waitForIdle()
        compose.onNodeWithText("Item removed").assertDoesNotExist()
    }

    @Test
    fun undoFeedbackReplacesOlderMessagesAndTargetsTheCurrentAction() {
        compose.setContent {
            val snack = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            var restored by remember { mutableStateOf(false) }
            LaunchedEffect(snack) {
                snack.showSnackbar("Previous feedback", duration = SnackbarDuration.Indefinite)
            }
            MaterialTheme {
                Column {
                    Button(
                        onClick = {
                            scope.launch {
                                if (
                                    snack.showShopUndo("Milk removed") ==
                                        SnackbarResult.ActionPerformed
                                )
                                    restored = true
                            }
                        }
                    ) {
                        Text("Remove milk")
                    }
                    if (restored) Text("Milk restored")
                    SnackbarHost(snack)
                }
            }
        }
        compose.onNodeWithText("Previous feedback").assertIsDisplayed()
        compose.onNodeWithText("Remove milk").performClick()
        compose.onNodeWithText("Milk removed").assertIsDisplayed()
        compose.onNodeWithText("Previous feedback").assertDoesNotExist()
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Milk restored").assertIsDisplayed()
    }
}
