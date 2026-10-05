package com.example.scanner

import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun CameraScannerView(
    modifier: Modifier = Modifier,
    isScanningActive: Boolean = true,
    isTorchEnabled: Boolean = false,
    useFrontCamera: Boolean = false,
    onBarcodeDetected: (rawValue: String, format: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    // Handle torch toggle
    LaunchedEffect(isTorchEnabled, cameraInstance) {
        cameraInstance?.cameraControl?.enableTorch(isTorchEnabled)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            @OptIn(ExperimentalGetImage::class)
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && isScanningActive) {
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                barcodeScanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        if (isScanningActive && barcodes.isNotEmpty()) {
                                            for (barcode in barcodes) {
                                                val raw = barcode.rawValue
                                                if (!raw.isNullOrBlank()) {
                                                    val formatName = getFormatName(barcode.format)
                                                    onBarcodeDetected(raw, formatName)
                                                    break
                                                }
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.w("CameraScannerView", "Barcode processing error", e)
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        cameraProvider.unbindAll()
                        cameraInstance = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (exc: Exception) {
                        Log.e("CameraScannerView", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            update = {
                // Rebind when camera selector changes
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = it.surfaceProvider
                        }
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(context))
            }
        )

        // Modern Scanner Reticle Overlay with Animated Scanning Line
        ScannerOverlay(isScanningActive = isScanningActive)
    }
}

@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    isScanningActive: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val boxWidth = width * 0.76f
        val boxHeight = boxWidth * 0.95f
        val left = (width - boxWidth) / 2f
        val top = (height - boxHeight) / 2.3f
        val cornerRadius = 24f

        val scanBoxRect = Rect(left, top, left + boxWidth, top + boxHeight)
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = scanBoxRect,
                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                )
            )
        }

        // Dim background outside target box
        clipPath(path, clipOp = ClipOp.Difference) {
            drawRect(color = Color(0x99000000))
        }

        // Draw Corner Brackets (High visibility scanner markers)
        val cornerLength = 48f
        val strokeWidth = 8f
        val reticleColor = if (isScanningActive) Color(0xFF00E676) else Color(0xFF888888)

        // Top Left
        drawLine(reticleColor, Offset(left - 2, top + cornerLength), Offset(left - 2, top), strokeWidth)
        drawLine(reticleColor, Offset(left - 2, top), Offset(left + cornerLength, top), strokeWidth)

        // Top Right
        drawLine(reticleColor, Offset(left + boxWidth + 2, top + cornerLength), Offset(left + boxWidth + 2, top), strokeWidth)
        drawLine(reticleColor, Offset(left + boxWidth + 2, top), Offset(left + boxWidth - cornerLength, top), strokeWidth)

        // Bottom Left
        drawLine(reticleColor, Offset(left - 2, top + boxHeight - cornerLength), Offset(left - 2, top + boxHeight), strokeWidth)
        drawLine(reticleColor, Offset(left - 2, top + boxHeight), Offset(left + cornerLength, top + boxHeight), strokeWidth)

        // Bottom Right
        drawLine(reticleColor, Offset(left + boxWidth + 2, top + boxHeight - cornerLength), Offset(left + boxWidth + 2, top + boxHeight), strokeWidth)
        drawLine(reticleColor, Offset(left + boxWidth + 2, top + boxHeight), Offset(left + boxWidth - cornerLength, top + boxHeight), strokeWidth)

        // Animated Laser Beam
        if (isScanningActive) {
            val laserY = top + (boxHeight * laserPosition)
            drawLine(
                color = Color(0xFF00E676),
                start = Offset(left + 8, laserY),
                end = Offset(left + boxWidth - 8, laserY),
                strokeWidth = 4f
            )
            // Glowing laser shadow
            drawLine(
                color = Color(0x5500E676),
                start = Offset(left + 8, laserY - 3),
                end = Offset(left + boxWidth - 8, laserY - 3),
                strokeWidth = 8f
            )
        }
    }
}

private fun getFormatName(format: Int): String {
    return when (format) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_ITF -> "ITF"
        else -> "Barcode"
    }
}
