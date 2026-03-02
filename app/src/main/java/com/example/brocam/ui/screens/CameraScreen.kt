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
import androidx.compose.foundation.background
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
                }
                override fun onError(e: ImageCaptureException) {}
            })
        }
    }

    // --- ANALYZER (Optimizando para 2 Teléfonos) ---
    LaunchedEffect(isHighQuality, isFrontCamera, cameraProviderState.value, previewViewRef) {
        val myProvider = cameraProviderState.value
        val myPreviewView = previewViewRef

        if (myProvider != null && myPreviewView != null) {
            try {
                myProvider.unbindAll()

                // 🚀 DESBLOQUEO DE RESOLUCIÓN
                // SD = 640x480 (VGA). HD = 1280x720 (720p).
                val targetSize = if (isHighQuality) Size(1280, 720) else Size(640, 480)
                val minDelay = if (isHighQuality) 80L else 40L // Permitimos más FPS (hasta ~25 FPS)

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
                analyzer.setAnalyzer(analysisExecutor) { proxy ->
                    val currentTime = System.currentTimeMillis()
                    if (isStreaming && (currentTime - lastFrameTime > minDelay)) {
                        lastFrameTime = currentTime
                        val stream = ByteArrayOutputStream()

                        // Ajuste de compresión: 30% para SD, 20% para HD (para no saturar el WiFi)
                        val quality = if (isHighQuality) 70 else 60
                        proxy.toBitmap().compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)

                        // Empujamos al canal. El CONFLATED se encargará si la red va lenta.
                        viewModel.enqueueFrame(stream.toByteArray())
                        stream.close()
                    }
                    proxy.close()
                }

                // Vinculamos cámara, vista previa, análisis de imagen y captura de foto
                val camera = myProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analyzer, imageCapture)
                cameraControl = camera.cameraControl
                if (!isFrontCamera && isFlashOn) cameraControl?.enableTorch(true)
            } catch (e: Exception) { }
        }
    }

    BackHandler { viewModel.setRole(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // VISTA PREVIA LIMPIA A PANTALLA COMPLETA
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

        // INFO DE ESTADO DE PRODUCCIÓN
        Column(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            if (isStreaming) {
                Text("🔴 LENTE EN VIVO", color = Color.Red, style = MaterialTheme.typography.titleMedium)
                Text(if (isHighQuality) "Calidad: HD" else "Calidad: SD", color = Color.White, style = MaterialTheme.typography.labelMedium)
            } else {
                Text("ESPERANDO CONTROL...", color = Color.Yellow, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}