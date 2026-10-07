package jm.yardmoney.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import jm.yardmoney.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuickAddUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun smartLineShowsEditablePreviewBeforeSaving() {
        var saved: QuickAddDraft? = null
        compose.setContent {
            YardTheme { Surface {
                var line by remember { mutableStateOf("") }
                var draft by remember { mutableStateOf(QuickAddDraft()) }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    QuickAddContent(line, draft, listOf(IdentityOption("cash", "Cash")),
                        listOf(IdentityOption("Transport", "Transport")), emptyList(), emptyList(),
                        false, draft.amount.isNotEmpty() && draft.category.isNotEmpty(),
                        onLine = { value ->
                            line = value
                            val parsed = QuickAddParser.parse(value, listOf(CaptureAccount("cash", "Cash")), LocalDate.of(2026,10,6))
                            draft = QuickAddDraft(parsed.amountMinor?.let(Money::input).orEmpty(),
                                parsed.description.orEmpty(), "Transport", "NEEDS",
                                parsed.accountId.orEmpty(), parsed.date?.toString().orEmpty())
                        }, onDraft = { draft = it }, onRepeat = { _, _ -> },
                        onSave = { saved = draft }, onEdit = {}, onAction = {})
                }
            } }
        }
        compose.onNodeWithText("Save expense").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("What did you spend?").performScrollTo().performTextInput("taxi 600 cash yesterday")
        compose.onNodeWithText("Amount: 600.00").assertExists()
        compose.onNodeWithText("Date: 2026-10-05").assertExists()
        assertNull(saved)
        compose.onNodeWithText("Save expense").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("600.00", saved?.amount)
            assertEquals("cash", saved?.accountId)
            assertEquals("taxi", saved?.description)
        }
    }
}
