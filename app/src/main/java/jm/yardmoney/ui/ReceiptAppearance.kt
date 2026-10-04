package jm.yardmoney.ui

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import jm.yardmoney.core.ColorContrast
import jm.yardmoney.data.ShoppingItem

enum class ReceiptLayout {
    Thermal,
    Minimal,
    Compact,
    Detailed,
}

internal data class ReceiptAppearance(
    val layout: ReceiptLayout = ReceiptLayout.Thermal,
    val background: Int = -1,
    val sync: Boolean = false,
)

internal fun receiptBackground(settings: ReceiptAppearance, background: Int, accent: Int): Int =
    if (settings.sync) ColorContrast.blend(background, accent, .06) else settings.background

internal fun receiptInk(background: Int) = ColorContrast.readable(0xFF222222.toInt(), background)

private fun SharedPreferences.receiptAppearance() =
    ReceiptAppearance(
        runCatching { ReceiptLayout.valueOf(getString("receiptLayout", "Thermal")!!) }
            .getOrDefault(ReceiptLayout.Thermal),
        getInt("receiptBackground", -1),
        getBoolean("receiptSync", false),
    )

@Composable
internal fun rememberReceiptAppearance(): ReceiptAppearance {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("appearance", 0) }
    var settings by remember(prefs) { mutableStateOf(prefs.receiptAppearance()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            settings = prefs.receiptAppearance()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}

@Composable
internal fun ReceiptSettings() {
    val prefs = LocalContext.current.getSharedPreferences("appearance", 0)
    val settings = rememberReceiptAppearance()
    val sample =
        ShopReceiptModel(
            "Demo Market",
            "2026-10-04",
            listOf(
                ShopReceiptLine(
                    ShoppingItem(
                        "preview",
                        "",
                        "Rice",
                        "1",
                        null,
                        45000,
                        false,
                        category = "Groceries",
                    ),
                    45000,
                    45000,
                )
            ),
        )
    Text("Receipt layout", style = MaterialTheme.typography.titleMedium)
    ReceiptLayout.entries.forEach { layout ->
        OutlinedButton(
            onClick = { prefs.edit().putString("receiptLayout", layout.name).apply() },
            // A receipt thumbnail is taller than a normal button: capsule clipping hides its edges.
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(if (settings.layout == layout) "Selected: ${layout.name}" else layout.name)
                Text(
                    when (layout) {
                        ReceiptLayout.Thermal -> "Classic paper receipt with a torn edge"
                        ReceiptLayout.Minimal -> "Open spacing with item totals alongside"
                        ReceiptLayout.Compact -> "Dense rows for a quick overview"
                        ReceiptLayout.Detailed -> "Product cards grouped by category"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                ShopReceipt(sample, appearance = settings.copy(layout = layout), thumbnail = true)
            }
        }
    }
    Tick("Sync receipt background with app background", settings.sync) {
        prefs.edit().putBoolean("receiptSync", it).apply()
    }
    Text("Background color")
    listOf(
            "Paper" to -1,
            "Cream" to 0xFFFFF3D6.toInt(),
            "Mint" to 0xFFE1F5EA.toInt(),
            "Rose" to 0xFFFFE5ED.toInt(),
            "Ink" to 0xFF17202B.toInt(),
        )
        .forEach { (label, color) ->
            OutlinedButton(
                onClick = {
                    prefs
                        .edit()
                        .putInt("receiptBackground", color)
                        .putBoolean("receiptSync", false)
                        .apply()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(label)
            }
        }
    ShopReceipt(sample)
    TextButton(
        onClick = {
            prefs
                .edit()
                .remove("receiptLayout")
                .remove("receiptBackground")
                .remove("receiptSync")
                .apply()
        }
    ) {
        Text("Reset receipt appearance")
    }
}
