package com.example.brocam.core.streaming

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream

class FrameEncoder {
    // 1. Instanciar el stream una sola vez
    private val reusableStream = ByteArrayOutputStream()

    fun encodeFrame(bitmap: Bitmap): ByteArray? {
        return try {
            // 2. Limpiar el stream antes de usarlo para el nuevo frame
            reusableStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, StreamConfig.JPEG_QUALITY, reusableStream)
            reusableStream.toByteArray()
        } catch (e: Exception) {
            Log.e("BroCam", "Error encoding frame: ${e.message}")
            null
        }
    }
}