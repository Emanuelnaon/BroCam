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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onRoleSelected: (AppRole, String?) -> Unit,
    onSettingsClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    // Paleta de colores "Industrial Dark"
    val bgColor = Color(0xFF0F172A) // Slate 900
    val cardColor = Color(0xFF1E293B) // Slate 800
    val lenteColor = Color(0xFF06B6D4) // Cyan 500
    val controlColor = Color(0xFFF59E0B) // Amber 500

    // Variables para controlar los paneles deslizables
    var showInfoSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = { Text("BROCAM", fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 2.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                actions = {
                    IconButton(onClick = { showInfoSheet = true }){
                        Icon(Icons.Default.Info, contentDescription = "Ayuda", tint = Color.LightGray)
                    }
                    IconButton(onClick ={ showSettingsSheet = true }){
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
                onClick = { onRoleSelected(AppRole.LENTE, null) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // BOTÓN CONTROL
            RoleCard(
                title = "SER CONTROL",
                subtitle = "Visualiza y dirige a distancia",
                accentColor = controlColor,
                cardColor = cardColor,
                onClick = { onRoleSelected(AppRole.CONTROL, null) }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // SECCIÓN: DISPOSITIVOS FRECUENTES
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Conexiones Recientes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LISTA REAL CONECTADA A LA BASE DE DATOS LOCAL
            if (recentDevices.isEmpty()) {
                Text("No hay conexiones recientes.", color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recentDevices.size) { index ->
                        val device = recentDevices[index]
                        RecentDeviceItem(
                            deviceName = device.deviceName,
                            date = device.getFormattedDate(),
                            // 🛠️ NUEVO: Lógica de autoconexión
                            onConnectClick = {
                                val realName = device.deviceName.substringAfter("(").substringBefore(")")
                                val autoRole = if (device.deviceName.startsWith("Lente")) AppRole.CONTROL else AppRole.LENTE
                                onRoleSelected(autoRole, realName)
                            }
                        )
                    }
                }
            }
        }
    }

    // --- PANEL DE INFORMACIÓN (TUTORIAL) ---
    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            containerColor = cardColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Manual de Uso Rápido", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "1. Lente vs Control: Un dispositivo debe ser el 'Lente' (cámara) y otro el 'Control' (pantalla remota). Inicia siempre el Lente primero.\n\n" +
                            "2. Fotos HD: Las fotos de alta resolución se guardan directamente en la galería del dispositivo LENTE, no en el Control.\n\n" +
                            "3. Ahorro de Energía: Si eres el Lente, usa el botón de 'Modo Ahorro' para apagar la pantalla y ahorrar batería mientras sigues transmitiendo.",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { showInfoSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = lenteColor)
                ) {
                    Text("Entendido", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // --- PANEL DE CONFIGURACIÓN (AJUSTES) ---
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = cardColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("Configuración de BroCam", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ajustes generales para tus sesiones de trabajo.", color = Color.Gray, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(24.dp))

                Text("Próximamente: Ajustes de calidad por defecto y gestión de almacenamiento local.", color = lenteColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { showSettingsSheet = false },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Cerrar", color = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))
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
fun RecentDeviceItem(deviceName: String, date: String, onConnectClick: () -> Unit) { // 🛠️ Añadimos onConnectClick
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.5f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(deviceName, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Última conexión: $date", color = Color.Gray, fontSize = 12.sp)
        }
        Button(
            onClick = onConnectClick, // 🛠️ Usamos la función aquí
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
        ) {
            Text("Conectar")
        }
    }
}