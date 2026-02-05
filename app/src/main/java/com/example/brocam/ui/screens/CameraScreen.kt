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
import androidx.compose.runtime.* // <--- ESTO IMPORTA 'getValue' y 'setValue'
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

// IMPORTANTE: Estas líneas arreglan el error de 'getValue'
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

    // --- DEBUG: ESCUCHA DEL FLASH CON MENSAJES ---
    LaunchedEffect(isFlashOn) {
        if (cameraControl == null) {
            // Toast.makeText(context, "⚠️ Cámara no lista", Toast.LENGTH_SHORT).show()
        } else if (isFrontCamera) {
            Toast.makeText(context, "🤳 Frontal: Sin Flash", Toast.LENGTH_SHORT).show()
        } else {
            try {
                cameraControl?.enableTorch(isFlashOn)
                val estado = if (isFlashOn) "ON 💡" else "OFF 🌑"
                Toast.makeText(context, "Flash: $estado", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "❌ Error Flash: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- DISPARO ---
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
                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(context, "Error Foto: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    // --- CONFIGURACIÓN DE CÁMARA (Lente/Calidad/Giro) ---
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
                analyzer.setAnalyzer(analysisExecutor) { proxy ->
                    val currentTime = System.currentTimeMillis()
                    if (isStreaming && (currentTime - lastFrameTime > minDelay)) {
                        lastFrameTime = currentTime
                        val stream = ByteArrayOutputStream()
                        val quality = if (isHighQuality) 20 else 10
                        proxy.toBitmap().compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
                        viewModel.sendFrame(stream.toByteArray())
                        stream.close()
                    }
                    proxy.close()
                }

                // VINCULACIÓN
                val camera = myProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analyzer, imageCapture)

                // ASIGNAMOS EL CONTROL
                cameraControl = camera.cameraControl

                // Restaurar estado del flash si es trasera
                if (!isFrontCamera && isFlashOn) {
                    cameraControl?.enableTorch(true)
                }

            } catch (e: Exception) {
                // Error silencioso al cambiar
            }
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