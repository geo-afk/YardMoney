package jm.yardmoney.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors

@Composable
internal fun ReceiptCamera(onDismiss: () -> Unit, onPhoto: (Uri) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    var taking by remember { mutableStateOf(false) }
    var lighting by remember { mutableStateOf<String?>(null) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    var activeCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var flash by remember { mutableStateOf(false) }
    val request =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permission = it
        }
    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val view = remember { PreviewView(context) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    LaunchedEffect(Unit) { if (!permission) request.launch(Manifest.permission.CAMERA) }
    DisposableEffect(permission) {
        var disposed = false
        if (permission) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    if (!disposed)
                        try {
                            val p = future.get()
                            provider = p
                            val preview =
                                Preview.Builder().build().also {
                                    it.surfaceProvider = view.surfaceProvider
                                }
                            val analysis =
                                ImageAnalysis.Builder()
                                    .setBackpressureStrategy(
                                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                    )
                                    .build()
                            var previous = 0L
                            analysis.setAnalyzer(analyzerExecutor) { frame ->
                                try {
                                    if (System.currentTimeMillis() - previous > 700) {
                                        previous = System.currentTimeMillis()
                                        val plane = frame.planes[0]
                                        val buffer = plane.buffer
                                        var total = 0L
                                        var n = 0
                                        for (y in 0 until frame.height step 12) for (x in
                                            0 until frame.width step 12) {
                                            val at = y * plane.rowStride + x * plane.pixelStride
                                            if (at < buffer.limit()) {
                                                total += buffer.get(at).toInt() and 255
                                                n++
                                            }
                                        }
                                        val mean = if (n > 0) total / n else 128
                                        ContextCompat.getMainExecutor(context).execute {
                                            lighting =
                                                if (mean < 65)
                                                    "Low light: move to even lighting or try flash."
                                                else if (mean > 240)
                                                    "Check for glare; tilt the light away from the paper."
                                                else null
                                        }
                                    }
                                } finally {
                                    frame.close()
                                }
                            }
                            activeCamera =
                                p.bindToLifecycle(
                                    owner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    capture,
                                    analysis,
                                )
                            ready = true
                        } catch (e: Exception) {
                            error = "Camera unavailable. Use photo import or a manual expense."
                        }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
        onDispose {
            disposed = true
            provider?.unbind(capture)
            provider?.unbindAll()
            activeCamera = null
        }
    }
    DisposableEffect(Unit) { onDispose { analyzerExecutor.shutdown() } }
    Dialog(
        onDismissRequest = { if (!taking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Scan receipt", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Keep the full receipt in frame, flatten folds and avoid glare. Check small text is sharp."
                )
                if (permission)
                    Box(
                        Modifier.fillMaxWidth().height(360.dp).pointerInput(activeCamera) {
                            detectTapGestures { at ->
                                activeCamera
                                    ?.cameraControl
                                    ?.startFocusAndMetering(
                                        FocusMeteringAction.Builder(
                                                view.meteringPointFactory.createPoint(at.x, at.y)
                                            )
                                            .build()
                                    )
                            }
                        }
                    ) {
                        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
                        Canvas(Modifier.fillMaxSize()) {
                            val offset = Offset(size.width * .12f, size.height * .04f)
                            val rect = Size(size.width * .76f, size.height * .92f)
                            drawRect(
                                Color.Black.copy(alpha = .7f),
                                offset,
                                rect,
                                style = Stroke(8f),
                            )
                            drawRect(Color.White, offset, rect, style = Stroke(3f))
                        }
                    }
                else Text("Camera permission was declined. You can import a photo instead.")
                Text(
                    "Align the paper inside the guide. Tap text to focus; keep both the first line and total visible. For very long receipts, use a sharp imported image.",
                    style = MaterialTheme.typography.bodySmall,
                )
                lighting?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = flash,
                        onCheckedChange = {
                            flash = it
                            capture.flashMode =
                                if (it) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Flash")
                }
                Button(
                    enabled = ready && !taking,
                    onClick = {
                        taking = true
                        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                        val file = File.createTempFile("receipt-", ".jpg", dir)
                        capture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                    taking = false
                                    onPhoto(
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.files",
                                            file,
                                        )
                                    )
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    taking = false
                                    error = "Capture failed. Try again."
                                    file.delete()
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(if (taking) "Capturing…" else "Capture for review")
                }
                TextButton(
                    enabled = !taking,
                    onClick = onDismiss,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
