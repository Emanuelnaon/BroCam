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
import androidx.camera.camera2.interop.Camera2Interop
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.brocam.ui.viewmodel.BroCamViewModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

@Composable
fun CameraScreen(viewModel: BroCamViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ESTADOS
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isHighQuality by viewModel.isHighQuality.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val isSosMode by viewModel.isSosMode.collectAsState()


    // ESTADOS AR Y PIZARRA
    val remotePointer by viewModel.remotePointer.collectAsState()
    val annotatedImage by viewModel.annotatedImage.collectAsState()
    val remoteLiveLine by viewModel.remoteLiveLine.collectAsState()

    var isBatterySaverMode by remember { mutableStateOf(false) }

    BackHandler { viewModel.setRole(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }

    val cameraProviderState = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }


    LaunchedEffect(isFlashOn) {
        if (!isFrontCamera) try { cameraControl?.enableTorch(isFlashOn) } catch (e: Exception) {}
    }

    // 🪄 BUCLE ESTROBOSCÓPICO (MODO SOS)
    LaunchedEffect(isSosMode, isFrontCamera) {
        if (isSosMode && !isFrontCamera && cameraControl != null) {
            while (isActive) {
                try { cameraControl?.enableTorch(true) } catch (e: Exception) {}
                kotlinx.coroutines.delay(150) // Prende 150 milisegundos
                try { cameraControl?.enableTorch(false) } catch (e: Exception) {}
                kotlinx.coroutines.delay(150) // Apaga 150 milisegundos
            }
        } else {
            // Si apagan el SOS, restauramos el flash a su estado normal
            try { cameraControl?.enableTorch(isFlashOn) } catch (e: Exception) {}
        }
    }

    LaunchedEffect(remotePointer) {
        val currentPointer = remotePointer
        val myPreviewView = previewViewRef
        val myCameraControl = cameraControl
        if (currentPointer != null && myPreviewView != null && myCameraControl != null) {
            try {
                val factory = myPreviewView.meteringPointFactory
                val point = factory.createPoint(currentPointer.first * myPreviewView.width, currentPointer.second * myPreviewView.height)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                myCameraControl.startFocusAndMetering(action)
            } catch (e: Exception) { android.util.Log.e("BroCam_Focus", "Error enfocando: ${e.message}") }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shutterEvent.collect {
            // 1. Obtenemos GPS dinámico
            viewModel.fetchCurrentLocation { gpsInfo ->

                // 2. Capturamos la foto en memoria para poder editarla
                imageCapture.takePicture(ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            // Convertimos el Proxy a Bitmap y aplicamos la rotación correcta
                            val bitmap = image.toBitmap()
                            val matrix = android.graphics.Matrix().apply {
                                postRotate(image.imageInfo.rotationDegrees.toFloat())
                            }
                            val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )

                            // 🎨 ESTAMPADO: Aquí aplicamos tu función de la Fase 4
                            val finalBitmap = viewModel.drawMetadataOnBitmap(rotatedBitmap, gpsInfo)

                            // 💾 GUARDADO MANUAL: Guardamos el bitmap procesado en la galería
                            saveBitmapToGallery(context, finalBitmap)

                            image.close()
                            viewModel.sendCommand("PHOTO_OK")
                            Toast.makeText(context, "📸 Evidencia Guardada: $gpsInfo", Toast.LENGTH_SHORT).show()
                        }

                        override fun onError(e: ImageCaptureException) {
                            android.util.Log.e("BroCam_Error", "Error captura: ${e.message}")
                        }
                    }
                )
            }
        }
    }

    LaunchedEffect(isHighQuality, isFrontCamera, cameraProviderState.value, previewViewRef) {
        val myProvider = cameraProviderState.value
        val myPreviewView = previewViewRef

        if (myProvider != null && myPreviewView != null) {
            try {
                myProvider.unbindAll()

                val targetSize = if (isHighQuality) Size(1280, 720) else Size(640, 480)
                val cameraSelector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                // 1. Pantalla del Usuario
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(myPreviewView.surfaceProvider)
                }

                // 2. Analizador de Imagen (NUESTRO PUENTE AL H.265)
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888) // 👈 Pedimos bytes puros
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(targetSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .build()

                analyzer.setAnalyzer(analysisExecutor) { proxy ->
                    if (isStreaming) {
                        val width = proxy.width
                        val height = proxy.height

                        // 1. Tamaño MILIMÉTRICO exacto (Ancho x Alto x 1.5 para YUV420)
                        val nv21 = ByteArray(width * height * 3 / 2)

                        val yBuffer = proxy.planes[0].buffer
                        val uBuffer = proxy.planes[1].buffer
                        val vBuffer = proxy.planes[2].buffer

                        val ySize = yBuffer.remaining()
                        val uSize = uBuffer.remaining()
                        val vSize = vBuffer.remaining()

                        // 2. Extraemos Y (Brillo y formas)
                        yBuffer.get(nv21, 0, ySize)

                        // 3. Extraemos UV (Color) - ¡La cura para el Síndrome de los Pitufos!
                        if (proxy.planes[1].pixelStride == 2) {
                            // 🪄 LA MAGIA ESTÁ AQUÍ:
                            // Le pedimos el buffer "U" primero para generar formato NV12 perfecto.
                            if (ySize + uSize <= nv21.size) {
                                uBuffer.get(nv21, ySize, uSize)
                            }
                        } else {
                            // Si por casualidad es un celular viejo con canales separados
                            if (ySize + uSize + vSize <= nv21.size) {
                                uBuffer.get(nv21, ySize, uSize)
                                vBuffer.get(nv21, ySize + uSize, vSize)
                            }
                        }

                        // 4. Se lo pasamos al motor con sus medidas reales
                        val timestampUs = proxy.imageInfo.timestamp / 1000
                        viewModel.feedEncoder(nv21, timestampUs, width, height)
                    }
                    proxy.close()
                }

                // 3. Vinculamos la sesión
                val camera = myProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analyzer, imageCapture)
                cameraControl = camera.cameraControl
                if (!isFrontCamera && isFlashOn) cameraControl?.enableTorch(true)

            } catch (e: Exception) {
                android.util.Log.e("BroCam_Camera", "Error bindeando: ${e.message}")
            }
        }
    }


    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // 1. CÁMARA (Fondo)
        AndroidView(
            factory = { ctx ->
                val pv = PreviewView(ctx).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                previewViewRef = pv
                ProcessCameraProvider.getInstance(ctx).addListener({ cameraProviderState.value = ProcessCameraProvider.getInstance(ctx).get() }, ContextCompat.getMainExecutor(ctx))
                pv
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. UI NORMAL (Solo visible si la pantalla NO está "apagada")
        if (!isBatterySaverMode) {

            // INFO (Arriba a la derecha)
            Column(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
                if (isStreaming) {
                    Text("🔴 LENTE EN VIVO", color = Color.Red, style = MaterialTheme.typography.titleMedium)
                    Text(if (isHighQuality) "Calidad: HD" else "Calidad: SD", color = Color.White, style = MaterialTheme.typography.labelMedium)
                } else {
                    Text("ESPERANDO CONTROL...", color = Color.Yellow, style = MaterialTheme.typography.titleMedium)
                }
            }

            // 🛠️ BARRA INFERIOR ORDENADA (Cámara/Flash a la izq, Ahorro a la der)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp) // 🛠️ CORREGIDO: Separado en dos paddings
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bloque Izquierdo (Flash y Cámara)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.toggleFlash() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFlashOn) Color(
                                0xFFFFC107
                            ) else Color(0xFF1E293B).copy(alpha = 0.8f)
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = if (isFlashOn) Icons.Default.Star else Icons.Default.Close,
                            contentDescription = "Flash",
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                    }

                    Button(
                        onClick = { viewModel.toggleCamera() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFrontCamera) Color(
                                0xFF06B6D4
                            ) else Color(0xFF1E293B).copy(alpha = 0.8f)
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Voltear",
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (isFrontCamera) "FRONT" else "BACK",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    // 🎙️ NUEVO: BOTÓN PUSH-TO-TALK (WALKIE-TALKIE) FIX
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()

                    // Esto "escucha" al botón mágicamente
                    LaunchedEffect(isPressed) {
                        if (isPressed) {
                            viewModel.startPushToTalk()
                        } else {
                            viewModel.stopPushToTalk()
                        }
                    }

                    Button(
                        onClick = { }, // No usamos esto
                        interactionSource = interactionSource, // 🛠️ Aquí está la magia
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPressed) Color(0xFF22C55E) else Color(0xFF2563EB), // Verde al hablar, Azul normal
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (isPressed) "HABLANDO..." else "🎤 HABLAR",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bloque Derecho (Apagar Pantalla)
                Button(
                    onClick = { isBatterySaverMode = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545).copy(alpha = 0.9f)), // Rojo intenso para identificarlo rápido
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Apagar Pantalla", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 🔴 LA MIRA LÁSER AR Y TRAZOS EN VIVO
        if (remotePointer != null || remoteLiveLine.isNotEmpty()) {
            val cameraRatio = if (isHighQuality) 9f / 16f else 3f / 4f

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.aspectRatio(cameraRatio)) {
                    // PUNTERO
                    if (remotePointer != null) {
                        val pointX = remotePointer!!.first * size.width
                        val pointY = remotePointer!!.second * size.height
                        drawCircle(color = Color.Red, radius = 24f, center = androidx.compose.ui.geometry.Offset(pointX, pointY))
                        drawCircle(color = Color.White, radius = 28f, center = androidx.compose.ui.geometry.Offset(pointX, pointY), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                    }

                    // 🛠️ LÁPIZ EN VIVO
                    if (remoteLiveLine.isNotEmpty()) {
                        for (i in 0 until remoteLiveLine.size - 1) {
                            drawLine(
                                color = Color.Red,
                                start = androidx.compose.ui.geometry.Offset(remoteLiveLine[i].first * size.width, remoteLiveLine[i].second * size.height),
                                end = androidx.compose.ui.geometry.Offset(remoteLiveLine[i+1].first * size.width, remoteLiveLine[i+1].second * size.height),
                                strokeWidth = 10f
                            )
                        }
                    }
                }
            }
        }

        // 3. MODO AHORRO (Sábana Negra)
        if (isBatterySaverMode) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { isBatterySaverMode = false }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icons.Default.Info
                    Text("PANTALLA APAGADA\nTRANSMITIENDO", color = Color.DarkGray, textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Toca la pantalla para encender", color = Color.Gray)
                }
            }
        }

        // 4. CAPA ESTÁTICA DE PIZARRA CONGELADA
        if (annotatedImage != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                val imageRatio = annotatedImage!!.width.toFloat() / annotatedImage!!.height.toFloat()
                Image(bitmap = annotatedImage!!.asImageBitmap(), contentDescription = "Indicación", modifier = Modifier.fillMaxWidth().aspectRatio(imageRatio))
                Button(onClick = { viewModel.clearAnnotatedImage() }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("CERRAR INDICACIÓN (X)", style = MaterialTheme.typography.titleLarge)
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
    uri?.let {
        context.contentResolver.openOutputStream(it).use { stream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, stream!!)
        }
    }
}