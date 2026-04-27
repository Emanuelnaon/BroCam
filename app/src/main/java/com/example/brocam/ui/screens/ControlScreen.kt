package com.example.brocam.ui.screens
/* ControlScreen */

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
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
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isHighQuality by viewModel.isHighQuality.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val remoteFrame by viewModel.receivedFrame.collectAsState() // 🛠️ FALTABA ESTA LÍNEA

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

        // 🛠️ NUEVO: AVISO REMOTO DE FOTO GUARDADA
        if (connectionState.message.contains("Foto")) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF59E0B).copy(alpha = 0.9f))
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
            Row(
                modifier = Modifier.padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { viewModel.toggleQuality() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isHighQuality) Color.Blue else Color.DarkGray),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(if (isHighQuality) "HD" else "SD")
                }

                Button(
                    onClick = { viewModel.toggleFlash() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFlashOn) Color.Yellow else Color.DarkGray,
                        contentColor = if (isFlashOn) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.Star else Icons.Default.Close,
                        contentDescription = "Flash"
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { viewModel.setRole(null) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("Salir")
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color.Gray, CircleShape)
                        .clickable { viewModel.triggerRemotePhoto() }
                )

                Spacer(Modifier.width(60.dp))
            }
        }
    }
}