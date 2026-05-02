package com.example.brocam.ui.viewmodel

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.MediaRecorder
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brocam.AppRole
import com.example.brocam.data.NearbyManager
import com.example.brocam.data.HistoryManager
import com.example.brocam.data.RecentDevice
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BroCamViewModel(application: Application) : AndroidViewModel(application) {
    private val nearbyManager = NearbyManager(application.applicationContext)
    private val historyManager = HistoryManager(application.applicationContext)

    private val _recentDevices = MutableStateFlow<List<RecentDevice>>(emptyList())
    val recentDevices = _recentDevices.asStateFlow()

    private val frameChannel = Channel<ByteArray>(capacity = 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var consumerJob: Job? = null

    // ESTADOS GENERALES
    private val _isSosMode = MutableStateFlow(false)
    val isSosMode = _isSosMode.asStateFlow()
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

    // 🔴 ESTADOS DE REALIDAD AUMENTADA Y DIBUJO
    private val _remotePointer = MutableStateFlow<Pair<Float, Float>?>(null)
    val remotePointer: StateFlow<Pair<Float, Float>?> = _remotePointer

    private val _annotatedImage = MutableStateFlow<android.graphics.Bitmap?>(null)
    val annotatedImage: StateFlow<android.graphics.Bitmap?> = _annotatedImage

    // 🛠️ NUEVO: Estado para el garabato temporal en vivo
    private val _remoteLiveLine = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val remoteLiveLine: StateFlow<List<Pair<Float, Float>>> = _remoteLiveLine

    data class ConnectionState(val isConnected: Boolean = false, val message: String = "Desconectado", val connectedEndpointId: String? = null)
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState = _connectionState.asStateFlow()

    private val deviceNamesMap = mutableMapOf<String, String>()

    init {
        loadHistory()
        startFrameConsumer()
    }

    private fun loadHistory() { _recentDevices.value = historyManager.getRecentDevices() }

    @Volatile
    private var isSendingFrame = false

    private fun startFrameConsumer() {
        consumerJob?.cancel()
        consumerJob = viewModelScope.launch(Dispatchers.IO) {
            for (frameBytes in frameChannel) {
                val endpointId = _connectionState.value.connectedEndpointId
                if (endpointId != null && _isStreaming.value && !isSendingFrame) {
                    isSendingFrame = true
                    try { nearbyManager.sendData(endpointId, Payload.fromBytes(frameBytes)) }
                    catch (e: Exception) { Log.e("BroCam", "Error de red: ${e.message}") }
                    finally { isSendingFrame = false }
                }
            }
        }
    }

    fun enqueueFrame(bytes: ByteArray) { frameChannel.trySend(bytes) }

    private var targetDeviceName: String? = null

    fun setRole(role: AppRole?, targetName: String? = null) {
        targetDeviceName = targetName
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

                    // 🛠️ INTERCEPTOR DE AUDIO CON COLA SEGURA
                    if (bytes.size > 4 && bytes[0] == 'A'.code.toByte() && bytes[1] == 'U'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == ':'.code.toByte()) {
                        startAudioPlayer() // Prende el trabajador (si no estaba prendido ya)
                        val audioData = bytes.copyOfRange(4, bytes.size)
                        audioReceiveChannel.trySend(audioData) // Enfila el audio de forma 100% segura
                        return@launch
                    }

                    if (bytes.size < 8000) {
                        val command = String(bytes)
                        withContext(Dispatchers.Main) {
                            if (command.startsWith("POINTER:")) {
                                val coords = command.substringAfter("POINTER:").split(",")
                                if (coords.size == 2) {
                                    val x = coords[0].toFloatOrNull()
                                    val y = coords[1].toFloatOrNull()
                                    if (x != null && y != null) {
                                        _remotePointer.value = Pair(x, y)
                                        viewModelScope.launch {
                                            kotlinx.coroutines.delay(2000)
                                            if (_remotePointer.value == Pair(x, y)) _remotePointer.value = null
                                        }
                                    }
                                }
                            }
                            // 🛠️ NUEVO: Atrapamos el garabato en vivo
                            else if (command.startsWith("LIVELINE:")) {
                                val coords = command.substringAfter("LIVELINE:").split(",")
                                val newLine = mutableListOf<Pair<Float, Float>>()
                                for (i in 0 until coords.size - 1 step 2) {
                                    val x = coords[i].toFloatOrNull()
                                    val y = coords[i+1].toFloatOrNull()
                                    if (x != null && y != null) newLine.add(Pair(x, y))
                                }
                                if (newLine.isNotEmpty()) {
                                    _remoteLiveLine.value = newLine
                                    // Se auto-borra a los 3 segundos
                                    viewModelScope.launch {
                                        kotlinx.coroutines.delay(3000)
                                        if (_remoteLiveLine.value == newLine) {
                                            _remoteLiveLine.value = emptyList()
                                        }
                                    }
                                }
                            } else {
                                when (command) {
                                    "START_STREAM" -> _isStreaming.value = true
                                    "STOP_STREAM" -> _isStreaming.value = false
                                    "TAKE_PHOTO" -> _shutterEvent.send(true)
                                    "PHOTO_OK" -> _connectionState.value = _connectionState.value.copy(message = "📸 Foto guardada en Lente")
                                    "QUALITY_HD" -> _isHighQuality.value = true
                                    "QUALITY_SD" -> _isHighQuality.value = false
                                    "FLASH_ON" -> _isFlashOn.value = true
                                    "FLASH_OFF" -> _isFlashOn.value = false
                                    "SOS_ON" -> _isSosMode.value = true
                                    "SOS_OFF" -> _isSosMode.value = false
                                    "CAM_FRONT" -> { _isFrontCamera.value = true; _rotationDegrees.value = 270 }
                                    "CAM_BACK" -> { _isFrontCamera.value = false; _rotationDegrees.value = 90 }
                                    "CLEAR_ANNOTATION" -> _annotatedImage.value = null
                                }
                            }
                        }
                    } else {
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        withContext(Dispatchers.Main) {
                            if (_currentRole.value == AppRole.CONTROL) _receivedFrame.value = bitmap
                            else if (_currentRole.value == AppRole.LENTE) _annotatedImage.value = bitmap
                        }
                    }
                }
            }
        }
        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {}
    }
    fun toggleSos() {
        val nv = !_isSosMode.value
        _isSosMode.value = nv
        sendCommand(if (nv) "SOS_ON" else "SOS_OFF")
        // Si prendo el SOS, apago la linterna normal para que no interfieran
        if (nv && _isFlashOn.value) {
            _isFlashOn.value = false
            sendCommand("FLASH_OFF")
        }
    }

    // ... (Funciones de conexión startLenteMode y startControlMode idénticas a las que ya tenías) ...
    private fun startLenteMode() {
        _connectionState.value = ConnectionState(message = "Haciéndome visible...")
        nearbyManager.startAdvertising(object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) { deviceNamesMap[id] = info.endpointName; nearbyManager.acceptConnection(id, payloadCallback) }
            override fun onConnectionResult(id: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    _connectionState.value = ConnectionState(isConnected = true, message = "Listo", connectedEndpointId = id)
                    historyManager.saveDevice(id, "Control (${deviceNamesMap[id] ?: "Dispositivo"})")
                    loadHistory()
                }
            }
            override fun onDisconnected(id: String) {
                _connectionState.value = ConnectionState(isConnected = false, message = "SEÑAL PERDIDA", connectedEndpointId = null)
                _isStreaming.value = false
            }
        })
    }

    private fun startControlMode() {
        _connectionState.value = ConnectionState(message = "Buscando...")
        nearbyManager.startDiscovery(object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                if (targetDeviceName != null && info.endpointName != targetDeviceName) return
                nearbyManager.requestConnection(id, object : ConnectionLifecycleCallback() {
                    override fun onConnectionInitiated(id: String, info: ConnectionInfo) { deviceNamesMap[id] = info.endpointName; nearbyManager.acceptConnection(id, payloadCallback) }
                    override fun onConnectionResult(id: String, result: ConnectionResolution) {
                        if (result.status.isSuccess) {
                            _connectionState.value = ConnectionState(isConnected = true, message = "Conectado", connectedEndpointId = id)
                            sendCommand("START_STREAM")
                            historyManager.saveDevice(id, "Lente (${deviceNamesMap[id] ?: "Dispositivo"})")
                            loadHistory()
                        }
                    }
                    override fun onDisconnected(id: String) {
                        _connectionState.value = ConnectionState(isConnected = false, message = "SEÑAL PERDIDA", connectedEndpointId = null)
                        _isStreaming.value = false
                    }
                })
            }
            override fun onEndpointLost(id: String) {}
        })
    }

    // COMANDOS SIMPLES
    fun toggleQuality() { val nv = !_isHighQuality.value; _isHighQuality.value = nv; sendCommand(if (nv) "QUALITY_HD" else "QUALITY_SD") }
    fun toggleFlash() { val nv = !_isFlashOn.value; _isFlashOn.value = nv; sendCommand(if (nv) "FLASH_ON" else "FLASH_OFF") }
    fun toggleCamera() { val nv = !_isFrontCamera.value; _isFrontCamera.value = nv; sendCommand(if (nv) "CAM_FRONT" else "CAM_BACK") }
    fun triggerRemotePhoto() { sendCommand("TAKE_PHOTO") }

    fun sendPointer(x: Float, y: Float) { sendCommand("POINTER:$x,$y") }

    // 🛠️ NUEVO: Enviar línea rápida
    fun sendLiveLine(line: List<Pair<Float, Float>>) {
        if (line.isEmpty()) return
        val coords = line.joinToString(",") { "${it.first},${it.second}" }
        sendCommand("LIVELINE:$coords")
    }

    fun sendCommand(cmd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val endpointId = _connectionState.value.connectedEndpointId
            if (endpointId != null) nearbyManager.sendData(endpointId, Payload.fromBytes(cmd.toByteArray()))
        }
    }

    fun disconnect() { nearbyManager.stopAll(); resetState(); _currentRole.value = null }
    private fun resetState() { _connectionState.value = ConnectionState(isConnected = false, message = "Desconectado", connectedEndpointId = null); _isStreaming.value = false; _receivedFrame.value = null; _isFlashOn.value = false; _isHighQuality.value = false }
    override fun onCleared() { super.onCleared(); consumerJob?.cancel(); nearbyManager.stopAll() }

    // FUNCIONES DE PIZARRA CONGELADA (FOTO)
    fun clearAnnotatedImage() { _annotatedImage.value = null; sendCommand("CLEAR_ANNOTATION") }
    fun sendAnnotatedFrame(baseImage: android.graphics.Bitmap, lines: List<List<Pair<Float, Float>>>) {
        viewModelScope.launch(Dispatchers.IO) {
            val mutableBitmap = baseImage.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(mutableBitmap)
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.RED; strokeWidth = 8f; style = android.graphics.Paint.Style.STROKE; strokeJoin = android.graphics.Paint.Join.ROUND; strokeCap = android.graphics.Paint.Cap.ROUND; isAntiAlias = true }
            val width = mutableBitmap.width.toFloat(); val height = mutableBitmap.height.toFloat()
            for (line in lines) {
                if (line.size < 2) continue
                val path = android.graphics.Path()
                path.moveTo(line[0].first * width, line[0].second * height)
                for (i in 1 until line.size) path.lineTo(line[i].first * width, line[i].second * height)
                canvas.drawPath(path, paint)
            }
            val stream = java.io.ByteArrayOutputStream()
            mutableBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream)
            val byteArray = stream.toByteArray()
            val endpointId = _connectionState.value.connectedEndpointId
            if (endpointId != null) nearbyManager.sendData(endpointId, com.google.android.gms.nearby.connection.Payload.fromBytes(byteArray))
        }
    }

    // ==========================================
    // 🎙️ MOTOR DE AUDIO (WALKIE-TALKIE) SEGURO
    // ==========================================
    // Fila india para los paquetes de audio
    private val audioReceiveChannel = Channel<ByteArray>(capacity = 100, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var audioPlayerJob: Job? = null

    @Volatile private var isRecordingAudio = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val audioBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // Un único trabajador que lee la fila y reproduce
    private fun startAudioPlayer() {
        if (audioPlayerJob != null) return // Si ya está trabajando, no hacemos nada

        audioPlayerJob = viewModelScope.launch(Dispatchers.IO) {
            var track: AudioTrack? = null
            try {
                val outBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, audioFormat)
                val safeBufferSize = if (outBufferSize > 0) outBufferSize else 4096

                track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(audioFormat).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(safeBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                Log.d("BroCam_Audio", "🔊 Reproductor iniciado. Esperando paquetes...")

                // Este bucle infinito saca audios de la cola de a uno y los reproduce sin chocar
                for (audioData in audioReceiveChannel) {
                    track.write(audioData, 0, audioData.size)
                }
            } catch (e: Exception) {
                Log.e("BroCam_Audio", "❌ Error en AudioTrack: ${e.message}")
            } finally {
                track?.release()
            }
        }
    }

    fun startPushToTalk() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(getApplication(), android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e("BroCam_Audio", "❌ Permiso denegado")
            return
        }

        if (isRecordingAudio) return // Evita múltiples clicks rápidos
        isRecordingAudio = true

        viewModelScope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            var echoCanceler: AcousticEchoCanceler? = null
            var noiseSuppressor: NoiseSuppressor? = null

            try {
                // 🛠️ FIX 1: Usamos VOICE_COMMUNICATION y lo inicializamos UNA SOLA VEZ
                audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, channelConfig, audioFormat, audioBufferSize)

                // 🪄 MAGIA ANTI-ECO Y ANTI-RUIDO
                val audioSessionId = audioRecord.audioSessionId
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(audioSessionId)
                    echoCanceler?.enabled = true
                }
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(audioSessionId)
                    noiseSuppressor?.enabled = true
                }

                // 🛠️ FIX 2: Iniciamos la grabación UNA SOLA VEZ
                audioRecord.startRecording()
                Log.d("BroCam_Audio", "🔴 Grabando y enviando...")

                val chunkBuffer = ByteArray(1024)
                val header = "AUD:".toByteArray()

                while (isRecordingAudio) {
                    val readBytes = audioRecord.read(chunkBuffer, 0, chunkBuffer.size)
                    if (readBytes > 0) {
                        val payloadBytes = header + chunkBuffer.copyOfRange(0, readBytes)
                        val endpointId = _connectionState.value.connectedEndpointId
                        if (endpointId != null) {
                            nearbyManager.sendData(endpointId, Payload.fromBytes(payloadBytes))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("BroCam_Audio", "❌ Error grabando: ${e.message}")
            } finally {
                // 🛠️ FIX 3: Liberamos el micrófono Y los filtros de memoria
                audioRecord?.stop()
                audioRecord?.release()
                echoCanceler?.release()
                noiseSuppressor?.release()
                Log.d("BroCam_Audio", "⏹️ Grabación detenida.")
            }
        }
    }

    fun stopPushToTalk() {
        isRecordingAudio = false
    }
}