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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.brocam.ui.viewmodel.BroCamViewModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun CameraScreen(viewModel: BroCamViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isStreaming by viewModel.isStreaming.collectAsState()
    val isHighQuality by viewModel.isHighQuality.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // --- EFECTOS (Flash, Disparo) --- (IGUAL QUE ANTES)
    LaunchedEffect(isFlashOn) {
        if (!isFrontCamera) try { cameraControl?.enableTorch(isFlashOn) } catch (e: Exception) { }
    }

    LaunchedEffect(Unit) {
        viewModel.shutterEvent.collect {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "BroCam_${System.currentTimeMillis()}.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BroCam")
            }
            val options = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
            ).build()

            imageCapture.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                    Toast.makeText(context, "📸 Foto Guardada", Toast.LENGTH_SHORT).show()
                    // Aquí podríamos enviar la miniatura
                }
                override fun onError(e: ImageCaptureException) {}
            })
        }
    }

    // --- CONFIGURACIÓN DE CÁMARA ---
    LaunchedEffect(isHighQuality, isFrontCamera, cameraProviderState.value, previewViewRef) {
        val myProvider = cameraProviderState.value
        val myPreviewView = previewViewRef

        if (myProvider != null && myPreviewView != null) {
            try {
                myProvider.unbindAll()

                val targetSize = if (isHighQuality) Size(640, 480) else Size(320, 240)
                val minDelay = if (isHighQuality) 100L else 50L

                val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(targetSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()

                val preview = Preview.Builder().build().also { it.setSurfaceProvider(myPreviewView.surfaceProvider) }
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(resolutionSelector)
                    .build()

                var lastFrameTime = 0L

                // --- OPTIMIZACIÓN DEL ANALYZER ---
                analyzer.setAnalyzer(analysisExecutor) { proxy ->
                    val currentTime = System.currentTimeMillis()

                    if (isStreaming && (currentTime - lastFrameTime > minDelay)) {
                        lastFrameTime = currentTime

                        // 1. Comprimir en hilo de fondo (Analyzer Thread)
                        val stream = ByteArrayOutputStream()
                        val quality = if (isHighQuality) 20 else 10
                        proxy.toBitmap().compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)

                        // 2. Encolar en el Canal (No bloqueante)
                        // Si el canal está lleno, DROP_OLDEST elimina el frame viejo automáticamente.
                        viewModel.enqueueFrame(stream.toByteArray())

                        stream.close()
                    }
                    proxy.close()
                }

                val camera = myProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analyzer, imageCapture)
                cameraControl = camera.cameraControl

                if (!isFrontCamera && isFlashOn) cameraControl?.enableTorch(true)

            } catch (e: Exception) { }
        }
    }

    BackHandler { viewModel.setRole(null) }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdownNow() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val pv = PreviewView(ctx)
                previewViewRef = pv
                ProcessCameraProvider.getInstance(ctx).addListener({
                    cameraProviderState.value = ProcessCameraProvider.getInstance(ctx).get()
                }, ContextCompat.getMainExecutor(ctx))
                pv
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            if (isStreaming) {
                Text("🔴 EN VIVO", color = Color.Red, style = MaterialTheme.typography.titleMedium)
                Text(if (isHighQuality) "HD" else "RÁPIDO", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Text(if (isFrontCamera) "SELFIE" else "TRASERA", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}