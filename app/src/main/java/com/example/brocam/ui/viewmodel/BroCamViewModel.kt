package com.example.brocam.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brocam.AppRole
import com.example.brocam.UserProfile
import com.example.brocam.data.NearbyManager
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BroCamViewModel(application: Application) : AndroidViewModel(application) {
    private val nearbyManager = NearbyManager(application.applicationContext)

    // --- PATRÓN PRODUCER-CONSUMER (BUFFER INTELIGENTE) ---
    // Capacity = 1 + DROP_OLDEST: Esto es la magia.
    // Si el sistema está ocupado enviando un frame y llega otro,
    // BORRA el anterior y se queda con el nuevo. Cero acumulación en el Main Thread.
    private val frameChannel = Channel<ByteArray>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // --- ESTADOS ---
    private val _currentRole = MutableStateFlow<AppRole?>(null)
    val currentRole = _currentRole.asStateFlow()
    var selectedProfile: UserProfile = UserProfile.STANDARD

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

    // Rotación
    private val _rotationDegrees = MutableStateFlow(0)
    val rotationDegrees = _rotationDegrees.asStateFlow()

    // Última foto
    private val _lastCapturedPhoto = MutableStateFlow<Bitmap?>(null)
    val lastCapturedPhoto = _lastCapturedPhoto.asStateFlow()

    private val _shutterEvent = Channel<Boolean>()
    val shutterEvent = _shutterEvent.receiveAsFlow()

    data class ConnectionState(
        val isConnected: Boolean = false,
        val message: String = "Desconectado",
        val connectedEndpointId: String? = null
    )
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState = _connectionState.asStateFlow()

    // --- INICIALIZACIÓN DEL CONSUMIDOR (WORKER) ---
    init {
        startFrameConsumer()
    }

    private fun startFrameConsumer() {
        // Este "Worker" vive en el hilo IO y nunca toca el Main Thread
        viewModelScope.launch(Dispatchers.IO) {
            // El bucle 'for' se suspende (duerme) si no hay datos. Eficiencia pura.
            for (bytes in frameChannel) {
                val endpointId = _connectionState.value.connectedEndpointId
                if (endpointId != null && _isStreaming.value) {
                    try {
                        // Enviamos a la red (Tarea Bloqueante)
                        nearbyManager.sendData(endpointId, Payload.fromBytes(bytes))
                    } catch (e: Exception) {
                        // Manejo silencioso para no romper el flujo
                    }
                }
            }
        }
    }

    // --- ENTRADA DEL PRODUCTOR ---
    // Esta función es llamada por la Cámara (Productor)
    fun enqueueFrame(bytes: ByteArray) {
        // trySend es no-bloqueante e instantáneo.
        // Si el canal está lleno, DROP_OLDEST elimina el viejo.
        frameChannel.trySend(bytes)
    }

    // --- MANEJO DE ROLES ---
    fun setRole(role: AppRole?) {
        _currentRole.value = role
        when (role) {
            AppRole.LENTE -> startLenteMode()
            AppRole.CONTROL -> startControlMode()
            null -> disconnect()
        }
    }

    // --- RECEPCIÓN DE DATOS ---
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
                        // Decodificar imagen en IO
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        _receivedFrame.value = bitmap
                    }
                }
            }
        }
        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {}
    }

    // --- FUNCIONES PÚBLICAS ---
    private fun startLenteMode() {
        _connectionState.value = ConnectionState(message = "Haciéndome visible...")
        nearbyManager.startAdvertising(object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) { nearbyManager.acceptConnection(id, payloadCallback) }
            override fun onConnectionResult(id: String, result: ConnectionResolution) { if (result.status.isSuccess) _connectionState.value = ConnectionState(isConnected = true, message = "Listo", connectedEndpointId = id) }
            override fun onDisconnected(id: String) { resetState() }
        })
    }

    private fun startControlMode() {
        _connectionState.value = ConnectionState(message = "Buscando...")
        nearbyManager.startDiscovery(object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                nearbyManager.requestConnection(id, object : ConnectionLifecycleCallback() {
                    override fun onConnectionInitiated(id: String, info: ConnectionInfo) { nearbyManager.acceptConnection(id, payloadCallback) }
                    override fun onConnectionResult(id: String, result: ConnectionResolution) { if (result.status.isSuccess) { _connectionState.value = ConnectionState(isConnected = true, message = "Conectado", connectedEndpointId = id); sendCommand("START_STREAM") } }
                    override fun onDisconnected(id: String) { resetState() }
                })
            }
            override fun onEndpointLost(id: String) {}
        })
    }

    fun toggleQuality() {
        val newValue = !_isHighQuality.value
        _isHighQuality.value = newValue
        sendCommand(if (newValue) "QUALITY_HD" else "QUALITY_SD")
    }

    fun toggleFlash() {
        val newValue = !_isFlashOn.value
        _isFlashOn.value = newValue
        sendCommand(if (newValue) "FLASH_ON" else "FLASH_OFF")
    }

    fun toggleCamera() {
        val newValue = !_isFrontCamera.value
        _isFrontCamera.value = newValue
        sendCommand(if (newValue) "CAM_FRONT" else "CAM_BACK")
    }

    fun sendCommand(cmd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _connectionState.value.connectedEndpointId?.let { nearbyManager.sendData(it, Payload.fromBytes(cmd.toByteArray())) }
        }
    }

    fun triggerRemotePhoto() { sendCommand("TAKE_PHOTO") }

    fun disconnect() {
        nearbyManager.stopAll()
        resetState()
        _currentRole.value = null
    }

    private fun resetState() {
        _connectionState.value = ConnectionState(message = "Desconectado")
        _isStreaming.value = false
        _receivedFrame.value = null
        _isHighQuality.value = false
        _isFlashOn.value = false
        _isFrontCamera.value = false
        // El canal se limpia solo
    }

    override fun onCleared() { super.onCleared(); nearbyManager.stopAll() }
}