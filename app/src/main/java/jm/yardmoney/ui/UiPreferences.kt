package jm.yardmoney.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

enum class ControlCorners(val radius: Int) {
    Square(4),
    Soft(12),
    Rounded(20),
}

enum class LayoutSpacing(val gap: Int) {
    Compact(12),
    Comfortable(20),
}

internal val LocalLayoutSpacing = staticCompositionLocalOf { LayoutSpacing.Comfortable.gap.dp }
