package com.example.brocam

import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val nearbyManager = remember { NearbyManager(context) }

    // El "motor" de las fotos reales
    val imageCapture = remember { ImageCapture.Builder().build() }
    var connectedControlId by remember { mutableStateOf<String?>(null) }

    // Función que se activa cuando el Control manda la orden
    fun takeHighResPhoto() {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val photoFile = File(context.getExternalFilesDir(null), "BroCam_$name.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(context, "Foto guardada en: ${photoFile.name}", Toast.LENGTH_LONG).show()
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e("BroCam", "Fallo al capturar: ${exc.message}")
                }
            }
        )
    }

    // Lógica de conexión Nearby
    LaunchedEffect(Unit) {
        nearbyManager.startAdvertising(object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                nearbyManager.acceptConnection(endpointId, object : PayloadCallback() {
                    override fun onPayloadReceived(id: String, payload: Payload) {
                        val command = String(payload.asBytes() ?: return)
                        if (command == "TAKE_PHOTO") {
                            takeHighResPhoto() // ¡DISPARA!
                        }
                    }
                    override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
                })
            }
            override fun onConnectionResult(id: String, res: ConnectionResolution) {
                if (res.status.isSuccess) connectedControlId = id
            }
            override fun onDisconnected(id: String) { connectedControlId = null }
        })
    }

    // Visor de Cámara
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                    val currentId = connectedControlId
                    if (currentId != null) {
                        val bitmap = imageProxy.toBitmap()
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, stream)
                        nearbyManager.sendData(currentId, Payload.fromBytes(stream.toByteArray()))
                    }
                    imageProxy.close()
                }

                try {
                    cameraProvider.unbindAll()
                    // Vinculamos todo el equipo: Visor, Analizador de video y Capturador de fotos
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageAnalysis, imageCapture
                    )
                } catch (e: Exception) { Log.e("BroCam", "Error de vínculo", e) }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}