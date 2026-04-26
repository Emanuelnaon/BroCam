package com.example.brocam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brocam.AppRole
import com.example.brocam.data.RecentDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentDevices: List<RecentDevice>,
    onRoleSelected: (AppRole) -> Unit,
    onSettingsClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    // Paleta de colores "Industrial Dark"
    val bgColor = Color(0xFF0F172A) // Slate 900
    val cardColor = Color(0xFF1E293B) // Slate 800
    val lenteColor = Color(0xFF06B6D4) // Cyan 500
    val controlColor = Color(0xFFF59E0B) // Amber 500

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = { Text("BROCAM", fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                actions = {
                    IconButton(onClick = onHelpClick) {
                        Icon(Icons.Default.Info, contentDescription = "Ayuda", tint = Color.LightGray)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración", tint = Color.LightGray)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Selecciona tu rol para esta sesión",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(30.dp))

            // BOTÓN LENTE
            RoleCard(
                title = "SER LENTE",
                subtitle = "Transmite video y recibe órdenes",
                accentColor = lenteColor,
                cardColor = cardColor,
                onClick = { onRoleSelected(AppRole.LENTE) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // BOTÓN CONTROL
            RoleCard(
                title = "SER CONTROL",
                subtitle = "Visualiza y dirige a distancia",
                accentColor = controlColor,
                cardColor = cardColor,
                onClick = { onRoleSelected(AppRole.CONTROL) }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // SECCIÓN: DISPOSITIVOS FRECUENTES (Estructura visual para el paso 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Conexiones Recientes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lista temporal (Mockup) hasta que conectemos la base de datos local
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(2) { index ->
                    RecentDeviceItem(
                        deviceName = if (index == 0) "Moto G56 (Lente)" else "Tablet Samsung",
                        date = if (index == 0) "Hoy, 14:30" else "Ayer, 09:15"
                    )
                }
            }
        }
    }
}

@Composable
fun RoleCard(title: String, subtitle: String, accentColor: Color, cardColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(2.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Text(title, color = accentColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = Color.LightGray, fontSize = 14.sp)
        }
    }
}

@Composable
fun RecentDeviceItem(deviceName: String, date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.5f)) // Slate 800 semitransparente
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(deviceName, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Última conexión: $date", color = Color.Gray, fontSize = 12.sp)
        }
        Button(
            onClick = { /* TODO: Reconexión rápida */ },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)) // Slate 700
        ) {
            Text("Conectar")
        }
    }
}