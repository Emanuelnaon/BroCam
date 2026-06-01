package com.example.brocam.core.camera

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import android.util.Log
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VideoDecoderManager {
   private var mediaCodec: MediaCodec? = null
   private var isConfigured = false
   private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) // Define a CoroutineScope

   // Arranca el motor y lo conecta a una pantalla física (Surface)
   fun startDecoder(surface: Surface, width: Int = 1280, height: Int = 720) {
      try {
         mediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)

         val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)

         // Si el hardware lo soporta, pedimos decodificación ultrarrápida
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
         }

         // El "0" final indica que esto es un Decodificador, no un Codificador
         mediaCodec?.configure(format, surface, null, 0)
         mediaCodec?.start()
         isConfigured = true
         Log.d("BroCam_Decoder", "✅ Motor Receptor H.265 encendido y conectado a la pantalla")

      } catch (e: Exception) {
         Log.e("BroCam_Decoder", "❌ Fallo al iniciar decodificador: ${e.message}")
      }
   }

   private val mutex = Mutex()
   // Esta función recibe los paquetes de red y los inyecta al chip gráfico
   fun decodeRawFrame(videoBytes: ByteArray) {
      scope.launch(Dispatchers.IO) { // Usamos la CoroutineScope definida en la clase
         mutex.withLock {
            if (!isConfigured || mediaCodec == null) return@withLock // Añadimos @withLock para el retorno no local

            try {
               // 1. Pedimos permiso para meter datos
               val inputBufferIndex = mediaCodec?.dequeueInputBuffer(10000) ?: -1
               if (inputBufferIndex >= 0) {
                  val inputBuffer = mediaCodec?.getInputBuffer(inputBufferIndex)
                  inputBuffer?.clear()
                  inputBuffer?.put(videoBytes)

                  // Metemos los bytes binarios
                  mediaCodec?.queueInputBuffer(
                     inputBufferIndex,
                     0,
                     videoBytes.size,
                     System.nanoTime() / 1000,
                     0
                  )
               }

               // 2. Le decimos al chip: "Si ya armaste la imagen, mandala a la pantalla"
               val info = MediaCodec.BufferInfo()
               var outputBufferIndex = mediaCodec?.dequeueOutputBuffer(info, 10000) ?: -1

               while (outputBufferIndex >= 0) {
                  // El 'true' es la magia: renderiza directamente en el Surface sin pasar por la RAM
                  mediaCodec?.releaseOutputBuffer(outputBufferIndex, true)
                  outputBufferIndex = mediaCodec?.dequeueOutputBuffer(info, 0) ?: -1
               }

            } catch (e: Exception) {
               Log.e("BroCam_Decoder", "Error decodificando frame: ${e.message}")
            }
         }
      }
   }

   fun stopDecoder() {
      try {
         mediaCodec?.stop()
         mediaCodec?.release()
         mediaCodec = null
      } catch (e: Exception) {
         Log.e("Decoder", "Error al destruir codec: ${e.message}")
      }
   }
}
