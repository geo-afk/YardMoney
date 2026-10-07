package jm.yardmoney.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    onAction: (String) -> Unit, showSaveAction: Boolean = true,
) {
    MoneyEntrySection("Smart line") {
        Field("What did you spend?", line, onLine)
        Text("Try taxi 600 cash yesterday. Nothing is saved until you confirm.",
            style = MaterialTheme.typography.bodySmall)
        issues.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        // The editable chips use labels as well as color so missing values are not color-only.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Amount" to draft.amount, "Category" to draft.category,
                "Account" to accounts.find { it.id == draft.accountId }?.label.orEmpty(),
                "Date" to draft.date).forEach { (label, value) ->
                AssistChip(onClick = onEdit, enabled = !busy,
                    label = { Text("$label: ${value.ifBlank { "Not specified" }}") },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = if (value.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        leadingIconContentColor = if (value.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                    leadingIcon = { Icon(if (value.isBlank()) Icons.AutoMirrored.Filled.HelpOutline else Icons.Default.Edit, null) },
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("More details") }
            if (showSaveAction) Button(onClick = onSave, enabled = canSave && !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Save expense") }
        }
    }
    if (repeats.isNotEmpty()) MoneyEntrySection("Repeat a recent expense") {
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("INCOME" to "Income", "TRANSFER" to "Transfer", "scan" to "Scan receipt").forEach { (route, label) ->
                OutlinedButton(onClick = { onAction(route) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
            }
        }
    }
}
