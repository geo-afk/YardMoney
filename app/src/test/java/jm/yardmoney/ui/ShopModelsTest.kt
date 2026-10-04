package jm.yardmoney.ui

import jm.yardmoney.core.Money
import jm.yardmoney.data.*
import org.junit.Assert.*
import org.junit.Test

class ShopModelsTest {
    private fun item(name: String, price: Long?, qty: String = "1", checked: Boolean = false) =
        ShoppingItem(name, "list", name, qty, null, price, false, checked)

    @Test
    fun capturedReceiptUnitPriceRequiresAnExplicitQuantity() {
        val unknown = jm.yardmoney.core.SuggestedLine("Milk 20.00", "Milk", "1", 2000)
        assertNull(capturedReceiptLine("one", unknown).price)
        val captured = unknown.copy(quantity = "2", quantitySpecified = true)
        assertEquals(1000L, capturedReceiptLine("two", captured).price)
        assertEquals(2000L, capturedReceiptLine("two", captured).total)
    }

    @Test
    fun totalsExcludeUnknownPricesAndTrackRemaining() {
        val receipt =
            shopReceipt(
                ShoppingList("list", "Trip"),
                listOf(item("Milk", 15000, "2", true), item("Rice", null), item("Bread", 3000)),
            )
        assertEquals(33000L, receipt.subtotal)
        assertEquals(1, receipt.missing)
        assertEquals(3000L, receipt.remaining)
        assertEquals(1, receipt.remainingMissing)
        assertNull(receipt.lines[1].price)
        assertNull(receipt.lines[1].total)
    }

    @Test
    fun zeroPriceIsExplicitAndDecimalQuantitiesUseExactRounding() {
        val receipt =
            shopReceipt(
                ShoppingList("list", "Trip"),
                listOf(item("Sample", 0), item("Loose fruit", 101, "1.5")),
            )
        assertEquals(152L, receipt.subtotal)
        assertEquals(0, receipt.missing)
    }

    @Test
    fun entirelyUnknownPricesArePartialNotFree() {
        val receipt =
            shopReceipt(ShoppingList("list", "Trip"), listOf(item("Rice", null), item("Tea", null)))
        assertEquals(0L, receipt.subtotal)
        assertEquals(2, receipt.missing)
    }

    @Test
    fun normalizationDeduplicatesCaseSpacingPunctuationAndCompatibilityCharacters() {
        assertEquals(normalizedShopName(" FRESH—Milk "), normalizedShopName("fresh milk"))
        assertEquals(normalizedShopName("ＲＩＣＥ"), normalizedShopName("rice"))
    }

    @Test
    fun searchMatchesAllWordsWithoutDependingOnTheirOrder() {
        val catalog =
            listOf(
                CatalogItem("Brown basmati rice", null, 100, "Groceries", "2026-10-03"),
                CatalogItem("Brown bread", null, null, "Groceries", "2026-10-01"),
            )
        assertEquals("Brown basmati rice", searchShopCatalog(catalog, "rice brown").single().name)
        assertEquals(2, searchShopCatalog(catalog, "  BROWN  ").size)
    }

