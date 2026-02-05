package com.example.brocam.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.brocam.AppRole

@Composable
fun WelcomeScreen(onRoleSelected: (AppRole) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Bienvenido a BroCam", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { onRoleSelected(AppRole.LENTE) }) {
            Text("SER CÁMARA (LENTE)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { onRoleSelected(AppRole.CONTROL) }) {
            Text("SER CONTROL REMOTO")
        }
    }
}