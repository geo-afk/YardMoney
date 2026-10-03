package jm.yardmoney.ui

import androidx.compose.ui.graphics.toArgb
import jm.yardmoney.core.ColorContrast
import org.junit.Assert.*
import org.junit.Test

class ThemePaletteTest {
    @Test
    fun allStaticThemesHaveReadablePairedRoles() {
        for (mode in listOf("Light", "Dark", "AMOLED")) for (accent in
            listOf(
                0xFF145D4D.toInt(),
                0xFF2357B5.toInt(),
                0xFFAD2857.toInt(),
                0xFFFFFF00.toInt(),
                -1,
                -0x1000000,
            )) {
            val c = customColors(Appearance(mode, false, accent), mode != "Light")
            for ((text, surface) in
                listOf(
                    c.onSurface to c.surface,
                    c.primary to c.surfaceContainerHighest,
                    c.secondary to c.surfaceContainerHighest,
                    c.tertiary to c.surfaceContainerHighest,
                    c.onBackground to c.background,
                    c.onPrimary to c.primary,
                    c.onPrimaryContainer to c.primaryContainer,
                    c.onSecondary to c.secondary,
                    c.onSecondaryContainer to c.secondaryContainer,
                    c.onTertiary to c.tertiary,
                    c.onTertiaryContainer to c.tertiaryContainer,
                    c.onError to c.error,
                )) assertTrue(
                "$mode $accent",
                ColorContrast.ratio(text.toArgb(), surface.toArgb()) >= 4.5,
            )
            if (mode == "AMOLED") {
                assertEquals(0xFF000000.toInt(), c.background.toArgb())
                assertEquals(0xFF000000.toInt(), c.surface.toArgb())
            }
        }
    }

    @Test
    fun systemAndExplicitModesResolveCorrectly() {
        assertFalse(Appearance("Light").isDark(true))
        assertTrue(Appearance("Dark").isDark(false))
        assertTrue(Appearance("AMOLED").isDark(false))
        assertFalse(Appearance("System").isDark(false))
        assertTrue(Appearance("System").isDark(true))
    }
}
