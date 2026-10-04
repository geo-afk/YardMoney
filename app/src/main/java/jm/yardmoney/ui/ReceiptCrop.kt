package jm.yardmoney.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import java.io.File
import jm.yardmoney.AppModel
import jm.yardmoney.receipts.ReceiptImages
import jm.yardmoney.receipts.ReceiptPreprocessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ReceiptCrop(model: AppModel, uri: Uri, close: () -> Unit, done: (Uri) -> Unit) {
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var qualityWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var corrected by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var quarter by rememberSaveable { mutableIntStateOf(0) }
    var left by rememberSaveable { mutableFloatStateOf(0f) }
    var right by rememberSaveable { mutableFloatStateOf(1f) }
    var top by rememberSaveable { mutableFloatStateOf(0f) }
    var bottom by rememberSaveable { mutableFloatStateOf(1f) }
    val busy by model.busy.collectAsState()
    LaunchedEffect(uri) {
        try {
            bitmap =
                withContext(Dispatchers.IO) {
                    ReceiptImages.decode(ReceiptImages.read(model.app, uri)).also {
                        qualityWarnings = ReceiptPreprocessor.quality(it).warnings
                    }
                }
        } catch (e: Exception) {
            failure = e.message
        }
    }
    val owned = bitmap
    DisposableEffect(owned) { onDispose { owned?.recycle() } }
    StagedEditSheet(
        "Prepare your receipt",
        "Crop and rotate",
        busy,
        quarter != 0 || left != 0f || right != 1f || top != 0f || bottom != 1f,
        {
            ReceiptImages.deleteTemporaryCapture(model.app, uri)
            close()
        },
    ) {
        val requestDismiss = LocalEditDismiss.current
        Text("Prepare your receipt", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Rotate if needed and trim the background. Keep the merchant, date, item lines and total inside the frame."
        )
        val image = bitmap
        if (image != null)
            Canvas(Modifier.fillMaxWidth().height(320.dp)) {
                val odd = quarter % 2 == 1
                val w = if (odd) image.height else image.width
                val h = if (odd) image.width else image.height
                val scale = minOf(size.width / w, size.height / h)
                val width = w * scale
                val height = h * scale
                val x = (size.width - width) / 2
                val y = (size.height - height) / 2
                rotate(quarter * 90f) {
                    drawImage(
                        image.asImageBitmap(),
                        dstOffset =
                            IntOffset(
                                ((size.width - image.width * scale) / 2).toInt(),
                                ((size.height - image.height * scale) / 2).toInt(),
                            ),
                        dstSize =
                            IntSize(
                                (image.width * scale).toInt().coerceAtLeast(1),
                                (image.height * scale).toInt().coerceAtLeast(1),
                            ),
                    )
                }
                val shade = Color.Black.copy(alpha = .55f)
                drawRect(shade, Offset(x, y), Size(width, top * height))
                drawRect(
                    shade,
                    Offset(x, y + bottom * height),
                    Size(width, (1 - bottom) * height),
                )
                drawRect(
                    shade,
                    Offset(x, y + top * height),
                    Size(left * width, (bottom - top) * height),
                )
                drawRect(
                    shade,
                    Offset(x + right * width, y + top * height),
                    Size((1 - right) * width, (bottom - top) * height),
                )
                drawRect(
                    Color.White,
                    Offset(x + left * width, y + top * height),
                    Size((right - left) * width, (bottom - top) * height),
                    style = Stroke(3f),
                )
            }
        else if (failure == null) CircularProgressIndicator()
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        qualityWarnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            if (corrected)
                "Detected paper was straightened. Check that no lines were cut off; restore the original if needed."
            else "You can try automatic paper detection, then adjust the edges manually."
        )
        OutlinedButton(
            enabled = !busy && image != null,
            onClick = {
                var replacement: Bitmap? = null
                model.act({
                    replacement?.let {
                        bitmap = it
                        corrected = true
                        left = 0f
                        right = 1f
                        top = 0f
                        bottom = 1f
                        quarter = 0
                    }
                }) {
                    val candidate = ReceiptPreprocessor.boundary(image!!)
                    require(candidate != null) {
                        "Receipt boundary is uncertain. Keep the full image or crop manually."
                    }
                    replacement = ReceiptPreprocessor.straighten(image, candidate)
                }
            },
            shape = MaterialTheme.shapes.small,
        ) {
            Text("Detect edges & correct perspective")
        }
        if (corrected)
            TextButton(
                enabled = !busy,
                onClick = {
                    var replacement: Bitmap? = null
                    model.act({
                        replacement?.let {
                            bitmap = it
                            corrected = false
                            left = 0f
                            right = 1f
                            top = 0f
                            bottom = 1f
                            quarter = 0
                        }
                    }) {
                        replacement = ReceiptImages.decode(ReceiptImages.read(model.app, uri))
                    }
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Restore original photo")
            }
        OutlinedButton(
            enabled = !busy && image != null,
            onClick = {
                quarter = (quarter + 1) % 4
                left = 0f
                right = 1f
                top = 0f
                bottom = 1f
            },
            shape = MaterialTheme.shapes.small,
        ) {
            Text("Rotate 90°")
        }
        Text("Top edge")
        Slider(
            top,
            { top = it },
            enabled = !busy && image != null,
            valueRange = 0f..(bottom - .05f),
        )
        Text("Bottom edge")
        Slider(
            bottom,
            { bottom = it },
            enabled = !busy && image != null,
            valueRange = (top + .05f)..1f,
        )
        Text("Left edge")
        Slider(
            left,
            { left = it },
            enabled = !busy && image != null,
            valueRange = 0f..(right - .05f),
        )
        Text("Right edge")
        Slider(
            right,
            { right = it },
            enabled = !busy && image != null,
            valueRange = (left + .05f)..1f,
        )
        Button(
            enabled = !busy && image != null,
            onClick = {
                var output: Uri? = null
                model.act({ output?.let(done) }) {
                    val source = image!!
                    val rotated =
                        if (quarter == 0) source
                        else
                            Bitmap.createBitmap(
                                source,
                                0,
                                0,
                                source.width,
                                source.height,
                                Matrix().apply { postRotate(quarter * 90f) },
                                true,
                            )
                    val x = (left * rotated.width).toInt()
                    val y = (top * rotated.height).toInt()
                    val w = ((right - left) * rotated.width).toInt().coerceIn(1, rotated.width - x)
                    val h =
                        ((bottom - top) * rotated.height).toInt().coerceIn(1, rotated.height - y)
                    val cropped = Bitmap.createBitmap(rotated, x, y, w, h)
                    try {
                        val directory = File(model.app.cacheDir, "exports").apply { mkdirs() }
                        val file = File.createTempFile("receipt-", ".jpg", directory)
                        file.outputStream().use {
                            check(cropped.compress(Bitmap.CompressFormat.JPEG, 92, it))
                        }
                        output =
                            FileProvider.getUriForFile(
                                model.app,
                                "${model.app.packageName}.files",
                                file,
                            )
                        ReceiptImages.deleteTemporaryCapture(model.app, uri)
                    } finally {
                        if (cropped !== source) cropped.recycle()
                        if (rotated !== source && rotated !== cropped) rotated.recycle()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(if (busy) "Preparing…" else "Recognise receipt offline")
        }
        TextButton(
            enabled = !busy,
            onClick = requestDismiss,
            shape = MaterialTheme.shapes.small,
        ) {
            Text("Cancel")
        }
    }
}
