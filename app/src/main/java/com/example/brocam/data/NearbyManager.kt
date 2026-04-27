package com.example.brocam.data

import android.content.Context
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NearbyManager(private val context: Context) {
    private val client = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.example.brocam.V2_SERVICE"

    private val STRATEGY = Strategy.P2P_STAR

    private val _connectionStatus = MutableStateFlow("Desconectado")
    val connectionStatus = _connectionStatus.asStateFlow()

    fun startAdvertising(callback: ConnectionLifecycleCallback) {
        val localName = android.os.Build.MODEL // 🛠️ EXTRAE EL NOMBRE REAL DEL CELULAR
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()

        client.startAdvertising(localName, SERVICE_ID, callback, options)
            .addOnSuccessListener {
                Toast.makeText(context, "✅ Lente visible. Esperando...", Toast.LENGTH_SHORT).show()
                _connectionStatus.value = "Anunciando..."
            }
            .addOnFailureListener { e ->
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
                Toast.makeText(context, "❌ Error al buscar: ${e.message}", Toast.LENGTH_LONG).show()
                _connectionStatus.value = "Error Buscando"
            }
    }

    fun acceptConnection(id: String, callback: PayloadCallback) {
        client.acceptConnection(id, callback)
            .addOnFailureListener { Toast.makeText(context, "Falló al aceptar conexión", Toast.LENGTH_SHORT).show() }
    }

    fun requestConnection(id: String, callback: ConnectionLifecycleCallback) {
        val localName = android.os.Build.MODEL // 🛠️ EXTRAE EL NOMBRE REAL DEL CELULAR
        client.requestConnection(localName, id, callback)
            .addOnFailureListener { Toast.makeText(context, "Falló al pedir conexión", Toast.LENGTH_SHORT).show() }
    }

    object BroCamLogger {
        private var frameCount = 0
        private var totalBytes = 0L
        private var lastLogTime = System.currentTimeMillis()

        fun logFrameTransmission(payloadSizeBytes: Int) {
            frameCount++
            totalBytes += payloadSizeBytes
            val now = System.currentTimeMillis()

            if (now - lastLogTime >= 1000) {
                val avgSizeKb = if (frameCount > 0) (totalBytes / frameCount) / 1024 else 0
                val totalKbps = totalBytes / 1024

                android.util.Log.d("BroCam_Telemetry",
                    "📊 FPS Enviados: $frameCount | Peso Promedio: $avgSizeKb KB/frame | Ancho de banda: $totalKbps KB/s")

                frameCount = 0
                totalBytes = 0L
                lastLogTime = now
            }
        }
    }

    fun sendData(id: String, payload: Payload) {
        BroCamLogger.logFrameTransmission(payload.asBytes()?.size ?: 0)
        client.sendPayload(id, payload)
    }

    fun stopAll() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        _connectionStatus.value = "Desconectado"
    }
}