package jm.yardmoney.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal val moneyDestinations =
    listOf(
        MoneyDestination("Home", Icons.Default.Home, Icons.Outlined.Home),
        MoneyDestination(
            "Activity",
            Icons.AutoMirrored.Filled.List,
            Icons.AutoMirrored.Outlined.List,
        ),
        MoneyDestination("Plan", Icons.Default.Event, Icons.Outlined.Event),
        MoneyDestination("Shop", Icons.Default.ShoppingCart, Icons.Outlined.ShoppingCart),
        MoneyDestination("More", Icons.Default.MoreHoriz, Icons.Outlined.MoreHoriz),
    )

@Composable
internal fun MoneyNavigation(
    selected: String,
    select: (String) -> Unit,
    add: () -> Unit,
    modifier: Modifier = Modifier,
    addEnabled: Boolean = true,
) {
    // Ivy's icon-first navigation and prominent Add action, using YardMoney's native tokens.
    // https://github.com/Ivy-Apps/ivy-wallet/blob/main/feature/main/src/main/java/com/ivy/main/MainBottomBar.kt
    Surface(
        modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            BoxWithConstraints(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val compact = maxWidth < 344.dp
                NavigationWithoutTapEffects {
                    Row(
                        Modifier.fillMaxWidth().selectableGroup(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (compact) {
                            // Six equal touch targets fit a 320dp phone without hiding a
                            // destination.
                            moneyDestinations.take(3).forEach { destination ->
                                MoneyNavigationTab(
                                    destination,
                                    selected == destination.label,
                                    { select(destination.label) },
                                    Modifier.weight(1f),
                                    labels = false,
                                )
                            }
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                MoneyAddButton(add, addEnabled, 48)
                            }
                            moneyDestinations.drop(3).forEach { destination ->
                                MoneyNavigationTab(
                                    destination,
                                    selected == destination.label,
                                    { select(destination.label) },
                                    Modifier.weight(1f),
                                    labels = false,
                                )
                            }
                        } else {
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                moneyDestinations.take(3).forEach { destination ->
                                    MoneyNavigationTab(
                                        destination,
                                        selected == destination.label,
                                        { select(destination.label) },
                                        Modifier.weight(1f),
                                    )
                                }
                            }
                            MoneyAddButton(add, addEnabled, 56)
                            Row(
                                Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                moneyDestinations.drop(3).forEach { destination ->
                                    MoneyNavigationTab(
                                        destination,
                                        selected == destination.label,
                                        { select(destination.label) },
                                        Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoneyAddButton(add: () -> Unit, enabled: Boolean, size: Int) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    FilledIconButton(
        onClick = add,
        enabled = enabled,
        modifier =
            Modifier.size(size.dp)
                .shadow(if (enabled) 4.dp else 0.dp, CircleShape)
                .border(
                    2.dp,
                    if (focused) MaterialTheme.colorScheme.onPrimary else Color.Transparent,
                    CircleShape,
                ),
        interactionSource = interaction,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Icon(Icons.Default.Add, "Add money or scan receipt", Modifier.size(28.dp))
    }
}

@Composable
private fun MoneyNavigationTab(
    destination: MoneyDestination,
    selected: Boolean,
    click: () -> Unit,
    modifier: Modifier = Modifier,
    labels: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .heightIn(min = 64.dp)
            .border(
                2.dp,
                if (focused) colors.primary else Color.Transparent,
                MaterialTheme.shapes.small,
            )
            .selectable(
                selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = click,
            )
            .semantics { contentDescription = destination.label }
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(
            if (selected) destination.filled else destination.outlined,
            null,
            Modifier.size(24.dp),
            tint = if (selected) colors.primary else colors.onSurfaceVariant,
        )
        if (labels)
            Text(
                destination.label,
                Modifier.clearAndSetSemantics {},
                color = if (selected) colors.primary else colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
    }
}
