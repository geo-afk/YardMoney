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
import java.time.format.DateTimeFormatter
import java.util.Locale
import jm.yardmoney.core.InputFormat
import jm.yardmoney.data.jamaicaToday

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
internal fun MoneyField(
    label: String,
    value: String,
    prominent: Boolean = false,
    change: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val raw = InputFormat.currencyText(value)
    val valid = raw.isEmpty() || Regex("-?[0-9]*(?:\\.[0-9]{0,2})?").matches(raw)
    LaunchedEffect(value) { if (raw != value && valid) change(raw) }
    // The primary amount keeps its currency and placeholder visible before the first tap.
    if (prominent) Text(label.replace(" (J$)", ""), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = raw,
        textStyle =
            if (prominent) MaterialTheme.typography.displaySmall
            else MaterialTheme.typography.bodyLarge,
        onValueChange = { change(InputFormat.currencyText(it)) },
        label =
            if (prominent) null
            else {
                { Text(label.replace(" (J$)", "")) }
            },
        prefix = {
            Text(
                "J$ ",
                style =
                    if (prominent) MaterialTheme.typography.displaySmall
                    else MaterialTheme.typography.bodyLarge,
            )
        },
        placeholder = {
            Text(
                "0.00",
                style =
                    if (prominent) MaterialTheme.typography.displaySmall
                    else MaterialTheme.typography.bodyLarge,
            )
        },
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
        modifier =
            Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { if (prominent) contentDescription = label.replace(" (J$)", "") },
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
    window: DateWindow = dateWindowFor(label),
    change: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val today = jamaicaToday()
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
                    // Days the repository would reject are greyed out instead of failing on save.
                    selectableDates = remember(window, today) { WindowDates(window, today) },
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

@OptIn(ExperimentalMaterial3Api::class)
private class WindowDates(private val window: DateWindow, private val today: LocalDate) :
    SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long) =
        window.allows(
            java.time.Instant.ofEpochMilli(utcTimeMillis)
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate(),
            today,
        )

    override fun isSelectableYear(year: Int) = window.allowsYear(year, today)
}
