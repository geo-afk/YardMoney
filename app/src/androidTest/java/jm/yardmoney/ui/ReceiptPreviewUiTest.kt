package jm.yardmoney.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import jm.yardmoney.AppModel
import jm.yardmoney.core.SafeToSpend
import jm.yardmoney.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReceiptPreviewUiTest {
    @get:Rule val compose = createComposeRule()
    private val raw =
        "DEMO MARKET\n2026-10-04\n1 RICE 10.00\n2 MILK 20.00\n1 BREAD 5.00\nSUBTOTAL 35.00\nTOTAL 35.00\nCASH 35.00\nCASHIER: RAW-TEXT-ONLY"

    private fun receipt() =
        Receipt(
            "receipt",
            "expense",
            "Demo Market",
            "",
            "",
            "2026-10-04",
            3500,
            0,
            raw,
            "fingerprint",
            null,
        )

    @Test
    fun oldTotalOnlyReceiptShowsCapturedItemsInsidePreviewWithoutRawScanDump() {
        val model = savedReceiptPreview(receipt(), emptyList())
        assertEquals(listOf("RICE", "MILK", "BREAD"), model.lines.map { it.item.name })
        assertEquals(3500L, model.actualTotal)
        compose.setContent {
            YardTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SavedReceiptContent(receipt(), emptyList())
                }
            }
        }
        compose.onNodeWithText("RICE").assertExists()
        compose.onNodeWithText("MILK").assertExists()
        compose.onNodeWithText("BREAD").assertExists()
        compose.onNodeWithText("Receipt total").assertExists()
        compose.onNodeWithText(raw).assertDoesNotExist()
        compose.onNodeWithText("Extracted text").assertDoesNotExist()
        compose
            .onNodeWithText("Total-only expense: no product prices were stored.")
            .assertDoesNotExist()
    }

    @Test
    fun confirmedProductCorrectionsTakePrecedenceOverOriginalScan() {
        val corrected =
            ReceiptItem(
                "item",
                "receipt",
                "1 RICE 10.00",
                "Brown rice",
                "1",
                1000,
                "",
                "item",
                true,
            )
        val model = savedReceiptPreview(receipt(), listOf(corrected))
        assertEquals("Brown rice", model.lines.single().item.name)
        assertEquals(1000L, model.lines.single().total)
        compose.setContent { YardTheme { SavedReceiptContent(receipt(), listOf(corrected)) } }
        compose.onNodeWithText("Brown rice").assertExists()
        compose.onNodeWithText("RICE").assertDoesNotExist()
        compose.onNodeWithText(raw).assertDoesNotExist()
    }

    @Test
    fun newScanStartsWithReceiptAndKeepsCorrectionsBehindEditDetails() {
        val store = ViewModelStore()
        lateinit var model: AppModel
        compose.runOnUiThread {
            val app =
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                    as Application
            model =
                ViewModelProvider(
                    store,
                    ViewModelProvider.AndroidViewModelFactory.getInstance(app),
                )[AppModel::class.java]
        }
        try {
            val data =
                FinanceSnapshot(
                    LedgerState(null, emptyList(), emptyList(), emptyList(), emptyList()),
                    ReceiptState(emptyList(), emptyList(), emptyList()),
                    ShoppingState(emptyList(), emptyList()),
                    emptyList(),
                    emptyList(),
                )
            compose.setContent {
                YardTheme {
                    ReceiptReview(
                        model,
                        data,
                        ReceiptDraft("scan", raw, "2026-10-04", null, "test"),
                        false,
                        {},
                    )
                }
            }
            compose.onNodeWithText("RICE").assertExists()
            compose.onNodeWithText("MILK").assertExists()
            compose.onNodeWithText("Merchant").assertDoesNotExist()
            compose.onNodeWithText("Extracted text").assertDoesNotExist()
            compose.onNodeWithText(raw).assertDoesNotExist()
            compose.onNodeWithText("Edit details").performScrollTo().performClick()
            compose.onNodeWithText("Merchant").assertExists()
            compose.onNodeWithText("Hide details").performScrollTo().performClick()
            compose.onNodeWithText("Merchant").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { store.clear() }
        }
    }

    @Test
    fun savedItemsStayBehindShopCardUntilOpened() {
        val store = ViewModelStore()
        lateinit var model: AppModel
        compose.runOnUiThread {
            val app =
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                    as Application
            model =
                ViewModelProvider(
                    store,
                    ViewModelProvider.AndroidViewModelFactory.getInstance(app),
                )[AppModel::class.java]
        }
        try {
            val data =
                FinanceSnapshot(
                    LedgerState(null, emptyList(), emptyList(), emptyList(), emptyList()),
                    ReceiptState(emptyList(), emptyList(), emptyList()),
                    ShoppingState(emptyList(), emptyList()),
                    emptyList(),
                    emptyList(),
                )
            compose.setContent {
                YardTheme {
                    ShoppingPage(
                        model,
                        data,
                        SafeToSpend(0, 0, 0, 0, null),
                        false,
                        remember { SnackbarHostState() },
                    )
                }
            }
            compose.onNodeWithText("Search saved items").assertDoesNotExist()
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Saved Items"))
            compose.onNodeWithText("Saved Items").performClick()
            compose.onNodeWithText("Search saved items").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back to shopping").performClick()
            compose.onNodeWithText("Search saved items").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { store.clear() }
        }
    }
}
