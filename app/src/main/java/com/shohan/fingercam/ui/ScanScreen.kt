package com.shohan.fingercam.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import java.util.concurrent.Executors

/** Side of the analysed square, as a fraction of the photo's short side. */
private const val CROP_FRACTION = 0.35f

@Composable
fun ScanScreen(
    title: String,
    hint: String,
    busy: Boolean,
    banner: Banner?,
    onDismissBanner: () -> Unit,
    onCaptureStart: () -> Unit,
    onCaptured: (Bitmap?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (granted) {
            CameraBox(busy = busy, onCaptureStart = onCaptureStart, onCaptured = onCaptured)
        } else {
            Text("স্ক্যান করতে ক্যামেরার অনুমতি লাগবে।", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { launcher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("অনুমতি দিন") }
        }

        banner?.let { ResultBanner(banner = it, onDismiss = onDismissBanner) }

        Text(text = hint, style = MaterialTheme.typography.bodySmall)

        OutlinedButton(
            onClick = onCancel,
            border = BorderStroke(1.dp, Color.Black),
            modifier = Modifier.fillMaxWidth()
        ) { Text("বাতিল") }
    }
}

@Composable
private fun CameraBox(
    busy: Boolean,
    onCaptureStart: () -> Unit,
    onCaptured: (Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
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
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            bound.unbindAll()
            camera = bound.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            worker.shutdown()
        }
    }

    LaunchedEffect(camera, torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .background(Color.Black)
    ) {
        AndroidView(
            factory = {
                previewView.apply {
                    setOnTouchListener { view, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            val point = meteringPointFactory.createPoint(event.x, event.y)
                            camera?.cameraControl?.startFocusAndMetering(
                                FocusMeteringAction.Builder(point).build()
                            )
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
            drawCircle(
                color = Color.White,
                radius = diameter / 2f,
                style = Stroke(width = 3.dp.toPx())
            )
        }
        if (busy) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = { torchOn = !torchOn },
            border = BorderStroke(1.dp, Color.Black),
            modifier = Modifier.weight(1f)
        ) { Text(if (torchOn) "টর্চ বন্ধ" else "টর্চ চালু") }

        Button(
            onClick = {
                if (!busy) {
                    onCaptureStart()
                    imageCapture.takePicture(
                        worker,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = try {
                                    decodeCenter(image)
                                } catch (e: Exception) {
                                    null
                                } finally {
                                    image.close()
                                }
                                onCaptured(bitmap)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                onCaptured(null)
                            }
                        }
                    )
                }
            },
            enabled = !busy && camera != null,
            modifier = Modifier.weight(2f)
        ) { Text("ছবি তুলুন") }
    }
}

/** Decodes only the centre square of the JPEG (full resolution) to save memory. */
@Suppress("DEPRECATION")
private fun decodeCenter(image: ImageProxy): Bitmap? {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false) ?: return null
    return try {
        val side = (minOf(decoder.width, decoder.height) * CROP_FRACTION).toInt()
        val left = (decoder.width - side) / 2
        val top = (decoder.height - side) / 2
        decoder.decodeRegion(Rect(left, top, left + side, top + side), BitmapFactory.Options())
    } finally {
        decoder.recycle()
    }
}
