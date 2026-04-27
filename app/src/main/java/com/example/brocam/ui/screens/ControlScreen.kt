package com.example.brocam.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.brocam.ui.viewmodel.BroCamViewModel

@Composable
fun ControlScreen(viewModel: BroCamViewModel) {
    // Borramos isStreaming para quitar el warning, no la usamos aquí.
    val isHighQuality by viewModel.isHighQuality.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState() // 🛠️ ¡USADA AHORA!
    val connectionState by viewModel.connectionState.collectAsState()
    val remoteFrame by viewModel.receivedFrame.collectAsState()

    BackHandler { viewModel.setRole(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (remoteFrame != null) {
            Image(
                bitmap = remoteFrame!!.asImageBitmap(),
                contentDescription = "Vista remota",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Text("Conectando...", color = Color.White)
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