package jm.yardmoney.core

import org.junit.Assert.*
import org.junit.Test

class ColorContrastTest {
    @Test
    fun knownContrastEndpoints() {
        assertEquals(21.0, ColorContrast.ratio(-1, -0x1000000), .001)
    }

    @Test
    fun arbitraryAccentsRemainReadableOnEveryBackground() {
        for (background in
            listOf(-1, 0xFFFAFAFC.toInt(), 0xFF121318.toInt(), 0xFF000000.toInt())) for (accent in
            listOf(
                0xFF145D4D.toInt(),
                0xFF2357B5.toInt(),
                0xFF7436A0.toInt(),
                0xFFAD4324.toInt(),
                0xFF9C6B00.toInt(),
                0xFFAD2857.toInt(),
                -1,
                -0x1000000,
                0xFFFFFF00.toInt(),
            )) {
            val repaired = ColorContrast.readable(accent, background)
            assertTrue(ColorContrast.ratio(repaired, background) >= 4.5)
        }
    }

    @Test
    fun sufficientContrastPreservesColor() {
        assertEquals(0xFF145D4D.toInt(), ColorContrast.readable(0xFF145D4D.toInt(), -1))
    }
}
