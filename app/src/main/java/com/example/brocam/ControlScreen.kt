package com.example.brocam

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.nearby.connection.*

@Composable
fun ControlScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val nearbyManager = remember { NearbyManager(context) }

    val discoveredLenses = remember { mutableStateListOf<Pair<String, String>>() }
    var connectedEndpointId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Buscando BroCam...") }

    // --- LA VARIABLE MÁGICA: Aquí guardamos el frame de video actual ---
    var remoteFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            nearbyManager.acceptConnection(endpointId, object : PayloadCallback() {
                override fun onPayloadReceived(id: String, payload: Payload) {
                    // 1. Recibimos los bytes
                    val bytes = payload.asBytes()
                    if (bytes != null) {
                        // 2. Los convertimos en imagen (Bitmap)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        // 3. Actualizamos la pantalla
                        remoteFrame = bitmap
                    }
                }
                override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
            })
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpointId = endpointId
                status = "¡CONECTADO!"
                nearbyManager.stopDiscovery()
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpointId = null
            remoteFrame = null
            status = "Lente desconectado"
        }
    }

    LaunchedEffect(Unit) {
        nearbyManager.startDiscovery(object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                if (!discoveredLenses.any { it.first == id }) discoveredLenses.add(id to info.endpointName)
            }
            override fun onEndpointLost(id: String) { discoveredLenses.removeAll { it.first == id } }
        })
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("MODO CONTROL", style = MaterialTheme.typography.titleLarge)
        Text(status, color = if (connectedEndpointId != null) Color(0xFF4CAF50) else Color.Gray)

        Spacer(modifier = Modifier.height(16.dp))

        if (connectedEndpointId == null) {
            // ... (Tu código de LazyColumn para buscar dispositivos sigue igual)
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(discoveredLenses) { (id, name) ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { nearbyManager.requestConnection(id, connectionCallback) }) {
                        ListItem(headlineContent = { Text(name) }, supportingContent = { Text("Toca para conectar") })
                    }
                }
            }
        } else {
            // --- EL VISOR DE VIDEO REAL ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                remoteFrame?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Vista remota",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit // Ajusta la imagen sin deformar
                    )
                } ?: Text("Esperando señal de video...", color = Color.White)
            }

            // BOTÓN DE DISPARO
            Button(
                onClick = {
                    // Enviamos el comando de texto al Lente
                    val command = "TAKE_PHOTO".toByteArray()
                    nearbyManager.sendData(connectedEndpointId!!, Payload.fromBytes(command))
                },
                modifier = Modifier.padding(24.dp).size(80.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { }
        }

        Button(onClick = { nearbyManager.stopAll(); onBackPressed() }) { Text("Salir") }
    }
}