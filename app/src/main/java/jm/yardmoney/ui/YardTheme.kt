package jm.yardmoney.ui

import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jm.yardmoney.core.ColorContrast

data class Appearance(
    val mode: String = "System",
    val dynamic: Boolean = false,
    val accent: Int = 0xFF145D4D.toInt(),
    val motion: MotionStyle = MotionStyle.Calm,
    val corners: ControlCorners = ControlCorners.Soft,
    val spacing: LayoutSpacing = LayoutSpacing.Comfortable,
)

private fun SharedPreferences.appearance() =
    Appearance(
        getString("theme", "System") ?: "System",
        getBoolean("dynamic", false),
        getInt("accent", 0xFF145D4D.toInt()),
        runCatching { MotionStyle.valueOf(getString("motion", "Calm") ?: "Calm") }
            .getOrDefault(MotionStyle.Calm),
        runCatching { ControlCorners.valueOf(getString("corners", "Soft") ?: "Soft") }
            .getOrDefault(ControlCorners.Soft),
        runCatching { LayoutSpacing.valueOf(getString("spacing", "Comfortable") ?: "Comfortable") }
            .getOrDefault(LayoutSpacing.Comfortable),
    )

@Composable
internal fun rememberAppearance(): State<Appearance> {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("appearance", 0) }
    val state = remember(prefs) { mutableStateOf(prefs.appearance()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            state.value = prefs.appearance()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

internal fun Appearance.isDark(system: Boolean) =
    mode == "Dark" || mode == "AMOLED" || mode == "System" && system

internal fun customColors(appearance: Appearance, dark: Boolean): ColorScheme {
    val black = appearance.mode == "AMOLED"
    val surface = Color(if (black) 0xFF000000 else if (dark) 0xFF121318 else 0xFFFAFAFC)
    fun tint(f: Double) = Color(ColorContrast.blend(surface.toArgb(), appearance.accent, f))
    val contrastSurface = if (dark) 0xFF30323B.toInt() else 0xFFE3E4EC.toInt()
    val primary = Color(ColorContrast.readable(appearance.accent, contrastSurface))
    val secondarySeed = ColorContrast.blend(appearance.accent, 0xFF535C93.toInt(), .55)
    val tertiarySeed = ColorContrast.blend(appearance.accent, 0xFF9C4B16.toInt(), .75)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base
        .copy(
            primary = primary,
            onPrimary = readable(Color.White, primary),
            primaryContainer = tint(if (dark) .23 else .12),
            onPrimaryContainer = readable(primary, tint(if (dark) .23 else .12)),
            secondary = Color(ColorContrast.readable(secondarySeed, contrastSurface)),
            secondaryContainer = tint(if (dark) .18 else .08),
            onSecondaryContainer =
                readable(if (dark) Color.White else Color.Black, tint(if (dark) .18 else .08)),
            tertiary = Color(ColorContrast.readable(tertiarySeed, contrastSurface)),
            tertiaryContainer = tint(if (dark) .15 else .06),
            onTertiaryContainer =
                readable(if (dark) Color.White else Color.Black, tint(if (dark) .15 else .06)),
            background = surface,
            onBackground = if (dark) Color(0xFFF3F3F8) else Color(0xFF191B22),
            surface = surface,
            onSurface = if (dark) Color(0xFFF3F3F8) else Color(0xFF191B22),
            surfaceVariant = if (dark) Color(0xFF282A32) else Color(0xFFE7E8EE),
            onSurfaceVariant = if (dark) Color(0xFFD0D2DC) else Color(0xFF444752),
            surfaceDim =
                if (black) Color.Black else if (dark) Color(0xFF121318) else Color(0xFFDFE0E7),
            surfaceBright = if (dark) Color(0xFF34363E) else Color.White,
            surfaceContainerLowest = if (dark) Color.Black else Color.White,
            surfaceContainerLow = if (dark) Color(0xFF191B21) else Color(0xFFF4F4F8),
            surfaceContainer = if (dark) Color(0xFF202229) else Color(0xFFF0F0F5),
            surfaceContainerHigh = if (dark) Color(0xFF282A32) else Color(0xFFEAEAF1),
            surfaceContainerHighest = if (dark) Color(0xFF30323B) else Color(0xFFE3E4EC),
            outline = if (dark) Color(0xFF9699A6) else Color(0xFF727582),
            outlineVariant = if (dark) Color(0xFF555864) else Color(0xFFC2C4CE),
            surfaceTint = primary,
        )
        .let(::accessibleColors)
}

private fun readable(foreground: Color, background: Color, minimum: Double = 4.5) =
    Color(ColorContrast.readable(foreground.toArgb(), background.toArgb(), minimum))

private fun accessibleColors(c: ColorScheme) =
    c.copy(
        onPrimary = readable(c.onPrimary, c.primary),
        onSecondary = readable(c.onSecondary, c.secondary),
        onTertiary = readable(c.onTertiary, c.tertiary),
        onPrimaryContainer = readable(c.onPrimaryContainer, c.primaryContainer),
        onSecondaryContainer = readable(c.onSecondaryContainer, c.secondaryContainer),
        onTertiaryContainer = readable(c.onTertiaryContainer, c.tertiaryContainer),
        onSurface = readable(c.onSurface, c.surface, 7.0),
        onSurfaceVariant = readable(c.onSurfaceVariant, c.surface),
        onBackground = readable(c.onBackground, c.background, 7.0),
        onError = readable(c.onError, c.error),
        onErrorContainer = readable(c.onErrorContainer, c.errorContainer),
        outline = readable(c.outline, c.surface, 3.0),
    )

private val YardTypography =
    Typography(
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
        bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
        labelLarge =
            TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
        labelMedium =
            TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelSmall =
            TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    )

@Composable
internal fun YardTheme(content: @Composable () -> Unit) {
    val appearance by rememberAppearance()
    val dark = appearance.isDark(isSystemInDarkTheme())
    val context = LocalContext.current
    val colors =
        if (appearance.dynamic && Build.VERSION.SDK_INT >= 31) {
            val dynamic =
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            accessibleColors(
                if (appearance.mode == "AMOLED")
                    dynamic.copy(
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceContainerLowest = Color.Black,
                    )
                else dynamic
            )
        } else customColors(appearance, dark)
    ConfigureSystemBars(dark)
    MaterialTheme(
        colorScheme = colors,
        typography = YardTypography,
        shapes =
            Shapes(
                extraSmall = RoundedCornerShape(appearance.corners.radius.dp),
                small = RoundedCornerShape(appearance.corners.radius.dp),
                medium = RoundedCornerShape((appearance.corners.radius + 8).dp),
                large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
        content = {
            CompositionLocalProvider(
                LocalMotion provides appearance.motion,
                LocalLayoutSpacing provides appearance.spacing.gap.dp,
                content = content,
            )
        },
    )
}

@Composable
internal fun AppearancePanel() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("appearance", 0) }
    val appearance by rememberAppearance()
    Choice("Control corners", appearance.corners.name, ControlCorners.entries.map { it.name }) {
        prefs.edit().putString("corners", it).apply()
    }
    Choice("Layout spacing", appearance.spacing.name, LayoutSpacing.entries.map { it.name }) {
        prefs.edit().putString("spacing", it).apply()
    }
    Text(
        "Soft uses gentle corners without pill-shaped buttons. Square is sharper; Rounded is softer. Compact reduces space between sections without shrinking touch targets.",
        style = MaterialTheme.typography.bodySmall,
    )
    Choice("Animation style", appearance.motion.name, MotionStyle.entries.map { it.name }) {
        prefs.edit().putString("motion", it).apply()
    }
    Text(
        "Calm fades details, Slide moves between category views, and Expressive gently scales details and smooths charts. Off removes app transitions. Main navigation switches immediately. Android’s reduced-motion setting is respected.",
        style = MaterialTheme.typography.bodySmall,
    )
    Choice("Appearance", appearance.mode, listOf("System", "Light", "Dark", "AMOLED")) {
        prefs.edit().putString("theme", it).apply()
    }
    Text(
        "System follows your phone. AMOLED uses a true black page background.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (Build.VERSION.SDK_INT >= 31)
        Tick("Use Android wallpaper colors", appearance.dynamic) {
            prefs.edit().putBoolean("dynamic", it).apply()
        }
    else
        Text(
            "Wallpaper colors require Android 12 or later. Custom colors work on this device.",
            style = MaterialTheme.typography.bodySmall,
        )
    val accents =
        mapOf(
            "Palm" to 0xFF145D4D,
            "Ocean" to 0xFF2357B5,
            "Plum" to 0xFF7436A0,
            "Terracotta" to 0xFFAD4324,
            "Gold" to 0xFF9C6B00,
            "Rose" to 0xFFAD2857,
        )
    Choice(
        "Accent",
        accents.entries.firstOrNull { it.value.toInt() == appearance.accent }?.key ?: "Custom",
        accents.keys.toList(),
    ) {
        prefs
            .edit()
            .putInt("accent", accents.getValue(it).toInt())
            .putBoolean("dynamic", false)
            .apply()
    }
    var custom by
        rememberSaveable(appearance.accent) {
            mutableStateOf("#%06X".format(appearance.accent and 0xFFFFFF))
        }
    Field("Custom accent (#RRGGBB)", custom) { custom = it }
    val valid = Regex("#[0-9a-fA-F]{6}").matches(custom)
    OutlinedButton(
        enabled = valid && !LocalSaving.current,
        onClick = {
            prefs
                .edit()
                .putInt("accent", (0xFF000000L or custom.drop(1).toLong(16)).toInt())
                .putBoolean("dynamic", false)
                .apply()
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Text("Apply custom color")
    }
    Text(
        "Accent tones adjust for readable text. Choosing an accent turns off wallpaper colors.",
        style = MaterialTheme.typography.bodySmall,
    )
}
