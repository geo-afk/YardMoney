package jm.yardmoney.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import jm.yardmoney.core.*
import jm.yardmoney.data.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ShopItemRow(
    item: ShoppingItem,
    busy: Boolean,
    toggle: (() -> Unit)?,
    update: (ShoppingItem) -> Unit,
    remove: () -> Unit,
) {
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    var actions by rememberSaveable(item.id) { mutableStateOf(false) }
    val fade by
        animateFloatAsState(
            if (item.checked) .65f else 1f,
            LocalMotion.current.floatSpec(),
            label = "Picked up item",
        )
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    // Gestures are transient: restoring a dismissed anchor from LazyColumn's saved state
    // would delete an item again when undo reintroduces its stable ID.
    val swipe =
        remember(threshold) { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled) { threshold } }
    // currentValue can change while a finger is down; settledValue fires once after dismissal.
    LaunchedEffect(swipe.settledValue) {
        when (swipe.settledValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                toggle?.invoke()
                swipe.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                remove()
                swipe.reset()
            }
            else -> Unit
        }
    }
    SwipeToDismissBox(
        modifier = Modifier.testTag("shop-item-${item.id}"),
        state = swipe,
        enableDismissFromStartToEnd = toggle != null && !busy,
        enableDismissFromEndToStart = !busy && !editing,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer, YardShape.card)
                    .padding(20.dp),
                contentAlignment =
                    if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                        Alignment.CenterEnd
                    else Alignment.CenterStart,
            ) {
                Icon(
                    if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                        Icons.Default.Delete
                    else Icons.Default.Check,
                    null,
                )
            }
        },
    ) {
        Card(
            Modifier.fillMaxWidth().animateContentSize(tween(LocalMotion.current.duration())),
            shape = YardShape.card,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(
                            enabled = !busy,
                            onClick = { if (toggle != null) toggle() else editing = !editing },
                            onLongClick = { actions = true },
                            onLongClickLabel = "Item actions",
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IdentityBadge(categoryIdentity(item.category))
                    Column(Modifier.weight(1f).alpha(fade)) {
                        AnimatedShopItemName(item.name, item.checked)
                        Text(
                            "${item.quantity} × ${item.manualPriceMinor?.let(Money::format) ?: "Not specified"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            item.category + if (item.optional) " • optional" else "",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (item.note.isNotBlank())
                            Text(item.note, style = MaterialTheme.typography.bodySmall)
                    }
                    if (toggle != null)
                        Checkbox(
                            item.checked,
                            onCheckedChange = { toggle() },
                            enabled = !busy,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "Picked up ${item.name}"
                                },
                        )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val next = Quantity.parse(item.quantity).subtract(BigDecimal.ONE)
                            if (next > BigDecimal.ZERO)
                                update(
                                    item.copy(quantity = next.stripTrailingZeros().toPlainString())
                                )
                        },
                        enabled = !busy && Quantity.parse(item.quantity) > BigDecimal.ONE,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Remove, "Decrease ${item.name} quantity")
                    }
                    Text(item.quantity, style = MaterialTheme.typography.titleMedium)
                    IconButton(
                        onClick = {
                            val next = Quantity.parse(item.quantity).add(BigDecimal.ONE)
                            if (next <= BigDecimal("1000000"))
                                update(
                                    item.copy(quantity = next.stripTrailingZeros().toPlainString())
                                )
                        },
                        enabled = !busy && Quantity.parse(item.quantity) < BigDecimal("1000000"),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Add, "Increase ${item.name} quantity")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { editing = !editing },
                        enabled = !busy,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Edit, "Edit ${item.name}")
                    }
                    IconButton(remove, enabled = !busy, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.DeleteOutline, "Remove ${item.name}")
                    }
                }
                if (editing)
                    ShopItemFields(item.name, item, busy, cancel = { editing = false }) {
                        update(it)
                        editing = false
                    }
            }
        }
    }
    if (actions)
        AlertDialog(
            onDismissRequest = { actions = false },
            title = { Text(item.name) },
            text = { Text("Edit its price, quantity or note, or remove it from your list.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions = false
                        editing = true
                    }
                ) {
                    Text("Edit item")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        actions = false
                        remove()
                    }
                ) {
                    Text("Remove item")
                }
            },
        )
}

@Composable
private fun AnimatedShopItemName(name: String, checked: Boolean) {
    val progress by
        animateFloatAsState(
            if (checked) 1f else 0f,
            LocalMotion.current.floatSpec(),
            label = "Checklist strike",
        )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val color = MaterialTheme.colorScheme.onSurface
    Text(
        name,
        style = MaterialTheme.typography.titleMedium,
        onTextLayout = { layout = it },
        modifier =
            Modifier.semantics { stateDescription = if (checked) "Picked up" else "Not picked up" }
                .drawWithContent {
                    drawContent()
                    layout?.let { result ->
                        repeat(result.lineCount) { line ->
                            val left = result.getLineLeft(line)
                            val right = result.getLineRight(line)
                            val y = (result.getLineTop(line) + result.getLineBottom(line)) / 2
                            drawLine(
                                color,
                                Offset(left, y),
                                Offset(left + (right - left) * progress, y),
                                strokeWidth = 1.5.dp.toPx(),
                            )
                        }
                    }
                },
    )
}
