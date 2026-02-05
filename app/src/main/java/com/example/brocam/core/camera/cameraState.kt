package com.example.brocam.core.camera

import android.graphics.Bitmap

data class CameraState(
    val capturedImage: Bitmap? = null,
    val isStreaming: Boolean = false
)
