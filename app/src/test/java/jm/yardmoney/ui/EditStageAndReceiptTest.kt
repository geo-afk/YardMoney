package jm.yardmoney.ui

import jm.yardmoney.core.ColorContrast
import org.junit.Assert.*
import org.junit.Test

class EditStageAndReceiptTest {
    @Test
    fun downFromFullCannotSkipHalfOrDismiss() {
        assertEquals(EditStage.Half, settledEditStage(EditStage.Full, 1000f, 1000f, 112f, 3000f))
        assertEquals(EditStage.Peek, settledEditStage(EditStage.Half, 1000f, 1000f, 112f, 3000f))
    }

    @Test
    fun onlyDeliberatePullPastPeekDismisses() {
        assertEquals(EditStage.Peek, settledEditStage(EditStage.Peek, 900f, 1000f, 112f, 0f))
        assertNull(settledEditStage(EditStage.Peek, 960f, 1000f, 112f, 0f))
        assertEquals(EditStage.Half, settledEditStage(EditStage.Peek, 500f, 1000f, 112f, -1200f))
    }

    @Test
    fun releaseSnapsToNearestStageWithoutFling() {
        assertEquals(EditStage.Full, settledEditStage(EditStage.Full, 180f, 1000f, 112f, 0f))
        assertEquals(EditStage.Half, settledEditStage(EditStage.Full, 400f, 1000f, 112f, 0f))
    }

    @Test
    fun receiptSyncFollowsBothBackgroundAndAccent() {
        val settings = ReceiptAppearance(sync = true)
        val light = receiptBackground(settings, -1, 0xFF00865A.toInt())
        val dark = receiptBackground(settings, 0xFF121318.toInt(), 0xFF00865A.toInt())
        assertNotEquals(light, dark)
        assertNotEquals(light, receiptBackground(settings, -1, 0xFFB52672.toInt()))
        assertEquals(settings.background, receiptBackground(settings.copy(sync = false), 0, 0))
    }

    @Test
    fun everyReceiptBackgroundHasReadableInk() {
        for (red in 0..255 step 17) for (green in 0..255 step 17) for (blue in 0..255 step 17) {
            val paper = 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
            assertTrue(ColorContrast.ratio(receiptInk(paper), paper) >= 4.5)
        }
    }
}
