package com.example.brocam

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.nearby.connection.*

@Composable
fun ControlScreen(viewModel: BroCamViewModel) {
    val context = LocalContext.current
    val nearbyManager = viewModel.nearbyManager

    var remoteBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var connectedId by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Iniciando búsqueda...") }
    var isSearching by remember { mutableStateOf(true) }

    BackHandler { viewModel.setRole(null) }

    LaunchedEffect(Unit) {
        nearbyManager.startDiscovery(object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                // AQUÍ SABREMOS SI LO ENCUENTRA
                statusText = "¡ENCONTRADO: ${info.endpointName}!"
                Toast.makeText(context, "Encontré un Lente, conectando...", Toast.LENGTH_SHORT).show()

                nearbyManager.requestConnection(id, object : ConnectionLifecycleCallback() {
                    override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                        Toast.makeText(context, "Iniciando conexión...", Toast.LENGTH_SHORT).show()
                        nearbyManager.acceptConnection(id, object : PayloadCallback() {
                            override fun onPayloadReceived(endpointId: String, p: Payload) {
                                val bytes = p.asBytes() ?: return
                                if (bytes.size > 100) {
                                    remoteBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                }
                            }
                            override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {}
                        })
                    }

                    override fun onConnectionResult(id: String, res: ConnectionResolution) {
                        if (res.status.isSuccess) {
                            connectedId = id
                            isSearching = false
                            statusText = "Conectado"
                            Toast.makeText(context, "¡CONECTADO!", Toast.LENGTH_SHORT).show()
                            // Pedimos video inmediatamente
                            nearbyManager.sendData(id, Payload.fromBytes("START_STREAM".toByteArray()))
                        } else {
                            statusText = "Error: ${res.status.statusMessage}"
                            Toast.makeText(context, "Error de conexión: ${res.status.statusCode}", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onDisconnected(id: String) {
                        isSearching = true
                        connectedId = null
                        remoteBitmap = null
                        statusText = "Se desconectó"
                    }
                })
            }

            override fun onEndpointLost(id: String) {
                statusText = "Se perdió la señal del Lente"
            }
        })
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (isSearching || remoteBitmap == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(statusText, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Asegúrate de que el Lente esté en pantalla", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                remoteBitmap?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
        }

        Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = { viewModel.setRole(null) }) { Text("Salir") }
                Button(
                    onClick = { connectedId?.let { nearbyManager.sendData(it, Payload.fromBytes("TAKE_PHOTO".toByteArray())) } },
                    enabled = connectedId != null
                ) { Text("📸 DISPARAR") }
            }
        }
    }
}