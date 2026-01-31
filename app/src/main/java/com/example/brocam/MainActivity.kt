package com.example.brocam // Asegúrate de que este sea TU paquete

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class ScreenState { WELCOME, CAMERA, CONTROL }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}

@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf(ScreenState.WELCOME) }

    // 1. Lanzador para la Cámara (Modo Lente)
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) currentScreen = ScreenState.CAMERA
    }

    // 2. Lanzador para Nearby/Bluetooth (Modo Control)
    val controlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) currentScreen = ScreenState.CONTROL
    }

    when (currentScreen) {
        ScreenState.WELCOME -> WelcomeScreen(
            onLenteClick = {
                cameraLauncher.launch(Manifest.permission.CAMERA)
            },
            onControlClick = {
                // Lista de permisos según la versión de Android
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                } else {
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                controlLauncher.launch(permissions)
            }
        )
        ScreenState.CAMERA -> CameraScreen()
        ScreenState.CONTROL -> ControlScreen(
            onBackPressed = { currentScreen = ScreenState.WELCOME }
        )
    }
} // <--- Esta es la llave que faltaba

@Composable
fun WelcomeScreen(onLenteClick: () -> Unit, onControlClick: () -> Unit) {
    var selectedProfile by remember { mutableStateOf(UserProfile.STANDARD) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "BroCam MVP", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onLenteClick,
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text("USAR COMO LENTE")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onControlClick,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("USAR COMO CONTROL")
        }

        Spacer(modifier = Modifier.height(48.dp))
        Text(text = "Perfil seleccionado:")
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selectedProfile == UserProfile.STANDARD, onClick = { selectedProfile = UserProfile.STANDARD })
            Text("Estándar")
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(selected = selectedProfile == UserProfile.SOLO_TRAVELER, onClick = { selectedProfile = UserProfile.SOLO_TRAVELER })
            Text("Viajero")
        }
    }
}