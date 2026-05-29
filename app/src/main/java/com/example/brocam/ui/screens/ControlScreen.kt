package com.example.brocam.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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

    var isFrozen by remember { mutableStateOf(false) }
    var drawingLines by remember { mutableStateOf(listOf<List<Pair<Float, Float>>>()) }
    var currentLine by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
    var frozenFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    var isLivePencilMode by remember { mutableStateOf(false) }
    var currentLiveLine by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }

    var isProMode by remember { mutableStateOf(false) }
    var remoteExposureValue by remember { mutableFloatStateOf(0.5f) }

    // 🎯 NUEVO: Referencia para sacar la captura de pantalla del motor de video
    var textureViewRef by remember { mutableStateOf<android.view.TextureView?>(null) }

    BackHandler { viewModel.setRole(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!connectionState.isConnected && connectionState.message == "SEÑAL PERDIDA") {
            Column(modifier = Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Desconectado", tint = Color.Red, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("CONEXIÓN PERDIDA", color = Color.Red, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { viewModel.setRole(null) }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) { Text("Volver al Inicio") }
            }
        } else if (isStreaming || frozenFrame != null) {
            val isLive = isStreaming && !isFrozen
            val displayRatio = if (frozenFrame != null) frozenFrame!!.width.toFloat() / frozenFrame!!.height.toFloat() else if (isHighQuality) 9f / 16f else 3f / 4f
            val exactScale = if (isHighQuality) 16f / 9f else 4f / 3f

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(displayRatio).clipToBounds()
                        .pointerInput(isFrozen, isLivePencilMode) {
                            if (!isFrozen && !isLivePencilMode) {
                                detectTapGestures { tapOffset ->
                                    viewModel.sendPointer(tapOffset.x / size.width.toFloat(), tapOffset.y / size.height.toFloat())
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
                    if (isLive) {
                        val videoRotation = if (isFrontCamera) 270f else 90f
                        AndroidView(
                            factory = { ctx ->
                                android.view.TextureView(ctx).apply {
                                    textureViewRef = this // 🎯 Guardamos la referencia para hacerle captura de pantalla luego
                                    layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                                    surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) { viewModel.startH265Decoder(android.view.Surface(surface)) }
                                        override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                                        override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean { viewModel.stopH265Decoder(); return true }
                                        override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = videoRotation; scaleX = exactScale; scaleY = 1f / exactScale }
                        )
                    } else if (frozenFrame != null) {
                        Image(bitmap = frozenFrame!!.asImageBitmap(), contentDescription = "Vista congelada", modifier = Modifier.fillMaxSize())
                    }

                    if (drawingLines.isNotEmpty() || currentLine.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (line in drawingLines) {
                                for (i in 0 until line.size - 1) drawLine(color = Color.Red, start = androidx.compose.ui.geometry.Offset(line[i].first * size.width, line[i].second * size.height), end = androidx.compose.ui.geometry.Offset(line[i+1].first * size.width, line[i+1].second * size.height), strokeWidth = 8f)
                            }
                            for (i in 0 until currentLine.size - 1) drawLine(color = Color.Red, start = androidx.compose.ui.geometry.Offset(currentLine[i].first * size.width, currentLine[i].second * size.height), end = androidx.compose.ui.geometry.Offset(currentLine[i+1].first * size.width, currentLine[i+1].second * size.height), strokeWidth = 8f)
                        }
                    }

                    if (currentLiveLine.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (i in 0 until currentLiveLine.size - 1) drawLine(color = Color.Red, start = androidx.compose.ui.geometry.Offset(currentLiveLine[i].first * size.width, currentLiveLine[i].second * size.height), end = androidx.compose.ui.geometry.Offset(currentLiveLine[i+1].first * size.width, currentLiveLine[i+1].second * size.height), strokeWidth = 10f)
                        }
                    }
                }
            }

            Row(modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isFrozen) {
                    Button(onClick = { isLivePencilMode = !isLivePencilMode }, colors = ButtonDefaults.buttonColors(containerColor = if (isLivePencilMode) Color(0xFF2563EB) else Color.DarkGray)) { Text("✏️ Lápiz", color = Color.White) }
                }

                Button(
                    onClick = {
                        if (!isFrozen) {
                            // 🎯 PARCHE APLICADO: Tomamos la foto de la memoria de la pantalla
                            frozenFrame = textureViewRef?.bitmap
                            if (frozenFrame != null) {
                                isFrozen = true
                                isLivePencilMode = false
                            }
                        } else {
                            isFrozen = false
                            frozenFrame = null
                            drawingLines = emptyList()
                            viewModel.clearAnnotatedImage()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isFrozen) Color.Red else Color.DarkGray)
                ) { Text(if (isFrozen) "DESCONGELAR" else "CONGELAR") }

                if (isFrozen && frozenFrame != null) {
                    Button(onClick = { viewModel.sendAnnotatedFrame(frozenFrame!!, drawingLines) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) { Text("ENVIAR AL TÉCNICO") }
                }
            }

            AnimatedVisibility(
                visible = isProMode,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(Color(0xFF1E293B).copy(alpha = 0.9f), RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Text("TELEMETRÍA LENTE (REMOTO)", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Text("0.5x", color = Color.White, modifier = Modifier.clickable { viewModel.setRemoteZoom(0.5f) })
                        Text("1x", color = Color.Yellow, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { viewModel.setRemoteZoom(1.0f) })
                        Text("2x", color = Color.White, modifier = Modifier.clickable { viewModel.setRemoteZoom(2.0f) })
                        Text("4x", color = Color.White, modifier = Modifier.clickable { viewModel.setRemoteZoom(4.0f) })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("☀️ Exp.", color = Color.White, fontSize = 14.sp, modifier = Modifier.width(60.dp))
                        Slider(value = remoteExposureValue, onValueChange = { remoteExposureValue = it; viewModel.setRemoteExposure(it) }, colors = SliderDefaults.colors(thumbColor = Color(0xFF06B6D4), activeTrackColor = Color(0xFF06B6D4)), modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(connectionState.message, color = Color.White)
            }
        }

        if (connectionState.message.contains("Foto")) {
            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF59E0B).copy(alpha = 0.9f)).statusBarsPadding().padding(12.dp).align(Alignment.TopCenter), contentAlignment = Alignment.Center) { Text(connectionState.message, color = Color.Black, fontWeight = FontWeight.Bold) }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.toggleQuality() }, colors = ButtonDefaults.buttonColors(containerColor = if (isHighQuality) Color(0xFF007BFF) else Color.DarkGray), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(if (isHighQuality) "HD" else "SD", fontWeight = FontWeight.Bold) }
                Button(onClick = { viewModel.toggleFlash() }, colors = ButtonDefaults.buttonColors(containerColor = if (isFlashOn) Color(0xFFFFC107) else Color.DarkGray, contentColor = if (isFlashOn) Color.Black else Color.White), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(8.dp)) { Text(if (isFlashOn) "⚡ ON" else "⚡ OFF", fontWeight = FontWeight.Bold) }
                Button(onClick = { viewModel.toggleSos() }, colors = ButtonDefaults.buttonColors(containerColor = if (isSosMode) Color.Red else Color.DarkGray, contentColor = Color.White), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(if (isSosMode) "🚨 SOS ON" else "SOS", fontWeight = FontWeight.Bold) }
                Button(onClick = { viewModel.toggleCamera() }, colors = ButtonDefaults.buttonColors(containerColor = if (isFrontCamera) Color(0xFF06B6D4) else Color.DarkGray, contentColor = if (isFrontCamera) Color.Black else Color.White), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Text(if (isFrontCamera) "FRONT" else "BACK", style = MaterialTheme.typography.labelSmall) }

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                LaunchedEffect(isPressed) { if (isPressed) viewModel.startPushToTalk() else viewModel.stopPushToTalk() }

                Button(onClick = { }, interactionSource = interactionSource, colors = ButtonDefaults.buttonColors(containerColor = if (isPressed) Color(0xFF22C55E) else Color(0xFF2563EB), contentColor = Color.White), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text(if (isPressed) "HABLANDO..." else "🎤 HABLAR", fontWeight = FontWeight.Bold) }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.setRole(null) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545))) { Text("Salir") }
                Button(onClick = { isProMode = !isProMode }, colors = ButtonDefaults.buttonColors(containerColor = if (isProMode) Color(0xFF06B6D4) else Color.DarkGray), shape = RoundedCornerShape(20.dp)) { Text(if (isProMode) "PRO" else "AUTO", fontWeight = FontWeight.Bold, color = if (isProMode) Color.Black else Color.White) }
                Box(modifier = Modifier.size(70.dp).background(Color.White, CircleShape).border(4.dp, Color.Gray, CircleShape).clickable { viewModel.triggerRemotePhoto() })
            }
        }
    }
}