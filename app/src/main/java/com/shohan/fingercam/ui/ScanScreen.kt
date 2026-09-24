package com.shohan.fingercam.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

private const val CROP_FRACTION = 0.35f

@Composable
fun ScanScreen(
    title: String,
    hint: String,
    busy: Boolean,
    banner: Banner?,
    onDismissBanner: () -> Unit,
    onCaptured: (Bitmap?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (granted) {
            CameraBox(busy = busy, onLiveFrame = onCaptured)
        } else {
            Text("স্ক্যান করতে ক্যামেরার অনুমতি লাগবে।", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) { Text("অনুমতি দিন") }
        }
        banner?.let { ResultBanner(it, onDismissBanner) }
        Text("লাইভ auto-scan চলছে — আঙুল বৃত্তের মধ্যে স্থির রাখুন।", style = MaterialTheme.typography.labelLarge)
        Text(hint, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onCancel, border = BorderStroke(1.dp, Color.Black), modifier = Modifier.fillMaxWidth()) { Text("বাতিল") }
    }
}

@Composable
private fun CameraBox(busy: Boolean, onLiveFrame: (Bitmap?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setImageQueueDepth(1)
            .build()
    }
    val worker = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            val bound = providerFuture.get()
            provider = bound
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            analysis.setAnalyzer(worker) { image ->
                val bitmap = try { decodeAndCrop(image) } catch (_: Throwable) { null } finally { image.close() }
                onLiveFrame(bitmap)
            }
            bound.unbindAll()
            camera = bound.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            analysis.clearAnalyzer()
            provider?.unbindAll()
            worker.shutdown()
        }
    }

    LaunchedEffect(camera, torchOn) { camera?.cameraControl?.enableTorch(torchOn) }

    Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Color.Black)) {
        AndroidView(
            factory = {
                previewView.apply {
                    setOnTouchListener { view, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            val point = meteringPointFactory.createPoint(event.x, event.y)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                            view.performClick()
                        }
                        true
                    }
                }
            },
            modifier = Modifier.matchParentSize()
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            val diameter = size.width * CROP_FRACTION
            drawCircle(Color.White, diameter / 2f, style = Stroke(width = 3.dp.toPx()))
        }
        if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { torchOn = !torchOn },
            border = BorderStroke(1.dp, Color.Black),
            modifier = Modifier.weight(1f)
        ) { Text(if (torchOn) "টর্চ বন্ধ" else "টর্চ চালু") }
        Text(
            text = "Frame বিশ্লেষণ হচ্ছে",
            modifier = Modifier.weight(2f).align(Alignment.CenterVertically),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Converts the YUV_420_888 frame to a correctly rotated, cropped bitmap. */
private fun decodeAndCrop(image: ImageProxy): Bitmap? {
    val width = image.width
    val height = image.height
    val nv21 = ByteArray(width * height + width * height / 2)
    copyPlane(image.planes[0], width, height, nv21, 0, 1)
    copyPlane(image.planes[2], width / 2, height / 2, nv21, width * height, 2)
    copyPlane(image.planes[1], width / 2, height / 2, nv21, width * height + 1, 2)

    val jpeg = ByteArrayOutputStream()
    if (!YuvImage(nv21, ImageFormat.NV21, width, height, null).compressToJpeg(Rect(0, 0, width, height), 92, jpeg)) return null
    val decoded = BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size()) ?: return null
    val rotated = if (image.imageInfo.rotationDegrees == 0) decoded else {
        val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { decoded.recycle() }
    }
    val side = (minOf(rotated.width, rotated.height) * CROP_FRACTION).toInt().coerceAtLeast(80)
    val left = (rotated.width - side) / 2
    val top = (rotated.height - side) / 2
    return Bitmap.createBitmap(rotated, left, top, side, side).also { if (it !== rotated) rotated.recycle() }
}

private fun copyPlane(
    plane: ImageProxy.PlaneProxy,
    planeWidth: Int,
    planeHeight: Int,
    output: ByteArray,
    offset: Int,
    outputPixelStride: Int
) {
    val buffer = plane.buffer.duplicate()
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val row = ByteArray((planeWidth - 1) * pixelStride + 1)
    var out = offset
    for (y in 0 until planeHeight) {
        buffer.position(y * rowStride)
        val length = minOf(row.size, buffer.remaining())
        buffer.get(row, 0, length)
        for (x in 0 until planeWidth) {
            val index = x * pixelStride
            if (index < length && out < output.size) {
                output[out] = row[index]
                out += outputPixelStride
            }
        }
    }
}
