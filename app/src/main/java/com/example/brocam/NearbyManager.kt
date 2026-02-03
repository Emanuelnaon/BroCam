package com.example.brocam

import android.content.Context
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NearbyManager(private val context: Context) {
    private val client = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.example.brocam.SERVICE_ID" // Identificador único

    // Estrategia P2P_STAR: Ideal para 1 Lente y 1 Control
    private val STRATEGY = Strategy.P2P_STAR

    private val _connectionStatus = MutableStateFlow("Desconectado")
    val connectionStatus = _connectionStatus.asStateFlow()

    fun startAdvertising(callback: ConnectionLifecycleCallback) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        client.startAdvertising("Lente_BroCam", SERVICE_ID, callback, options)
            .addOnSuccessListener {
                Toast.makeText(context, "✅ Lente visible. Esperando...", Toast.LENGTH_SHORT).show()
                _connectionStatus.value = "Anunciando..."
            }
            .addOnFailureListener { e ->
                // AQUÍ VERÁS SI FALLA
                Toast.makeText(context, "❌ Error al anunciar: ${e.message}", Toast.LENGTH_LONG).show()
                _connectionStatus.value = "Error: ${e.message}"
            }
    }

    fun startDiscovery(callback: EndpointDiscoveryCallback) {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()

        client.startDiscovery(SERVICE_ID, callback, options)
            .addOnSuccessListener {
                Toast.makeText(context, "🔍 Buscando Lente...", Toast.LENGTH_SHORT).show()
                _connectionStatus.value = "Buscando..."
            }
            .addOnFailureListener { e ->
                // AQUÍ VERÁS SI FALLA
                Toast.makeText(context, "❌ Error al buscar: ${e.message}", Toast.LENGTH_LONG).show()
                _connectionStatus.value = "Error Buscando"
            }
    }

    fun acceptConnection(id: String, callback: PayloadCallback) {
        client.acceptConnection(id, callback)
            .addOnFailureListener { Toast.makeText(context, "Falló al aceptar conexión", Toast.LENGTH_SHORT).show() }
    }

    fun requestConnection(id: String, callback: ConnectionLifecycleCallback) {
        client.requestConnection("Control_Remoto", id, callback)
            .addOnFailureListener { Toast.makeText(context, "Falló al pedir conexión", Toast.LENGTH_SHORT).show() }
    }

    fun sendData(id: String, payload: Payload) {
        client.sendPayload(id, payload)
    }

    fun stopAll() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        _connectionStatus.value = "Desconectado"
    }
}