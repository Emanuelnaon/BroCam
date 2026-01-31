package com.example.brocam

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

class NearbyManager(private val context: Context) {
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.example.brocam.SERVICE_ID"

    fun startAdvertising(callback: ConnectionLifecycleCallback) {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        connectionsClient.startAdvertising("Lente_BroCam", SERVICE_ID, callback, options)
    }

    fun startDiscovery(callback: EndpointDiscoveryCallback) {
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        connectionsClient.startDiscovery(SERVICE_ID, callback, options)
    }

    fun requestConnection(endpointId: String, callback: ConnectionLifecycleCallback) {
        connectionsClient.requestConnection("Control_BroCam", endpointId, callback)
    }

    // Es vital pasar el payloadCallback aquí para manejar los datos que llegan
    fun acceptConnection(endpointId: String, payloadCallback: PayloadCallback) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
    }

    fun sendData(endpointId: String, payload: Payload) {
        connectionsClient.sendPayload(endpointId, payload)
    }

    fun stopAll() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
    }
}