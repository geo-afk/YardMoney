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
