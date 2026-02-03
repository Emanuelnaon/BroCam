package com.example.brocam

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import com.google.android.gms.nearby.connection.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun CameraScreen(viewModel: BroCamViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val nearbyManager = viewModel.nearbyManager
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val cameraProviderRef = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    var countdown by remember { mutableIntStateOf(0) }
    var connectedId by remember { mutableStateOf<String?>(null) }
    var isStreaming by remember { mutableStateOf(false) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    BackHandler { viewModel.setRole(null) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdownNow()
            cameraProviderRef.value?.unbindAll()
            viewModel.disconnect()
        }
    }

    // --- MANEJO DE CONEXIÓN NEARBY ---
    LaunchedEffect(Unit) {
        nearbyManager.startAdvertising(object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                nearbyManager.acceptConnection(id, object : PayloadCallback() {
                    override fun onPayloadReceived(endpointId: String, payload: Payload) {
                        payload.asBytes()?.let {
                            when (String(it)) {
                                "TAKE_PHOTO" -> { /* Lógica para tomar foto */ }
                                "START_STREAM" -> isStreaming = true
                                "STOP_STREAM" -> isStreaming = false
                            }
                        }
                    }

                    override fun onPayloadTransferUpdate(p0: String, p1: PayloadTransferUpdate) {}
                })
            }

            override fun onConnectionResult(id: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    connectedId = id
                }
            }

            override fun onDisconnected(id: String) {
                connectedId = null
                isStreaming = false
            }
        })
    }

    var lastFrameTime by remember { mutableLongStateOf(0L) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val pv = PreviewView(ctx)
                cameraProviderFuture.addListener({
                    val lp = cameraProviderFuture.get()
                    cameraProviderRef.value = lp

                    val pre = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                    val ana = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(480, 640))
                        .build()

                    ana.setAnalyzer(analysisExecutor) { proxy ->
                        val currentTime = System.currentTimeMillis()
                        val id = connectedId

                        // LIMITADOR: Solo enviamos un frame cada 100ms (máximo 10 fotos por segundo)
                        if (isStreaming && id != null && (currentTime - lastFrameTime) > 100) {
                            lastFrameTime = currentTime

                            try {
                                val bitmap = proxy.toBitmap()
                                val stream = ByteArrayOutputStream()

                                // Bajamos la calidad al 10%. Para un visor de 6 pulgadas es perfecto.
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 10, stream)

                                val data = stream.toByteArray()
                                nearbyManager.sendData(id, Payload.fromBytes(data))

                                // Limpieza inmediata de memoria
                                bitmap.recycle()
                                stream.close()
                            } catch (e: Exception) {
                                Log.e("BroCam", "Error en streaming: ${e.message}")
                            }
                        }
                        proxy.close() // Cerramos siempre el frame de la cámara
                    }

                    try {
                        lp.unbindAll()
                        lp.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, pre, ana, imageCapture)
                    } catch (e: Exception) { Log.e("BroCam", "Error bind", e) }
                }, ContextCompat.getMainExecutor(ctx))
                pv
            },
            modifier = Modifier.fillMaxSize()
        )

        // Indicador de Streaming en el Lente
        if (isStreaming) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Un circulo rojo pequeño
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = Color.Red)
                }
                Spacer(Modifier.width(8.dp))
                Text("EN VIVO", color = Color.Red, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}