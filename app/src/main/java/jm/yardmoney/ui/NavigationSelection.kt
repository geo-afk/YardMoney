package jm.yardmoney.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun NavigationSelectionIcon(
    selected: Boolean,
    filled: ImageVector,
    outlined: ImageVector,
    focused: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val background = Color.Transparent
    val outline = if (focused) 1f else 0f
    Box(
        Modifier.size(48.dp, 32.dp)
            .background(background, MaterialTheme.shapes.small)
            .border(
                if (focused) 2.dp else 1.dp,
                c.primary.copy(alpha = outline.coerceIn(0f, 1f)),
                MaterialTheme.shapes.small,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (selected) filled else outlined,
            null,
            tint = if (selected) c.primary else c.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
internal fun NavigationSelectionLabel(text: String, selected: Boolean) {
    Text(
        text,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color =
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NavigationWithoutTapEffects(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
}
