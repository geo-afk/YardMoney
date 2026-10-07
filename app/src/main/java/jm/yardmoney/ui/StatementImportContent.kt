package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jm.yardmoney.core.*

@Composable
internal fun StatementMappingContent(document: StatementDocument, mapping: StatementMapping,
    enabled: Boolean, change: (StatementMapping) -> Unit) {
    MoneyEntrySection("Column mapping") {
        Tick("First row contains column names", mapping.header) { if (enabled) change(mapping.copy(header = it)) }
        val width = document.records.maxOf { it.size }
        val options = mapOf("-1" to "Not specified") + (0 until width).associate { index -> index.toString() to
            "${index + 1}: ${if (mapping.header) document.records.first().getOrNull(index).orEmpty().take(60) else "Column ${index + 1}"}" }
        DropdownField("Date column", mapping.dateColumn.toString(), options) { change(mapping.copy(dateColumn = it.toInt())) }
        DropdownField("Description column", mapping.descriptionColumn.toString(), options) { change(mapping.copy(descriptionColumn = it.toInt())) }
        DropdownField("Date format", mapping.dateFormat, StatementCsv.dateFormats.associateWith { it }) { change(mapping.copy(dateFormat = it)) }
        DropdownField("Signed amount column (optional)", mapping.amountColumn.toString(), options) { change(mapping.copy(amountColumn = it.toInt())) }
        if (mapping.amountColumn < 0) {
            DropdownField("Debit column", mapping.debitColumn.toString(), options) { change(mapping.copy(debitColumn = it.toInt())) }
            DropdownField("Credit column", mapping.creditColumn.toString(), options) { change(mapping.copy(creditColumn = it.toInt())) }
        }
        DropdownField("Balance column (optional)", mapping.balanceColumn.toString(), options) { change(mapping.copy(balanceColumn = it.toInt())) }
        Text("Choose the date format from your statement. Dates such as 01/02 need your choice of day/month or month/day.")
    }
}

@Composable
internal fun StatementPreviewContent(rows: List<StatementRow>, selected: Set<Int>, allowDuplicates: Boolean,
    busy: Boolean, notice: String?, onToggle: (Int) -> Unit, onDuplicates: (Boolean) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(LocalLayoutSpacing.current)) {
        Text("Review ${rows.size} rows", style = MaterialTheme.typography.headlineSmall)
        Text("Duplicates are skipped by default. Invalid rows cannot be imported.")
        Tick("Allow selected duplicate rows", allowDuplicates) { if (!busy) onDuplicates(it) }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Change mapping") }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows, key = { it.index }) { row ->
                MoneyCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IdentityBadge(categoryIdentity("Other"))
                        Column(Modifier.weight(1f)) {
                            Text(row.description.ifBlank { "Not specified" }, style = MaterialTheme.typography.titleMedium)
                            Text("${row.date ?: "Not specified"} · ${row.signedMinor?.let(Money::format) ?: "Not specified"}")
                            if (row.duplicate) Text("Possible duplicate", color = MaterialTheme.colorScheme.tertiary)
                            row.issues.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    Tick("Include row ${row.index + 1}", row.index in selected && (!row.duplicate || allowDuplicates), enabled = !busy && row.issues.isEmpty() && (!row.duplicate || allowDuplicates)) {
                        if (!busy && row.issues.isEmpty() && (!row.duplicate || allowDuplicates)) onToggle(row.index)
                    }
                }
            }
        }
    }
}
