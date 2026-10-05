package jm.yardmoney.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import jm.yardmoney.data.*
import org.json.*

internal class ShopDraftViewModel(private val saved: SavedStateHandle) : ViewModel() {
    // The activity-scoped handle survives tab changes, rotation and Android process recreation.
    val state = saved.getStateFlow("shopDraft", "")

    fun form(key: String) = saved.getStateFlow(key, "")

    fun formField(key: String, field: String, value: String) {
        val json =
            // Interrupted or incompatible saved state must not make editing crash on every launch.
            runCatching { saved.get<String>(key)?.takeIf { it.isNotBlank() }?.let(::JSONObject) }
                .getOrNull() ?: JSONObject()
        saved[key] = json.put(field, value).toString()
    }

    fun clearForm(key: String) {
        saved[key] = ""
    }

    fun store(draft: ShopDraft?) {
        val previous = ShopDraft.decode(state.value)
        if (previous?.list?.id != draft?.list?.id) {
            previous?.items?.forEach { clearForm("shopForm:${it.id}") }
            val picker = runCatching {
                saved.get<String>("shopCatalog")?.takeIf { it.isNotBlank() }?.let(::JSONObject)
            }
                .getOrNull()
            picker?.optString("query")?.let { clearForm("shopForm:${normalizedShopName(it)}") }
            clearForm("shopCatalog")
        }
        saved["shopDraft"] = draft?.encode() ?: ""
    }
}

internal data class ShopDraft(
    val list: ShoppingList,
    val items: List<ShoppingItem>,
    val replacing: Boolean = false,
) {
    fun encode(): String =
        JSONObject()
            .put("id", list.id)
            .put("name", list.name)
            .put("date", list.createdDate ?: JSONObject.NULL)
            .put("replacing", replacing)
            .put(
                "items",
                JSONArray().apply {
                    items.forEach { item ->
                        put(
                            JSONObject()
                                .put("id", item.id)
                                .put("name", item.name)
                                .put("quantity", item.quantity)
                                .put("key", item.productKey ?: JSONObject.NULL)
                                .put("price", item.manualPriceMinor ?: JSONObject.NULL)
                                .put("optional", item.optional)
                                .put("checked", item.checked)
                                .put("category", item.category)
                                .put("note", item.note)
                        )
                    }
                },
            )
            .toString()

    companion object {
        fun decode(json: String): ShopDraft? =
            if (json.isBlank()) null
            else
                runCatching {
                    val root = JSONObject(json)
                    val list =
                        ShoppingList(
                            root.getString("id"),
                            root.getString("name"),
                            if (root.isNull("date")) null else root.getString("date"),
                        )
                    val rows = root.getJSONArray("items")
                    ShopDraft(
                        list,
                        (0 until rows.length()).map { i ->
                            val r = rows.getJSONObject(i)
                            ShoppingItem(
                                r.getString("id"),
                                list.id,
                                r.getString("name"),
                                r.getString("quantity"),
                                if (r.isNull("key")) null else r.getString("key"),
                                if (r.isNull("price")) null else r.getLong("price"),
                                r.getBoolean("optional"),
                                r.getBoolean("checked"),
                                r.getString("category"),
                                r.getString("note"),
                            )
                        },
                        root.getBoolean("replacing"),
                    )
                }
                    .getOrNull()
    }
}
