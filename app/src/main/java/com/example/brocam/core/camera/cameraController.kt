package com.example.brocam.core.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
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

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(StreamConfig.RESOLUTION)
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
    }

    private fun createFrameAnalyzer(): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastFrameTime) > StreamConfig.FRAME_RATE_LIMIT_MS) {
                lastFrameTime = currentTime
                imageProxy.toBitmap()?.let { bitmap ->
                    frameEncoder.encodeFrame(bitmap)?.let {
                        onFrameReady(it)
                    }
                    bitmap.recycle()
                }
            }
            imageProxy.close()
        }
    }

    fun stopCamera() {
        cameraProvider?.unbindAll()
        if (!analysisExecutor.isShutdown) {
            analysisExecutor.shutdown()
        }
    }
}
