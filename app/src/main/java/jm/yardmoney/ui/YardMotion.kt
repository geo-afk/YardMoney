package jm.yardmoney.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*

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
