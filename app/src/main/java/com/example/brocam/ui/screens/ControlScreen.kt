package com.example.brocam.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import com.example.brocam.ui.viewmodel.BroCamViewModel
import androidx.compose.ui.draw.clipToBounds

@Composable
fun ControlScreen(viewModel: BroCamViewModel) {
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isSosMode by viewModel.isSosMode.collectAsState()
    val isHighQuality by viewModel.isHighQuality.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    // El frame estático solo nos servirá si congelamos una imagen real enviada por la cámara
    val remoteFrame by viewModel.receivedFrame.collectAsState()

    // Estados Pizarra Congelada
    var isFrozen by remember { mutableStateOf(false) }
    var drawingLines by remember { mutableStateOf(listOf<List<Pair<Float, Float>>>()) }
    var currentLine by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
    var frozenFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Estados Modo Lápiz en Vivo
    var isLivePencilMode by remember { mutableStateOf(false) }
    var currentLiveLine by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }

    BackHandler { viewModel.setRole(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // 1. ESTADO: DESCONECTADO
        if (!connectionState.isConnected && connectionState.message == "SEÑAL PERDIDA") {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Desconectado", tint = Color.Red, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("CONEXIÓN PERDIDA", color = Color.Red, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("El Lente se apagó o salió del rango.", color = Color.LightGray)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { viewModel.setRole(null) }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                    Text("Volver al Inicio")
                }
            }
        }
        // 2. ESTADO: VIDEO EN VIVO (O CONGELADO)
        else if (isStreaming || frozenFrame != null) {
            val isLive = isStreaming && !isFrozen

            // 1. Proporción de la caja (Vertical)
            val displayRatio = if (frozenFrame != null) {
                frozenFrame!!.width.toFloat() / frozenFrame!!.height.toFloat()
            } else {
                if (isHighQuality) 9f / 16f else 3f / 4f
            }

            // 2. Escala del video (Exacta, sin perder decimales)
            val exactScale = if (isHighQuality) 16f / 9f else 4f / 3f

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

                // CAJA "FANTASMA" DE INTERACCIÓN (Ahora con tijeras: clipToBounds)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(displayRatio)
                        .clipToBounds() // 👈 ESTO MATA EL DESFACE: Corta lo que sobra
                        .pointerInput(isFrozen, isLivePencilMode) {
                            if (!isFrozen && !isLivePencilMode) {
                                detectTapGestures { tapOffset ->
                                    val xP = tapOffset.x / size.width.toFloat()
                                    val yP = tapOffset.y / size.height.toFloat()
                                    viewModel.sendPointer(xP, yP)
                                }
                            }
                        }
                        .pointerInput(isFrozen, isLivePencilMode) {
                            if (isFrozen) {
                                detectDragGestures(
                                    onDragStart = { offset -> currentLine = listOf(Pair((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f))) },
                                    onDragEnd = { drawingLines = drawingLines + listOf(currentLine); currentLine = emptyList() }
                                ) { change, _ ->
                                    change.consume()
                                    currentLine = currentLine + Pair((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                                }
                            } else if (isLivePencilMode) {
                                detectDragGestures(
                                    onDragStart = { offset -> currentLiveLine = listOf(Pair((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f))) },
                                    onDragEnd = { currentLiveLine = emptyList() }
                                ) { change, _ ->
                                    change.consume()
                                    currentLiveLine = currentLiveLine + Pair((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                                    viewModel.sendLiveLine(currentLiveLine)
                                }
                            } else {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    viewModel.sendPointer((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                                }
                            }
                        }
                ) {
                    // CAPA INFERIOR: EL VIDEO HARDWARE H.265 (O LA FOTO)
                    if (isLive) {
                        val videoRotation = if (isFrontCamera) 270f else 90f

                        AndroidView(
                            factory = { ctx ->
                                android.view.TextureView(ctx).apply {
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                            viewModel.startH265Decoder(android.view.Surface(surface))
                                        }
                                        override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                                        override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                                            viewModel.stopH265Decoder()
                                            return true
                                        }
                                        override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = videoRotation
                                    scaleX = exactScale
                                    scaleY = 1f / exactScale
                                }
                        )
                    } else if (frozenFrame != null) {
                        Image(
                            bitmap = frozenFrame!!.asImageBitmap(),
                            contentDescription = "Vista congelada",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // CAPA SUPERIOR 1: DIBUJO DE PIZARRA CONGELADA
                    if (drawingLines.isNotEmpty() || currentLine.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 8f
                            val color = Color.Red
                            for (line in drawingLines) {
                                for (i in 0 until line.size - 1) { drawLine(color = color, start = androidx.compose.ui.geometry.Offset(line[i].first * size.width, line[i].second * size.height), end = androidx.compose.ui.geometry.Offset(line[i+1].first * size.width, line[i+1].second * size.height), strokeWidth = strokeWidth) }
                            }
                            for (i in 0 until currentLine.size - 1) { drawLine(color = color, start = androidx.compose.ui.geometry.Offset(currentLine[i].first * size.width, currentLine[i].second * size.height), end = androidx.compose.ui.geometry.Offset(currentLine[i+1].first * size.width, currentLine[i+1].second * size.height), strokeWidth = strokeWidth) }
                        }
                    }

                    // CAPA SUPERIOR 2: DIBUJO DE LÁPIZ EN VIVO
                    if (currentLiveLine.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (i in 0 until currentLiveLine.size - 1) {
                                drawLine(color = Color.Red, start = androidx.compose.ui.geometry.Offset(currentLiveLine[i].first * size.width, currentLiveLine[i].second * size.height), end = androidx.compose.ui.geometry.Offset(currentLiveLine[i+1].first * size.width, currentLiveLine[i+1].second * size.height), strokeWidth = 10f)
                            }
                        }
                    }
                }
            }

            // CONTROLES SUPERPUESTOS
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isFrozen) {
                    Button(
                        onClick = { isLivePencilMode = !isLivePencilMode },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isLivePencilMode) Color(0xFF2563EB) else Color.DarkGray)
                    ) {
                        // RECUERDA: Si te marca error aquí, cambia ManoLapizIcon por tu ícono correspondiente
                        Text("✏️ Lápiz", color = Color.White)
                    }
                }

                Button(
                    onClick = {
                        if (!isFrozen) {
                            frozenFrame = remoteFrame
                            isFrozen = true
                            isLivePencilMode = false
                        } else {
                            isFrozen = false
                            frozenFrame = null
                            drawingLines = emptyList()
                            viewModel.clearAnnotatedImage()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isFrozen) Color.Red else Color.DarkGray)
                ) {
                    Text(if (isFrozen) "DESCONGELAR" else "CONGELAR")
                }

                if (isFrozen && frozenFrame != null) {
                    Button(
                        onClick = { viewModel.sendAnnotatedFrame(frozenFrame!!, drawingLines) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) { Text("ENVIAR AL TÉCNICO") }
                }
            }
        }
        // 3. ESTADO: CARGANDO
        else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(connectionState.message, color = Color.White)
            }
        }

        // --- UI COMÚN (Botones de abajo) ---
        if (connectionState.message.contains("Foto")) {
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFF59E0B).copy(alpha = 0.9f)).statusBarsPadding().padding(12.dp).align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) { Text(connectionState.message, color = Color.Black, fontWeight = FontWeight.Bold) }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.toggleQuality() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isHighQuality) Color(0xFF007BFF) else Color.DarkGray),
                    shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text(if (isHighQuality) "HD" else "SD", fontWeight = FontWeight.Bold) }

                Button(
                    onClick = { viewModel.toggleFlash() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isFlashOn) Color(0xFFFFC107) else Color.DarkGray, contentColor = if (isFlashOn) Color.Black else Color.White),
                    shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(8.dp)
                ) { Icon(imageVector = if (isFlashOn) Icons.Default.Star else Icons.Default.Close, contentDescription = "Flash", modifier = Modifier.size(20.dp)) }

                Button(
                    onClick = { viewModel.toggleSos() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSosMode) Color.Red else Color.DarkGray, contentColor = Color.White),
                    shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text(if (isSosMode) "🚨 SOS ON" else "SOS", fontWeight = FontWeight.Bold) }

                Button(
                    onClick = { viewModel.toggleCamera() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isFrontCamera) Color(0xFF06B6D4) else Color.DarkGray, contentColor = if (isFrontCamera) Color.Black else Color.White),
                    shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Voltear cámara", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isFrontCamera) "FRONT" else "BACK", style = MaterialTheme.typography.labelSmall)
                }

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                LaunchedEffect(isPressed) { if (isPressed) viewModel.startPushToTalk() else viewModel.stopPushToTalk() }

                Button(
                    onClick = { }, interactionSource = interactionSource,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isPressed) Color(0xFF22C55E) else Color(0xFF2563EB), contentColor = Color.White),
                    shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) { Text(if (isPressed) "HABLANDO..." else "🎤 HABLAR", fontWeight = FontWeight.Bold) }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.setRole(null) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545))) { Text("Salir") }
                Box(modifier = Modifier.size(70.dp).background(Color.White, CircleShape).border(4.dp, Color.Gray, CircleShape).clickable { viewModel.triggerRemotePhoto() })
                Spacer(Modifier.width(60.dp))
            }
        }
    }
}