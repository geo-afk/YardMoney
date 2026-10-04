package jm.yardmoney.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import jm.yardmoney.core.ColorContrast
import jm.yardmoney.core.Money
import jm.yardmoney.data.*

// Native adaptations of Ivy's quick entry, YNAB's available amounts, Monarch's account overview
// and Copilot's category progress. No external UI code is copied.
// https://github.com/Ivy-Apps/ivy-wallet · https://www.ynab.com/
// https://www.monarch.com/ · https://www.copilot.money/
internal object YardSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

internal object YardShape {
    val card
        @Composable get() = MaterialTheme.shapes.large

    val badge
        @Composable get() = MaterialTheme.shapes.medium
}

internal data class MoneyIdentity(val icon: ImageVector, val seed: Long)

private val identitySeeds =
    listOf(
        0xFF00865A,
        0xFF2563EB,
        0xFF9333EA,
        0xFFD54628,
        0xFFB52672,
        0xFF007F91,
        0xFF8B6500,
        0xFF6651DB,
    )

internal fun categoryIdentity(name: String): MoneyIdentity {
    val text = name.lowercase().removePrefix("demo ").trim()
    val icon =
        when {
            listOf("grocer", "food", "rice", "milk", "bread", "fruit").any { it in text } ->
                Icons.Default.ShoppingBasket
            listOf("transport", "fuel", "travel").any { it in text } -> Icons.Default.DirectionsBus
            listOf("rent", "home", "household").any { it in text } -> Icons.Default.Home
            listOf("utilit", "electric", "water").any { it in text } -> Icons.Default.Bolt
            listOf("health", "medical").any { it in text } -> Icons.Default.Favorite
            listOf("dining", "coffee", "restaurant").any { it in text } -> Icons.Default.Restaurant
            listOf("entertain", "hobb").any { it in text } -> Icons.Default.SportsEsports
            "clothing" in text -> Icons.Default.Checkroom
            listOf("salary", "income", "freelance").any { it in text } -> Icons.Default.Work
            listOf("saving", "goal", "emergency").any { it in text } -> Icons.Default.Savings
            listOf("bill", "phone", "internet").any { it in text } -> Icons.Default.Event
            "transfer" in text -> Icons.Default.SwapHoriz
            "refund" in text -> Icons.AutoMirrored.Filled.Undo
            else -> Icons.Default.Category
        }
    return MoneyIdentity(icon, identitySeeds[Math.floorMod(text.hashCode(), identitySeeds.size)])
}

internal fun accountIdentity(account: Account) =
    MoneyIdentity(
        when (account.kind) {
            "SAVINGS" -> Icons.Default.Savings
            "CURRENT" -> Icons.Default.AccountBalance
            "WALLET" -> Icons.Default.AccountBalanceWallet
            else -> Icons.Default.Payments
        },
        // Account kinds have distinct hue families; the ID keeps accounts of the same kind
        // individual.
        Color.hsl(
                ((when (account.kind) {
                        "CASH" -> 155
                        "CURRENT" -> 218
                        "SAVINGS" -> 278
                        "WALLET" -> 20
                        else -> 185
                    }) + Math.floorMod(account.id.hashCode(), 25) - 12)
                    .toFloat(),
                .72f,
                .40f,
            )
            .toArgb()
            .toLong(),
    )

@Composable
internal fun identityColor(identity: MoneyIdentity): Color {
    val c = MaterialTheme.colorScheme
    val seed =
        if (ColorContrast.luminance(c.surface.toArgb()) < .5)
            ColorContrast.blend(identity.seed.toInt(), 0xFFFFFFFF.toInt(), .32)
        else identity.seed.toInt()
    return Color(ColorContrast.readable(seed, c.surfaceContainerLow.toArgb(), 4.5))
}

@Composable
internal fun IdentityBadge(identity: MoneyIdentity, modifier: Modifier = Modifier) {
    val color = identityColor(identity)
    Surface(modifier, shape = YardShape.badge, color = color.copy(alpha = .12f)) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(identity.icon, null, tint = color)
        }
    }
}

@Composable
internal fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YardSpace.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(YardSpace.xs)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle != null)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        action?.invoke()
    }
}

@Composable
internal fun MoneyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = YardShape.card,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(YardSpace.xl),
            verticalArrangement = Arrangement.spacedBy(YardSpace.lg),
            content = content,
        )
    }
}

@Composable
internal fun EmptyState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.AutoAwesome,
    action: (@Composable () -> Unit)? = null,
) {
    MoneyCard(modifier) {
        IdentityBadge(MoneyIdentity(icon, 0xFF00865A))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
internal fun LoadingBudget(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = "Loading your budget" },
        verticalArrangement = Arrangement.spacedBy(YardSpace.lg),
    ) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        // Static skeletons respect reduced-motion preferences and avoid distracting shimmer.
        listOf(156.dp, 72.dp, 220.dp).forEach { height ->
            Box(
                Modifier.fillMaxWidth()
                    .height(height)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, YardShape.card)
            )
        }
    }
}

@Composable
internal fun ProgressMoneyCard(
    title: String,
    detail: String,
    used: Long,
    total: Long,
    identity: MoneyIdentity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val color = identityColor(identity)
    val fraction =
        if (total > 0) (used.toDouble() / total).toFloat().coerceIn(0f, 1f)
        else if (used > 0) 1f else 0f
    val progress by
        animateFloatAsState(fraction, LocalMotion.current.floatSpec(), label = "Money progress")
    val body: @Composable ColumnScope.() -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YardSpace.md),
        ) {
            IdentityBadge(identity)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onClick != null) Icon(Icons.Default.ChevronRight, null)
        }
        Text(
            Money.format(used) + " / " + Money.format(total),
            style = MaterialTheme.typography.titleLarge,
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = color,
            trackColor = color.copy(alpha = .13f),
        )
        Text(
            if (used > total) "Over by " + Money.format(used - total)
            else Money.format(total - used) + " remaining",
            color =
                if (used > total) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (onClick == null) MoneyCard(modifier, body)
    else
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = YardShape.card,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
        ) {
            Column(
                Modifier.padding(YardSpace.lg),
                verticalArrangement = Arrangement.spacedBy(YardSpace.md),
                content = body,
            )
        }
}

