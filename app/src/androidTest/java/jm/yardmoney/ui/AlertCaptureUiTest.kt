package jm.yardmoney.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import jm.yardmoney.core.AlertTextParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AlertCaptureUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun sharedTextIsOnlyASuggestionUntilReviewAndSave() {
        val text = "Payment J$1,200 at Market yesterday"
        var reviewed = false
        var pasted = false
        compose.setContent { YardTheme { Column { AlertCaptureContent(text,
            AlertTextParser.parse(text, LocalDate.of(2026, 10, 6)), null, {},
            { pasted = true }, { reviewed = true }) } } }
        compose.onNodeWithText("Amount: J$1,200").assertExists()
        compose.onNodeWithText("Merchant: Market").assertExists()
        compose.onNodeWithText("Review record").performClick()
        assertTrue(reviewed)
        assertFalse(pasted)
        compose.onNodeWithText("Paste alert text").performClick()
        assertTrue(pasted)
    }
}
