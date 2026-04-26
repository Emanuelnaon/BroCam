package com.example.brocam.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brocam.AppRole
import com.example.brocam.UserProfile
import com.example.brocam.data.NearbyManager
import com.example.brocam.data.HistoryManager
import com.example.brocam.data.RecentDevice
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BroCamViewModel(application: Application) : AndroidViewModel(application) {
    private val nearbyManager = NearbyManager(application.applicationContext)
    private val historyManager = HistoryManager(application.applicationContext)

    private val _recentDevices = MutableStateFlow<List<RecentDevice>>(emptyList())
    val recentDevices = _recentDevices.asStateFlow()

    // =================================================================
    // 🚀 CONFIGURACIÓN DE PRODUCCIÓN
    // =================================================================
    private val isNetworkStressTest = false // APAGADO: Sin lag artificial
    private val isLoopbackTest = false      // APAGADO: Listo para 2 teléfonos reales
    // =================================================================

    // --- PRODUCTOR-CONSUMIDOR ---
    private val frameChannel = Channel<ByteArray>(
        capacity = 3,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var consumerJob: Job? = null

    // --- ESTADOS ---
    private val _currentRole = MutableStateFlow<AppRole?>(null)
    val currentRole = _currentRole.asStateFlow()

    private val _receivedFrame = MutableStateFlow<Bitmap?>(null)
    val receivedFrame = _receivedFrame.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()

    private val _isHighQuality = MutableStateFlow(false)
    val isHighQuality = _isHighQuality.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn = _isFlashOn.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera = _isFrontCamera.asStateFlow()

    private val _rotationDegrees = MutableStateFlow(0)
    val rotationDegrees = _rotationDegrees.asStateFlow()

    private val _shutterEvent = Channel<Boolean>()
    val shutterEvent = _shutterEvent.receiveAsFlow()

    data class ConnectionState(
        val isConnected: Boolean = false,
        val message: String = "Desconectado",
        val connectedEndpointId: String? = null
    )
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState = _connectionState.asStateFlow()

    init {
        loadHistory() // <-- AÑADIR ESTO
        startFrameConsumer()
    }

    // <-- AÑADIR ESTA FUNCIÓN NUEVA
    private fun loadHistory() {
        _recentDevices.value = historyManager.getRecentDevices()
    }

    // Dentro de BroCamViewModel.kt

    @Volatile
    private var isSendingFrame = false

    private fun startFrameConsumer() {
        consumerJob?.cancel()
        consumerJob = viewModelScope.launch(Dispatchers.IO) {
            for (frameBytes in frameChannel) {
                val endpointId = _connectionState.value.connectedEndpointId
                // Solo procesamos si hay conexión, estamos streameando y la red NO está ocupada
                if (endpointId != null && _isStreaming.value && !isSendingFrame) {
                    isSendingFrame = true
                    try {
                        // Enviar a través de Nearby
                        nearbyManager.sendData(endpointId, Payload.fromBytes(frameBytes))

                        // Pequeña pausa para permitir que el hardware de red respire
                        //kotlinx.coroutines.delay(10)
                    } catch (e: Exception) {
                        Log.e("BroCam", "Error de red: ${e.message}")
                    } finally {
                        // Liberamos la bandera para permitir el siguiente frame
                        isSendingFrame = false
                    }
                }
            }
        }
    }

    // --- EL PRODUCTOR (Cámara) ---
    fun enqueueFrame(bytes: ByteArray) {
        // En Producción, simplemente metemos al canal.
        // El canal se encarga de descartar si la red va lenta.
        frameChannel.trySend(bytes)
    }

    fun setRole(role: AppRole?) {
        _currentRole.value = role
        when (role) {
            AppRole.LENTE -> startLenteMode()
            AppRole.CONTROL -> startControlMode()
            null -> disconnect()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { bytes ->
                viewModelScope.launch(Dispatchers.IO) {
                    if (bytes.size < 100) {
                        val command = String(bytes)
                        withContext(Dispatchers.Main) {
                            when (command) {
                                "START_STREAM" -> _isStreaming.value = true
                                "STOP_STREAM" -> _isStreaming.value = false
                                "TAKE_PHOTO" -> _shutterEvent.send(true)
                                "QUALITY_HD" -> _isHighQuality.value = true
                                "QUALITY_SD" -> _isHighQuality.value = false
                                "FLASH_ON" -> _isFlashOn.value = true
                                "FLASH_OFF" -> _isFlashOn.value = false
                                "CAM_FRONT" -> { _isFrontCamera.value = true; _rotationDegrees.value = 270 }
                                "CAM_BACK" -> { _isFrontCamera.value = false; _rotationDegrees.value = 90 }
                            }
                        }
                    } else {
                        // Decodificación real en el teléfono CONTROL
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        _receivedFrame.value = bitmap
                    }
                }
            }
        }
        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {}
    }

    private fun startLenteMode() {
        _connectionState.value = ConnectionState(message = "Haciéndome visible...")
        // Ya no forzamos conexión falsa. Esperamos a Nearby real.
        nearbyManager.startAdvertising(object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) { nearbyManager.acceptConnection(id, payloadCallback) }
            override fun onConnectionResult(id: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    _connectionState.value = ConnectionState(isConnected = true, message = "Listo", connectedEndpointId = id)
                    // Nota: Esperamos a que el Control mande START_STREAM para activar _isStreaming
                    historyManager.saveDevice(id, "Control ($id)")
                    loadHistory()
                }
            }
            override fun onDisconnected(id: String) { resetState() }
        })
    }

    private fun startControlMode() {
        _connectionState.value = ConnectionState(message = "Buscando...")
        nearbyManager.startDiscovery(object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                nearbyManager.requestConnection(id, object : ConnectionLifecycleCallback() {
                    override fun onConnectionInitiated(id: String, info: ConnectionInfo) { nearbyManager.acceptConnection(id, payloadCallback) }
                    override fun onConnectionResult(id: String, result: ConnectionResolution) {
                        if (result.status.isSuccess) {
                            _connectionState.value = ConnectionState(isConnected = true, message = "Conectado", connectedEndpointId = id)
                            sendCommand("START_STREAM") // El Control inicia la fiesta
                            historyManager.saveDevice(id, "Lente ($id)")
                            loadHistory()
                        }
                    }
                    override fun onDisconnected(id: String) { resetState() }
                })
            }
            override fun onEndpointLost(id: String) {}
        })
    }

    // ... Toggles y comandos (igual que siempre) ...
    fun toggleQuality() {
        val nv = !_isHighQuality.value
        _isHighQuality.value = nv
        sendCommand(if (nv) "QUALITY_HD" else "QUALITY_SD")
    }

    fun toggleFlash() {
        val nv = !_isFlashOn.value
        _isFlashOn.value = nv
        sendCommand(if (nv) "FLASH_ON" else "FLASH_OFF")
    }

    fun toggleCamera() {
        val nv = !_isFrontCamera.value
        _isFrontCamera.value = nv
        sendCommand(if (nv) "CAM_FRONT" else "CAM_BACK")
    }

    fun sendCommand(cmd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val endpointId = _connectionState.value.connectedEndpointId
            if (endpointId != null) {
                nearbyManager.sendData(endpointId, Payload.fromBytes(cmd.toByteArray()))
            }
        }
    }

    fun triggerRemotePhoto() { sendCommand("TAKE_PHOTO") }

    fun disconnect() {
        nearbyManager.stopAll()
        resetState()
        _currentRole.value = null
    }

    private fun resetState() {
        _connectionState.value = ConnectionState(isConnected = false, message = "Desconectado", connectedEndpointId = null)
        _isStreaming.value = false
        _receivedFrame.value = null

        _isFlashOn.value = false
        _isHighQuality.value = false
    }

    override fun onCleared() {
        super.onCleared()
        consumerJob?.cancel()
        nearbyManager.stopAll()
    }
}