@Composable
internal fun CategoryLimits(
    data: FinanceSnapshot,
    today: LocalDate,
    edit: (CategoryLimit) -> Unit,
) {
    val ids =
        data.ledger.transactions
            .filter { it.date >= data.ledger.profile!!.periodStart && it.date <= today.toString() }
            .map { it.id }
            .toSet()
    if (data.limits.isEmpty())
        EmptyState(
            "Give your categories a little direction",
            "Set your first limit to see spending and the amount left at a glance.",
            icon = Icons.Default.Tune,
        )
    else {
        var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
        Row {
            TextButton(onClick = { expanded = data.limits.map { it.id } }) { Text("Expand all") }
            TextButton(onClick = { expanded = emptyList() }) { Text("Collapse all") }
        }
        data.limits.forEach { limit ->
            val accountTxIds =
                limit.accountId?.let { accountId ->
                    data.accountEntries
                        .filter { it.accountId == accountId }
                        .map { it.transactionId }
                        .toSet()
                }
            val used =
                Money.sum(
                    data.splits
                        .filter {
                            it.transactionId in ids &&
                                (accountTxIds == null || it.transactionId in accountTxIds) &&
                                it.bucket == limit.bucket &&
                                it.category.equals(limit.category, true)
                        }
                        .map { it.amountMinor }
                )
            val open = limit.id in expanded
            val angle by
                androidx.compose.animation.core.animateFloatAsState(
                    if (open) 180f else 0f,
                    LocalMotion.current.floatSpec(),
                    label = "Category chevron",
                )
            Card(
                Modifier.fillMaxWidth()
                    .animateContentSize(
                        androidx.compose.animation.core.tween(LocalMotion.current.duration())
                    )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                expanded = if (open) expanded - limit.id else expanded + limit.id
                            }
                            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IdentityBadge(categoryIdentity(limit.category))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(limit.category, style = MaterialTheme.typography.titleMedium)
                            Text("${Money.format(used)} / ${Money.format(limit.limitMinor)}")
                        }
                        Icon(Icons.Default.ExpandMore, "Toggle category", Modifier.rotate(angle))
                    }
                    LinearProgressIndicator(
                        progress = {
                            if (limit.limitMinor > 0)
                                (used.toFloat() / limit.limitMinor).coerceIn(0f, 1f)
                            else 0f
                        },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                    if (open) {
                        Text(
                            limit.bucket.lowercase() +
                                " · " +
                                (data.ledger.accounts
                                    .find { it.account.id == limit.accountId }
                                    ?.account
                                    ?.name ?: "All accounts")
                        )
                        Text("Remaining: ${Money.format(limit.limitMinor - used)}")
                        TextButton(onClick = { edit(limit) }) { Text("Edit category limit") }
                    }
                }
            }
        }
    }
}

internal data class IdentityOption(
    val id: String,
    val label: String,
    val detail: String = "",
    val identity: MoneyIdentity = categoryIdentity(label),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdentityPicker(
    label: String,
    selected: String,
    options: List<IdentityOption>,
    modifier: Modifier = Modifier,
    allowCustom: Boolean = false,
    onSelect: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val option =
        options.firstOrNull { it.id == selected }
            ?: IdentityOption(selected, selected.ifBlank { "Choose $label" })
    Card(
        onClick = {
            open = true
            query = ""
        },
        modifier = modifier.fillMaxWidth(),
        enabled = !LocalSaving.current,
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
    ) {
        Row(
            Modifier.padding(YardSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YardSpace.md),
        ) {
            IdentityBadge(option.identity)
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(option.label, style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.Default.ExpandMore, null)
        }
    }
    if (open)
        StagedEditSheet(
            "Choose $label",
            option.label,
            LocalSaving.current,
            allowCustom &&
                query.isNotBlank() &&
                options.none { it.label.equals(query.trim(), true) },
            { open = false },
            scrollContent = false,
        ) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(YardSpace.lg),
            ) {
                SectionHeading(
                    "Choose $label",
                    action = {
                        IconButton(onClick = LocalEditDismiss.current) {
                            Icon(Icons.Default.Close, "Close $label picker")
                        }
                    },
                )
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text("Search $label") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                val matches = options.filter { it.label.contains(query, true) }
                if (
                    allowCustom &&
                        query.isNotBlank() &&
                        options.none { it.label.equals(query.trim(), true) }
                ) {
                    FilledTonalButton(
                        onClick = {
                            onSelect(query.trim())
                            open = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Use “" + query.trim() + "”")
                    }
                }
                if (matches.isEmpty())
                    Text(
                        "No matches. Try another name.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                LazyVerticalGrid(
                    GridCells.Adaptive(136.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(matches, key = { it.id }) { item ->
                        val chosen = item.id == selected
                        Card(
                            onClick = {
                                onSelect(item.id)
                                open = false
                            },
                            modifier = Modifier.fillMaxWidth().semantics { this.selected = chosen },
                            shape = MaterialTheme.shapes.medium,
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (chosen) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    IdentityBadge(item.identity)
                                    if (chosen)
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                }
                                Text(item.label, style = MaterialTheme.typography.titleMedium)
                                if (item.detail.isNotBlank())
                                    Text(item.detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
}
