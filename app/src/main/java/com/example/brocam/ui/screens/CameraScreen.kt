package com.example.brocam.ui.screens
/*CameraScreen*/
import android.content.ContentValues
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    val remotePointer by viewModel.remotePointer.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isHighQuality by viewModel.isHighQuality.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    var isBatterySaverMode by remember { mutableStateOf(false) }

    BackHandler { viewModel.setRole(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    LaunchedEffect(isFlashOn) {
        if (!isFrontCamera) try {
            cameraControl?.enableTorch(isFlashOn)
        } catch (e: Exception) {
        }
    }
    // 🟠 NUEVO: AUTO-ENFOQUE REMOTO (Tap-to-Focus)
    LaunchedEffect(remotePointer) {
        val currentPointer = remotePointer
        val myPreviewView = previewViewRef
        val myCameraControl = cameraControl

        // Solo enfocamos si tenemos la posición, la vista de la cámara y los controles listos
        if (currentPointer != null && myPreviewView != null && myCameraControl != null) {
            try {
                // 1. Traducimos el porcentaje a las coordenadas de la cámara
                val factory = myPreviewView.meteringPointFactory
                val pointX = currentPointer.first * myPreviewView.width
                val pointY = currentPointer.second * myPreviewView.height

                // 2. Creamos el punto de enfoque
                val point = factory.createPoint(pointX, pointY)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS) // Vuelve al enfoque automático después de 3 seg
                    .build()

                // 3. ¡Disparamos el motor del lente físico!
                myCameraControl.startFocusAndMetering(action)

            } catch (e: Exception) {
                android.util.Log.e("BroCam_Focus", "Error enfocando: ${e.message}")
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shutterEvent.collect {
            val contentValues = ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    "BroCam_${System.currentTimeMillis()}.jpg"
                )
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BroCam")
            }
            val options = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
            ).build()

            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                        Toast.makeText(context, "📸 Foto Full HD Guardada", Toast.LENGTH_SHORT)
                            .show()
                        // NUEVO: Le mandamos un mensaje de confirmación al operador remoto
                        viewModel.sendCommand("PHOTO_OK")
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

                val targetSize = if (isHighQuality) Size(1280, 720) else Size(640, 480)
                val minDelay = if (isHighQuality) 80L else 40L

                val cameraSelector =
                    if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            targetSize,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                // Quitamos el setTargetRotation manual. Dejaremos que PreviewView haga su magia.
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(myPreviewView.surfaceProvider) }

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
                        val quality = if (isHighQuality) 70 else 60

                        // 🛠️ FIX 1 (PARA EL CONTROL): Enderezar la imagen antes de enviarla
                        val bitmap = proxy.toBitmap()
                        val rotationDegrees = proxy.imageInfo.rotationDegrees.toFloat()

                        val finalBitmap = if (rotationDegrees != 0f) {
                            val matrix =
                                android.graphics.Matrix().apply { postRotate(rotationDegrees) }
                            android.graphics.Bitmap.createBitmap(
                                bitmap,
                                0,
                                0,
                                bitmap.width,
                                bitmap.height,
                                matrix,
                                true
                            )
                        } else {
                            bitmap
                        }

                        finalBitmap.compress(
                            android.graphics.Bitmap.CompressFormat.JPEG,
                            quality,
                            stream
                        )
                        viewModel.enqueueFrame(stream.toByteArray())
                        stream.close()
                    }
                    proxy.close()
                }

                val camera = myProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    analyzer,
                    imageCapture
                )
                cameraControl = camera.cameraControl
                if (!isFrontCamera && isFlashOn) cameraControl?.enableTorch(true)
            } catch (e: Exception) {
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // 1. LA CÁMARA SIEMPRE EXISTE (Capa Base)
        // Al no estar dentro de un 'if', nunca se desconecta del hardware.
        AndroidView(
            factory = { ctx ->
                val pv = PreviewView(ctx).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                previewViewRef = pv
                ProcessCameraProvider.getInstance(ctx).addListener({
                    cameraProviderState.value = ProcessCameraProvider.getInstance(ctx).get()
                }, ContextCompat.getMainExecutor(ctx))
                pv
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. UI NORMAL (Capa Intermedia - Solo se muestra si NO hay ahorro)
        if (!isBatterySaverMode) {
            // BOTÓN DE AHORRO DE ENERGÍA
            Button(
                onClick = { isBatterySaverMode = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)
                )
            ) {
                Text("Modo Ahorro (Apagar Pantalla)", color = Color.White)
            }

            // INFO DE ESTADO
            Column(
                Modifier.align(Alignment.TopEnd).padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (isStreaming) {
                    Text("🔴 LENTE EN VIVO", color = Color.Red, style = MaterialTheme.typography.titleMedium)
                    Text(if (isHighQuality) "Calidad: HD" else "Calidad: SD", color = Color.White, style = MaterialTheme.typography.labelMedium)
                } else {
                    Text("ESPERANDO CONTROL...", color = Color.Yellow, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // 🔴 LA MIRA LÁSER AR (Capa superior de dibujo)
        if (remotePointer != null) {
            // Calculamos la proporción del video dependiendo la calidad (en modo vertical)
            val cameraRatio = if (isHighQuality) 9f / 16f else 3f / 4f

            // Centramos el lienzo para que coincida exactamente con el FIT_CENTER de la cámara
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.aspectRatio(cameraRatio)) {
                    val pointX = remotePointer!!.first * size.width
                    val pointY = remotePointer!!.second * size.height

                    // 1. Círculo rojo central (El puntero)
                    drawCircle(color = Color.Red, radius = 24f, center = androidx.compose.ui.geometry.Offset(pointX, pointY))
                    // 2. Anillo blanco exterior
                    drawCircle(color = Color.White, radius = 28f, center = androidx.compose.ui.geometry.Offset(pointX, pointY), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                }
            }
        }

        // 3. LA SÁBANA NEGRA (Capa Superior - Tapa todo cuando SÍ hay ahorro)
        if (isBatterySaverMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black) // El negro puro apaga los LEDs de la pantalla
                    .clickable { isBatterySaverMode = false }, // Tocar para despertar
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material.icons.Icons.Default.Info
                    Text(
                        "PANTALLA APAGADA\nTRANSMITIENDO",
                        color = Color.DarkGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Toca la pantalla para encender", color = Color.Gray)
                }
            }
        }
    }
}