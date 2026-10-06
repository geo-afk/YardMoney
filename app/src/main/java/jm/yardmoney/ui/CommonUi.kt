package jm.yardmoney.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jm.yardmoney.core.*
import jm.yardmoney.data.*

internal val LocalSaving = staticCompositionLocalOf { false }

@Composable
internal fun Page(applySystemInsets: Boolean = false, content: @Composable ColumnScope.() -> Unit) =
    Box(
        if (applySystemInsets) Modifier.fillMaxSize().safeDrawingPadding()
        else Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 840.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(LocalLayoutSpacing.current),
            content = content,
        )
    }

/**
 * One text-entry row whose widget is chosen from its label: "YYYY-MM-DD" gives a date picker,
 * "J$" a money field, "pay day" a 1-31 chooser, "%", "quantity" or "Package size" a number field;
 * anything else is plain text. Keep that convention when adding fields.
 */
@Composable
internal fun Field(label: String, value: String, change: (String) -> Unit) {
    when {
        label.contains("YYYY-MM-DD") ->
            DateDropdown(
                label,
                value,
                optional = label.contains("optional", true) || label.contains("blank", true),
                change = change,
            )
        label.contains("J$") -> MoneyField(label, value, change = change)
        label.contains("pay day", true) ->
            Choice(label, value, (1..31).map { it.toString() }, change)
        label.contains("%") ||
            label.contains("quantity", true) ||
            label.contains("Package size", true) -> NumberField(label, value, change)
        else ->
            OutlinedTextField(
                value = value,
                onValueChange = change,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                enabled = !LocalSaving.current,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                shape = MaterialTheme.shapes.small,
            )
    }
}

@Composable
internal fun Choice(label: String, value: String, options: List<String>, change: (String) -> Unit) =
    DropdownField(
        label,
        value,
        options.associateWith {
            it.lowercase().replace('_', ' ').replaceFirstChar { c -> c.titlecase() }
        },
        change,
    )

@Composable
internal fun Record(title: String, detail: String, amount: String, click: (() -> Unit)? = null) {
    val body: @Composable () -> Unit = {
        Row(
            Modifier.padding(YardSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YardSpace.md),
        ) {
            IdentityBadge(categoryIdentity(title))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(YardSpace.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(amount.replace(",", ",\u200B"), style = MaterialTheme.typography.titleLarge)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (click != null) Icon(Icons.Default.ChevronRight, null)
        }
    }
    if (click != null)
        Card(
            onClick = click,
            modifier = Modifier.fillMaxWidth(),
            shape = YardShape.card,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
        ) {
            body()
        }
    else
        Card(
            Modifier.fillMaxWidth(),
            shape = YardShape.card,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
        ) {
            body()
        }
}

@Composable
internal fun AmountRow(label: String, amount: Long) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            Money.format(amount).replace(",", ",\u200B"),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
internal fun SafeCard(safe: SafeToSpend, payday: String) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.AccountBalanceWallet, null)
                Text("Safe to spend", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                Money.format(safe.safeMinor).replace(",", ",\u200B"),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (safe.days <= 0) "Payday has arrived. Update your plan."
                else "${safe.days} days to payday · $payday"
            )
            val color = MaterialTheme.colorScheme.onPrimary
            val base = MaterialTheme.colorScheme.onPrimary.copy(alpha = .20f)
            val motion = LocalMotion.current
            val fraction =
                if (safe.availableMinor > 0)
                    (safe.safeMinor.toDouble() / safe.availableMinor).toFloat().coerceIn(0f, 1f)
                else 0f
            val shown by
                animateFloatAsState(
                    fraction,
                    animationSpec = motion.floatSpec(),
                    label = "Safe balance",
                )
            LinearProgressIndicator(
                progress = { shown },
                color = color,
                trackColor = base,
                modifier = Modifier.fillMaxWidth().height(14.dp),
            )
            Text(
                safe.dailyMinor?.let { "About ${Money.format(it)} a day" }
                    ?: if (safe.safeMinor < 0)
                        "Your commitments exceed available money. Review your plan."
                    else "No daily spending allowance right now."
            )
        }
    }
}

@Composable
internal fun SimpleForm(
    title: String,
    labels: List<String>,
    initial: List<String>,
    busy: Boolean,
    close: () -> Unit,
    choices: List<Pair<String, List<String>>> = emptyList(),
    description: String? = null,
    save: (List<String>, List<String>) -> Unit,
) {
    val stringStatesSaver =
        listSaver<List<MutableState<String>>, String>(
            save = { states -> states.map { it.value } },
            restore = { strings -> strings.map { mutableStateOf(it) } },
        )
    val values =
        rememberSaveable(title, initial, saver = stringStatesSaver) {
            initial.map { mutableStateOf(it) }
        }
    val selected =
        rememberSaveable(title, choices, saver = stringStatesSaver) {
            choices.map { mutableStateOf(it.second.first()) }
        }
    MoneyEntrySheet(
        title,
        "Save",
        busy,
        true,
        save = { save(values.map { it.value }, selected.map { it.value }) },
        close = close,
        dirty =
            values.map { it.value } != initial ||
                selected.map { it.value } != choices.map { it.second.first() },
        keyValue = values.firstOrNull()?.value.orEmpty(),
    ) {
        if (description != null) Text(description)
        labels.forEachIndexed { i, label -> Field(label, values[i].value) { values[i].value = it } }
        choices.forEachIndexed { i, c ->
            Choice(c.first, selected[i].value, c.second) { selected[i].value = it }
        }
    }
}
