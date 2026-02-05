package com.example.brocam.core.nearby

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NearbyController(private val context: Context) {
    private val client = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.example.brocam.SERVICE_ID"
    private val STRATEGY = Strategy.P2P_STAR

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState = _connectionState.asStateFlow()

    fun startAdvertising(payloadCallback: PayloadCallback) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(
            "Lente_BroCam",
            SERVICE_ID,
            object : ConnectionLifecycleCallback() {
                override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                    acceptConnection(id, payloadCallback)
                }

                override fun onConnectionResult(id: String, result: ConnectionResolution) {
                    if (result.status.isSuccess) {
                        _connectionState.value = ConnectionState(isConnected = true, connectedEndpointId = id, message = "Connected")
                    } else {
                        _connectionState.value = ConnectionState(message = "Connection Failed")
                    }
                }

                override fun onDisconnected(id: String) {
                    _connectionState.value = ConnectionState()
                }
            },
            options
        ).addOnFailureListener {
            _connectionState.value = ConnectionState(message = "Advertising Failed")
        }
    }

    fun startDiscovery(payloadCallback: PayloadCallback) {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(SERVICE_ID, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                requestConnection(id, payloadCallback)
            }

            override fun onEndpointLost(id: String) { }
        }, options).addOnFailureListener {
            _connectionState.value = ConnectionState(message = "Discovery Failed")
        }
    }

    private fun acceptConnection(id: String, payloadCallback: PayloadCallback) {
        client.acceptConnection(id, payloadCallback)
    }

    private fun requestConnection(id: String, payloadCallback: PayloadCallback) {
        client.requestConnection("Control_Remoto", id, object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                acceptConnection(id, payloadCallback)
            }

            override fun onConnectionResult(id: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    _connectionState.value = ConnectionState(isConnected = true, connectedEndpointId = id, message = "Connected")
                } else {
                    _connectionState.value = ConnectionState(message = "Connection Failed")
                }
            }

            override fun onDisconnected(id: String) {
                _connectionState.value = ConnectionState()
            }
        })
    }

    fun sendData(id: String, payload: Payload) {
        client.sendPayload(id, payload)
    }

    fun stopAll() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        _connectionState.value = ConnectionState()
    }
}
