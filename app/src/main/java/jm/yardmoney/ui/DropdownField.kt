package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DropdownField(
    label: String,
    selectedId: String,
    options: Map<String, String>,
    change: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val enabled = !LocalSaving.current && options.isNotEmpty()
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = options[selectedId] ?: "Choose",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = MaterialTheme.shapes.small,
            modifier =
                Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            maxLines = 3,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        change(id)
                        expanded = false
                    },
                    trailingIcon =
                        if (id == selectedId) {
                            { Icon(Icons.Default.Check, null) }
                        } else null,
                    modifier = Modifier.semantics { selected = id == selectedId },
                )
            }
        }
    }
}
