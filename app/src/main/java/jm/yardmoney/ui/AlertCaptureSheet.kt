package jm.yardmoney.ui

import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import jm.yardmoney.core.*
import jm.yardmoney.data.*

@Composable
internal fun AlertCaptureSheet(text: String, data: FinanceSnapshot, today: LocalDate,
    close: () -> Unit, review: (String, QuickAddDraft) -> Unit) {
    var input by rememberSaveable { mutableStateOf(text) }
    val preview = remember(input, today) { AlertTextParser.parse(input, today) }
    val context = LocalContext.current
    var clipboardNotice by remember { mutableStateOf<String?>(null) }
    StagedEditSheet("Review alert text", input, false, input != text, close) {
        AlertCaptureContent(input, preview, clipboardNotice, { input = it }, onPaste = {
            // Clipboard access is a deliberate tap; no read happens on composition or launch.
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val clip = clipboard.primaryClip
            val pasted = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            when {
                pasted.isNullOrBlank() -> clipboardNotice = "Copy some alert text first."
                pasted.length > 10000 -> clipboardNotice = "Paste alert text of up to 10,000 characters."
                else -> { input = pasted; clipboardNotice = null }
            }
        }, onReview = {
            review(preview.kind.orEmpty(), QuickAddDraft(preview.amountMinor?.let(Money::input).orEmpty(),
                preview.merchant.orEmpty(), "", "NEEDS", "",
                preview.date?.toString().orEmpty()))
        })
    }
}

@Composable
internal fun AlertCaptureContent(text: String, preview: AlertPreview, notice: String?,
    onText: (String) -> Unit, onPaste: () -> Unit, onReview: () -> Unit) {
    MoneyEntrySection("Shared or pasted text") {
        Text("Check the suggestions. Nothing is saved until you confirm the record.")
        Field("Alert text", text, onText)
        OutlinedButton(onClick = onPaste, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Default.ContentPaste, null)
            Spacer(Modifier.width(8.dp))
            Text("Paste alert text")
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    MoneyEntrySection("Suggestions") {
        listOf("Amount" to preview.amountMinor?.let(Money::format), "Merchant" to preview.merchant,
            "Type" to preview.kind?.lowercase()?.replaceFirstChar { it.uppercase() },
            "Date" to preview.date?.toString()).forEach { (label, value) ->
            Text("$label: ${value ?: "Not specified"}", color = if (value == null)
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        preview.issues.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Review record")
        }
    }
}
