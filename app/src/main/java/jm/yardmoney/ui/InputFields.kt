package jm.yardmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import jm.yardmoney.core.InputFormat

internal class CurrencyVisualTransformation(private val editing: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val result = InputFormat.moneyDisplay(text.text, editing)
        return TransformedText(
            AnnotatedString(result.text),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) =
                    result.originalToDisplay[offset.coerceIn(0, result.originalToDisplay.lastIndex)]

                override fun transformedToOriginal(offset: Int) =
                    result.displayToOriginal[offset.coerceIn(0, result.displayToOriginal.lastIndex)]
            },
        )
    }
}

@Composable
internal fun MoneyField(label: String, value: String, change: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val raw = InputFormat.currencyText(value)
    val valid = raw.isEmpty() || Regex("-?[0-9]*(?:\\.[0-9]{0,2})?").matches(raw)
    LaunchedEffect(value) { if (raw != value && valid) change(raw) }
    OutlinedTextField(
        value = raw,
        onValueChange = { change(InputFormat.currencyText(it)) },
        label = { Text(label.replace(" (J$)", "")) },
        prefix = { Text("J$ ") },
        placeholder = { Text("0.00") },
        singleLine = true,
        isError = !valid,
        supportingText =
            if (!valid) {
                { Text("Enter dollars and up to two cents digits, such as J$1,250.50.") }
            } else null,
        trailingIcon =
            if (focused && !raw.contains('.')) {
                {
                    IconButton(
                        onClick = { change(if (raw.isBlank()) "0." else "$raw.") },
                        modifier =
                            Modifier.semantics { contentDescription = "Add cents separator" },
                    ) {
                        Text(".", style = MaterialTheme.typography.titleLarge)
                    }
                }
            } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = CurrencyVisualTransformation(focused),
        enabled = !LocalSaving.current,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        colors =
            OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
            ),
        shape = MaterialTheme.shapes.small,
    )
}

@Composable
internal fun NumberField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        enabled = !LocalSaving.current,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.small,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DateDropdown(
    label: String,
    value: String,
    optional: Boolean = false,
    change: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now(ZoneId.of("America/Jamaica"))
    val selected = runCatching { LocalDate.parse(value) }.getOrNull()
    val date = selected ?: today
    val display =
        selected?.format(DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH))
            ?: if (optional) "No date selected" else "Choose date"
    val cleanLabel = label.substringBefore(" (YYYY")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(cleanLabel, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = { expanded = !expanded },
            enabled = !LocalSaving.current,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            Icon(Icons.Default.CalendarToday, null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(display, Modifier.weight(1f))
            Icon(
                Icons.Default.ExpandMore,
                if (expanded) "Close date selector" else "Open date selector",
            )
        }
        if (optional)
            Text(
                "Optional. Clear the date to leave it unscheduled.",
                style = MaterialTheme.typography.bodySmall,
            )
        if (expanded) {
            val picker =
                rememberDatePickerState(
                    initialSelectedDateMillis =
                        selected
                            ?.atStartOfDay(java.time.ZoneOffset.UTC)
                            ?.toInstant()
                            ?.toEpochMilli(),
                    initialDisplayedMonthMillis =
                        date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                )
            DatePickerDialog(
                onDismissRequest = { expanded = false },
                confirmButton = {
                    TextButton(
                        enabled = picker.selectedDateMillis != null && !LocalSaving.current,
                        onClick = {
                            picker.selectedDateMillis?.let {
                                change(
                                    java.time.Instant.ofEpochMilli(it)
                                        .atZone(java.time.ZoneOffset.UTC)
                                        .toLocalDate()
                                        .toString()
                                )
                            }
                            expanded = false
                        },
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text("Use this date")
                    }
                },
                dismissButton = {
                    Row {
                        if (optional)
                            TextButton(
                                onClick = {
                                    change("")
                                    expanded = false
                                },
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text("Clear date")
                            }
                        TextButton(
                            onClick = { expanded = false },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("Cancel")
                        }
                    }
                },
            ) {
                DatePicker(
                    state = picker,
                    modifier =
                        Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    title = { Text(cleanLabel, Modifier.padding(start = 24.dp, top = 16.dp)) },
                )
            }
        }
    }
}
