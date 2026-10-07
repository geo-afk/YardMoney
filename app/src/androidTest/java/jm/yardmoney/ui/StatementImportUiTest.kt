package jm.yardmoney.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import jm.yardmoney.core.StatementRow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StatementImportUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun reviewAllowsValidRowsAndSkipsDuplicatesAndInvalidRows() {
        val date = LocalDate.of(2026,10,6)
        val rows = listOf(StatementRow(0,date,-60000,"Taxi","a",false,emptyList()),
            StatementRow(1,date,-60000,"Taxi duplicate","b",true,emptyList()),
            StatementRow(2,null,null,"Invalid","c",false,listOf("Check the date.")))
        var toggled = -1
        compose.setContent { YardTheme { Box(Modifier.fillMaxSize()) {
            StatementPreviewContent(rows, emptySet(),false,false,null,{toggled=it},{},{})
        } } }
        compose.onNodeWithText("Include row 1").performScrollTo().assertIsEnabled().performClick()
        assertEquals(0,toggled)
        compose.onNodeWithText("Include row 2").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Include row 3").performScrollTo().assertIsNotEnabled()
    }
}
