
package com.example.brocam.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Nuestro modelo de datos
data class RecentDevice(
    val endpointId: String,
    val deviceName: String,
    val lastConnectionTimestamp: Long
) {
    // Función para mostrar la fecha bonita en la UI
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        return sdf.format(Date(lastConnectionTimestamp))
    }
}

class HistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("BroCamHistory", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val HISTORY_KEY = "recent_devices"

    // Leer la lista guardada
    fun getRecentDevices(): List<RecentDevice> {
        val json = prefs.getString(HISTORY_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<RecentDevice>>() {}.type
        return gson.fromJson(json, type)
    }

    // Guardar un nuevo dispositivo (o actualizar su fecha si ya existe)
    fun saveDevice(endpointId: String, deviceName: String) {
        val currentList = getRecentDevices().toMutableList()

        // Si ya existe, lo borramos para volver a agregarlo arriba con fecha nueva
        currentList.removeAll { it.endpointId == endpointId }

        val newDevice = RecentDevice(
            endpointId = endpointId,
            deviceName = deviceName,
            lastConnectionTimestamp = System.currentTimeMillis()
        )

        currentList.add(0, newDevice) // Agregar al principio (más reciente)

        // Guardamos máximo los 5 más recientes para no saturar
        val limitedList = currentList.take(5)

        prefs.edit().putString(HISTORY_KEY, gson.toJson(limitedList)).apply()
    }
}