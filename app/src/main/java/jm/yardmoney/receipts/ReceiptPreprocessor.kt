package jm.yardmoney.receipts

import android.graphics.*
import kotlin.math.*

/** Conservative bright-paper detector. Ambiguous scenes keep the full image for manual cropping. */
object ReceiptPreprocessor {
    fun quality(source: Bitmap): ReceiptQuality {
        val w = minOf(400, source.width)
        val h = maxOf(1, (source.height.toDouble() * w / source.width).toInt())
        val small = Bitmap.createScaledBitmap(source, w, h.coerceAtMost(1600), true)
        val p = IntArray(small.width * small.height)
        small.getPixels(p, 0, small.width, 0, 0, small.width, small.height)
        var sum = 0.0
        var lap = 0.0
        var lap2 = 0.0
        var count = 0
        fun gray(c: Int) =
            ((c shr 16 and 255) * .299 + (c shr 8 and 255) * .587 + (c and 255) * .114)
        for (y in 1 until small.height - 1) for (x in 1 until small.width - 1) {
            val i = y * small.width + x
            val v = gray(p[i])
            sum += v
            val l =
                4 * v -
                    gray(p[i - 1]) -
                    gray(p[i + 1]) -
                    gray(p[i - small.width]) -
                    gray(p[i + small.width])
            lap += l
            lap2 += l * l
            count++
        }
        if (small !== source) small.recycle()
        val brightness = sum / maxOf(1, count)
        val sharpness = lap2 / maxOf(1, count) - (lap / maxOf(1, count)).pow(2)
        val warnings = buildList {
            if (brightness < 85) add("Low light: move to even lighting and avoid casting a shadow.")
            if (brightness > 242)
                add("Very bright image: check for glare and faded or washed-out text.")
            if (sharpness < 110)
                add("Text may be blurred or faded. Hold still, tap to focus, or retake closer.")
            if (source.width < 700)
                add("Small image: a sharper, higher-resolution photo may recover more items.")
        }
        return ReceiptQuality(brightness, sharpness, warnings)
    }

    fun boundary(source: Bitmap): ReceiptBoundary? {
        val w = minOf(360, source.width)
        val h = (source.height.toDouble() * w / source.width).toInt().coerceIn(1, 1200)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val p = IntArray(w * h)
        small.getPixels(p, 0, w, 0, 0, w, h)
        if (small !== source) small.recycle()
        fun light(c: Int): Int = ((c shr 16 and 255) * 3 + (c shr 8 and 255) * 6 + (c and 255)) / 10
        val edge = buildList {
            for (x in 0 until w) {
                add(light(p[x]))
                add(light(p[(h - 1) * w + x]))
            }
            for (y in 0 until h) {
                add(light(p[y * w]))
                add(light(p[y * w + w - 1]))
            }
        }
            .sorted()
        // Keep the required contrast even on bright backgrounds. Capping this at 225
        // made an entirely white image appear to contain a paper boundary.
        val threshold = (edge[edge.size / 2] + 35).coerceAtLeast(145)
        val rows = mutableListOf<Triple<Int, Int, Int>>()
        for (y in 0 until h step 3) {
            val xs = (0 until w).filter { light(p[y * w + it]) > threshold }
            if (xs.size > w * .18) {
                val l = xs[xs.size / 20]
                val r = xs[xs.size * 19 / 20]
                if (r - l > w * .22) rows.add(Triple(y, l, r))
            }
        }
        if (rows.size < 15) return null
        // Largest contiguous paper run; gaps caused by text are tolerated.
        val runs = mutableListOf<MutableList<Triple<Int, Int, Int>>>()
        rows.forEach { row ->
            if (runs.isEmpty() || row.first - runs.last().last().first > 12)
                runs.add(mutableListOf())
            runs.last().add(row)
        }
        val run = runs.maxByOrNull { it.size } ?: return null
        val top = run.first().first
        val bottom = run.last().first
        if (bottom - top < h * .3) return null
        fun median(values: List<Int>) = values.sorted()[values.size / 2].toFloat()
        val count = maxOf(3, run.size / 8)
        val tl = median(run.take(count).map { it.second })
        val tr = median(run.take(count).map { it.third })
        val bl = median(run.takeLast(count).map { it.second })
        val br = median(run.takeLast(count).map { it.third })
        val area = ((tr - tl) + (br - bl)) / 2 * (bottom - top) / (w * h)
        if (area < .18 || area > .93 || tl < 2 || tr > w - 3 || bl < 2 || br > w - 3) return null
        val sx = source.width.toFloat() / w
        val sy = source.height.toFloat() / h
        val topLeft = run.minBy { it.second.toDouble() / w + it.first.toDouble() / h }
        val topRight = run.maxBy { it.third.toDouble() / w - it.first.toDouble() / h }
        val bottomRight = run.maxBy { it.third.toDouble() / w + it.first.toDouble() / h }
        val bottomLeft = run.minBy { it.second.toDouble() / w - it.first.toDouble() / h }
        val margin = 5f
        val corners =
            floatArrayOf(
                (topLeft.second - margin).coerceAtLeast(0f) * sx,
                (topLeft.first - margin).coerceAtLeast(0f) * sy,
                (topRight.third + margin).coerceAtMost(w - 1f) * sx,
                (topRight.first - margin).coerceAtLeast(0f) * sy,
                (bottomRight.third + margin).coerceAtMost(w - 1f) * sx,
                (bottomRight.first + margin).coerceAtMost(h - 1f) * sy,
                (bottomLeft.second - margin).coerceAtLeast(0f) * sx,
                (bottomLeft.first + margin).coerceAtMost(h - 1f) * sy,
            )
        return ReceiptBoundary(corners, .65)
    }

