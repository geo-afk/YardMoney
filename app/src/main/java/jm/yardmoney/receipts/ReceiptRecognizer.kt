package jm.yardmoney.receipts

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.*
import kotlinx.coroutines.tasks.await

/** Geometry-aware, dual-pass on-device OCR. Caller owns the input bitmap. */
class ReceiptRecognizer {
    private data class Line(val text: String, val x: Int, val y: Int, val height: Int)

    private fun lines(result: Text, offset: Int): List<Line> =
        result.textBlocks
            .flatMap { it.lines }
            .mapNotNull { line ->
                line.boundingBox?.let {
                    Line(line.text, it.left, it.centerY() + offset, it.height().coerceAtLeast(1))
                }
            }

    private fun score(lines: List<Line>) =
        lines.sumOf { it.text.count(Char::isLetterOrDigit) } +
            lines.count { Regex("\\d+[.,]\\d{2}").containsMatchIn(it.text) } * 12

    suspend fun recognize(bitmap: Bitmap, progress: (String) -> Unit = {}): String {
        val reader = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var working = bitmap
        val detected = mutableListOf<Line>()
        val quality = ReceiptPreprocessor.quality(bitmap)
        try {
            progress("Checking orientation and text alignment…")
            val probeScale =
                minOf(1.0, sqrt(2_000_000.0 / (bitmap.width.toDouble() * bitmap.height)))
            val probe =
                if (probeScale < 1)
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * probeScale).toInt().coerceAtLeast(1),
                        (bitmap.height * probeScale).toInt().coerceAtLeast(1),
                        true,
                    )
                else bitmap
            try {
                var result = reader.process(InputImage.fromBitmap(probe, 0)).await()
                var rotation = 0
                var best = result.text.count(Char::isLetterOrDigit)
                if (best < 30)
                    for (turn in listOf(90, 180, 270)) {
                        val rotated =
                            Bitmap.createBitmap(
                                probe,
                                0,
                                0,
                                probe.width,
                                probe.height,
                                Matrix().apply { postRotate(turn.toFloat()) },
                                true,
                            )
                        try {
                            val candidate =
                                reader.process(InputImage.fromBitmap(rotated, 0)).await()
                            val candidateScore = candidate.text.count(Char::isLetterOrDigit)
                            if (candidateScore > best) {
                                best = candidateScore
                                result = candidate
                                rotation = turn
                            }
                        } finally {
                            if (rotated !== probe) rotated.recycle()
                        }
                    }
                val angles =
                    result.textBlocks
                        .flatMap { it.lines }
                        .mapNotNull { line ->
                            line.cornerPoints
                                ?.takeIf { it.size >= 2 }
                                ?.let { corners ->
                                    Math.toDegrees(
                                        atan2(
                                            (corners[1].y - corners[0].y).toDouble(),
                                            (corners[1].x - corners[0].x).toDouble(),
                                        )
                                    )
                                }
                        }
                        .filter { abs(it) <= 15 }
                        .sorted()
                val skew = if (angles.size >= 3) angles[angles.size / 2] else 0.0
                val correction = rotation - (if (abs(skew) >= .8) skew else 0.0)
                if (abs(correction) >= .8)
                    working =
                        Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            Matrix().apply { postRotate(correction.toFloat()) },
                            true,
                        )
            } finally {
                if (probe !== bitmap) probe.recycle()
            }
            val tileHeight = (2_000_000 / working.width).coerceIn(400, 1800)
            val overlap = 220
            val count =
                if (working.height <= tileHeight) 1
                else ceil((working.height - overlap).toDouble() / (tileHeight - overlap)).toInt()
            for (index in 0 until count) {
                val y = if (count == 1) 0 else index * (tileHeight - overlap)
                val h = if (count == 1) working.height else minOf(tileHeight, working.height - y)
                if (h <= 0) break
                val tile = Bitmap.createBitmap(working, 0, y, working.width, h)
                var enhanced: Bitmap? = null
                try {
                    progress("Recognising section ${index+1} of $count…")
                    val original = lines(reader.process(InputImage.fromBitmap(tile, 0)).await(), y)
                    progress("Checking faded text · section ${index+1} of $count…")
                    enhanced = ReceiptPreprocessor.enhance(tile)
                    val improved =
                        lines(reader.process(InputImage.fromBitmap(enhanced, 0)).await(), y)
                    val selected = if (score(improved) > score(original)) improved else original
                    // Ownership at overlap midpoints preserves identical repeated purchases on
                    // distinct rows.
                    val start = if (index == 0) 0 else y + overlap / 2
                    val end = if (index == count - 1) working.height else y + h - overlap / 2
                    detected.addAll(selected.filter { it.y >= start && it.y < end })
                } finally {
                    enhanced?.recycle()
                    if (tile !== working) tile.recycle()
                }
            }
        } finally {
            reader.close()
            if (working !== bitmap) working.recycle()
        }
        progress("Matching receipt lines…")
        val rows = mutableListOf<MutableList<Line>>()
        for (line in detected.sortedBy { it.y }) {
            val row = rows.lastOrNull()
            if (
                row != null &&
                    abs(row.first().y - line.y) <= minOf(row.first().height, line.height) * .45
            )
                row.add(line)
            else rows.add(mutableListOf(line))
        }
        val raw =
            rows.joinToString("\n") { row -> row.sortedBy { it.x }.joinToString("  ") { it.text } }
        require(raw.isNotBlank()) {
            "No text found. Retake closer with even light, or enter a manual expense."
        }
        val annotated =
            raw +
                if (quality.warnings.isEmpty()) ""
                else "\n\n[Image review]\n" + quality.warnings.joinToString("\n")
        return annotated
    }
}
