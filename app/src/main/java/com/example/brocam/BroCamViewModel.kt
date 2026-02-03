package com.example.brocam

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Heredamos de AndroidViewModel para tener acceso al "contexto" de la aplicación
class BroCamViewModel(application: Application) : AndroidViewModel(application) {

    // Instanciamos el Manager aquí. Sobrevivirá a cambios de pantalla.
    val nearbyManager = NearbyManager(application.applicationContext)

    // ESTADO: ¿Qué rol tengo ahora? (Lente, Control o Nada)
    private val _currentRole = MutableStateFlow<AppRole?>(null)
    val currentRole = _currentRole.asStateFlow()

    // ESTADO: Perfil seleccionado (Viajero/Estándar)
    var selectedProfile: UserProfile = UserProfile.STANDARD

    // Función para establecer el rol y limpiar si es necesario
    fun setRole(role: AppRole?) {
        _currentRole.value = role
        if (role == null) {
            // Si el rol es null (volvimos al inicio), desconectamos todo.
            disconnect()
        }
    }

    // Función segura para desconectar
    fun disconnect() {
        nearbyManager.stopAll()
    }

    // Cuando la app se cierra definitivamente (se mata el proceso), limpiamos.
    override fun onCleared() {
        super.onCleared()
        nearbyManager.stopAll()
    }
}