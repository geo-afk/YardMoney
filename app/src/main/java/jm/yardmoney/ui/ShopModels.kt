package jm.yardmoney.ui

import java.text.Normalizer
import java.util.Locale
import jm.yardmoney.core.*
import jm.yardmoney.data.*

private val shopNameSeparator = Regex("[^\\p{L}\\p{N}]+")

internal fun normalizedShopName(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(shopNameSeparator, " ")
        .trim()

internal data class CatalogItem(
    val name: String,
    val productKey: String?,
    val price: Long?,
    val category: String,
    val date: String,
) {
    val searchKey = normalizedShopName(name)
}

internal fun shopCatalog(data: FinanceSnapshot): List<CatalogItem> {
    // Only confirmed items from the already scoped receipt projection can enter the catalog.
    val receipts = data.receipt.receipts.associateBy { it.id }
    val observations = data.receipt.prices.associateBy { it.itemId }
    val transactions = data.ledger.transactions.associateBy { it.id }
    return data.receiptItems
        .filter { it.confirmed && it.receiptId in receipts && it.confirmedName.isNotBlank() }
        .map { item ->
            val receipt = receipts.getValue(item.receiptId)
            val price = observations[item.id]
            CatalogItem(
                item.confirmedName.trim(),
                price?.productKey,
                price?.packPriceMinor
                    ?: runCatching {
                        Money.unitPrice(item.totalMinor, Quantity.parse(item.quantity))
                    }
                        .getOrNull(),
                transactions[receipt.transactionId]?.category?.takeIf { it.isNotBlank() }
                    ?: "Other",
                receipt.date,
            )
        }
        .groupBy { it.searchKey }
        .values
        .map { rows -> rows.maxWith(compareBy<CatalogItem> { it.date }.thenBy { it.name }) }
        .sortedBy { it.searchKey }
}

internal fun searchShopCatalog(catalog: List<CatalogItem>, query: String): List<CatalogItem> {
    val words = normalizedShopName(query).split(' ').filter { it.isNotBlank() }
    return catalog.filter { item -> words.all { it in item.searchKey } }
}

internal fun offerNewShopItem(catalog: List<CatalogItem>, query: String) =
    normalizedShopName(query).isNotEmpty() && searchShopCatalog(catalog, query).isEmpty()

internal data class ShopReceiptLine(val item: ShoppingItem, val price: Long?, val total: Long?)

internal data class ShopReceiptModel(
    val name: String,
    val date: String?,
    val lines: List<ShopReceiptLine>,
) {
    val subtotal = Money.sum(lines.mapNotNull { it.total })
    val missing = lines.count { it.total == null }
    val remaining = Money.sum(lines.filter { !it.item.checked }.mapNotNull { it.total })
    val remainingMissing = lines.count { !it.item.checked && it.total == null }
}

internal fun validatedShopItem(item: ShoppingItem): ShoppingItem {
    require(item.name.trim().length in 1..120)
    require(item.category.trim().length in 1..120 && item.note.length <= 500)
    Quantity.parse(item.quantity)
    require(item.manualPriceMinor == null || item.manualPriceMinor in 0..Money.MAX_MINOR)
    item.manualPriceMinor?.let { Quantity.estimate(it, item.quantity) }
    return item
}

internal fun shopReceipt(list: ShoppingList, items: List<ShoppingItem>): ShopReceiptModel =
    ShopReceiptModel(
        list.name,
        list.createdDate,
        items.map { item ->
            // A receipt estimate uses the chosen price snapshot, never a changing catalog fallback.
            ShopReceiptLine(
                item,
                item.manualPriceMinor,
                item.manualPriceMinor?.let { Quantity.estimate(it, item.quantity) },
            )
        },
    )

internal fun groceryBudgetRemaining(data: FinanceSnapshot, today: String): Long? {
    val limit =
        data.limits.firstOrNull { it.category.equals("Groceries", true) && it.bucket == "NEEDS" }
            ?: return null
    val start = data.ledger.profile?.periodStart ?: return null
    val ids =
        data.ledger.transactions
            .filter { it.date >= start && it.date <= today }
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
    return limit.limitMinor - used
}

internal fun shopCount(count: Int, noun: String) = "$count $noun${if(count == 1) "" else "s"}"
