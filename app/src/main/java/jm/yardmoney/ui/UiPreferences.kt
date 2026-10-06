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

enum class MotionStyle {
    Calm,
    Slide,
    Expressive,
    Off,
}

data class Appearance(
    val mode: String = "System",
    val dynamic: Boolean = false,
    val accent: Int = 0xFF145D4D.toInt(),
    val motion: MotionStyle = MotionStyle.Calm,
    val corners: ControlCorners = ControlCorners.Soft,
    val spacing: LayoutSpacing = LayoutSpacing.Comfortable,
)

enum class ReceiptLayout {
    Thermal,
    Minimal,
    Compact,
    Detailed,
}

internal data class ReceiptAppearance(
    val layout: ReceiptLayout = ReceiptLayout.Thermal,
    val background: Int = -1,
    val sync: Boolean = false,
)
