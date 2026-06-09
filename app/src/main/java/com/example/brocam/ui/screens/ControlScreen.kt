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

@Composable
fun ControlScreen(viewModel: BroCamViewModel) {
    val context = LocalContext.current
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isHighQuality by viewModel.isHighQuality.collectAsState()
    val isFlashOn by viewModel.isFlashOn.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var isFrozen by remember { mutableStateOf(false) }
    var drawingLines by remember { mutableStateOf(listOf<List<Pair<Float, Float>>>()) }
    var currentLine by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
    var frozenFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isMenuOpen by remember { mutableStateOf(false) }
    var textureViewRef by remember { mutableStateOf<android.view.TextureView?>(null) }

    // Estado del blindaje
    var isDecoderStarted by remember { mutableStateOf(false) }

    BackHandler { viewModel.setRole(null) }

    val buttonBg = Color.Black.copy(alpha = 0.6f)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!connectionState.isConnected) {
            Text("CONEXIÓN PERDIDA", color = Color.Red, modifier = Modifier.align(Alignment.Center))
        } else if (isStreaming || frozenFrame != null) {
            val isLive = isStreaming && !isFrozen

            // 1. VISOR DE VIDEO (Cero Zoom y Selfie Corregida)
            Box(modifier = Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {

                // 🪄 FIX MATEMÁTICO FINAL: Anula el zoom y arregla la rotación frontal
                val videoModifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .graphicsLayer {
                        // 1. ROTACIÓN: 90 para trasera, 270 para frontal (quita el "de cabeza")
                        rotationZ = if (isFrontCamera) 270f else 90f

                        // 2. ESCALA Y ESPEJO
                        val scaleRatio = 4f / 3f
                        scaleX = scaleRatio
                        // Al girar la imagen, el negativo en Y crea el efecto espejo correcto
                        scaleY = if (isFrontCamera) -scaleRatio else scaleRatio
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

                    // BLINDAJE DE RECONEXIÓN
                    LaunchedEffect(isStreaming) {
                        if (isStreaming && !isDecoderStarted && textureViewRef != null && textureViewRef!!.isAvailable) {
                            viewModel.startH265Decoder(android.view.Surface(textureViewRef!!.surfaceTexture))
                            isDecoderStarted = true
                        }
                    }

                } else if (frozenFrame != null) {
                    Image(
                        bitmap = frozenFrame!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = videoModifier
                    )
                }
            }

            // 2. UI FLOTANTE (Menús y Botones)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { viewModel.toggleQuality() }, colors = ButtonDefaults.buttonColors(containerColor = if (isHighQuality) Color.Cyan else buttonBg)) {
                    Text(if (isHighQuality) "HD" else "SD", color = if (isHighQuality) Color.Black else Color.White)
                }
                IconButton(onClick = { isMenuOpen = !isMenuOpen }, modifier = Modifier.background(buttonBg, CircleShape)) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                }
            }

            // Barra inferior principal
            Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.setRole(null) }, modifier = Modifier.background(Color.Red, CircleShape)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                }
                Box(modifier = Modifier.size(72.dp).background(Color.White, CircleShape).clickable { viewModel.triggerRemotePhoto() })
                IconButton(onClick = { viewModel.toggleCamera() }, modifier = Modifier.background(buttonBg, CircleShape)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                }
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }}