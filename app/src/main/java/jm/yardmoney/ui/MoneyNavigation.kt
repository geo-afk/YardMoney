package jm.yardmoney.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            NavigationWithoutTapEffects {
                // Equal columns keep the action aligned with every destination, including small phones.
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                    .height(IntrinsicSize.Min).selectableGroup()) {
                    val items = moneyDestinations.take(3) + quickAddDestination + moneyDestinations.drop(3)
                    items.forEach { destination ->
                        val quick = destination == quickAddDestination
                        MoneyNavigationTab(destination, selected == destination.label,
                            { if (quick) add() else select(destination.label) },
                            Modifier.weight(1f).fillMaxHeight(), enabled = !quick || addEnabled)
                    }
                }
            }
        }
    }
}

internal val quickAddDestination = MoneyDestination(
    "Quick Add", Icons.Default.AddCircle, Icons.Outlined.AddCircle,
)

@Composable
private fun MoneyNavigationTab(
    destination: MoneyDestination,
    selected: Boolean,
    click: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val contentColor = (if (selected) colors.primary else colors.onSurfaceVariant)
        .copy(alpha = if (enabled) 1f else .38f)
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
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = click,
            )
            .semantics { contentDescription = destination.label }
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            if (selected) destination.filled else destination.outlined,
            null,
            Modifier.size(24.dp).testTag("Navigation ${destination.label} icon"),
            tint = contentColor,
        )
        Text(
            destination.label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}
