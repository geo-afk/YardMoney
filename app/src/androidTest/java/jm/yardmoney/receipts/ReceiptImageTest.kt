package jm.yardmoney.receipts

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import jm.yardmoney.core.ReceiptParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptImageTest {
    @Test
    fun uniformDarkImageWarnsAboutLightingAndFocus() {
        val image = Bitmap.createBitmap(800, 1600, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.rgb(30, 30, 30))
        try {
            val q = ReceiptPreprocessor.quality(image)
            assertTrue(q.warnings.any { it.startsWith("Low light") })
            assertTrue(q.warnings.any { it.contains("blurred") })
        } finally {
            image.recycle()
        }
    }

    @Test
    fun shadowNormalizationPreservesTextAndLightensPaper() {
        val image = Bitmap.createBitmap(800, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint =
            Paint().apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        800f,
                        0f,
                        Color.rgb(65, 65, 65),
                        Color.rgb(210, 210, 210),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, 0f, 800f, 1600f, paint)
        canvas.drawText(
            "RICE 100.00",
            80f,
            200f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 42f
            },
        )
        val improved = ReceiptPreprocessor.enhance(image)
        try {
            assertTrue(Color.red(improved.getPixel(30, 500)) > Color.red(image.getPixel(30, 500)))
            assertTrue(Color.red(improved.getPixel(30, 500)) > 180)
        } finally {
            improved.recycle()
            image.recycle()
        }
    }

    @Test
    fun uncertainBoundaryNeverAutomaticallyCrops() {
        val image = Bitmap.createBitmap(800, 1600, Bitmap.Config.ARGB_8888)
        try {
            for (brightness in listOf(30, 145, 210, 226, 245, 255)) {
                image.eraseColor(Color.rgb(brightness, brightness, brightness))
                assertNull(
                    "Uniform brightness $brightness has no paper boundary",
                    ReceiptPreprocessor.boundary(image),
                )
            }
        } finally {
            image.recycle()
        }
    }

    @Test
    fun paperQuadrilateralCanBeStraightened() {
        val image = Bitmap.createBitmap(800, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(35, 35, 35))
        val path =
            Path().apply {
                moveTo(200f, 140f)
                lineTo(650f, 200f)
                lineTo(580f, 1450f)
                lineTo(120f, 1360f)
                close()
            }
        canvas.drawPath(path, Paint().apply { color = Color.WHITE })
        try {
            val boundary = ReceiptPreprocessor.boundary(image)
            assertNotNull(boundary)
            val straight = ReceiptPreprocessor.straighten(image, boundary!!)
            try {
                assertTrue(straight.height > 800)
                assertTrue(straight.width > 250)
            } finally {
                straight.recycle()
            }
        } finally {
            image.recycle()
        }
    }

    @Test
    fun manyItemReceiptOcrKeepsTopMiddleAndBottom() = runBlocking {
        val image = Bitmap.createBitmap(1000, 8000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.WHITE)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 36f
                typeface = Typeface.MONOSPACE
            }
        canvas.drawText("KINGSTON MARKET", 60f, 70f, paint)
        for (i in 1..100) canvas.drawText("ITEM $i     10.00", 60f, 150f + i * 70f, paint)
        canvas.drawText("TOTAL 1000.00", 60f, 7550f, paint)
        try {
            val raw = ReceiptRecognizer().recognize(image)
            val r = ReceiptParser.parse(raw)
            assertTrue(raw.contains("ITEM 1"))
            assertTrue(raw.contains("ITEM 50"))
            assertTrue(raw.contains("ITEM 100"))
            assertEquals(100000L, r.totalMinor)
            assertTrue(r.lines.size >= 95)
        } finally {
            image.recycle()
        }
    }
}
