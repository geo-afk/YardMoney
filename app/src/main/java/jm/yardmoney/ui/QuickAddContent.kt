package jm.yardmoney.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jm.yardmoney.core.Money
import jm.yardmoney.core.RepeatExpense

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuickAddContent(
    line: String, draft: QuickAddDraft, accounts: List<IdentityOption>, categories: List<IdentityOption>,
    repeats: List<RepeatExpense>, issues: List<String>, busy: Boolean, canSave: Boolean,
    onLine: (String) -> Unit, onDraft: (QuickAddDraft) -> Unit,
    onRepeat: (RepeatExpense, Boolean) -> Unit, onSave: () -> Unit, onEdit: () -> Unit,
    onAction: (QuickAddAction) -> Unit, showSaveAction: Boolean = true,
) {
    var help by rememberSaveable { mutableStateOf<String?>(null) }
    val explanations = mapOf(
        "Amount" to "The amount to spend, in Jamaican dollars. Edit Amount below; commas and decimals are supported.",
        "Category" to "Choose what this expense was for using Category below. A saved merchant rule may suggest it; you can change it before saving.",
        "Account" to "The account this expense is paid from. Include its name in your smart line, or select Account below.",
        "Date" to "When the expense happened. Try today or yesterday in your smart line, or edit Date below. Future expenses cannot be recorded here.",
    )
    help?.let { field ->
        AlertDialog(onDismissRequest = { help = null },
            title = { Text("About ${field.lowercase()}") },
            text = { Text(explanations.getValue(field)) },
            confirmButton = { TextButton(onClick = { help = null }) { Text("Got it") } })
    }
    MoneyEntrySection("Smart line") {
        Field("What did you spend?", line, onLine)
        Text("Try taxi 600 cash yesterday. Nothing is saved until you confirm.",
            style = MaterialTheme.typography.bodySmall)
        issues.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        // Preview chips explain their values instead of opening the full transaction form.
        Text("Preview · tap an info icon or value for help. Edit fields below.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Amount" to draft.amount, "Category" to draft.category,
                "Account" to accounts.find { it.id == draft.accountId }?.label.orEmpty(),
                "Date" to draft.date).forEach { (label, value) ->
                AssistChip(onClick = { help = label }, enabled = !busy,
                    label = { Text("$label: ${value.ifBlank { "Not specified" }}") },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = if (value.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        leadingIconContentColor = if (value.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null, Modifier.size(18.dp).testTag("Quick Add help $label")) },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
    MoneyEntrySection("Review and save") {
        MoneyField("Amount (J$)", draft.amount, prominent = true) { onDraft(draft.copy(amount = it)) }
        Field("Description", draft.description) { onDraft(draft.copy(description = it)) }
        IdentityPicker("Account", draft.accountId, accounts) { onDraft(draft.copy(accountId = it)) }
        IdentityPicker("Category", draft.category, categories, allowCustom = true) { onDraft(draft.copy(category = it)) }
        Choice("Budget group", draft.bucket, listOf("NEEDS", "WANTS", "SAVINGS")) { onDraft(draft.copy(bucket = it)) }
        Field("Date (YYYY-MM-DD)", draft.date) { onDraft(draft.copy(date = it)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp).testTag("Quick Add details icon"))
                Spacer(Modifier.width(8.dp))
                Text("More details")
            }
            if (showSaveAction) Button(onClick = onSave, enabled = canSave && !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save expense") }
        }
    }
    if (repeats.isNotEmpty()) MoneyEntrySection("Repeat a recent expense") {
        Text("Tap to fill this form. Touch and hold to open detailed editing.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeats.forEach { repeat ->
                val label = "${repeat.description.ifBlank { repeat.category }} · ${Money.format(repeat.amountMinor)}"
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.heightIn(min = 48.dp).combinedClickable(enabled = !busy,
                        role = Role.Button, onClick = { onRepeat(repeat, false) },
                        onLongClickLabel = "Edit this repeat", onLongClick = { onRepeat(repeat, true) })
                        .semantics { contentDescription = label }) {
                    Text(label, Modifier.padding(12.dp))
                }
            }
        }
    }
    MoneyEntrySection("Other records") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple(QuickAddAction.PASTE, "Paste alert text", Icons.Default.ContentPaste),
                Triple(QuickAddAction.INCOME, "Income", Icons.Default.SouthWest),
                Triple(QuickAddAction.TRANSFER, "Transfer", Icons.Default.SwapHoriz),
                Triple(QuickAddAction.SCAN, "Scan receipt", Icons.AutoMirrored.Filled.ReceiptLong),
            ).forEach { (route, label, icon) ->
                OutlinedButton(onClick = { onAction(route) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(icon, null, Modifier.size(18.dp).testTag("Quick Add ${route.name} icon"))
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
        }
    }
}
