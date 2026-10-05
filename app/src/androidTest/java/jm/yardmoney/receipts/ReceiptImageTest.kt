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
    fun storageFailureBeforeCameraStartsReportsRecoveryWithoutLeavingAPhoto() {
        val context =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val root =
            java.io.File(context.cacheDir, "capture-failure-${System.nanoTime()}").apply {
                mkdirs()
            }
        val blocked = java.io.File(root, "exports").apply { writeText("Occupied path") }
        var message: String? = null
        try {
            assertNull(jm.yardmoney.ui.prepareReceiptCapture(blocked) { message = it })
            assertTrue(requireNotNull(message).contains("Free some space"))
            assertEquals("Occupied path", blocked.readText())
            assertEquals(1, root.listFiles()!!.size)
            blocked.delete()
            val recovered =
                requireNotNull(jm.yardmoney.ui.prepareReceiptCapture(blocked) { fail(it) })
            assertTrue(recovered.isFile)
            recovered.delete()
            blocked.delete()
        } finally {
            blocked.delete()
            root.delete()
        }
    }

    @Test
    fun realOfflineRecognitionProducesPrivateDraftWithoutFinancialPosting() = runBlocking {
        val app =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .targetContext
                .applicationContext as jm.yardmoney.YardMoneyApplication
        val image = Bitmap.createBitmap(1000, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.WHITE)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 48f
                typeface = Typeface.MONOSPACE
            }
        listOf(
                "DEMO MARKET",
                "2026-10-04",
                "1 RICE 10.00",
                "2 MILK 20.00",
                "TOTAL 30.00",
                "CASH 30.00",
            )
            .forEachIndexed { index, line ->
                canvas.drawText(line, 70f, 140f + index * 110f, paint)
            }
        val output = java.io.ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.PNG, 100, output)
        image.recycle()
        val before = app.repository.dao.readTransactions().size
        var id: String? = null
        try {
            id =
                kotlinx.coroutines.withTimeout(30000) {
                    ReceiptReader(app).readBytes(output.toByteArray())
                }
            val draft = requireNotNull(app.repository.dao.draft(id))
            assertNull(draft.imageRef)
            val parsed = jm.yardmoney.data.ReceiptDraftCodec.parse(draft.rawText)
            assertEquals(3000L, parsed.totalMinor)
            assertTrue(parsed.lines.any { it.name.contains("RICE") })
            assertEquals(before, app.repository.dao.readTransactions().size)
        } finally {
            id?.let { app.repository.dao.deleteDraft(it) }
        }
    }

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