    @Test
    fun noMatchOffersNewItemButBlankAndExistingQueriesDoNot() {
        val catalog = listOf(CatalogItem("Rice", null, null, "Groceries", "2026-10-03"))
        assertTrue(offerNewShopItem(catalog, "Soap"))
        assertFalse(offerNewShopItem(catalog, "ri"))
        assertFalse(offerNewShopItem(catalog, "  "))
        assertFalse(offerNewShopItem(catalog, "!!!"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun itemQuantityCannotExceedTheSupportedRange() {
        validatedShopItem(item("Rice", 100, "1000001"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativePricesAreRejected() {
        validatedShopItem(item("Rice", -1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun itemEstimateOverflowIsRejected() {
        validatedShopItem(item("Rice", Money.MAX_MINOR, "2"))
    }

    @Test
    fun catalogDeduplicatesNewestConfirmedReceiptAndUsesItsActualPrice() {
        val data = catalogFixture()
        val catalog = shopCatalog(data)
        assertEquals(1, catalog.size)
        assertEquals(250L, catalog.single().price)
        assertEquals("2026-10-03", catalog.single().date)
        assertEquals("Groceries", catalog.single().category)
    }

    @Test
    fun catalogCannotLeakReceiptItemsOutsideTheSelectedAccount() {
        val data = catalogFixture()
        val scoped = scopedFinance(data, "cash")
        assertEquals(100L, shopCatalog(scoped).single().price)
        assertEquals("2026-10-01", shopCatalog(scoped).single().date)
    }

    @Test
    fun savedCatalogIncludesManualItemsAndDeduplicatesAcrossReceipts() {
        val data = catalogFixture()
        val updated =
            data.copy(
                shopping =
                    ShoppingState(
                        listOf(ShoppingList("list", "Trip", "2026-10-04")),
                        listOf(item(" MILK ", 300), item("Tea", null)),
                    )
            )
        val catalog = shopCatalog(updated)
        assertEquals(2, catalog.size)
        val milk = searchShopCatalog(catalog, "milk").single()
        assertEquals(300L, milk.price)
        assertEquals(2, milk.purchases)
        assertNull(searchShopCatalog(catalog, "tea").single().price)
    }

    @Test
    fun savedCorrectionsKeepSearchAndPlanningPricesInSyncAndMergeRenames() {
        val catalog =
            listOf(
                CatalogItem("Rice", null, 100, "Groceries", "2026-10-01"),
                CatalogItem("Oats", null, 200, "Groceries", "2026-10-04"),
            )
        val corrections = mapOf("rice" to CatalogCorrection(" OATS ", null, "Household"))
        val merged = correctedShopCatalog(catalog, corrections)
        assertEquals(1, merged.size)
        assertEquals(setOf("rice", "oats"), merged.single().sourceKeys.toSet())
        assertEquals(200L, searchShopCatalog(merged, "oats").single().price)
        val deleted =
            correctedShopCatalog(
                catalog,
                mapOf("rice" to CatalogCorrection("Rice", 100, "Groceries", true)),
            )
        assertTrue(searchShopCatalog(deleted, "rice").isEmpty())
        val renamed =
            correctedShopCatalog(
                catalog,
                mapOf("rice" to CatalogCorrection("Brown rice", null, "Household")),
            )
        assertNull(searchShopCatalog(renamed, "brown rice").single().price)
        assertEquals("Household", searchShopCatalog(renamed, "brown rice").single().category)
    }

    private fun catalogFixture(): FinanceSnapshot {
        val old =
            MoneyTransaction(
                "old",
                "old",
                "EXPENSE",
                200,
                "2026-10-01",
                "Trip",
                "Groceries",
                "NEEDS",
            )
        val recent = old.copy(id = "new", submissionKey = "new", date = "2026-10-03")
        val receipts =
            listOf(
                Receipt("r1", old.id, "Market", "", "", old.date, 200, 0, "", "one", null),
                Receipt("r2", recent.id, "Market", "", "", recent.date, 500, 0, "", "two", null),
            )
        val items =
            listOf(
                ReceiptItem("a", "r1", "MILK", "Milk", "2", 200, "", "", true),
                ReceiptItem("b", "r2", "milk", " milk ", "2", 500, "", "", true),
                ReceiptItem("c", "r2", "Tea", "Tea", "1", 100, "", "", false),
            )
        return FinanceSnapshot(
            LedgerState(
                null,
                listOf(
                    AccountBalance(Account("cash", "Cash", "CASH", 0, true), 0),
                    AccountBalance(Account("bank", "Bank", "CURRENT", 0, true), 0),
                ),
                listOf(old, recent),
                emptyList(),
                emptyList(),
            ),
            ReceiptState(emptyList(), receipts, emptyList()),
            ShoppingState(emptyList(), emptyList()),
            emptyList(),
            emptyList(),
            accountEntries =
                listOf(
                    AccountEntry("e1", old.id, "cash", -200),
                    AccountEntry("e2", recent.id, "bank", -500),
                ),
            receiptItems = items,
        )
    }
}
