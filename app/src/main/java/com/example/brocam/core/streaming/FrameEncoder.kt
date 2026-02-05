package com.example.brocam.core.streaming

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream

class FrameEncoder {

    fun encodeFrame(bitmap: Bitmap): ByteArray? {
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, StreamConfig.JPEG_QUALITY, stream)
            val data = stream.toByteArray()
            stream.close()
            data
        } catch (e: Exception) {
            Log.e("BroCam", "Error encoding frame: ${e.message}")
            null
        }
    }
}
