package jm.yardmoney.core

import kotlin.math.pow

/** WCAG sRGB contrast, also used to repair user-selected and wallpaper colors. */
object ColorContrast {
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val v = ((argb ushr shift) and 255) / 255.0
            return if (v <= .04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
        }
        return .2126 * channel(16) + .7152 * channel(8) + .0722 * channel(0)
    }

    fun ratio(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + .05) / (minOf(x, y) + .05)
    }

    fun blend(a: Int, b: Int, fraction: Double): Int {
        var result = -0x1000000
        for (shift in listOf(16, 8, 0)) {
            val x = (a ushr shift) and 255
            val y = (b ushr shift) and 255
            result = result or ((x + (y - x) * fraction.coerceIn(0.0, 1.0)).toInt() shl shift)
        }
        return result
    }

    fun readable(color: Int, background: Int, minimum: Double = 4.5): Int {
        if (ratio(color, background) >= minimum) return color
        val target = if (ratio(-1, background) > ratio(-0x1000000, background)) -1 else -0x1000000
        for (step in 1..100) {
            val candidate = blend(color, target, step / 100.0)
            if (ratio(candidate, background) >= minimum) return candidate
        }
        return target
    }
}
