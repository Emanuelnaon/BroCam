package com.example.brocam

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels // Importante para usar el ViewModel
import androidx.compose.runtime.*
import com.example.brocam.ui.theme.BroCamTheme

class MainActivity : ComponentActivity() {

    // Inyectamos el cerebro (ViewModel)
    private val viewModel by viewModels<BroCamViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- GESTIÓN DE PERMISOS (Igual que antes) ---
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
        launcher.launch(permissions.toTypedArray())

        setContent {
            BroCamTheme {
                // Observamos el rol actual desde el ViewModel
                val role by viewModel.currentRole.collectAsState()

                // NAVEGACIÓN BASADA EN ESTADO
                when (role) {
                    null -> {
                        // Si no hay rol, mostramos la Bienvenida
                        WelcomeScreen { selectedRole, profile ->
                            viewModel.selectedProfile = profile
                            viewModel.setRole(selectedRole)
                        }
                    }
                    AppRole.LENTE -> {
                        // Pasamos el viewModel completo a la pantalla
                        CameraScreen(viewModel)
                    }
                    AppRole.CONTROL -> {
                        // Pasamos el viewModel completo a la pantalla
                        ControlScreen(viewModel)
                    }
                }
            }
        }
    }
}