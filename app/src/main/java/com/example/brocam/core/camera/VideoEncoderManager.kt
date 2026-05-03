package com.example.brocam.core.camera

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.util.Log

class VideoEncoderManager(
   private val onVideoDataReady: (ByteArray) -> Unit
) {
   private var mediaCodec: MediaCodec? = null
   private var configBuffer: ByteArray? = null

   // ✅ AQUÍ VA LA COLA DE TICKETS (Adentro del Manager)
   private val availableInputBuffers = java.util.concurrent.ConcurrentLinkedQueue<Int>()

   fun startEncoder(width: Int = 1280, height: Int = 720) {
      try {
         mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)

         val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height).apply {
            // Pedimos bytes puros (YUV420) en lugar de una superficie
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
               setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
               setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            }
         }

         mediaCodec?.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
               // ✅ EL CHIP NOS DA UN TICKET DE ENTRADA LIBRE
               availableInputBuffers.offer(index)
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
               val outputBuffer = codec.getOutputBuffer(index)
               if (outputBuffer != null && info.size > 0) {
                  val chunk = ByteArray(info.size)
                  outputBuffer.position(info.offset)
                  outputBuffer.limit(info.offset + info.size)
                  outputBuffer.get(chunk)

                  if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                     configBuffer = chunk
                  } else {
                     val isKeyFrame = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                     if (isKeyFrame && configBuffer != null) {
                        val keyFrameWithConfig = configBuffer!! + chunk
                        onVideoDataReady(keyFrameWithConfig)
                     } else {
                        onVideoDataReady(chunk)
                     }
                  }
               }
               codec.releaseOutputBuffer(index, false)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
               Log.e("BroCam_Encoder", "Catástrofe de Códec: ${e.message}")
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
               Log.d("BroCam_Encoder", "Formato fijado: $format")
            }
         })

         mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
         mediaCodec?.start()
         Log.d("BroCam_Encoder", "Motor H.265 (Inyección de Bytes) rugiendo")

      } catch (e: Exception) {
         Log.e("BroCam_Encoder", "Fallo al iniciar: ${e.message}")
      }
   }

   // ✅ LA INYECCIÓN USA LOS TICKETS
   fun encodeRawFrame(imageBytes: ByteArray, timestampUs: Long) {
      try {
         val inputBufferIndex = availableInputBuffers.poll()

         if (inputBufferIndex != null) {
            val inputBuffer = mediaCodec?.getInputBuffer(inputBufferIndex)
            inputBuffer?.clear()
            inputBuffer?.put(imageBytes)

            mediaCodec?.queueInputBuffer(inputBufferIndex, 0, imageBytes.size, timestampUs, 0)
         }
      } catch (e: Exception) {
         Log.e("BroCam_Encoder", "Error inyectando frame: ${e.message}")
      }
   }

   fun stopEncoder() {
      try {
         mediaCodec?.stop()
         mediaCodec?.release()
         mediaCodec = null
         configBuffer = null
         availableInputBuffers.clear() // Limpiamos la cola al apagar
         Log.d("BroCam_Encoder", "Motor H.265 apagado")
      } catch (e: Exception) {
         Log.e("BroCam_Encoder", "Error al apagar: ${e.message}")
      }
   }
}