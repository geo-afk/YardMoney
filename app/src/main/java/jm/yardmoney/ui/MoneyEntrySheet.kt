package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoneyEntrySheet(
    title: String,
    action: String,
    busy: Boolean,
    canSave: Boolean,
    save: () -> Unit,
    close: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state =
        rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })
    ModalBottomSheet(
        onDismissRequest = { if (!busy) close() },
        sheetState = state,
        sheetMaxWidth = 640.dp,
        dragHandle = null,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding()) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Keep your balances up to date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = close, enabled = !busy) {
                    Icon(Icons.Default.Close, "Close money entry")
                }
            }
            HorizontalDivider()
            Column(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(LocalLayoutSpacing.current),
                content = content,
            )
            HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!canSave && !busy)
                    Text(
                        "Enter a valid amount and choose the accounts to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                Button(
                    onClick = save,
                    enabled = !busy && canSave,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(if (busy) "Saving…" else action)
                }
            }
        }
    }
}

@Composable
internal fun MoneyEntrySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(LocalLayoutSpacing.current),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
