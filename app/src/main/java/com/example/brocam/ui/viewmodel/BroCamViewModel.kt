package com.example.brocam.ui.viewmodel

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.MediaRecorder
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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BroCamViewModel(application: Application) : AndroidViewModel(application) {
    private val nearbyManager = NearbyManager(application.applicationContext)
    private val historyManager = HistoryManager(application.applicationContext)

    private val _recentDevices = MutableStateFlow<List<RecentDevice>>(emptyList())
    val recentDevices = _recentDevices.asStateFlow()

    private val frameChannel = Channel<ByteArray>(capacity = 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var consumerJob: Job? = null

    private val _isSosMode = MutableStateFlow(false)
    val isSosMode = _isSosMode.asStateFlow()
    private val _currentRole = MutableStateFlow<AppRole?>(null)
    val currentRole = _currentRole.asStateFlow()
    private val _receivedFrame = MutableStateFlow<Bitmap?>(null)
    val receivedFrame = _receivedFrame.asStateFlow()
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()

    private var videoEncoder: com.example.brocam.core.camera.VideoEncoderManager? = null
    private var videoDecoder: com.example.brocam.core.camera.VideoDecoderManager? = null

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

    private val _remotePointer = MutableStateFlow<Pair<Float, Float>?>(null)
    val remotePointer: StateFlow<Pair<Float, Float>?> = _remotePointer

    private val _annotatedImage = MutableStateFlow<android.graphics.Bitmap?>(null)
    val annotatedImage: StateFlow<android.graphics.Bitmap?> = _annotatedImage

    private val _remoteLiveLine = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val remoteLiveLine: StateFlow<List<Pair<Float, Float>>> = _remoteLiveLine

    private val _remoteZoom = MutableStateFlow(1.0f)
    val remoteZoom = _remoteZoom.asStateFlow()
    private val _remoteExposure = MutableStateFlow(0.5f)
    val remoteExposure = _remoteExposure.asStateFlow()

    data class ConnectionState(val isConnected: Boolean = false, val message: String = "Desconectado", val connectedEndpointId: String? = null)
    private val _connectionState = MutableStateFlow(ConnectionState())
    // Estados del Temporizador
    private val _timerDuration = MutableStateFlow(0)
    val timerDuration = _timerDuration.asStateFlow()
    private val _currentCountdown = MutableStateFlow(0)
    val currentCountdown = _currentCountdown.asStateFlow()
    private var countdownJob: Job? = null

    fun cycleTimer() {
        val next = when (_timerDuration.value) { 0 -> 3; 3 -> 10; else -> 0 }
        _timerDuration.value = next
        sendCommand("SET_TIMER:$next")
    }

    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch(Dispatchers.Main) {
            for (i in seconds downTo 1) {
                _currentCountdown.value = i
                kotlinx.coroutines.delay(1000)
            }
            _currentCountdown.value = 0
            // Solo el Lente ejecuta el disparo físico al terminar la cuenta
            if (_currentRole.value == AppRole.LENTE) {
                _shutterEvent.send(true)
            }
        }
    }
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
                    catch (e: Exception) {}
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
                    val prefix = if (bytes.size >= 4) String(bytes, 0, 4) else ""
                    if (prefix == "VID:") {
                        val videoData = bytes.copyOfRange(4, bytes.size)
                        videoDecoder?.decodeRawFrame(videoData)
                        return@launch
                    } else if (prefix == "AUD:") {
                        startAudioPlayer()
                        val audioData = bytes.copyOfRange(4, bytes.size)
                        audioReceiveChannel.trySend(audioData)
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
                                        viewModelScope.launch { kotlinx.coroutines.delay(2000); if (_remotePointer.value == Pair(x, y)) _remotePointer.value = null }
                                    }
                                }
                            } else if (command.startsWith("LIVELINE:")) {
                                val coords = command.substringAfter("LIVELINE:").split(",")
                                val newLine = mutableListOf<Pair<Float, Float>>()
                                for (i in 0 until coords.size - 1 step 2) {
                                    val x = coords[i].toFloatOrNull()
                                    val y = coords[i+1].toFloatOrNull()
                                    if (x != null && y != null) newLine.add(Pair(x, y))
                                }
                                if (newLine.isNotEmpty()) {
                                    _remoteLiveLine.value = newLine
                                    viewModelScope.launch { kotlinx.coroutines.delay(3000); if (_remoteLiveLine.value == newLine) _remoteLiveLine.value = emptyList() }
                                }
                            } else if (command.startsWith("ZOOM:")) {
                                val zoomValue = command.substringAfter("ZOOM:").toFloatOrNull()
                                if (zoomValue != null) _remoteZoom.value = zoomValue
                            } else if (command.startsWith("EXP:")) {
                                val expValue = command.substringAfter("EXP:").toFloatOrNull()
                                if (expValue != null) _remoteExposure.value = expValue
                            } else if (command.startsWith("SET_TIMER:")) {
                                _timerDuration.value = command.substringAfter("SET_TIMER:").toIntOrNull() ?: 0
                            } else if (command.startsWith("START_TIMER:")) {
                                val duration = command.substringAfter("START_TIMER:").toIntOrNull() ?: 3
                                startCountdown(duration)
                            } else if (command.startsWith("ZOOM:")) {
                                _remoteZoom.value =
                                    command.substringAfter("ZOOM:").toFloatOrNull() ?: 1f

                            } else {
                                when (command) {
                                    "START_STREAM" -> _isStreaming.value = true
                                    "STOP_STREAM" -> { _isStreaming.value = false; if (_currentRole.value == AppRole.LENTE) stopH265Encoder() }
                                    "TAKE_PHOTO" -> _shutterEvent.send(true)
                                    // 🪄 FIX NOTIFICACIÓN: Disparamos un aviso a nivel de sistema operativo
                                    "PHOTO_OK" -> {
                                        _connectionState.value = _connectionState.value.copy(message = "📸 Foto guardada en Lente")
                                        android.widget.Toast.makeText(getApplication(), "📸 Evidencia guardada en el Lente", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                    "QUALITY_HD" -> {
                                        _isHighQuality.value = true
                                        purgeVideoMemory()
                                    }
                                    "QUALITY_SD" -> {
                                        _isHighQuality.value = false
                                        purgeVideoMemory()
                                    }
                                    "FLASH_ON" -> _isFlashOn.value = true
                                    "FLASH_OFF" -> _isFlashOn.value = false
                                    "SOS_ON" -> _isSosMode.value = true
                                    "SOS_OFF" -> _isSosMode.value = false
                                    "CAM_FRONT" -> { _isFrontCamera.value = true; _rotationDegrees.value = 270; purgeVideoMemory() }
                                    "CAM_BACK" -> { _isFrontCamera.value = false; _rotationDegrees.value = 90; purgeVideoMemory() }
                                    "CLEAR_ANNOTATION" -> _annotatedImage.value = null
                                }
                            }
                        }
                    } else {
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        withContext(Dispatchers.Main) { if (_currentRole.value == AppRole.LENTE) _annotatedImage.value = bitmap }
                    }
                }
            }
        }
        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {}
    }

    private fun purgeVideoMemory() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Detenemos motores
            if (_currentRole.value == AppRole.LENTE) stopH265Encoder()
            if (_currentRole.value == AppRole.CONTROL) stopH265Decoder()

            // 2. Limpiamos la tubería de fotogramas
            while(frameChannel.tryReceive().isSuccess) {}

            // 3. Pausa de seguridad para que el hardware suelte el buffer
            kotlinx.coroutines.delay(300)
        }
    }

    fun toggleSos() {
        val nv = !_isSosMode.value
        _isSosMode.value = nv
        sendCommand(if (nv) "SOS_ON" else "SOS_OFF")
        if (nv && _isFlashOn.value) { _isFlashOn.value = false; sendCommand("FLASH_OFF") }
    }

    fun feedEncoder(rawBytes: ByteArray, timestampUs: Long, width: Int, height: Int) {
        if (videoEncoder == null) {
            videoEncoder = com.example.brocam.core.camera.VideoEncoderManager(
                onVideoDataReady = { chunk ->
                    if (_isStreaming.value) {
                        val prefix = "VID:".toByteArray()
                        enqueueFrame(prefix + chunk)
                    }
                }
            )
            videoEncoder?.startEncoder(width, height)
        }
        videoEncoder?.encodeRawFrame(rawBytes, timestampUs)
    }

    fun startH265Decoder(surface: android.view.Surface) {
        if (videoDecoder == null) videoDecoder = com.example.brocam.core.camera.VideoDecoderManager()

        // 🪄 FIX DIMENSIONES: Le decimos al decodificador qué tamaño exacto esperar
        val w = if (_isHighQuality.value) 1280 else 640
        val h = if (_isHighQuality.value) 960 else 480

        videoDecoder?.startDecoder(surface, w, h)
    }

    fun stopH265Decoder() {
        videoDecoder?.stopDecoder()
        videoDecoder = null
    }

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
                            _isStreaming.value = true
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

    fun toggleQuality() {
        val nv = !_isHighQuality.value

        viewModelScope.launch {
            // 1. APAGAMOS EL GRIFO DE VIDEO PRIMERO
            val wasStreaming = _isStreaming.value
            if (wasStreaming) {
                _isStreaming.value = false // Corta el envío de bytes local
                sendCommand("STOP_STREAM") // Le dice al otro celular que deje de renderizar
                purgeVideoMemory()
                kotlinx.coroutines.delay(300) // Damos 300ms para que las tuberías de red se vacíen
            }

            // 2. CAMBIAMOS LA CALIDAD Y AVISAMOS
            _isHighQuality.value = nv
            sendCommand(if (nv) "QUALITY_HD" else "QUALITY_SD")

            // 3. DAMOS TIEMPO A LA UI PARA RECONSTRUIR EL TEXTUREVIEW (El key() de Compose)
            kotlinx.coroutines.delay(500)

            // 4. VOLVEMOS A ABRIR EL GRIFO
            if (wasStreaming) {
                _isStreaming.value = true
                sendCommand("START_STREAM")
            }
        }
    }
    fun toggleFlash() { val nv = !_isFlashOn.value; _isFlashOn.value = nv; sendCommand(if (nv) "FLASH_ON" else "FLASH_OFF") }
    fun toggleCamera() {
        val nv = !_isFrontCamera.value

        viewModelScope.launch {
            val wasStreaming = _isStreaming.value
            if (wasStreaming) {
                _isStreaming.value = false
                sendCommand("STOP_STREAM")
                purgeVideoMemory()
                kotlinx.coroutines.delay(300)
            }

            _isFrontCamera.value = nv
            sendCommand(if (nv) "CAM_FRONT" else "CAM_BACK")

            // 🪄 FIX FILTRO VERDE: Le damos 800ms al hardware del Lente para que
            // la cámara enfoque y regule la luz antes de enviar el primer frame.
            kotlinx.coroutines.delay(800)

            if (wasStreaming) {
                _isStreaming.value = true
                sendCommand("START_STREAM")
            }
        }
    }
    fun triggerRemotePhoto() {
        val duration = _timerDuration.value
        if (duration > 0) {
            sendCommand("START_TIMER:$duration")
            startCountdown(duration)
        } else {
            sendCommand("TAKE_PHOTO")
        }
    }

    fun takeLocalPhoto() {
        val duration = _timerDuration.value
        if (duration > 0) {
            sendCommand("START_TIMER:$duration") // Avisa al Control que inició la cuenta
            startCountdown(duration)
        } else {
            viewModelScope.launch { _shutterEvent.send(true) }
        }
    }

    fun setRemoteExposure(value: Float) { sendCommand("EXP:$value") }

    fun setRemoteZoom(zoomRatio: Float) {
        _remoteZoom.value = zoomRatio
        sendCommand("ZOOM:$zoomRatio")
    }
    fun sendPointer(x: Float, y: Float) { sendCommand("POINTER:$x,$y") }
    fun sendLiveLine(line: List<Pair<Float, Float>>) {
        if (line.isEmpty()) return
        val coords = line.joinToString(",") { "${it.first},${it.second}" }
        sendCommand("LIVELINE:$coords")
    }
    fun sendCommand(cmd: String) { viewModelScope.launch(Dispatchers.IO) { val endpointId = _connectionState.value.connectedEndpointId; if (endpointId != null) nearbyManager.sendData(endpointId, Payload.fromBytes(cmd.toByteArray())) } }

    fun disconnect() {
        nearbyManager.stopAll()
        purgeVideoMemory()
        resetState()
        _currentRole.value = null
    }

    private fun resetState() { _connectionState.value = ConnectionState(isConnected = false, message = "Desconectado", connectedEndpointId = null); _isStreaming.value = false; _receivedFrame.value = null; _isFlashOn.value = false; _isHighQuality.value = false }
    override fun onCleared() { super.onCleared(); consumerJob?.cancel(); nearbyManager.stopAll() }

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

    private val audioReceiveChannel = Channel<ByteArray>(capacity = 100, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var audioPlayerJob: Job? = null
    @Volatile private var isRecordingAudio = false
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val audioBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private fun startAudioPlayer() {
        if (audioPlayerJob != null) return
        audioPlayerJob = viewModelScope.launch(Dispatchers.IO) {
            var track: AudioTrack? = null
            try {
                val outBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, audioFormat)
                val safeBufferSize = if (outBufferSize > 0) outBufferSize else 4096
                track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(AudioFormat.Builder().setEncoding(audioFormat).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(safeBufferSize).setTransferMode(AudioTrack.MODE_STREAM).build()
                track.play()
                for (audioData in audioReceiveChannel) track.write(audioData, 0, audioData.size)
            } catch (e: Exception) {} finally { track?.release() }
        }
    }

    fun startPushToTalk() {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(getApplication(), android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        if (isRecordingAudio) return
        isRecordingAudio = true
        viewModelScope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            var echoCanceler: AcousticEchoCanceler? = null
            var noiseSuppressor: NoiseSuppressor? = null
            try {
                audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, channelConfig, audioFormat, audioBufferSize)
                val audioSessionId = audioRecord.audioSessionId
                if (AcousticEchoCanceler.isAvailable()) { echoCanceler = AcousticEchoCanceler.create(audioSessionId); echoCanceler?.enabled = true }
                if (NoiseSuppressor.isAvailable()) { noiseSuppressor = NoiseSuppressor.create(audioSessionId); noiseSuppressor?.enabled = true }
                audioRecord.startRecording()
                val chunkBuffer = ByteArray(1024)
                val header = "AUD:".toByteArray()
                while (isRecordingAudio) {
                    val readBytes = audioRecord.read(chunkBuffer, 0, chunkBuffer.size)
                    if (readBytes > 0) {
                        val payloadBytes = header + chunkBuffer.copyOfRange(0, readBytes)
                        val endpointId = _connectionState.value.connectedEndpointId
                        if (endpointId != null) nearbyManager.sendData(endpointId, Payload.fromBytes(payloadBytes))
                    }
                }
            } catch (e: Exception) {} finally { audioRecord?.stop(); audioRecord?.release(); echoCanceler?.release(); noiseSuppressor?.release() }
        }
    }

    fun stopPushToTalk() { isRecordingAudio = false }

    fun drawMetadataOnBitmap(originalBitmap: android.graphics.Bitmap, location: String = "GPS: No disponible"): android.graphics.Bitmap {
        val bitmap = originalBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = bitmap.width / 35f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        paint.setShadowLayer(10f, 0f, 0f, android.graphics.Color.BLACK)
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val margin = 40f
        val lineSpacing = paint.textSize * 1.2f
        canvas.drawText("BroCam - Evidencia Forense", margin, bitmap.height - (lineSpacing * 2) - margin, paint)
        canvas.drawText("Fecha: $date", margin, bitmap.height - lineSpacing - margin, paint)
        canvas.drawText(location, margin, bitmap.height - margin, paint)
        return bitmap
    }

    fun fetchCurrentLocation(onLocationResult: (String) -> Unit) {
        val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(getApplication<Application>())
        if (androidx.core.app.ActivityCompat.checkSelfPermission(getApplication(), android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) { onLocationResult("GPS: Sin permiso"); return }
        val token = com.google.android.gms.tasks.CancellationTokenSource().token
        fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, token)
            .addOnSuccessListener { loc -> if (loc != null) onLocationResult("Lat: ${String.format(java.util.Locale.US, "%.6f", loc.latitude)} | Lon: ${String.format(java.util.Locale.US, "%.6f", loc.longitude)}") else onLocationResult("GPS: Localizando...") }
            .addOnFailureListener { onLocationResult("GPS: Error de sensor") }
    }

    fun startH265Encoder() {
        if (videoEncoder != null) return
        videoEncoder = com.example.brocam.core.camera.VideoEncoderManager(onVideoDataReady = { chunk -> if (_isStreaming.value) enqueueFrame("VID:".toByteArray() + chunk) })

        // 🪄 FIX DIMENSIONES: Le decimos al codificador qué tamaño exacto enviar
        val w = if (_isHighQuality.value) 1280 else 640
        val h = if (_isHighQuality.value) 960 else 480

        videoEncoder?.startEncoder(w, h)
    }

    fun stopH265Encoder() { videoEncoder?.stopEncoder(); videoEncoder = null }
}