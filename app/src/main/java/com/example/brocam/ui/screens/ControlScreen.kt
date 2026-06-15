package com.example.brocam.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.brocam.ui.viewmodel.BroCamViewModel
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTransformGestures
@Composable
fun ControlScreen(viewModel: BroCamViewModel) {
    val context = LocalContext.current
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isHighQuality by viewModel.isHighQuality.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val timerDuration by viewModel.timerDuration.collectAsState()
    val currentCountdown by viewModel.currentCountdown.collectAsState()

    var isFrozen by remember { mutableStateOf(false) }
    var drawingLines by remember { mutableStateOf(listOf<List<Pair<Float, Float>>>()) }
    var currentLine by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
    var frozenFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    var isLivePencilMode by remember { mutableStateOf(false) }
    var currentLiveLine by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }

    var isMenuOpen by remember { mutableStateOf(false) }
    var isExposureMenuOpen by remember { mutableStateOf(false) }
    var remoteExposureValue by remember { mutableFloatStateOf(0.5f) }

    var textureViewRef by remember { mutableStateOf<android.view.TextureView?>(null) }
    var isDecoderStarted by remember { mutableStateOf(false) }
    var isGridVisible by remember { mutableStateOf(false) }
    var currentZoom by remember { mutableFloatStateOf(1f) } // 1f es el 100% del lente nativo

    BackHandler { viewModel.setRole(null) }

    val buttonBg = Color.Black.copy(alpha = 0.6f)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!connectionState.isConnected) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Desconectado", tint = Color.Red, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("CONEXIÓN PERDIDA", color = Color.Red, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { viewModel.setRole(null) }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("Volver al Inicio") }
            }
        } else if (isStreaming || frozenFrame != null) {
            val isLive = isStreaming && !isFrozen

            // ==========================================
            // 1. CAPA BASE: VISOR DE VIDEO (Mecánica Intacta)
            // ==========================================
            Box(modifier = Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {

                val videoModifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).
                graphicsLayer {
                        rotationZ = if (isFrontCamera) 270f else 90f
                        val scaleRatio = 4f / 3f
                        scaleX = scaleRatio
                        scaleY = if (isFrontCamera) -scaleRatio else scaleRatio
                    }
                    .pointerInput(isFrozen, isLivePencilMode) {
                        if (!isFrozen && !isLivePencilMode) {
                            detectTapGestures { tapOffset -> viewModel.sendPointer(tapOffset.x / size.width.toFloat(), tapOffset.y / size.height.toFloat()) }
                        }
                    }
                    .pointerInput(isFrozen, isLivePencilMode) {
                        if (isFrozen) {
                            detectDragGestures(
                                onDragStart = { offset -> currentLine = listOf(Pair((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f))) },
                                onDragEnd = { drawingLines = drawingLines + listOf(currentLine); currentLine = emptyList() }
                            ) { change, _ -> change.consume(); currentLine = currentLine + Pair((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f)) }
                        } else if (isLivePencilMode) {
                            detectDragGestures(
                                onDragStart = { offset -> currentLiveLine = listOf(Pair((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f))) },
                                onDragEnd = { currentLiveLine = emptyList() }
                            ) { change, _ -> change.consume(); currentLiveLine = currentLiveLine + Pair((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f)); viewModel.sendLiveLine(currentLiveLine) }
                        } else {
                            detectDragGestures { change, _ -> change.consume(); viewModel.sendPointer((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f)) }
                        }
                    }
                    // NUEVO: GESTOS DE ZOOM (Pinch-to-Zoom)
                    .pointerInput(isFrozen, isLivePencilMode) {
                        // Solo permitimos hacer zoom si no estamos dibujando
                        if (!isFrozen && !isLivePencilMode) {
                            detectTransformGestures { _, _, zoomDelta, _ ->
                                currentZoom = (currentZoom * zoomDelta).coerceIn(1f, 10f)
                                viewModel.setRemoteZoom(currentZoom)
                            }
                        }
                    }

                if (isLive) {
                    key(isHighQuality, isFrontCamera) {
                        AndroidView(
                            factory = { ctx ->
                                android.view.TextureView(ctx).apply {
                                    textureViewRef = this
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                            viewModel.startH265Decoder(android.view.Surface(surface))
                                            isDecoderStarted = true
                                        }
                                        override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                                            viewModel.stopH265Decoder()
                                            isDecoderStarted = false
                                            return true
                                        }
                                        override fun onSurfaceTextureSizeChanged(s: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                                        override fun onSurfaceTextureUpdated(s: android.graphics.SurfaceTexture) {}
                                    }
                                }
                            },
                            modifier = videoModifier
                        )
                    }

                    LaunchedEffect(isStreaming) {
                        if (isStreaming && !isDecoderStarted && textureViewRef != null && textureViewRef!!.isAvailable) {
                            viewModel.startH265Decoder(android.view.Surface(textureViewRef!!.surfaceTexture))
                            isDecoderStarted = true
                        }
                    }

                } else if (frozenFrame != null) {
                    Image(
                        bitmap = frozenFrame!!.asImageBitmap(),
                        contentDescription = "Congelado",
                        modifier = videoModifier
                    )
                }

                // ==========================================
                // CAPA DE DIBUJO Y OVERLAYS (Forzada a 4:3)
                // ==========================================
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {

                    // 1. Temporizador Gigante
                    if (currentCountdown > 0) {
                        Text(
                            text = currentCountdown.toString(),
                            color = Color.White,
                            fontSize = 160.sp,
                            fontWeight = FontWeight.Black,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 24f)
                            ),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // 2. Cuadrícula 3x3
                    if (isGridVisible) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 2f
                            val gridColor = Color.White.copy(alpha = 0.4f)
                            // Verticales
                            drawLine(color = gridColor, start = Offset(size.width / 3, 0f), end = Offset(size.width / 3, size.height), strokeWidth = stroke)
                            drawLine(color = gridColor, start = Offset(size.width * 2 / 3, 0f), end = Offset(size.width * 2 / 3, size.height), strokeWidth = stroke)
                            // Horizontales
                            drawLine(color = gridColor, start = Offset(0f, size.height / 3), end = Offset(size.width, size.height / 3), strokeWidth = stroke)
                            drawLine(color = gridColor, start = Offset(0f, size.height * 2 / 3), end = Offset(size.width, size.height * 2 / 3), strokeWidth = stroke)
                        }
                    }

                    // 3. Trazos Pizarra (Tus if de drawingLines y currentLiveLine van aquí adentro)
                    if (drawingLines.isNotEmpty() || currentLine.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (line in drawingLines) for (i in 0 until line.size - 1) drawLine(color = Color.Red, start = Offset(line[i].first * size.width, line[i].second * size.height), end = Offset(line[i+1].first * size.width, line[i+1].second * size.height), strokeWidth = 8f)
                            for (i in 0 until currentLine.size - 1) drawLine(color = Color.Red, start = Offset(currentLine[i].first * size.width, currentLine[i].second * size.height), end = Offset(currentLine[i+1].first * size.width, currentLine[i+1].second * size.height), strokeWidth = 8f)
                        }
                    }
                    if (currentLiveLine.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (i in 0 until currentLiveLine.size - 1) drawLine(color = Color.Red, start = Offset(currentLiveLine[i].first * size.width, currentLiveLine[i].second * size.height), end = Offset(currentLiveLine[i+1].first * size.width, currentLiveLine[i+1].second * size.height), strokeWidth = 10f)
                        }
                    }
                }
            }

            // ==========================================
            // 2. CAPA UI FLOTANTE (Estructura Limpia)
            // ==========================================

            // Botón central "Enviar Imagen" (Solo visible si está congelado)
            if (isFrozen) {
                Button(
                    onClick = { viewModel.sendAnnotatedFrame(frozenFrame!!, drawingLines) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.align(Alignment.Center).padding(bottom = 60.dp)
                ) { Text("ENVIAR IMAGEN", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }

            // --- SECCIÓN SUPERIOR ---
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 40.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Izquierda: Calidad y Temporizador agrupados
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.background(if (isHighQuality) Color(0xFF06B6D4) else buttonBg, RoundedCornerShape(12.dp)).clickable { viewModel.toggleQuality() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(if (isHighQuality) "HD" else "SD", color = if (isHighQuality) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(16.dp)) // Espaciador que evita la superposición

                    Box(modifier = Modifier.background(if (timerDuration > 0) Color(0xFFF59E0B) else buttonBg, RoundedCornerShape(12.dp)).clickable { viewModel.cycleTimer() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(if (timerDuration > 0) "⏱️ ${timerDuration}s" else "⏱️ OFF", color = if (timerDuration > 0) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(16.dp)) // Separación

                    // NUEVO: Botón Cuadrícula 3x3
                    Box(modifier = Modifier.background(if (isGridVisible) Color.White else buttonBg, RoundedCornerShape(12.dp)).clickable { isGridVisible = !isGridVisible }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("⌗", color = if (isGridVisible) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.weight(1f)) // Empuja el centro

                // Centro: Piloto En Vivo
                if (isLive) {
                    Box(modifier = Modifier.background(Color.Red.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("🔴 EN VIVO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(60.dp)) // Estabiliza el layout offline
                }

                Spacer(modifier = Modifier.weight(1f)) // Empuja a la derecha

                // Derecha: Ajustes y Menú Desplegable
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = { isMenuOpen = !isMenuOpen }, modifier = Modifier.background(if(isMenuOpen) Color.White else buttonBg, CircleShape)) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes", tint = if(isMenuOpen) Color.Black else Color.White)
                    }

                    // Sub-menú desplegable debajo de Ajustes
                    AnimatedVisibility(visible = isMenuOpen, modifier = Modifier.padding(top = 56.dp)) {
                        Column(modifier = Modifier.background(buttonBg, RoundedCornerShape(24.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.size(48.dp).background(if (isLivePencilMode) Color.Red else Color.Transparent, CircleShape).clickable { if (!isFrozen) isLivePencilMode = !isLivePencilMode }, contentAlignment = Alignment.Center) {
                                Text("✏️", fontSize = 20.sp)
                            }
                            Box(modifier = Modifier.size(48.dp).background(if (isFrozen) Color.Red else Color.Transparent, CircleShape).clickable {
                                if (!isFrozen) {
                                    val rawBitmap = textureViewRef?.bitmap
                                    if (rawBitmap != null) { frozenFrame = rawBitmap; isFrozen = true; isLivePencilMode = false }
                                } else { isFrozen = false; frozenFrame = null; drawingLines = emptyList(); viewModel.clearAnnotatedImage() }
                            }, contentAlignment = Alignment.Center) {
                                Text(if(isFrozen) "❌" else "❄️", fontSize = 20.sp)
                            }
                        }
                    }
                }
            }

            // --- SECCIÓN INFERIOR ---
            Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp, start = 16.dp, end = 16.dp)) {

                // Slider de Exposición (Flotante)
                AnimatedVisibility(visible = isExposureMenuOpen) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).background(buttonBg, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("☀️", fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
                        Slider(value = remoteExposureValue, onValueChange = { remoteExposureValue = it; viewModel.setRemoteExposure(it) }, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White), modifier = Modifier.weight(1f))
                    }
                }

                // Nivel 1 (Sub-Bottom): Salir y Galería
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, start = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { viewModel.setRole(null) }, modifier = Modifier.background(Color.Red.copy(alpha=0.8f), CircleShape)) {
                        Icon(Icons.Default.Close, contentDescription = "Salir", tint = Color.White)
                    }
                    Box(modifier = Modifier.size(48.dp).background(buttonBg, CircleShape).clickable {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply { type = "image/*"; flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        try { context.startActivity(intent) } catch (e: Exception) {}
                    }, contentAlignment = Alignment.Center) {
                        Text("📁", fontSize = 18.sp)
                    }
                }

                // Nivel 2 (Main Bottom): Mic, Exp, Obturador, Flash, Cambio de Cámara
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

                    // Micrófono
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    LaunchedEffect(isPressed) { if (isPressed) viewModel.startPushToTalk() else viewModel.stopPushToTalk() }
                    Box(modifier = Modifier.size(56.dp).background(if (isPressed) Color.Green else buttonBg, CircleShape).clickable(interactionSource = interactionSource, indication = null) {}, contentAlignment = Alignment.Center) {
                        Text("🎤", fontSize = 24.sp)
                    }

                    // Exposición
                    Box(modifier = Modifier.size(56.dp).background(if (isExposureMenuOpen) Color(0xFF06B6D4) else buttonBg, CircleShape).clickable { isExposureMenuOpen = !isExposureMenuOpen }, contentAlignment = Alignment.Center) {
                        Text("☀️", fontSize = 24.sp)
                    }

                    // Obturador Principal (Captura)
                    Box(modifier = Modifier.size(76.dp).background(Color.White, CircleShape).border(4.dp, Color.LightGray, CircleShape).clickable { viewModel.triggerRemotePhoto() })

                    // Flash
                    Box(modifier = Modifier.size(56.dp).background(buttonBg, CircleShape).clickable { viewModel.toggleFlash() }, contentAlignment = Alignment.Center) {
                        Text("⚡", fontSize = 24.sp, color = if (isFlashOn) Color.Yellow else Color.White)
                        if (!isFlashOn) {
                            Canvas(modifier = Modifier.size(32.dp)) { drawLine(color = Color.White, start = Offset(0f, 0f), end = Offset(size.width, size.height), strokeWidth = 4f) }
                        }
                    }

                    // Cambio de Cámara
                    IconButton(onClick = { viewModel.toggleCamera() }, modifier = Modifier.background(buttonBg, CircleShape).size(56.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Voltear", tint = Color.White)
                    }
                }
            }
        }
    }
}