    fun straighten(source: Bitmap, boundary: ReceiptBoundary): Bitmap {
        val c = boundary.corners
        fun distance(i: Int, j: Int) = hypot(c[i] - c[j], c[i + 1] - c[j + 1])
        val w = maxOf(distance(0, 2), distance(6, 4)).toInt().coerceAtLeast(1)
        val h = maxOf(distance(0, 6), distance(2, 4)).toInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val matrix = Matrix()
        check(
            matrix.setPolyToPoly(
                c,
                0,
                floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat()),
                0,
                4,
            )
        )
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return output
    }

    /** Local illumination normalization plus restrained contrast/sharpening for thermal paper. */
    fun enhance(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray =
            IntArray(w * h) { i ->
                val c = pixels[i]
                ((c shr 16 and 255) * 3 + (c shr 8 and 255) * 6 + (c and 255)) / 10
            }
        // Suppress isolated sensor/JPEG speckles while preserving connected character strokes.
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val a = gray[i - 1]
            val b = gray[i + 1]
            val c = gray[i - w]
            val d = gray[i + w]
            val mean = (a + b + c + d) / 4.0
            if (maxOf(a, b, c, d) - minOf(a, b, c, d) < 18 && abs(gray[i] - mean) > 65)
                gray[i] = (gray[i] * .75 + mean * .25).roundToInt()
        }
        val radius = (w / 35).coerceIn(8, 60)
        val horizontal = IntArray(w * h)
        for (y in 0 until h) {
            var sum = 0
            var left = 0
            var right = -1
            for (x in 0 until w) {
                val target = (x + radius).coerceAtMost(w - 1)
                while (right < target) {
                    right++
                    sum += gray[y * w + right]
                }
                while (left < x - radius) {
                    sum -= gray[y * w + left]
                    left++
                }
                horizontal[y * w + x] = sum / (right - left + 1)
            }
        }
        for (x in 0 until w) {
            var sum = 0
            var top = 0
            var bottom = -1
            for (y in 0 until h) {
                val target = (y + radius).coerceAtMost(h - 1)
                while (bottom < target) {
                    bottom++
                    sum += horizontal[bottom * w + x]
                }
                while (top < y - radius) {
                    sum -= horizontal[top * w + x]
                    top++
                }
                val i = y * w + x
                val background = maxOf(35, sum / (bottom - top + 1))
                val normalized = (gray[i] * 235.0 / background)
                val adjacent =
                    (gray[y * w + (x - 1).coerceAtLeast(0)] +
                        gray[y * w + (x + 1).coerceAtMost(w - 1)]) / 2.0
                val sharp = (gray[i] - adjacent) * .25
                val value = ((normalized - 128) * 1.18 + 128 + sharp).roundToInt().coerceIn(0, 255)
                pixels[i] = Color.rgb(value, value, value)
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
