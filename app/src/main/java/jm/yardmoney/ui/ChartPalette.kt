package jm.yardmoney.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import jm.yardmoney.core.ColorContrast

// Stable category hues keep Needs, Wants and Savings recognizable across screens and accents.
internal fun chartColors(scheme: ColorScheme): List<Color> {
    val dark = ColorContrast.luminance(scheme.surface.toArgb()) < .5
    val seeds =
        if (dark) listOf(0xFF34D399, 0xFF60A5FA, 0xFFC084FC)
        else listOf(0xFF00865A, 0xFF2563EB, 0xFF9333EA)
    return seeds.map {
        Color(ColorContrast.readable(it.toInt(), scheme.surfaceContainerHighest.toArgb(), 3.0))
    }
}

@Composable internal fun chartColors(): List<Color> = chartColors(MaterialTheme.colorScheme)
