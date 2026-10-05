package jm.yardmoney.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import jm.yardmoney.data.ShoppingItem
import jm.yardmoney.data.ShoppingList
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ShopStateRobustnessTest {
    @Test
    fun corruptedTransientFormsCanBeEditedAndDiscardedWithoutCrash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val handle =
                SavedStateHandle(
                    mapOf("shopCatalog" to "{broken", "shopForm:rice" to "{", "shopDraft" to "[]")
                )
            val model = ShopDraftViewModel(handle)
            model.formField("shopForm:rice", "name", "Rice 🍚")
            assertEquals("Rice 🍚", JSONObject(model.form("shopForm:rice").value).getString("name"))
            model.store(ShopDraft(ShoppingList("new", "New list"), emptyList()))
            assertEquals("", model.form("shopCatalog").value)
            assertEquals("new", ShopDraft.decode(model.state.value)!!.list.id)
            model.store(null)
            assertEquals("", model.state.value)
        }
    }

    @Test
    fun restoredDraftKeepsEveryEditedFieldAndClearsOldEditorsWhenReplaced() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val item =
                ShoppingItem(
                    "row",
                    "list",
                    "Rice 🍚",
                    "0.125",
                    null,
                    1,
                    true,
                    true,
                    "Groceries",
                    "Unsweetened & small",
                )
            val draft =
                ShopDraft(ShoppingList("list", "Weekly shop 🛒", "2026-10-04"), listOf(item), true)
            val handle =
                SavedStateHandle(mapOf("shopDraft" to draft.encode(), "shopForm:row" to "{}"))
            val restored = ShopDraftViewModel(handle)
            assertEquals(draft, ShopDraft.decode(restored.state.value))
            restored.formField("shopForm:row", "note", "keep me")
            restored.store(draft.copy(items = listOf(item.copy(quantity = "2"))))
            assertEquals(
                "keep me",
                JSONObject(restored.form("shopForm:row").value).getString("note"),
            )
            restored.store(null)
            assertEquals("", restored.form("shopForm:row").value)
        }
    }
}
