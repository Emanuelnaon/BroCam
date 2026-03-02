package com.example.brocam.core.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.brocam.core.streaming.FrameEncoder
import com.example.brocam.core.streaming.StreamConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrameReady: (ByteArray) -> Unit
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private val imageCapture: ImageCapture = ImageCapture.Builder().build()
    private val frameEncoder = FrameEncoder()
    private var lastFrameTime = 0L

    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // SOLUCIÓN 1: Uso de ResolutionSelector en lugar del Deprecated setTargetResolution
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        StreamConfig.RESOLUTION,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolutionSelector) // Aplicamos el nuevo selector
                .build()

            imageAnalysis.setAnalyzer(analysisExecutor, createFrameAnalyzer())

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("BroCam", "Error binding camera lifecycle", e)
            }
        }, ContextCompat.getMainExecutor(context))
        // Nota: Si ContextCompat sigue en rojo, ve a tu build.gradle (Module: app)
        // y asegúrate de tener: implementation("androidx.core:core-ktx:1.12.0") (o superior)
    }

    private fun createFrameAnalyzer(): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastFrameTime) > StreamConfig.FRAME_RATE_LIMIT_MS) {
                lastFrameTime = currentTime

                // SOLUCIÓN 2: toBitmap() ya no es anulable, se asigna directo sin el '?'
                val bitmap = imageProxy.toBitmap()
                frameEncoder.encodeFrame(bitmap)?.let {
                    onFrameReady(it)
                }
                bitmap.recycle() // Liberamos el bitmap procesado

                imageProxy.close() // Cerrar después de procesar
            } else {
                // CRÍTICO: Si no ha pasado el tiempo, también debes cerrarlo,
                // de lo contrario CameraX dejará de enviar nuevos frames.
                imageProxy.close()
            }
        }
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        if (!analysisExecutor.isShutdown) {
            analysisExecutor.shutdown()
        }
    }
}