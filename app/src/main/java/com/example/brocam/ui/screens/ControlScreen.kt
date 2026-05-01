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
// --- NUEVAS IMPORTACIONES ---
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
// ----------------------------
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
import com.example.brocam.ui.viewmodel.BroCamViewModel

@Composable
fun ControlScreen(viewModel: BroCamViewModel) {
    // Borramos isStreaming para quitar el warning, no la usamos aquí.
    val isHighQuality by viewModel.isHighQuality.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState() // 🛠️ ¡USADA AHORA!
    val connectionState by viewModel.connectionState.collectAsState()
    val remoteFrame by viewModel.receivedFrame.collectAsState()
// Estados para la Pizarra Congelada
    var isFrozen by remember { mutableStateOf(false) }
    // Guardaremos una lista de trazos. Cada trazo es una lista de puntos (X,Y) relativos
    var drawingLines by remember { mutableStateOf(listOf<List<Pair<Float, Float>>>()) }
    var currentLine by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }

    // Guardamos el último frame congelado para no perderlo
    var frozenFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    BackHandler { viewModel.setRole(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        if (!connectionState.isConnected && connectionState.message == "SEÑAL PERDIDA") {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                    contentDescription = "Desconectado",
                    tint = Color.Red,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("CONEXIÓN PERDIDA", color = Color.Red, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("El Lente se apagó o salió del rango.", color = Color.LightGray)
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.setRole(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Volver al Inicio")
                }
            }
        }

// 2. VIDEO EN VIVO (O CONGELADO)
        else if (remoteFrame != null || frozenFrame != null) {
            // Si estamos congelados mostramos el frame guardado, si no, el en vivo
            val displayBitmap = if (isFrozen) frozenFrame else remoteFrame
            val imageRatio = displayBitmap!!.width.toFloat() / displayBitmap.height.toFloat()

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = "Vista remota",
                    modifier = Modifier
                        .aspectRatio(imageRatio)
                        .pointerInput(isFrozen) { // 🛠️ Reacciona a cambios en isFrozen
                            if (!isFrozen) {
                                // --- MODO NORMAL: LÁSER AR ---
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val xP = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val yP = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    viewModel.sendPointer(xP, yP)
                                }
                            } else {
                                // --- MODO PIZARRA: DIBUJAR ---
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        // Empezamos un trazo nuevo
                                        val xP = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val yP = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                        currentLine = listOf(Pair(xP, yP))
                                    },
                                    onDragEnd = {
                                        // Guardamos el trazo terminado
                                        drawingLines = drawingLines + listOf(currentLine)
                                        currentLine = emptyList()
                                    }
                                ) { change, _ ->
                                    change.consume()
                                    // Agregamos puntos al trazo actual
                                    val xP = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val yP = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    currentLine = currentLine + Pair(xP, yP)
                                }
                            }
                        }
                        // Agregamos el toque rápido (Tap) solo si no está congelado
                        .pointerInput(isFrozen) {
                            if (!isFrozen) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        val xP = tapOffset.x / size.width.toFloat()
                                        val yP = tapOffset.y / size.height.toFloat()
                                        viewModel.sendPointer(xP, yP)
                                    }
                                )
                            }
                        }
                )

                // 🔴 CAPA DE DIBUJO (Solo visible si hay líneas)
                if (drawingLines.isNotEmpty() || currentLine.isNotEmpty()) {
                    Canvas(modifier = Modifier.aspectRatio(imageRatio)) {
                        val strokeWidth = 8f
                        val color = Color.Red

                        // Dibujamos las líneas guardadas
                        for (line in drawingLines) {
                            for (i in 0 until line.size - 1) {
                                drawLine(
                                    color = color,
                                    start = androidx.compose.ui.geometry.Offset(line[i].first * size.width, line[i].second * size.height),
                                    end = androidx.compose.ui.geometry.Offset(line[i+1].first * size.width, line[i+1].second * size.height),
                                    strokeWidth = strokeWidth
                                )
                            }
                        }
                        // Dibujamos la línea que se está haciendo AHORA
                        for (i in 0 until currentLine.size - 1) {
                            drawLine(
                                color = color,
                                start = androidx.compose.ui.geometry.Offset(currentLine[i].first * size.width, currentLine[i].second * size.height),
                                end = androidx.compose.ui.geometry.Offset(currentLine[i+1].first * size.width, currentLine[i+1].second * size.height),
                                strokeWidth = strokeWidth
                            )
                        }
                    }
                }
            }

            // BOTÓN PARA CONGELAR / DESCONGELAR (Superpuesto arriba a la izquierda)
            Button(
                onClick = {
                    if (!isFrozen) {
                        frozenFrame = remoteFrame // Guardamos la foto actual
                        isFrozen = true
                    } else {
                        isFrozen = false
                        frozenFrame = null
                        drawingLines = emptyList() // Borramos los dibujos al descongelar
                    }
                },
                modifier = Modifier.padding(16.dp), // .align(Alignment.TopStart) si te pide centrado en un Box externo
                colors = ButtonDefaults.buttonColors(containerColor = if (isFrozen) Color.Red else Color.DarkGray)
            ) {
                Text(if (isFrozen) "DESCONGELAR" else "CONGELAR")
            }
        }else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(connectionState.message, color = Color.White)
            }
        }

        // Fix de Safe Area (detrás del notch)
        if (connectionState.message.contains("Foto")) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF59E0B).copy(alpha = 0.9f))
                    .statusBarsPadding() // Respeta la cámara frontal
                    .padding(12.dp)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                Text(connectionState.message, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- FILA DE CONTROLES INFERIORES ---
            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), // Un poco más apretado para que entren 3
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. CALIDAD (HD/SD)
                Button(
                    onClick = { viewModel.toggleQuality() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isHighQuality) Color(0xFF007BFF) else Color.DarkGray),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(if (isHighQuality) "HD" else "SD", fontWeight = FontWeight.Bold)
                }

                // 2. FLASH (Icono)
                Button(
                    onClick = { viewModel.toggleFlash() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFlashOn) Color(0xFFFFC107) else Color.DarkGray,
                        contentColor = if (isFlashOn) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.Star else Icons.Default.Close,
                        contentDescription = "Flash",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 🛠️ 3. NUEVO: VOLTEAR CÁMARA (Icono + Texto dinámico)
                Button(
                    onClick = { viewModel.toggleCamera() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFrontCamera) Color(0xFF06B6D4) else Color.DarkGray, // Slate oscuro o Cyan
                        contentColor = if (isFrontCamera) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Voltear cámara",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    // Aquí usamos 'isFrontCamera', resolviendo el warning
                    Text(if (isFrontCamera) "FRONT" else "BACK", style = MaterialTheme.typography.labelSmall)
                }
            }

            // --- FILA DE OBTURADOR ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.setRole(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545)) // Rojo
                ) {
                    Text("Salir")
                }

                // BOTÓN FOTO
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color.Gray, CircleShape)
                        .clickable { viewModel.triggerRemotePhoto() }
                )

                // Espaciador para centrar el botón de foto
                Spacer(Modifier.width(60.dp))
            }
        }
    }
}