package com.example.brocam.core.nearby

data class ConnectionState(
    val isConnected: Boolean = false,
    val connectedEndpointId: String? = null,
    val message: String = "Disconnected"
)
