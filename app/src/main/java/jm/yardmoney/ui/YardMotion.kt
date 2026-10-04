package jm.yardmoney.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

enum class MotionStyle {
    Calm,
    Slide,
    Expressive,
    Off,
}

internal val LocalMotion = staticCompositionLocalOf { MotionStyle.Calm }

internal fun MotionStyle.duration() =
    when (this) {
        MotionStyle.Off -> 0
        MotionStyle.Calm -> 180
        MotionStyle.Slide -> 240
        MotionStyle.Expressive -> 300
    }

internal fun MotionStyle.floatSpec(): FiniteAnimationSpec<Float> =
    when (this) {
        MotionStyle.Off -> snap()
        MotionStyle.Expressive ->
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        else -> tween(duration(), easing = FastOutSlowInEasing)
    }

internal fun MotionStyle.transition(): ContentTransform =
    when (this) {
        MotionStyle.Off -> EnterTransition.None togetherWith ExitTransition.None
        MotionStyle.Calm -> fadeIn(tween(duration())) togetherWith fadeOut(tween(90))
        MotionStyle.Slide ->
            (fadeIn(tween(duration())) +
                slideInHorizontally(tween(duration())) { it / 12 }) togetherWith
                (fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { -it / 12 })
        MotionStyle.Expressive ->
            (fadeIn(tween(duration())) +
                scaleIn(tween(duration()), initialScale = .96f)) togetherWith fadeOut(tween(120))
    }

// Material fade-through suits peer destinations (bottom navigation), avoiding overlapping text.
// https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md#fade-through
// Keep the navigation chrome fixed and never animate page height as scroll content changes.
internal fun MotionStyle.menuTransition(travelPx: Int): ContentTransform {
    if (this == MotionStyle.Off)
        return ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    val total =
        when (this) {
            MotionStyle.Calm -> 220
            MotionStyle.Slide -> 240
            MotionStyle.Expressive -> 260
            MotionStyle.Off -> 0
        }
    val exit = total * 3 / 10
    val enter = total - exit
    val fade = fadeIn(tween(enter, delayMillis = exit, easing = LinearOutSlowInEasing))
    val incoming =
        when (this) {
            MotionStyle.Slide ->
                fade +
                    slideInVertically(
                        tween(enter, delayMillis = exit, easing = LinearOutSlowInEasing)
                    ) {
                        travelPx
                    }
            MotionStyle.Expressive ->
                fade +
                    scaleIn(
                        tween(enter, delayMillis = exit, easing = LinearOutSlowInEasing),
                        initialScale = .98f,
                    )
            else -> fade
        }
    return ContentTransform(
        incoming,
        fadeOut(tween(exit, easing = FastOutLinearInEasing)),
        sizeTransform = null,
    )
}

@Composable
internal fun MenuTransition(
    destination: String,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    val motion = LocalMotion.current
    val travel = with(LocalDensity.current) { 16.dp.roundToPx() }
    // Compose's duration scale automatically follows Android's animator setting, including zero.
    AnimatedContent(
        targetState = destination,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { motion.menuTransition(travel) },
        label = "Menu fade through",
    ) { shownDestination ->
        content(shownDestination)
    }
}
