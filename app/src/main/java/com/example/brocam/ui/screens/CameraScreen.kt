package com.example.brocam.ui.screens

import android.content.ContentValues
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.brocam.ui.viewmodel.BroCamViewModel
import java.util.concurrent.Executors
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.isActive

@Composable
fun CameraScreen(viewModel: BroCamViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isStreaming by viewModel.isStreaming.collectAsState()
    val isHighQuality by viewModel.isHighQuality.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val isSosMode by viewModel.isSosMode.collectAsState()

    val remotePointer by viewModel.remotePointer.collectAsState()
    val annotatedImage by viewModel.annotatedImage.collectAsState()
    val remoteLiveLine by viewModel.remoteLiveLine.collectAsState()
    val remoteZoom by viewModel.remoteZoom.collectAsState()
    val remoteExposure by viewModel.remoteExposure.collectAsState()

    var isBatterySaverMode by remember { mutableStateOf(false) }

    BackHandler { viewModel.setRole(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var cameraInfo: CameraInfo? by remember { mutableStateOf(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(isFlashOn) { if (!isFrontCamera) try { cameraControl?.enableTorch(isFlashOn) } catch (e: Exception) {} }
    LaunchedEffect(remoteZoom) { try { cameraControl?.setZoomRatio(remoteZoom) } catch (e: Exception) {} }
    LaunchedEffect(remoteExposure) {
        try {
            cameraInfo?.exposureState?.let { state ->
                if (state.isExposureCompensationSupported) {
                    val range = state.exposureCompensationRange
                    val index = range.lower + (remoteExposure * (range.upper - range.lower)).toInt()
                    cameraControl?.setExposureCompensationIndex(index)
                }
            }
        } catch (e: Exception) {}
    }

    LaunchedEffect(isSosMode, isFrontCamera) {
        if (isSosMode && !isFrontCamera && cameraControl != null) {
            while (isActive) {
                try { cameraControl?.enableTorch(true) } catch (e: Exception) {}
                kotlinx.coroutines.delay(150)
                try { cameraControl?.enableTorch(false) } catch (e: Exception) {}
                kotlinx.coroutines.delay(150)
            }
        } else { try { cameraControl?.enableTorch(isFlashOn) } catch (e: Exception) {} }
    }

    LaunchedEffect(remotePointer) {
        val currentPointer = remotePointer
        if (currentPointer != null && previewViewRef != null && cameraControl != null) {
            try {
                val factory = previewViewRef!!.meteringPointFactory
                val point = factory.createPoint(currentPointer.first * previewViewRef!!.width, currentPointer.second * previewViewRef!!.height)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                cameraControl!!.startFocusAndMetering(action)
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shutterEvent.collect {
            viewModel.fetchCurrentLocation { gpsInfo ->
                imageCapture.takePicture(ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bitmap = image.toBitmap()
                            val matrix = android.graphics.Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                            val rotatedBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            val finalBitmap = viewModel.drawMetadataOnBitmap(rotatedBitmap, gpsInfo)
                            saveBitmapToGallery(context, finalBitmap)
                            image.close()
                            viewModel.sendCommand("PHOTO_OK")
                            Toast.makeText(context, "📸 Evidencia Guardada", Toast.LENGTH_SHORT).show()
                        }
                        override fun onError(e: ImageCaptureException) {}
                    }
                )
            }
        }
    }

    LaunchedEffect(isHighQuality, isFrontCamera, cameraProviderState.value, previewViewRef) {
        val myProvider = cameraProviderState.value
        if (myProvider != null && previewViewRef != null) {
            try {
                myProvider.unbindAll()
                val targetSize = if (isHighQuality) Size(1280, 960) else Size(640, 480)
                val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewViewRef!!.surfaceProvider) }
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(targetSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .build()

                analyzer.setAnalyzer(analysisExecutor) { proxy ->
                    if (isStreaming) {
                        val width = proxy.width
                        val height = proxy.height
                        val nv21 = ByteArray(width * height * 3 / 2)
                        val yBuffer = proxy.planes[0].buffer
                        val uBuffer = proxy.planes[1].buffer
                        val vBuffer = proxy.planes[2].buffer
                        val ySize = yBuffer.remaining()
                        val uSize = uBuffer.remaining()
                        val vSize = vBuffer.remaining()
                        yBuffer.get(nv21, 0, ySize)
                        if (proxy.planes[1].pixelStride == 2) {
                            if (ySize + uSize <= nv21.size) uBuffer.get(nv21, ySize, uSize)
                        } else {
                            if (ySize + uSize + vSize <= nv21.size) { uBuffer.get(nv21, ySize, uSize); vBuffer.get(nv21, ySize + uSize, vSize) }
                        }
                        viewModel.feedEncoder(nv21, proxy.imageInfo.timestamp / 1000, width, height)
                    }
                    proxy.close()
                }

                val camera = myProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analyzer, imageCapture)
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                if (!isFrontCamera && isFlashOn) cameraControl?.enableTorch(true)
            } catch (e: Exception) {}
        }
    }

    val buttonBg = Color.Black.copy(alpha = 0.5f)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val pv = PreviewView(ctx).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    // Usamos FIT_CENTER para que no se recorte nada y se vea la vista completa del sensor
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                previewViewRef = pv
                ProcessCameraProvider.getInstance(ctx).addListener({ cameraProviderState.value = ProcessCameraProvider.getInstance(ctx).get() }, ContextCompat.getMainExecutor(ctx))
                pv
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isBatterySaverMode) {

            // Top Row: [ CONFIG ] ------ [ SD/HD ] ------ [ 🔴 EN VIVO ]
            Row(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 40.dp, start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // Config (Modo Ahorro / Pantalla apagada)
                Box(modifier = Modifier.background(buttonBg, RoundedCornerShape(12.dp)).clickable { isBatterySaverMode = true }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("🌙", color = Color.White, fontSize = 16.sp)
                }

                // Calidad
                Box(modifier = Modifier.background(if (isHighQuality) Color(0xFF06B6D4) else buttonBg, RoundedCornerShape(12.dp)).clickable { viewModel.toggleQuality() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(if (isHighQuality) "HD" else "SD", color = if (isHighQuality) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                }

                if (isStreaming) {
                    Box(modifier = Modifier.background(Color.Red.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("EN VIVO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    Box(modifier = Modifier.background(Color.DarkGray.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("OFFLINE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Bloque Inferior
            Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp, start = 16.dp, end = 16.dp)) {

                // Row A: [ ❌ SALIR ] ------ [ 📁 GALERÍA ]
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { viewModel.setRole(null) }, modifier = Modifier.background(buttonBg, CircleShape)) {
                        Icon(Icons.Default.Close, contentDescription = "Salir", tint = Color.White)
                    }
                    Box(modifier = Modifier.size(48.dp).background(buttonBg, CircleShape).clickable {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply { type = "image/*"; flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        try { context.startActivity(intent) } catch (e: Exception) {}
                    }, contentAlignment = Alignment.Center) {
                        Text("📁", fontSize = 18.sp)
                    }
                }

                // Row B: [ ⚡ Flash ] [ 🔄 Cambio ] [ ⚪ Captura ] [ 🚨 SOS ] [ 🎤 Hablar ]
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

                    // Flash
                    Box(modifier = Modifier.size(56.dp).background(if (isFlashOn) Color.Yellow else buttonBg, CircleShape).clickable { viewModel.toggleFlash() }, contentAlignment = Alignment.Center) {
                        Text("⚡", fontSize = 24.sp)
                        if (!isFlashOn) { Canvas(modifier = Modifier.size(24.dp)) { drawLine(color = Color.Red, start = Offset(size.width, 0f), end = Offset(0f, size.height), strokeWidth = 4f) } }
                    }

                    // Cambio de cámara
                    IconButton(onClick = { viewModel.toggleCamera() }, modifier = Modifier.background(buttonBg, CircleShape).size(56.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Voltear", tint = Color.White)
                    }

                    // Botón Central (Capturar Local)
                    Box(modifier = Modifier.size(72.dp).background(Color.White, CircleShape).border(4.dp, Color.LightGray, CircleShape).clickable { viewModel.takeLocalPhoto() })

                    // SOS (El Lente no necesita slider de exposición, usamos SOS)
                    Box(modifier = Modifier.size(56.dp).background(if (isSosMode) Color.Red else buttonBg, CircleShape).clickable { viewModel.toggleSos() }, contentAlignment = Alignment.Center) {
                        Text("🚨", fontSize = 24.sp)
                    }

                    // Micrófono
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    LaunchedEffect(isPressed) { if (isPressed) viewModel.startPushToTalk() else viewModel.stopPushToTalk() }
                    Box(modifier = Modifier.size(56.dp).background(if (isPressed) Color.Green else buttonBg, CircleShape).clickable(interactionSource = interactionSource, indication = null) {}, contentAlignment = Alignment.Center) {
                        Text("🎤", fontSize = 24.sp)
                    }
                }
            }
        }

        if (remotePointer != null || remoteLiveLine.isNotEmpty()) {
            val cameraRatio = if (isHighQuality) 9f / 16f else 3f / 4f
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.aspectRatio(cameraRatio)) {
                    if (remotePointer != null) {
                        drawCircle(color = Color.Red, radius = 24f, center = Offset(remotePointer!!.first * size.width, remotePointer!!.second * size.height))
                        drawCircle(color = Color.White, radius = 28f, center = Offset(remotePointer!!.first * size.width, remotePointer!!.second * size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                    }
                    if (remoteLiveLine.isNotEmpty()) {
                        for (i in 0 until remoteLiveLine.size - 1) drawLine(color = Color.Red, start = Offset(remoteLiveLine[i].first * size.width, remoteLiveLine[i].second * size.height), end = Offset(remoteLiveLine[i+1].first * size.width, remoteLiveLine[i+1].second * size.height), strokeWidth = 10f)
                    }
                }
            }
        }

        if (isBatterySaverMode) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { isBatterySaverMode = false }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌙", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("PANTALLA APAGADA", color = Color.Gray, style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        if (annotatedImage != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                val imageRatio = annotatedImage!!.width.toFloat() / annotatedImage!!.height.toFloat()
                Image(bitmap = annotatedImage!!.asImageBitmap(), contentDescription = "Indicación", modifier = Modifier.fillMaxWidth().aspectRatio(imageRatio))
                Button(onClick = { viewModel.clearAnnotatedImage() }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("CERRAR IMAGEN RECIBIDA", fontWeight = FontWeight.Bold)
                    Text("CERRAR IMAGEN RECIBIDA", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun saveBitmapToGallery(context: android.content.Context, bitmap: android.graphics.Bitmap) {
    val filename = "BroCam_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BroCam")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let { context.contentResolver.openOutputStream(it).use { stream -> bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, stream!!) } }
}