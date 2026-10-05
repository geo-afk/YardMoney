package jm.yardmoney.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import jm.yardmoney.data.*
import org.junit.Rule
import org.junit.Test

class LargeCatalogUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun thousandsOfSavedItemsRemainLazySearchableAndReachableInRtl() {
        val items =
            (0 until 6000).map { i ->
                ShoppingItem(
                    "row-$i",
                    "list",
                    "Product ${i.toString().padStart(4, '0')}",
                    "1",
                    null,
                    100L,
                    false,
                )
            }
        val snapshot =
            FinanceSnapshot(
                LedgerState(null, emptyList(), emptyList(), emptyList(), emptyList()),
                ReceiptState(emptyList(), emptyList(), emptyList()),
                ShoppingState(listOf(ShoppingList("list", "Bulk")), items),
                emptyList(),
                emptyList(),
            )
        compose.setContent {
            YardTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SavedItemsSection(snapshot, false, {})
                }
            }
        }
        // A far-away row must not be composed until scrolled to; this catches eager giant cards.
        compose.onNodeWithText("Product 5999").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(6001)
        compose.onNodeWithText("Product 5999").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose
            .onNode(hasSetTextAction() and hasText("Search saved items"))
            .performTextInput("5999")
        compose.onNodeWithText("Product 5999").assertIsDisplayed()
        compose.onNodeWithText("Product 0000").assertDoesNotExist()
    }
}
