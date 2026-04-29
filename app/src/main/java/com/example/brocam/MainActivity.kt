package com.example.brocam

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.brocam.ui.screens.CameraScreen
import com.example.brocam.ui.screens.ControlScreen
import com.example.brocam.ui.screens.WelcomeScreen
import com.example.brocam.ui.viewmodel.BroCamViewModel
import com.example.brocam.AppRole
import com.example.brocam.ui.screens.HomeScreen




class MainActivity : ComponentActivity() {


    private val permissionsToRequest = mutableListOf(
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
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }.launch(permissionsToRequest)

        // SOLUCIÓN: Iniciamos el ViewModel a la antigua (Compatible 100%)
        val viewModel = ViewModelProvider(this).get(BroCamViewModel::class.java)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    val currentRole by viewModel.currentRole.collectAsState()
                    val recentDevices by viewModel.recentDevices.collectAsState()

                    // SOLUCIÓN: Dirigir a la pantalla correcta según el rol
                    when (currentRole) {
                        null -> {
                            // Si no hay rol, mostramos la Home
                            HomeScreen(
                                recentDevices = recentDevices,
                                onRoleSelected = { role, targetname -> viewModel.setRole(role, targetname) },
                                onSettingsClick = { /* TODO */ },
                                onHelpClick = { /* TODO */ }
                            )
                        }
                        AppRole.LENTE -> {
                            // Si eligió Lente, abrimos la cámara
                            CameraScreen(viewModel = viewModel)
                        }
                        AppRole.CONTROL -> {
                            // Si eligió Control, abrimos la pantalla del receptor
                            ControlScreen(viewModel = viewModel)
                        }
                    }

                }
            }
        }
    }
}