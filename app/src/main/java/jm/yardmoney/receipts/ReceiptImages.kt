package jm.yardmoney.receipts

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

object ReceiptImages {
    fun read(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                require(total <= 15_000_000) { "Use a photo under 15 MB." }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: error("Could not read this photo.")

    fun decode(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a readable image." }
        val options =
            BitmapFactory.Options().apply {
                var sample = 1
                while (
                    bounds.outWidth / sample > 3000 ||
                        bounds.outHeight / sample > 12000 ||
                        (bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) >
                            8_000_000
                ) sample *= 2
                inSampleSize = sample
            }
        val decoded =
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: error("Could not decode this photo.")
        val exif = bytes.inputStream().use { ExifInterface(it) }
        val matrix =
            Matrix().apply {
                postRotate(exif.rotationDegrees.toFloat())
                if (exif.isFlipped) postScale(-1f, 1f)
            }
        return if (matrix.isIdentity) decoded
        else
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                decoded.recycle()
            }
    }

    fun deleteTemporaryCapture(context: Context, uri: Uri) {
        if (uri.authority == "${context.packageName}.files")
            uri.lastPathSegment
                ?.takeIf { Regex("receipt-[a-zA-Z0-9-]+\\.jpg").matches(it) }
                ?.let { java.io.File(context.cacheDir, "exports/$it").delete() }
    }
}
